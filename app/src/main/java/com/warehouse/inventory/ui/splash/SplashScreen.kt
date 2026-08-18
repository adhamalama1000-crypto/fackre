package com.warehouse.inventory.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.theme.BrandAmber
import com.warehouse.inventory.ui.theme.BrandNavy
import com.warehouse.inventory.ui.theme.BrandNavyDeep

/**
 * Branded splash, shown while the session is being read from DataStore.
 *
 * This is not a timed screen: it is the visual state of [com.warehouse.inventory.ui.navigation.SessionState.Loading]
 * and disappears the moment the session resolves, so it never delays the user artificially.
 * The navy gradient and centred mark deliberately match `splash_window_background`, which
 * the framework paints before any Composable runs — the handoff from window background to
 * this screen is therefore seamless rather than a flash of a different colour.
 *
 * Colours are the fixed brand navy in both themes, not `colorScheme` roles: the window
 * background behind it is a static drawable that cannot follow the theme, so following it
 * here is what keeps the two identical.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    // Fades and scales the mark in once, so a slow session read looks intentional rather
    // than frozen. A fast read simply cuts away mid-animation, which is fine.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "splashReveal"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BrandNavy, BrandNavyDeep))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(124.dp)
                    .scale(0.88f + 0.12f * progress)
                    .alpha(progress)
            )

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .alpha(progress)
            )

            Text(
                text = stringResource(R.string.app_tagline),
                color = BrandAmber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .alpha(progress)
            )

            LinearProgressIndicator(
                color = BrandAmber,
                trackColor = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .padding(top = 40.dp)
                    .width(120.dp)
                    .height(3.dp)
                    .alpha(progress)
            )
        }
    }
}
