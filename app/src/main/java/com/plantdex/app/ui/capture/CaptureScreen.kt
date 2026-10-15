package com.plantdex.app.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.ui.components.EmojiBadge
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.appContainer
import java.io.File

@Composable
fun CaptureScreen(
    onSaved: (entryId: String, message: String?) -> Unit,
    onOpenEntry: (entryId: String) -> Unit,
) {
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
                    catalogRepository = container.catalogRepository,
                    draftRepository = container.draftRepository,
                    workDir = context.cacheDir,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    var showDrafts by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(state) {
        val saved = state as? CaptureUiState.Saved ?: return@LaunchedEffect
        viewModel.reset()
        onSaved(saved.entryId, saved.message)
    }

    when (val s = state) {
        is CaptureUiState.Camera -> CameraPermissionGate {
            BackHandler(enabled = s.photoCount > 0, onBack = viewModel::backToReview)
            CameraContent(
                photoCount = s.photoCount,
                draftCount = drafts.size,
                onOpenDrafts = { showDrafts = true },
                onCancelAdd = viewModel::backToReview,
                onShutter = viewModel::onShutter,
                onPhotoSaved = viewModel::onPhotoSaved,
                onError = viewModel::onCaptureError,
            )
        }
        is CaptureUiState.Review -> ReviewContent(
            state = s,
            onAddMore = viewModel::addMorePhoto,
            onRemove = viewModel::removePhoto,
            onCrop = viewModel::cropPhoto,
            onIdentify = viewModel::identifyPhotos,
            onReset = viewModel::reset,
        )
        is CaptureUiState.Identifying -> IdentifyingContent(s.photoFile)
        is CaptureUiState.Results -> IdentifyResultContent(
            state = s,
            onSelect = viewModel::selectCandidate,
            onMemoChange = viewModel::onMemoChange,
            onPublicChange = viewModel::onPublicChange,
            onSave = viewModel::save,
            onRetake = viewModel::reset,
            onRetryLocation = viewModel::retryLocation,
            onOpenEntry = onOpenEntry,
            onSaveDraft = viewModel::saveDraft,
        )
        is CaptureUiState.Failed -> FailedContent(
            state = s,
            onRetry = viewModel::retryIdentify,
            onRetake = viewModel::reset,
            onSaveDraft = viewModel::saveDraft,
        )
        is CaptureUiState.Saved -> IdentifyingContent(photoFile = null)
    }

    if (showDrafts) {
        DraftsSheet(
            drafts = drafts,
            photoOf = { container.draftRepository.photoFiles(it).firstOrNull() },
            onOpen = {
                showDrafts = false
                viewModel.openDraft(it)
            },
            onDelete = viewModel::deleteDraft,
            onDismiss = { showDrafts = false },
        )
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
    // 설정 앱에서 권한을 허용하고 돌아온 경우를 반영합니다.
    LifecycleResumeEffect(Unit) {
        cameraGranted = granted(Manifest.permission.CAMERA)
        onPauseOrDispose { }
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
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            EmojiBadge("📷", size = 104.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.capture_camera_permission_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.capture_location_permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { launcher.launch(permissions) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.capture_grant_permission))
            }
            if (askedOnce) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.capture_grant_in_settings)) }
            }
        }
    }
}

@Composable
private fun CameraContent(
    photoCount: Int,
    draftCount: Int,
    onOpenDrafts: () -> Unit,
    onCancelAdd: () -> Unit,
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
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (photoCount == 0) {
                    stringResource(R.string.capture_camera_hint_first)
                } else {
                    stringResource(R.string.capture_camera_hint_extra, photoCount + 1, MAX_PHOTOS)
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (photoCount > 0) {
                AssistChip(
                    onClick = onCancelAdd,
                    label = { Text(stringResource(R.string.capture_stop_adding), color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                )
            } else if (draftCount > 0) {
                AssistChip(
                    onClick = onOpenDrafts,
                    label = { Text(stringResource(R.string.capture_drafts_chip, draftCount), color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                )
            }
        }
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
            if (isTaking) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
        }
    }
}

/** 식별 중(사진 위에 어둡게) 또는 등록 중(크림색 바탕) 안내 */
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
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (photoFile == null) EmojiBadge("🌱")
            CircularProgressIndicator(color = if (photoFile != null) Color.White else MaterialTheme.colorScheme.primary)
            Text(
                if (photoFile != null) stringResource(R.string.capture_identifying) else stringResource(R.string.capture_saving),
                color = if (photoFile != null) Color.White else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FailedContent(
    state: CaptureUiState.Failed,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onSaveDraft: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (state.photo != null) {
            PaperCard(shape = MaterialTheme.shapes.large) {
                AsyncImage(
                    model = state.photo.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        } else {
            EmojiBadge("⚠️")
        }
        Spacer(Modifier.height(4.dp))
        Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.capture_retake))
        }
        if (state.photo != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.capture_retry_identify))
            }
            TextButton(onClick = onSaveDraft) { Text(stringResource(R.string.capture_save_draft_identify_later)) }
        }
    }
}
