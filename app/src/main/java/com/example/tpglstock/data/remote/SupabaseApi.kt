package com.example.tpglstock.data.remote

import com.example.tpglstock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Postgres error from Supabase, e.g. code "23505" for a unique violation. */
class SupabaseException(val code: String?, message: String) : IOException(message)

/** Minimal PostgREST client for the project's REST endpoint. */
class SupabaseApi(
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val key: String = BuildConfig.SUPABASE_KEY,
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && key.isNotBlank()

    /** Reads every row of [table], paging past the server's row limit. */
    suspend fun selectAll(table: String, order: String): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        while (true) {
            val page = JSONArray(
                request(
                    "GET",
                    "rest/v1/$table?select=*&order=${enc(order)}",
                    headers = mapOf("Range-Unit" to "items", "Range" to "${rows.size}-${rows.size + PAGE - 1}"),
                ),
            )
            for (i in 0 until page.length()) rows += page.getJSONObject(i)
            if (page.length() < PAGE) return rows
        }
    }

    suspend fun rpc(function: String, args: JSONObject): String =
        request("POST", "rest/v1/rpc/$function", body = args.toString())

    suspend fun delete(table: String, id: Long) {
        request("DELETE", "rest/v1/$table?id=eq.$id")
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        if (!configured) throw SupabaseException(null, "Supabase is not configured. Add SUPABASE_URL and SUPABASE_KEY to secrets.properties.")
        val conn = URL("$baseUrl/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("apikey", key)
            // Legacy anon keys are JWTs and also go in Authorization; new sb_publishable_ keys must not.
            if (!key.startsWith("sb_")) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach(conn::setRequestProperty)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code >= 400) {
                val err = runCatching { JSONObject(text) }.getOrNull()
                throw SupabaseException(err?.optString("code"), err?.optString("message")?.ifBlank { null } ?: "HTTP $code")
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val PAGE = 1000
    }
}
