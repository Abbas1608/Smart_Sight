package com.abbas.smartsight

import android.Manifest
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.abbas.smartsight.auth.AuthManager
import com.abbas.smartsight.auth.UserAvatar
import com.abbas.smartsight.detection.BoundingBox
import com.abbas.smartsight.detection.ObjectDetector
import com.abbas.smartsight.screens.LoginScreen
import com.abbas.smartsight.screens.SplashScreen
import com.abbas.smartsight.tts.TTSManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.AspectRatio


class MainActivity : ComponentActivity() {
    private lateinit var ttsManager: TTSManager
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        installSplashScreen()
        super.onCreate(savedInstanceState)

        ttsManager = TTSManager(this)
        authManager = AuthManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationFlow(
                        authManager = authManager,
                        ttsManager = ttsManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}

@Composable
fun AppNavigationFlow(
    authManager: AuthManager,
    ttsManager: TTSManager
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onSplashComplete = {
                    val destination = if (authManager.isUserLoggedIn()) "home" else "login"
                    navController.navigate(destination) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                authManager = authManager,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            SmartCameraPreviewScreen(
                ttsManager = ttsManager,
                authManager = authManager,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SmartCameraPreviewScreen(
    ttsManager: TTSManager,
    authManager: AuthManager,
    onLogout: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        CameraDetectionScreen(ttsManager, authManager, onLogout)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Camera permission is required for object detection",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

@Composable
fun CameraDetectionScreen(
    ttsManager: TTSManager,
    authManager: AuthManager,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val detector = remember {
        ObjectDetector(
            context = context,
            modelPath = "best_final.tflite",
            labelsPath = "labels.txt"
        )
    }

    var detections by remember { mutableStateOf<List<BoundingBox>>(emptyList()) }
    var imageWidth by remember { mutableStateOf(1) }
    var imageHeight by remember { mutableStateOf(1) }
    var frameWidth by remember { mutableStateOf(1) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var totalDetections by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
        }
    }

    // FULL SCREEN BOX - No padding, no margins
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()  // Respects system bars
    ) {
        // CAMERA PREVIEW - FULL SCREEN
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindSmartCamera(
                    cameraProvider,
                    previewView,
                    lifecycleOwner,
                    detector,
                    ttsManager
                ) { width, height, fWidth, boxes ->
                    imageWidth = width
                    imageHeight = height
                    frameWidth = fWidth
                    detections = boxes
                    totalDetections = boxes.size
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // OVERLAY - NO PARTITION LINES
        SmartOverlay(detections, imageWidth, imageHeight, frameWidth)

        // STATS PANEL
        SmartStatsPanel(detections, totalDetections)

        // PROFILE AVATAR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(onClick = { showProfileMenu = !showProfileMenu }) {
                UserAvatar(email = authManager.getUserEmail(), size = 48.dp)
            }

            DropdownMenu(
                expanded = showProfileMenu,
                onDismissRequest = { showProfileMenu = false }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = authManager.getUserDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = authManager.getUserEmail(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Divider()

                DropdownMenuItem(
                    text = { Text("Sign Out") },
                    onClick = {
                        scope.launch {
                            authManager.signOut()
                            showProfileMenu = false
                            onLogout()
                        }
                    }
                )
            }
        }
    }
}


private fun bindSmartCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    detector: ObjectDetector,
    ttsManager: TTSManager,
    onDetection: (Int, Int, Int, List<BoundingBox>) -> Unit
) {
    val preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)  // CHANGED: Use aspect ratio instead
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

    val isProcessing = AtomicBoolean(false)
    var lastAnnouncedObjects = mutableSetOf<String>()

    val imageAnalysis = ImageAnalysis.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)  // CHANGED: Match preview
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also {
            it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                if (isProcessing.getAndSet(true)) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    val bitmap = imageProxy.toBitmap()
                    val detections = detector.detect(bitmap, bitmap.width)

                    val currentObjects = detections.map { "${it.clsName}-${it.location}" }.toSet()
                    val newDetections = currentObjects - lastAnnouncedObjects

                    newDetections.forEach { objKey ->
                        val parts = objKey.split("-")
                        if (parts.size == 2) {
                            val message = "${parts[0]} detected in ${parts[1]}"
                            ttsManager.speakWithDebounce(message)
                        }
                    }

                    lastAnnouncedObjects = currentObjects.toMutableSet()

                    onDetection(bitmap.width, bitmap.height, previewView.width, detections)
                } catch (e: Exception) {
                    Log.e("Camera", "Error processing frame: ${e.message}")
                } finally {
                    isProcessing.set(false)
                    imageProxy.close()
                }
            }
        }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    try {
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
    } catch (e: Exception) {
        Log.e("Camera", "Camera binding error: ${e.message}")
        e.printStackTrace()
    }
}


@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun ImageProxy.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(planes[0].buffer)
    return bitmap
}

@Composable
fun SmartOverlay(
    detections: List<BoundingBox>,
    imageWidth: Int,
    imageHeight: Int,
    frameWidth: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val leftLine = canvasWidth * 0.33f
        val rightLine = canvasWidth * 0.67f

        drawLine(
            color = Color.White,
            start = Offset(leftLine, 0f),
            end = Offset(leftLine, canvasHeight),
            strokeWidth = 3f
        )

        drawLine(
            color = Color.White,
            start = Offset(rightLine, 0f),
            end = Offset(rightLine, canvasHeight),
            strokeWidth = 3f
        )

        if (imageWidth > 0 && imageHeight > 0) {
            val scaleX = canvasWidth / imageWidth
            val scaleY = canvasHeight / imageHeight

            detections.forEach { detection ->
                val left = detection.x1 * scaleX
                val top = detection.y1 * scaleY
                val right = detection.x2 * scaleX
                val bottom = detection.y2 * scaleY

                drawRect(
                    color = Color.Green,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 4f)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val label = "${detection.clsName} ${(detection.cnf * 100).toInt()}%"
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 40f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }

                    val textBounds = android.graphics.Rect()
                    paint.getTextBounds(label, 0, label.length, textBounds)
                    drawRect(
                        left, top - textBounds.height() - 20f,
                        left + textBounds.width() + 20f, top,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(200, 0, 0, 0)
                        }
                    )

                    drawText(label, left + 10f, top - 10f, paint)
                }
            }
        }
    }
}

@Composable
fun SmartStatsPanel(detections: List<BoundingBox>, totalDetections: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎯 Smart Sight Detection",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Live Detections: $totalDetections",
                    color = Color.Green,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                detections.groupBy { it.location }.forEach { (location, list) ->
                    Text(
                        text = "├─ ${location.uppercase()}: ${list.size}",
                        color = Color.Yellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
