package com.example.tpglstock.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tpglstock.ui.addstock.AddStockScreen
import com.example.tpglstock.ui.dashboard.DashboardScreen
import com.example.tpglstock.ui.history.HistoryScreen
import com.example.tpglstock.ui.inventory.InventoryScreen
import com.example.tpglstock.ui.product.ProductDetailScreen
import com.example.tpglstock.ui.product.ProductEditScreen
import com.example.tpglstock.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val INVENTORY = "inventory?filter={filter}"
    const val ADD = "add?productId={productId}&mode={mode}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRODUCT = "product/{id}"
    const val PRODUCT_EDIT = "product/edit?id={id}"

    fun inventory(filter: String = "") = "inventory?filter=$filter"
    fun add(productId: Long = -1, mode: String = "") = "add?productId=$productId&mode=$mode"
    fun product(id: Long) = "product/$id"
    fun productEdit(id: Long = -1) = "product/edit?id=$id"
}

private data class TopTab(
    val route: String,
    val navigate: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    TopTab(Routes.DASHBOARD, Routes.DASHBOARD, "Dashboard", Icons.Outlined.Dashboard, Icons.Rounded.Dashboard),
    TopTab(Routes.INVENTORY, Routes.inventory(), "Inventory", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
    TopTab(Routes.ADD, Routes.add(), "Add Stock", Icons.Outlined.AddCircleOutline, Icons.Rounded.AddCircle),
    TopTab(Routes.HISTORY, Routes.HISTORY, "History", Icons.Outlined.History, Icons.Rounded.History),
)

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    fun goTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = tab.route == currentRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) goTab(tab.navigate) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenInventory = { filter -> goTab(Routes.inventory(filter)) },
                    onOpenProduct = { navController.navigate(Routes.product(it)) },
                )
            }
            composable(
                Routes.INVENTORY,
                arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "" }),
            ) {
                InventoryScreen(
                    onOpenProduct = { navController.navigate(Routes.product(it)) },
                    onNewProduct = { navController.navigate(Routes.productEdit()) },
                )
            }
            composable(
                Routes.ADD,
                arguments = listOf(
                    navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                AddStockScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenProduct = { navController.navigate(Routes.product(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ProductDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.productEdit(id)) },
                )
            }
            composable(
                Routes.PRODUCT_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) {
                ProductEditScreen(
                    onDone = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack(Routes.PRODUCT, inclusive = true)
                    },
                )
            }
        }
    }
}
