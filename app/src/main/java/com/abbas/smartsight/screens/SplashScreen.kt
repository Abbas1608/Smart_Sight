package com.abbas.smartsight.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbas.smartsight.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "logo_scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000L)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(  // Changed to horizontal for landscape
                    colors = listOf(
                        Color(0xFF222833),
                        Color(0xFF667799)
                    )
                )),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.7f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            Surface(
                modifier = Modifier.size(140.dp),
                shape = RoundedCornerShape(35.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id =  R.drawable.app_logo1),
                        contentDescription = "App Logo",
                        tint = Color.White,
                        modifier = Modifier.size(350.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(60.dp))

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Smart Sight",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Real-Time Object Detection",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(.6f)
                )
                Spacer(modifier = Modifier.height(30.dp))
                Text(
                    text = "Developed By Team Outliers",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(.8f)
                )
            }
        }
    }
}
