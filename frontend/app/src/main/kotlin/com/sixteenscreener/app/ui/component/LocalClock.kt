package com.sixteenscreener.app.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import com.sixteenscreener.app.domain.DeviceClock
import java.time.Clock

/** Clock used for time-dependent display such as the cache indicator; replaced with a fixed clock in tests. */
val LocalClock = staticCompositionLocalOf<Clock> { DeviceClock() }
