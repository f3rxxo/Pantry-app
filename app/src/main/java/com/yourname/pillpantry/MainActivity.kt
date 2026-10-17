package com.yourname.pillpantry

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourname.pillpantry.data.PillPantryRepository
import com.yourname.pillpantry.notifications.NotificationHelper
import com.yourname.pillpantry.ui.pantry.PantryScreen
import com.yourname.pillpantry.ui.shoppinglist.ShoppingListScreen
import com.yourname.pillpantry.ui.theme.PillPantryTheme

private sealed class Tab(val route: String, val label: String) {
    data object Pantry : Tab("pantry", "Pantry")
    data object ShoppingList : Tab("shopping_list", "Shopping List")
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { PillPantryRepository.from(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        com.yourname.pillpantry.work.PortionDecayWorker.schedule(this)
        com.yourname.pillpantry.work.PillReminderWorker.schedule(this)

        setContent {
            PillPantryTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* no-op: refill alerts simply won't show if denied */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(Unit) {
                    // Catch up any missed daily portion decrements (see
                    // PillPantryRepository.applyMissedPortionDecrements for
                    // why this runs here instead of relying solely on a
                    // background job to fire at exactly 3am).
                    repository.applyMissedPortionDecrements(context = this@MainActivity)
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    PillPantryApp(repository)
                }
            }
        }
    }
}

@Composable
private fun PillPantryApp(repository: PillPantryRepository) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Pantry, Tab.ShoppingList)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (tab) {
                                Tab.Pantry ->
                                    if (selected) Icons.Filled.Inventory2
                                    else Icons.Outlined.Inventory2

                                Tab.ShoppingList ->
                                    if (selected) Icons.Filled.ShoppingCart
                                    else Icons.Outlined.ShoppingCart
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(tab.label)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent // Use primaryContainer for the pill style
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Pantry.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(Tab.Pantry.route) {
                PantryScreen(repository = repository)
            }
            composable(Tab.ShoppingList.route) {
                ShoppingListScreen(repository = repository)
            }
        }
    }
}
