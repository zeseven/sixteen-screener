package com.sixteenscreener.app.testing

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher

object AccessibilityMatchers {
    val isPoliteLiveRegion: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
}
