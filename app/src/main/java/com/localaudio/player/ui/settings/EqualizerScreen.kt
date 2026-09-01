package com.localaudio.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.data.settings.EQUALIZER_FREQUENCIES_HZ
import com.localaudio.player.data.settings.EQUALIZER_MAX_GAIN_DB
import com.localaudio.player.data.settings.EQUALIZER_MIN_GAIN_DB
import com.localaudio.player.data.settings.EqualizerPreset
import com.localaudio.player.data.settings.EqualizerSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    settings: EqualizerSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (EqualizerPreset) -> Unit,
    onBandGainChange: (Int, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.equalizer_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.equalizer_back),
                    )
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.equalizer_enabled)) },
                        supportingContent = {
                            Text(stringResource(R.string.equalizer_realtime_hint))
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = onEnabledChange,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                EqualizerPresetSelector(
                    preset = settings.preset,
                    onPresetSelected = onPresetSelected,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.equalizer_bands),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        EQUALIZER_FREQUENCIES_HZ.forEachIndexed { index, frequencyHz ->
                            EqualizerBandSlider(
                                frequencyHz = frequencyHz,
                                gainDb = settings.gainsDb.getOrElse(index) { 0 },
                                onGainChange = { onBandGainChange(index, it) },
                            )
                            if (index < EQUALIZER_FREQUENCIES_HZ.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerPresetSelector(
    preset: EqualizerPreset,
    onPresetSelected: (EqualizerPreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = presetLabel(preset),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.equalizer_preset)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            EqualizerPreset.entries
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(presetLabel(option)) },
                        onClick = {
                            expanded = false
                            onPresetSelected(option)
                        },
                    )
                }
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    frequencyHz: Int,
    gainDb: Int,
    onGainChange: (Int) -> Unit,
) {
    var sliderValue by remember(gainDb) { mutableFloatStateOf(gainDb.toFloat()) }
    val displayedGain = sliderValue.roundToInt()
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = frequencyLabel(frequencyHz),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = gainLabel(displayedGain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onGainChange(displayedGain) },
            valueRange = EQUALIZER_MIN_GAIN_DB.toFloat()..EQUALIZER_MAX_GAIN_DB.toFloat(),
            steps = EQUALIZER_MAX_GAIN_DB - EQUALIZER_MIN_GAIN_DB - 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun presetLabel(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.FLAT -> stringResource(R.string.equalizer_preset_flat)
    EqualizerPreset.VOCAL -> stringResource(R.string.equalizer_preset_vocal)
    EqualizerPreset.BASS -> stringResource(R.string.equalizer_preset_bass)
    EqualizerPreset.POP -> stringResource(R.string.equalizer_preset_pop)
    EqualizerPreset.ROCK -> stringResource(R.string.equalizer_preset_rock)
    EqualizerPreset.CLASSICAL -> stringResource(R.string.equalizer_preset_classical)
    EqualizerPreset.CUSTOM -> stringResource(R.string.equalizer_preset_custom)
}

@Composable
private fun frequencyLabel(frequencyHz: Int): String = if (frequencyHz >= 1_000) {
    stringResource(R.string.equalizer_frequency_khz, frequencyHz / 1_000)
} else {
    stringResource(R.string.equalizer_frequency_hz, frequencyHz)
}

private fun gainLabel(gainDb: Int): String = when {
    gainDb > 0 -> "+$gainDb dB"
    else -> "$gainDb dB"
}
