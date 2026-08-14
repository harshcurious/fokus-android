package dev.fokus.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fokus.app.R
import dev.fokus.app.ui.timer.TimerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.settings_durations), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            MinutesSlider(
                label = stringResource(R.string.settings_focus_length),
                value = settings.focusMinutes,
                range = 5f..120f,
                steps = 22,
                onChange = { viewModel.setFocusMinutes(it) },
            )
            MinutesSlider(
                label = stringResource(R.string.settings_short_break),
                value = settings.shortBreakMinutes,
                range = 1f..30f,
                steps = 28,
                onChange = { viewModel.setShortBreakMinutes(it) },
            )
            MinutesSlider(
                label = stringResource(R.string.settings_long_break),
                value = settings.longBreakMinutes,
                range = 5f..60f,
                steps = 10,
                onChange = { viewModel.setLongBreakMinutes(it) },
            )
            MinutesSlider(
                label = stringResource(R.string.settings_postpone),
                value = settings.postponeMinutes,
                range = 1f..15f,
                steps = 13,
                onChange = { viewModel.setPostponeMinutes(it) },
            )
            MinutesSlider(
                label = stringResource(R.string.settings_sessions_per_cycle),
                value = settings.sessionsPerCycle,
                range = 1f..8f,
                steps = 6,
                unit = "",
                onChange = { viewModel.setSessionsPerCycle(it) },
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_auto_start), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_auto_start_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.autoStartOnUnlock, onCheckedChange = { viewModel.setAutoStartOnUnlock(it) })
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_auto_start_focus_after_break), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_auto_start_focus_after_break_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.autoStartFocusAfterBreak, onCheckedChange = { viewModel.setAutoStartFocusAfterBreak(it) })
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_battery_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_battery_action))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MinutesSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
    unit: String = "min",
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (unit.isEmpty()) "$value" else "$value $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range,
            steps = steps,
        )
    }
}
