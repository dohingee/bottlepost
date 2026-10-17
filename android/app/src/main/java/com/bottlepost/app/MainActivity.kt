package com.bottlepost.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.bottlepost.app.ui.theme.BottlepostTheme
import com.bottlepost.app.ui.thread.ThreadScreen

// AppCompatActivity (not ComponentActivity) so the per-app language picker works below Android 13.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as BottlepostApp).container
        setContent {
            BottlepostTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val appVm: AppViewModel = viewModel { AppViewModel(container) }
                    val state by appVm.state.collectAsStateWithLifecycle()
                    when (state) {
                        StartState.Loading -> Centered { CircularProgressIndicator() }
                        StartState.Offline -> Centered {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(R.string.error_network))
                                Button(onClick = appVm::start) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                        StartState.NeedsOnboarding -> OnboardingScreen(container, onDone = appVm::onOnboarded)
                        StartState.Ready -> AppNavHost(container, onAccountDeleted = appVm::onAccountDeleted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
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
