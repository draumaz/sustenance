package xyz.draumaz.sustenance.ui.components

import android.graphics.Bitmap
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch


/** Prefer 4:3 for both preview and capture so the photo matches what the viewfinder shows. */
private val FourByThreeSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

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
            .setResolutionSelector(FourByThreeSelector)
            .build()
    }
    val previewView = remember { PreviewView(context) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var accumulatedZoom by remember { mutableFloatStateOf(1f) }

    val chompScaleX = remember { Animatable(1f) }
    val chompScaleY = remember { Animatable(1f) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                // Ignore errors on release
            }
        }
    }

    DisposableEffect(camera) {
        val cameraInfo = camera?.cameraInfo ?: return@DisposableEffect onDispose {}
        val observer = Observer<ZoomState> { state ->
            currentZoomRatio = state.zoomRatio
            minZoomRatio = state.minZoomRatio
            maxZoomRatio = state.maxZoomRatio
        }
        cameraInfo.zoomState.observe(lifecycleOwner, observer)
        onDispose {
            cameraInfo.zoomState.removeObserver(observer)
        }
    }

    LaunchedEffect(currentZoomRatio) {
        accumulatedZoom = currentZoomRatio
    }

    LaunchedEffect(isTorchOn) {
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
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

            // Playful M3 Expressive "Chomp" squash & stretch animation
            launch {
                launch {
                    chompScaleY.animateTo(0.70f, tween(110, easing = FastOutLinearInEasing))
                    chompScaleY.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    chompScaleX.animateTo(1.08f, tween(110, easing = FastOutLinearInEasing))
                    chompScaleX.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        } else {
            chompScaleX.snapTo(1f)
            chompScaleY.snapTo(1f)
            frozenBitmap = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(3f / 4f)
                .graphicsLayer {
                    scaleX = chompScaleX.value
                    scaleY = chompScaleY.value
                }
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                .pointerInput(camera, minZoomRatio, maxZoomRatio) {
                    detectTransformGestures { _, _, zoomFactor, _ ->
                        if (camera != null && maxZoomRatio > minZoomRatio) {
                            val targetZoom = (accumulatedZoom * zoomFactor).coerceIn(minZoomRatio, maxZoomRatio)
                            accumulatedZoom = targetZoom
                            camera?.cameraControl?.setZoomRatio(targetZoom)
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { _ ->
                    val executor = ContextCompat.getMainExecutor(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setResolutionSelector(FourByThreeSelector)
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
        }
    }
}
