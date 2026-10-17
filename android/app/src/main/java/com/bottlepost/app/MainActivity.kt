package com.bottlepost.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bottlepost.app.ui.AppViewModel
import com.bottlepost.app.ui.StartState
import com.bottlepost.app.ui.compose.ComposeScreen
import com.bottlepost.app.ui.main.MainScreen
import com.bottlepost.app.ui.onboarding.OnboardingScreen
import com.bottlepost.app.ui.splash.BrandSplash
import com.bottlepost.app.ui.theme.BottlepostTheme
import com.bottlepost.app.ui.thread.ThreadScreen

// AppCompatActivity (not ComponentActivity) so the per-app language picker works below Android 13.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (bottle icon) hands over to BrandSplash, which shows the app name.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as BottlepostApp).container
        setContent {
            BottlepostTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val appVm: AppViewModel = viewModel { AppViewModel(container) }
                    val state by appVm.state.collectAsStateWithLifecycle()
                    when (state) {
                        StartState.Loading -> BrandSplash()
                        StartState.Offline -> BrandSplash(stringResource(R.string.error_network), onRetry = appVm::start)
                        StartState.NeedsOnboarding -> OnboardingScreen(container, onDone = appVm::onOnboarded)
                        StartState.Ready -> AppNavHost(container, onAccountDeleted = appVm::onAccountDeleted)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer, onAccountDeleted: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                container = container,
                onOpenThread = { nav.navigate("thread/$it") },
                onCompose = { nav.navigate("compose") },
                onAccountDeleted = onAccountDeleted,
            )
        }
        composable("compose") {
            ComposeScreen(container, onBack = { nav.popBackStack() }, onSent = {
                nav.popBackStack()
                nav.navigate("thread/$it")
            })
        }
        composable("thread/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ThreadScreen(container, threadId = entry.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
        }
    }
}
