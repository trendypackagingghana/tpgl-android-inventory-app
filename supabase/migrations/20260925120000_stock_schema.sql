-- TPGL Stock: products and their movement history.
-- No auth: the app talks to the database with the anon/publishable key.

create table public.products (
    id            bigint generated always as identity primary key,
    category      text   not null,
    name          text   not null,
    size          text   not null default '',
    unit          text   not null default 'pcs' check (unit in ('pcs', 'bags')),
    pcs_per_bag   int    not null default 0,
    quantity      bigint not null default 0 check (quantity >= 0),
    reorder_level bigint not null default 0,
    updated_at    timestamptz not null default now(),
    unique (category, size, name)
);

create table public.movements (
    id            bigint generated always as identity primary key,
    product_id    bigint not null references public.products (id) on delete cascade,
    product_label text   not null,
    category      text   not null,
    unit          text   not null,
    type          text   not null check (type in ('IN', 'OUT', 'COUNT', 'OPENING')),
    previous_qty  bigint not null,
    new_qty       bigint not null,
    source        text   not null,
    note          text   not null default '',
    batch_id      uuid   not null,
    occurred_at   timestamptz not null
);

create index movements_product_id_idx on public.movements (product_id);
create index movements_occurred_at_idx on public.movements (occurred_at);
create index movements_batch_id_idx on public.movements (batch_id);

-- Open access for the app's key. Anyone holding the key can read and write.
alter table public.products enable row level security;
alter table public.movements enable row level security;

create policy "app full access" on public.products for all to anon, authenticated using (true) with check (true);
create policy "app full access" on public.movements for all to anon, authenticated using (true) with check (true);

-- Creates products and applies stock changes in one transaction, logging every movement.
--
-- p_new_products: [{category, name, size, unit, pcs_per_bag, quantity, reorder_level, label, note}]
--   Each is inserted with an OPENING movement.
-- p_changes: [{product_id, mode: ADD|REMOVE|SET, amount, note, label, timestamp (epoch ms, optional)}]
--   A negative product_id -n refers to the n-th entry of p_new_products.
--   Changes that leave the quantity unchanged are skipped.
--
-- Returns {"applied": <changes that altered stock>, "new_ids": [<ids of created products>]}.
create or replace function public.apply_batch(
    p_new_products jsonb,
    p_changes      jsonb,
    p_source       text,
    p_batch_note   text
) returns jsonb
language plpgsql
set search_path = public
as $$
declare
    v_now     timestamptz := now();
    v_batch   uuid := gen_random_uuid();
    v_ids     bigint[] := '{}';
    v_applied int := 0;
    np        jsonb;
    ch        jsonb;
    p         products%rowtype;
    v_id      bigint;
    v_amount  bigint;
    v_new     bigint;
begin
    for np in select * from jsonb_array_elements(coalesce(p_new_products, '[]'::jsonb)) loop
        insert into products (category, name, size, unit, pcs_per_bag, quantity, reorder_level, updated_at)
        values (
            np ->> 'category',
            np ->> 'name',
            coalesce(np ->> 'size', ''),
            coalesce(np ->> 'unit', 'pcs'),
            coalesce((np ->> 'pcs_per_bag')::int, 0),
            greatest(coalesce((np ->> 'quantity')::bigint, 0), 0),
            coalesce((np ->> 'reorder_level')::bigint, 0),
            v_now
        )
        returning * into p;
        v_ids := v_ids || p.id;

        insert into movements (product_id, product_label, category, unit, type, previous_qty, new_qty,
                               source, note, batch_id, occurred_at)
        values (p.id, np ->> 'label', p.category, p.unit, 'OPENING', 0, p.quantity,
                p_source, coalesce(nullif(np ->> 'note', ''), 'New product'), gen_random_uuid(), v_now);
    end loop;

    for ch in select * from jsonb_array_elements(coalesce(p_changes, '[]'::jsonb)) loop
        v_id := (ch ->> 'product_id')::bigint;
        if v_id < 0 then
            v_id := v_ids[-v_id];
        end if;

        select * into p from products where id = v_id for update;
        if not found then
            continue;
        end if;

        v_amount := (ch ->> 'amount')::bigint;
        v_new := case ch ->> 'mode'
            when 'ADD' then p.quantity + v_amount
            when 'REMOVE' then greatest(p.quantity - v_amount, 0)
            else greatest(v_amount, 0)
        end;
        if v_new = p.quantity then
            continue;
        end if;

        update products set quantity = v_new, updated_at = v_now where id = p.id;

        insert into movements (product_id, product_label, category, unit, type, previous_qty, new_qty,
                               source, note, batch_id, occurred_at)
        values (
            p.id,
            ch ->> 'label',
            p.category,
            p.unit,
            case ch ->> 'mode' when 'ADD' then 'IN' when 'REMOVE' then 'OUT' else 'COUNT' end,
            p.quantity,
            v_new,
            p_source,
            concat_ws(' · ', nullif(ch ->> 'note', ''), nullif(p_batch_note, '')),
            v_batch,
            coalesce(to_timestamp((ch ->> 'timestamp')::bigint / 1000.0), v_now)
        );
        v_applied := v_applied + 1;
    end loop;

    return jsonb_build_object('applied', v_applied, 'new_ids', to_jsonb(v_ids));
end;
$$;

-- Updates product details. Quantity only changes through apply_batch.
create or replace function public.update_product(
    p_id            bigint,
    p_category      text,
    p_name          text,
    p_size          text,
    p_unit          text,
    p_pcs_per_bag   int,
    p_reorder_level bigint
) returns void
language sql
set search_path = public
as $$
    update products
    set category = p_category, name = p_name, size = p_size, unit = p_unit,
        pcs_per_bag = p_pcs_per_bag, reorder_level = p_reorder_level, updated_at = now()
    where id = p_id;
$$;

grant execute on function public.apply_batch(jsonb, jsonb, text, text) to anon, authenticated;
grant execute on function public.update_product(bigint, text, text, text, text, int, bigint) to anon, authenticated;
