package ru.myit.vlevpn.ui.splash

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun StartupSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    SplashSystemBars(visible = visible)
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 360)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF05060B),
                            0.55f to Color(0xFF111326),
                            1f to Color(0xFF071514),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "UMBRA",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 40.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(31.dp)
                            .background(Color.White.copy(alpha = 0.42f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "VPN",
                        style = TextStyle(
                            color = Color(0xFFBFF5E9),
                            fontSize = 40.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashSystemBars(visible: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible, view) {
        val activity = view.context as? Activity
        if (!visible || activity == null) {
            onDispose { }
        } else {
            val controller = WindowInsetsControllerCompat(activity.window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
}
