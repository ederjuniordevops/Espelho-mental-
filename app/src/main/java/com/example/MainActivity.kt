package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.dao())

        setContent {
            MyApplicationTheme {
                val viewModel: EspelhoViewModel = viewModel(
                    factory = EspelhoViewModelFactory(application, repository)
                )
                val currentScreen by viewModel.currentScreen.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CosmicBackground)
                ) {
                    when (currentScreen) {
                        AppScreen.ONBOARDING -> OnboardingScreen(viewModel = viewModel)
                        AppScreen.MAIN_HUB -> MainHubScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
