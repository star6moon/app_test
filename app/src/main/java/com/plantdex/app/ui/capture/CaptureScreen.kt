package com.plantdex.app.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.ui.components.appContainer
import java.io.File

@Composable
fun CaptureScreen(onSaved: (entryId: String) -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val viewModel: CaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CaptureViewModel(
                    plantIdentifier = container.plantIdentifier,
                    locationProvider = container.locationProvider,
                    collectionRepository = container.collectionRepository,
                    authRepository = container.authRepository,
                    workDir = context.cacheDir,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val saved = state as? CaptureUiState.Saved ?: return@LaunchedEffect
        viewModel.reset()
        onSaved(saved.entryId)
    }

    when (val s = state) {
        CaptureUiState.Camera -> CameraPermissionGate {
            CameraContent(
                onShutter = viewModel::onShutter,
                onPhotoSaved = viewModel::onPhotoSaved,
                onError = viewModel::onCaptureError,
            )
        }
        is CaptureUiState.Identifying -> IdentifyingContent(s.photoFile)
        is CaptureUiState.Results -> IdentifyResultContent(
            state = s,
            onSelect = viewModel::selectCandidate,
            onMemoChange = viewModel::onMemoChange,
            onPublicChange = viewModel::onPublicChange,
            onSave = viewModel::save,
            onRetake = viewModel::reset,
        )
        is CaptureUiState.Failed -> FailedContent(
            state = s,
            onRetry = viewModel::retryIdentify,
            onRetake = viewModel::reset,
        )
        is CaptureUiState.Saved -> IdentifyingContent(photoFile = null)
    }
}

/** 카메라 권한을 확인하고, 위치 권한도 함께 요청합니다 (위치는 선택). */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
        askedOnce = true
    }
    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    LaunchedEffect(Unit) {
        val locationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!askedOnce && (!cameraGranted || !locationGranted)) launcher.launch(permissions)
    }

    if (cameraGranted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("식물을 촬영하려면 카메라 권한이 필요해요", style = MaterialTheme.typography.titleMedium)
            Text(
                "위치 권한을 허용하면 어디서 발견했는지도 함께 기록돼요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { launcher.launch(permissions) }) { Text("권한 허용하기") }
            if (askedOnce) {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text("설정에서 허용하기") }
            }
        }
    }
}

@Composable
private fun CameraContent(
    onShutter: () -> Unit,
    onPhotoSaved: (File) -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    var isTaking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "잎이나 꽃이 잘 보이도록 가까이에서 찍어 주세요",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        FilledIconButton(
            onClick = {
                if (isTaking) return@FilledIconButton
                isTaking = true
                onShutter()
                val file = File(context.cacheDir, "raw_${System.currentTimeMillis()}.jpg")
                controller.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            isTaking = false
                            onPhotoSaved(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isTaking = false
                            onError(exception.message)
                        }
                    },
                )
            },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(76.dp)
                .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        ) {
            if (isTaking) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun IdentifyingContent(photoFile: File?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (photoFile != null) {
            AsyncImage(
                model = photoFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Color.White)
            Text(
                if (photoFile != null) "AI 가 어떤 식물인지 살펴보는 중…" else "도감에 등록하는 중…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun FailedContent(state: CaptureUiState.Failed, onRetry: () -> Unit, onRetake: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (state.photo != null) {
            AsyncImage(
                model = state.photo.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(200.dp).clip(MaterialTheme.shapes.large),
            )
        }
        Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetake) { Text("다시 찍기") }
        if (state.photo != null) {
            OutlinedButton(onClick = onRetry) { Text("이 사진으로 다시 식별") }
        }
    }
}
