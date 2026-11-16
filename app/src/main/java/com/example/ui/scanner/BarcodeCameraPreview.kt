package com.example.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeCameraPreview(
  onBarcodeDetected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
    }

  if (!hasCameraPermission) {
    Surface(
      modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
      color = MaterialTheme.colorScheme.surfaceVariant,
      tonalElevation = 1.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.CameraAlt,
          contentDescription = null,
          modifier = Modifier.size(36.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.camera_permission_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.camera_permission_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("grant_camera_button"),
        ) {
          Text(stringResource(R.string.grant_camera_permission))
        }
      }
    }
    return
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  val currentCallback by rememberUpdatedState(onBarcodeDetected)
  val executor = remember { Executors.newSingleThreadExecutor() }
  val scanner = remember {
    BarcodeScanning.getClient(
      BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
    )
  }

  var boundCamera by remember { mutableStateOf<Camera?>(null) }
  var torchEnabled by remember { mutableStateOf(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var lastCode by remember { mutableStateOf("") }
  var lastTimestamp by remember { mutableLongStateOf(0L) }

  DisposableEffect(Unit) {
    onDispose {
      runCatching { scanner.close() }
      runCatching { executor.shutdown() }
    }
  }

  Box(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
  ) {
    if (cameraError != null) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Camera unavailable on this device. Use manual barcode entry above.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    } else {
      AndroidView(
        factory = { ctx ->
          val previewView =
            PreviewView(ctx).apply {
              scaleType = PreviewView.ScaleType.FILL_CENTER
              implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
          val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
          cameraProviderFuture.addListener(
            {
              try {
                val cameraProvider = cameraProviderFuture.get()
                val preview =
                  Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                  }
                val imageAnalysis =
                  ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                      analysis.setAnalyzer(executor) { imageProxy ->
                        analyzeBarcodeFrame(
                          imageProxy = imageProxy,
                          scanner = scanner,
                          onDetected = { raw ->
                            val now = System.currentTimeMillis()
                            if (raw != lastCode || (now - lastTimestamp) > 1600L) {
                              lastCode = raw
                              lastTimestamp = now
                              previewView.post { currentCallback(raw) }
                            }
                          },
                        )
                      }
                    }
                cameraProvider.unbindAll()
                boundCamera =
                  cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                  )
              } catch (e: Exception) {
                Log.w("BarcodeScanner", "Camera binding failed", e)
                cameraError = e.localizedMessage ?: "Camera unavailable"
              }
            },
            ContextCompat.getMainExecutor(ctx),
          )
          previewView
        },
        modifier = Modifier.fillMaxSize(),
      )

      ScannerReticleOverlay(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxSize(),
      )

      Row(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.55f),
          shape = RoundedCornerShape(99.dp),
        ) {
          Text(
            text = "Align box barcode in frame",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }

        if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
          Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = CircleShape,
          ) {
            IconButton(
              onClick = {
                val next = !torchEnabled
                torchEnabled = next
                boundCamera?.cameraControl?.enableTorch(next)
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("torch_toggle_button"),
            ) {
              Icon(
                imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                contentDescription = if (torchEnabled) "Turn off flash" else "Turn on flash",
                tint = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.White,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeBarcodeFrame(
  imageProxy: androidx.camera.core.ImageProxy,
  scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
  onDetected: (String) -> Unit,
) {
  val mediaImage = imageProxy.image
  if (mediaImage == null) {
    imageProxy.close()
    return
  }
  val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
  scanner
    .process(inputImage)
    .addOnSuccessListener { barcodes ->
      val detected =
        barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
      if (!detected.isNullOrEmpty()) {
        onDetected(detected)
      }
    }
    .addOnCompleteListener { imageProxy.close() }
}

@Composable
private fun ScannerReticleOverlay(
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
  val lineProgress by
    infiniteTransition.animateFloat(
      initialValue = 0.15f,
      targetValue = 0.85f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1800, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "line_y",
    )

  Canvas(modifier = modifier) {
    val boxWidth = size.width * 0.72f
    val boxHeight = size.height * 0.52f
    val left = (size.width - boxWidth) / 2f
    val top = (size.height - boxHeight) / 2f
    val right = left + boxWidth
    val bottom = top + boxHeight
    val cornerLen = 26.dp.toPx()
    val strokeW = 3.dp.toPx()

    // Top-left
    drawLine(Color.White, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
    drawLine(Color.White, Offset(left, top), Offset(left, top + cornerLen), strokeW, StrokeCap.Round)
    // Top-right
    drawLine(Color.White, Offset(right - cornerLen, top), Offset(right, top), strokeW, StrokeCap.Round)
    drawLine(Color.White, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
    // Bottom-left
    drawLine(Color.White, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeW, StrokeCap.Round)
    drawLine(Color.White, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
    // Bottom-right
    drawLine(Color.White, Offset(right - cornerLen, bottom), Offset(right, bottom), strokeW, StrokeCap.Round)
    drawLine(Color.White, Offset(right, bottom - cornerLen), Offset(right, bottom), strokeW, StrokeCap.Round)

    val scanY = top + boxHeight * lineProgress
    drawLine(
      color = accentColor.copy(alpha = 0.85f),
      start = Offset(left + 10.dp.toPx(), scanY),
      end = Offset(right - 10.dp.toPx(), scanY),
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round,
    )
  }
}
