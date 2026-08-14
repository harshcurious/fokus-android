package dev.fokus.app.ui.timer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fokus.app.R
import dev.fokus.app.timer.TimerEngine
import dev.fokus.app.ui.TimerFace
import dev.fokus.app.ui.dialSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val context = LocalContext.current

    val notificationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* state is refreshed by the ON_RESUME observer below */ }

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    overlayGranted = Settings.canDrawOverlays(context)
                    notificationsGranted =
                        Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!overlayGranted) {
                PermissionCard(
                    text = stringResource(R.string.permission_overlay_explain),
                    actionLabel = stringResource(R.string.permission_overlay_action),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (!notificationsGranted) {
                PermissionCard(
                    text = stringResource(R.string.permission_notifications_explain),
                    actionLabel = stringResource(R.string.permission_notifications_action),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val s = snapshot
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (s != null) {
                    TimerFace(
                        snapshot = s,
                        caption = timerCaption(s),
                        ringColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onBackground,
                        dimColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.dialSize(),
                    )
                }
            }

            if (s != null) {
                TimerControls(
                    snapshot = s,
                    onStart = { viewModel.start() },
                    onPause = { viewModel.pause() },
                    onSkip = { viewModel.skip() },
                    onPostpone = { viewModel.postpone() },
                    onStop = { viewModel.stop() },
                )
            }
        }
    }
}

@Composable
private fun timerCaption(s: TimerEngine.Snapshot): String =
    when {
        s.overtime -> stringResource(R.string.caption_overtime)
        s.paused -> stringResource(R.string.caption_paused)
        s.phase == TimerEngine.Phase.IDLE -> stringResource(R.string.caption_idle)
        s.phase == TimerEngine.Phase.FOCUS ->
            stringResource(R.string.caption_focus, s.focusIndex, s.sessionsPerCycle)
        s.phase == TimerEngine.Phase.LONG_BREAK -> stringResource(R.string.caption_long_break)
        else -> stringResource(R.string.caption_short_break)
    }

@Composable
private fun TimerControls(
    snapshot: TimerEngine.Snapshot,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onSkip: () -> Unit,
    onPostpone: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            snapshot.overtime -> {
                Button(onClick = onStart) { Text(stringResource(R.string.action_start_focus)) }
            }
            snapshot.running -> {
                Button(onClick = onPause) { Text(stringResource(R.string.action_pause)) }
            }
            snapshot.paused -> {
                Button(onClick = onStart) { Text(stringResource(R.string.action_resume)) }
            }
            else -> {
                Button(onClick = onStart) { Text(stringResource(R.string.action_start)) }
            }
        }

        if (snapshot.phase != TimerEngine.Phase.IDLE) {
            if (snapshot.isBreak && !snapshot.overtime) {
                OutlinedButton(onClick = onPostpone) { Text(stringResource(R.string.action_postpone)) }
            }
            OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
            TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
        }
    }
}

@Composable
private fun PermissionCard(text: String, actionLabel: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}
