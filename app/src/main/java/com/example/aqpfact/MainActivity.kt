package com.example.aqpfact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aqpfact.ui.MainViewModel
import com.example.aqpfact.ui.screens.AddReadingScreen
import com.example.aqpfact.ui.screens.HomeScreen
import com.example.aqpfact.ui.screens.ReportScreen
import com.example.aqpfact.ui.theme.AQPFactTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AQPFactTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToAddReading = { meterId ->
                    navController.navigate("add_reading/$meterId")
                },
                onNavigateToReport = {
                    navController.navigate("report")
                }
            )
        }
        composable(
            "add_reading/{meterId}",
            arguments = listOf(navArgument("meterId") { type = NavType.IntType })
        ) { backStackEntry ->
            val meterId = backStackEntry.arguments?.getInt("meterId") ?: 0
            AddReadingScreen(
                meterId = meterId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("report") {
            ReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
