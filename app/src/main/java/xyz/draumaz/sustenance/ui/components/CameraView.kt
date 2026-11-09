package xyz.draumaz.sustenance.ui.components

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import xyz.draumaz.sustenance.R
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
    onToggleTorch: () -> Unit = {},
    showGallery: Boolean = false,
    onSelectGallery: () -> Unit = {},
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

            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            if (showGallery || hasFlash) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (showGallery) GalleryButton(onClick = onSelectGallery)
                    if (hasFlash) TorchButton(isOn = isTorchOn, onClick = onToggleTorch)
                }
            }
        }
    }
}

/**
 * The viewfinder's flashlight toggle: a [ScallopButton] that fills with a light tint when on, and
 * whose cookie turns a quarter-scallop so the change is felt as well as seen, with an on/off
 * toggle click.
 */
@Composable
private fun TorchButton(isOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        if (isOn) 15f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "torch_rotation"
    )
    val container by animateColorAsState(
        if (isOn) Color.White.copy(alpha = 0.85f) else ScallopScrim,
        label = "torch_container"
    )
    val content by animateColorAsState(
        if (isOn) Color.Black else Color.White,
        label = "torch_content"
    )
    val label = stringResource(if (isOn) R.string.torch_off else R.string.torch_on)
    val haptics = LocalHapticFeedback.current

    ScallopButton(
        icon = if (isOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
        container = container,
        content = content,
        rotation = rotation,
        role = Role.Switch,
        onClick = {
            // The toggle haptics read as a firmer click on and a softer one off.
            haptics.performHapticFeedback(
                if (isOn) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
            )
            onClick()
        },
        modifier = modifier.semantics {
            contentDescription = label
            toggleableState = ToggleableState(isOn)
        },
    )
}

/** Opens the photo picker to add gallery photos to the batch; sits beside the flashlight. */
@Composable
private fun GalleryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.select_from_gallery)
    val haptics = LocalHapticFeedback.current
    ScallopButton(
        icon = Icons.Rounded.Image,
        container = ScallopScrim,
        content = Color.White,
        rotation = 0f,
        role = Role.Button,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            onClick()
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}

// A dark see-through scrim keeps the white icons readable over any scene.
private val ScallopScrim = Color.Black.copy(alpha = 0.35f)

/**
 * A see-through scallop (the 12-sided cookie) over the viewfinder, so the preview stays visible
 * behind it. A springy squish on press, like the rest of the Expressive controls. Only the shape
 * takes [rotation]; the icon stays upright.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScallopButton(
    icon: ImageVector,
    container: Color,
    content: Color,
    rotation: Float,
    role: Role,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scallop_press"
    )
    val shape = MaterialShapes.Cookie12Sided.toShape()

    Box(
        modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = role,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .background(container, shape)
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
        )
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(26.dp)
        )
    }
}
