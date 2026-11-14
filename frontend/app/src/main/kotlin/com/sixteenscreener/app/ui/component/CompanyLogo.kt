package com.sixteenscreener.app.ui.component

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

/**
 * Company logo loaded from the backend-relative [logoUrl]; the default logo is shown while loading and on any error.
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
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
    )
}
