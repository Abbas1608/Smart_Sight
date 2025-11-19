package com.abbas.smartsight.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbas.smartsight.auth.AuthManager
import kotlinx.coroutines.launch
import com.abbas.smartsight.R

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
        if (authManager.isUserLoggedIn()) {
            onLoginSuccess()
        }
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
                )
            )
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(1000)) + slideInHorizontally { it / 2 }
        ) {
            // LANDSCAPE LAYOUT - Side by side
            Row(
                modifier = Modifier
                    .fillMaxSize()
//                    .padding(32.dp)
                ,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // LEFT SIDE - Branding
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // App Logo
                    Surface(
                        modifier = Modifier.size(140.dp),
                        shape = RoundedCornerShape(35.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id =  R.drawable.app_logo1),
                                contentDescription = "App Logo",
                                tint = Color.White,
                                modifier = Modifier.size(300.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Smart Sight",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Real-Time Object Detection",
                        fontSize = 18.sp,
                        color = Color.White.copy(0.9f),
                        textAlign = TextAlign.Center
                    )

//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    Text(
//                        text = "Team Outliers",
//                        fontSize = 16.sp,
//                        fontWeight = FontWeight.Bold,
//                        color = Color.White.copy(0.8f),
//                        textAlign = TextAlign.Center
//                    )
                }

                // RIGHT SIDE - Login Form
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                ) {
                    // Welcome Card
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(25.dp)
                        ) {
                            Text(
                                text = "Welcome Back!",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Sign in with your Google account to continue",
                                fontSize = 13.sp,
                                color = Color.White.copy(0.85f),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Google Sign-In Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null

                                        val result = authManager.signInWithGoogle()
                                        result.onSuccess {
                                            isLoading = false
                                            onLoginSuccess()
                                        }.onFailure { e ->
                                            isLoading = false
                                            errorMessage = e.message
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(.85f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isLoading,
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 8.dp,
                                    pressedElevation = 12.dp
                                )
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = Color(0xFF667eea),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        // Google Icon
                                        Surface(
                                            modifier = Modifier.size(28.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Transparent
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = "G",
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF222833)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Text(
                                            text = "Continue with Google",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF222833)
                                        )
                                    }
                                }
                            }

                            // Error message
                            errorMessage?.let { error ->
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    color = Color.Red.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = error,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(15.dp))

                            // Privacy text
                            Text(
                                text = "By continuing, you agree to our Terms of Service and Privacy Policy",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
