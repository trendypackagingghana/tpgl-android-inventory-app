package com.example.tpglstock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tpglstock.TPGLApp
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.shortTitle
import com.example.tpglstock.ui.addstock.AssistantScreen
import com.example.tpglstock.ui.addstock.LogStockScreen
import com.example.tpglstock.ui.components.LocalToast
import com.example.tpglstock.ui.components.SheetHandle
import com.example.tpglstock.ui.components.Swatch
import com.example.tpglstock.ui.components.Toast
import com.example.tpglstock.ui.components.ToastState
import com.example.tpglstock.ui.dashboard.DashboardScreen
import com.example.tpglstock.ui.history.HistoryScreen
import com.example.tpglstock.ui.inventory.InventoryScreen
import com.example.tpglstock.ui.product.ProductDetailScreen
import com.example.tpglstock.ui.product.ProductEditScreen
import com.example.tpglstock.ui.settings.SettingsScreen
import com.example.tpglstock.ui.theme.StockTheme
import kotlinx.coroutines.delay

object Routes {
    const val TODAY = "today"
    const val STOCK = "stock?filter={filter}"
    const val ACTIVITY = "activity"
    const val YOU = "you"
    const val PRODUCT = "product/{id}"
    const val PRODUCT_EDIT = "product/edit?id={id}"
    const val LOG = "log?productId={productId}&mode={mode}"
    const val ASSISTANT = "assistant"

    fun stock(filter: String = "") = "stock?filter=$filter"
    fun product(id: Long) = "product/$id"
    fun productEdit(id: Long = -1) = "product/edit?id=$id"
    fun log(productId: Long = -1, mode: ChangeMode = ChangeMode.ADD) = "log?productId=$productId&mode=${mode.name}"
}

private data class Tab(val route: String, val navigate: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val leftTabs = listOf(
    Tab(Routes.TODAY, Routes.TODAY, "Today", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.STOCK, Routes.stock(), "Stock", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
)
private val rightTabs = listOf(
    Tab(Routes.ACTIVITY, Routes.ACTIVITY, "Activity", Icons.Outlined.History, Icons.Rounded.History),
    Tab(Routes.YOU, Routes.YOU, "You", Icons.Outlined.Person, Icons.Rounded.Person),
)

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = (leftTabs + rightTabs).any { it.route == currentRoute }
    val toast = remember { ToastState() }
    var sheetOpen by remember { mutableStateOf(false) }
    val c = StockTheme.colors

    fun goTab(route: String, restore: Boolean = true) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = restore
        }
    }

    fun openProduct(id: Long) = navController.navigate(Routes.product(id))
    fun log(id: Long = -1, mode: ChangeMode = ChangeMode.ADD) {
        sheetOpen = false
        navController.navigate(Routes.log(id, mode))
    }

    CompositionLocalProvider(LocalToast provides toast) {
        Box(Modifier.fillMaxSize().background(c.paper)) {
            Column(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.TODAY,
                    modifier = Modifier.weight(1f),
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable(Routes.TODAY) {
                        DashboardScreen(
                            onOpenYou = { goTab(Routes.YOU) },
                            onSeeAttention = { goTab(Routes.stock("attention"), restore = false) },
                            onOpenProduct = ::openProduct,
                            onRestock = { log(it, ChangeMode.ADD) },
                        )
                    }
                    composable(
                        Routes.STOCK,
                        arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "" }),
                    ) {
                        InventoryScreen(
                            onOpenProduct = ::openProduct,
                            onNewProduct = { navController.navigate(Routes.productEdit()) },
                        )
                    }
                    composable(Routes.ACTIVITY) { HistoryScreen(onOpenProduct = ::openProduct) }
                    composable(Routes.YOU) { SettingsScreen() }
                    composable(
                        Routes.LOG,
                        arguments = listOf(
                            navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("mode") { type = NavType.StringType; defaultValue = ChangeMode.ADD.name },
                        ),
                    ) {
                        LogStockScreen(
                            onClose = { navController.popBackStack() },
                            onSaved = { id ->
                                val previous = navController.previousBackStackEntry
                                if (previous?.destination?.route == Routes.PRODUCT && previous.arguments?.getLong("id") == id) {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(Routes.product(id)) { popUpTo(Routes.LOG) { inclusive = true } }
                                }
                            },
                        )
                    }
                    composable(Routes.ASSISTANT) {
                        AssistantScreen(onBack = { navController.popBackStack() }, onOpenSettings = { goTab(Routes.YOU) })
                    }
                    composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ProductDetailScreen(
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate(Routes.productEdit(id)) },
                            onLog = { id, mode -> log(id, mode) },
                        )
                    }
                    composable(
                        Routes.PRODUCT_EDIT,
                        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                    ) {
                        ProductEditScreen(
                            onDone = { navController.popBackStack() },
                            onDeleted = { navController.popBackStack(Routes.PRODUCT, inclusive = true) },
                        )
                    }
                }
                if (showBar) BottomBar(currentRoute, onTab = { if (it.route != currentRoute) goTab(it.navigate) }, onAdd = { sheetOpen = true })
            }

            val message = toast.current
            LaunchedEffect(message) {
                if (message != null) {
                    delay(if (message.error) 4_000 else 2_400)
                    toast.dismiss(message)
                }
            }
            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (showBar) 84.dp else 16.dp),
            ) {
                var shown by remember { mutableStateOf(message) }
                if (message != null) shown = message
                shown?.let { Toast(it) }
            }
        }

        if (sheetOpen) {
            QuickLogSheet(
                onDismiss = { sheetOpen = false },
                onAssistant = {
                    sheetOpen = false
                    navController.navigate(Routes.ASSISTANT)
                },
                onManual = { log() },
                onProduct = { log(it, ChangeMode.ADD) },
            )
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    val c = StockTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            leftTabs.forEach { NavItem(it, it.route == currentRoute, Modifier.weight(1f)) { onTab(it) } }
            Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = (-22).dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = c.accent, spotColor = c.accent)
                        .size(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.accent)
                        .clickable(role = Role.Button, onClickLabel = "Log stock", onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, contentDescription = "Log stock", tint = c.onAccent, modifier = Modifier.size(30.dp)) }
            }
            rightTabs.forEach { NavItem(it, it.route == currentRoute, Modifier.weight(1f)) { onTab(it) } }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = StockTheme.colors
    val fg = if (selected) c.ink else c.faint
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = fg,
        )
    }
}

/** The + sheet: paste an update for the assistant, log by product, or jump to a recently logged product. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogSheet(onDismiss: () -> Unit, onAssistant: () -> Unit, onManual: () -> Unit, onProduct: (Long) -> Unit) {
    val container = (LocalContext.current.applicationContext as TPGLApp).container
    val name by container.settings.userName.collectAsStateWithLifecycle()
    val products by container.repository.products.collectAsStateWithLifecycle(emptyList())
    val movements by container.repository.movements.collectAsStateWithLifecycle(emptyList())
    val recent = remember(products, movements) {
        val byId = products.associateBy { it.id }
        movements.asSequence()
            .filter { it.type != MovementType.OPENING }
            .map { it.productId }
            .distinct()
            .mapNotNull { byId[it] }
            .take(4)
            .toList()
    }
    val c = StockTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.paper,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (name.isBlank()) "What happened?" else "What happened, $name?",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            SheetOption(
                icon = Icons.Rounded.AutoAwesome,
                title = "Paste a WhatsApp update",
                subtitle = "The assistant suggests the changes",
                dark = true,
                onClick = onAssistant,
            )
            SheetOption(
                icon = Icons.Rounded.EditNote,
                title = "Log by product",
                subtitle = "Stock in, stock out or a count",
                dark = false,
                onClick = onManual,
            )
            if (recent.isNotEmpty()) {
                Text("Logged recently", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { p ->
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(c.surface)
                                .clickable { onProduct(p.id) }
                                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Swatch(p, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(p.shortTitle, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, dark: Boolean, onClick: () -> Unit) {
    val c = StockTheme.colors
    val fg = if (dark) c.onHero else c.ink
    val sub = if (dark) c.heroMuted else c.muted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) c.hero else c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (dark) c.accent else c.track),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (dark) c.onAccent else c.ink, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = fg)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = sub)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = if (dark) fg else c.muted, modifier = Modifier.size(22.dp))
    }
}
