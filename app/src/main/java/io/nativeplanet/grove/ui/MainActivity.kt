package io.nativeplanet.grove.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.nativeplanet.grove.ui.browse.BrowseScreen
import io.nativeplanet.grove.ui.browse.BrowseViewModel
import io.nativeplanet.grove.ui.preview.PreviewScreen
import io.nativeplanet.grove.ui.settings.SettingsScreen
import io.nativeplanet.grove.ui.theme.GroveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GroveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GroveNavigation()
                }
            }
        }
    }
}

@Composable
fun GroveNavigation() {
    val navController = rememberNavController()
    val browseViewModel: BrowseViewModel = viewModel()

    NavHost(navController = navController, startDestination = "browse") {
        composable("browse") {
            BrowseScreen(
                viewModel = browseViewModel,
                onFileClick = { fileId ->
                    navController.navigate("preview/$fileId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("preview/{fileId}") { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId") ?: return@composable
            PreviewScreen(
                fileId = fileId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
