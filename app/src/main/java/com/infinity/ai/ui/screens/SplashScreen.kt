package com.infinity.ai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinity.ai.ui.components.GradientBackground
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(900, easing = EaseOut))
        delay(1400)
        onNavigate()
    }

    GradientBackground(darkTheme = true, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(alpha.value)
            ) {
                com.infinity.ai.ui.components.GoneEmblem(size = 80.dp)
                Spacer(Modifier.height(24.dp))
                com.infinity.ai.ui.components.GoneWordmark(isDarkTheme = true, fontSize = 40.sp, letterSpacing = 8.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "EDGE HEALTH INTELLIGENCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    letterSpacing = 3.sp
                )
            }
        }
    }
}
