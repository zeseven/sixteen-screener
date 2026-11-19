package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sixteenscreener.app.R
import com.sixteenscreener.app.ui.theme.SixteenTheme

private val LogoShape = RoundedCornerShape(8.dp)

/**
 * Company logo loaded from the backend-relative [logoUrl]; the default logo is shown while loading and on any error.
 * Logos larger than the slot are scaled down to fit it, smaller raster logos keep their pixel size and are centered.
 * Every logo is drawn on a white rounded tile in both themes, so dark logos stay visible on a dark background.
 */
@Composable
fun CompanyLogo(
    logoUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val defaultLogo = painterResource(R.drawable.ic_default_logo)
    AsyncImage(
        model = logoUrl,
        contentDescription = contentDescription,
        placeholder = defaultLogo,
        error = defaultLogo,
        fallback = defaultLogo,
        contentScale = ContentScale.Inside,
        modifier = modifier
            .size(size)
            .clip(LogoShape)
            .background(SixteenTheme.companyColors.logoBackground, LogoShape),
    )
}
