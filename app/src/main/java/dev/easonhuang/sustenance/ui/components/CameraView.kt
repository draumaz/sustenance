package dev.easonhuang.sustenance.ui.components

import android.graphics.Bitmap
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isCapturing: Boolean = false,
    isBatchMode: Boolean = false,
    isTorchOn: Boolean = false,
    onImageCaptured: (ImageProxy) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    val previewView = remember { PreviewView(context) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val shutterProgress = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                // Ignore errors on release
            }
        }
    }

    LaunchedEffect(isTorchOn) {
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            shutterProgress.animateTo(1f, tween(500))
            frozenBitmap = previewView.bitmap
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        onImageCaptured(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        frozenBitmap = null
                    }
                }
            )
        } else {
            if (isBatchMode) {
                shutterProgress.animateTo(0f, tween(500))
            }
            frozenBitmap = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
        ) {
            AndroidView(
                factory = { _ ->
                    val executor = ContextCompat.getMainExecutor(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            // Handle camera binding failure
                        }
                    }, executor)
                    previewView.apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            frozenBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            if (shutterProgress.value > 0f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                ) {
                    drawRect(Color.Black)
                    drawCircle(
                        color = Color.Transparent,
                        radius = (size.maxDimension) * (1f - shutterProgress.value),
                        center = center,
                        blendMode = BlendMode.Clear
                    )
                }
            }
        }
    }
}
