package com.sixteenscreener.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.navigation.SixteenNavigation
import com.sixteenscreener.app.ui.theme.SixteenTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var clock: Clock

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SixteenTheme {
                CompositionLocalProvider(LocalClock provides clock) {
                    SixteenNavigation()
                }
            }
        }
    }
}
