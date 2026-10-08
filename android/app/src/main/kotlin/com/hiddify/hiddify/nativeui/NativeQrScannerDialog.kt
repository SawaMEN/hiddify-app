package com.hiddify.hiddify.nativeui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.hiddify.hiddify.R
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CenterCropStrategy

/** The Dart scanner's frame is visual only; recognition covers the entire camera preview. */
private class NativeQrCameraView(context: Context) : BarcodeView(context) {
    override fun calculateFramingRect(container: Rect, surface: Rect): Rect =
        Rect(container).apply { intersect(surface) }
}

/** Full-screen QrCodeScannerDialog, without CaptureActivity's finder, prompt or laser. */
@Composable
internal fun NativeQrScannerDialog(onDismiss: () -> Unit, onDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestDetected by rememberUpdatedState(onDetected)
    var accepted by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(permissionGranted) }
    var permissionResolved by rememberSaveable { mutableStateOf(permissionGranted) }
    var previewReady by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val camera = remember(context) {
        NativeQrCameraView(context).apply {
            setUseTextureView(true)
            setBackgroundColor(android.graphics.Color.BLACK)
            setPreviewScalingStrategy(CenterCropStrategy())
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionResolved = true
    }
    LaunchedEffect(Unit) {
        if (!permissionRequested) {
            permissionRequested = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    fun close() {
        if (accepted) return
        accepted = true
        camera.pause()
        latestDismiss()
    }
    DisposableEffect(camera, lifecycleOwner, permissionGranted) {
        fun start() {
            if (!permissionGranted || accepted || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            cameraFailed = false
            previewReady = false
            camera.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    val value = result.text
                    if (accepted || value.isNullOrEmpty()) return
                    accepted = true
                    camera.pause()
                    latestDetected(value)
                }
            })
            try {
                camera.resume()
            } catch (_: Exception) {
                cameraFailed = true
                camera.pause()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Returning from settings must observe both a grant and a revocation.
                    permissionGranted = hasCameraPermission(context)
                    if (permissionGranted) start() else camera.pause()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> camera.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            camera.stopDecoding()
            camera.pause()
        }
    }
    // Listeners live for exactly as long as this camera view, including permission recomposition.
    DisposableEffect(camera) {
        var disposed = false
        camera.addStateListener(object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() { if (!disposed) previewReady = true }
            override fun previewStopped() { if (!disposed) previewReady = false }
            override fun cameraError(error: Exception) {
                if (!disposed) {
                    cameraFailed = true
                    previewReady = false
                    camera.pause()
                }
            }
            override fun cameraClosed() = Unit
        })
        onDispose { disposed = true }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(
        dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        // Flutter DialogRoute uses a black54 barrier; placeholder/error builders are transparent.
        SideEffect { window?.setDimAmount(.54f) }
        NativeQrScannerContent(
            loading = !cameraFailed && (!permissionResolved || permissionGranted && !previewReady),
            failed = cameraFailed || permissionResolved && !permissionGranted,
            onClose = ::close,
            preview = {
                if (permissionGranted && !cameraFailed) AndroidView(factory = { camera },
                    modifier = Modifier.fillMaxSize().alpha(if (previewReady) 1f else 0f))
            },
        )
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun NativeQrScannerContent(loading: Boolean, failed: Boolean, onClose: () -> Unit,
    preview: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameSize = maxWidth * .7f
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                preview()
                if (loading) CircularProgressIndicator()
                if (failed) Text(stringResource(R.string.native_qr_permission_denied), color = scheme.onSurface)
                // MobileScanner shows its overlay only after camera initialization succeeds.
                if (!failed && !loading) Box(Modifier.size(frameSize).border(4.dp, scheme.primaryContainer, RoundedCornerShape(16.dp)))
            }
            IconButton(onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp)
                    .background(scheme.primaryContainer, CircleShape)) {
                Icon(painterResource(R.drawable.native_close), stringResource(R.string.native_qr_close),
                    Modifier.size(24.dp), tint = scheme.onPrimaryContainer)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "QR scanner · light", widthDp = 390, heightDp = 844)
@androidx.compose.ui.tooling.preview.Preview(name = "QR scanner · dark", widthDp = 390, heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QrScannerPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.SYSTEM) {
        NativeQrScannerContent(false, false, {}) { Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "QR scanner · permission denied", widthDp = 390, heightDp = 844)
@Composable
private fun QrScannerDeniedPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere { NativeQrScannerContent(false, true, {}) {} }
    }
}
