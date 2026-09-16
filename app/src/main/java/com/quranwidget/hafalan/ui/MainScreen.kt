package com.quranwidget.hafalan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranwidget.hafalan.AppScreen
import com.quranwidget.hafalan.MainUiState
import com.quranwidget.hafalan.data.PlaybackSpeeds
import com.quranwidget.hafalan.data.ProgressSummary
import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.data.SurahInfo
import com.quranwidget.hafalan.data.SurahProgress
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onChooseSurah: () -> Unit,
    onDismissSurahPicker: () -> Unit,
    onSurahSelected: (Int) -> Unit,
    onRemembered: () -> Unit,
    onPlay: () -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
    onScriptEdition: (ScriptEdition) -> Unit,
    onRepeatAyah: (Boolean) -> Unit,
    onShowProgress: () -> Unit,
    onShowHome: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (state.screen) {
        AppScreen.Progress -> ProgressScreen(
            progress = state.progress,
            onBack = onShowHome,
        )
        AppScreen.Home -> HomeScreen(
            state = state,
            onChooseSurah = onChooseSurah,
            onDismissSurahPicker = onDismissSurahPicker,
            onSurahSelected = onSurahSelected,
            onRemembered = onRemembered,
            onPlay = onPlay,
            onPlaybackSpeed = onPlaybackSpeed,
            onScriptEdition = onScriptEdition,
            onRepeatAyah = onRepeatAyah,
            onShowProgress = onShowProgress,
            onRefresh = onRefresh,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: MainUiState,
    onChooseSurah: () -> Unit,
    onDismissSurahPicker: () -> Unit,
    onSurahSelected: (Int) -> Unit,
    onRemembered: () -> Unit,
    onPlay: () -> Unit,
    onPlaybackSpeed: (Float) -> Unit,
    onScriptEdition: (ScriptEdition) -> Unit,
    onRepeatAyah: (Boolean) -> Unit,
    onShowProgress: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hafalan") },
                actions = {
                    IconButton(onClick = onShowProgress) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Progress")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh ayah")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "One ayah at a time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Listen, read, then mark when it sits with you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            TextButton(onClick = onChooseSurah) {
                Text(
                    text = "${state.surah.number}  ·  ${state.surah.nameTransliterated}  ·  ${state.surah.nameArabic}",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ayah ${state.hafalan.ayahNumber} of ${state.surah.ayahCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(28.dp))

            when {
                state.loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(40.dp))
                }
                else -> {
                    AyahArabicText(
                        text = state.ayahText.ifBlank { "…" },
                        script = state.scriptEdition,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPlay)
                            .padding(vertical = 16.dp),
                        style = TextStyle(
                            fontFamily = QuranFonts.UthmanicHafs,
                            fontSize = 32.sp,
                            lineHeight = 56.sp,
                            textAlign = TextAlign.Center,
                            textDirection = TextDirection.Rtl,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Arabic script",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            ScriptEditionChips(
                selected = state.scriptEdition,
                onSelect = onScriptEdition,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "King Fahad Complex",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Reciter speed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaybackSpeeds.all.forEach { speed ->
                    val selected = PlaybackSpeeds.normalize(state.playbackSpeed) == speed
                    FilterChip(
                        selected = selected,
                        onClick = { onPlaybackSpeed(speed) },
                        label = { Text(PlaybackSpeeds.label(speed)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Repeat ayah",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Loop while listening (app + widget)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
                Switch(
                    checked = state.repeatAyah,
                    onCheckedChange = onRepeatAyah,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    text = "  Listen with Al-Afasy",
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            val currentKey = "${state.hafalan.surahNumber}:${state.hafalan.ayahNumber}"
            val atLastAyah = state.hafalan.ayahNumber >= state.surah.ayahCount
            val lastAlreadyRemembered =
                atLastAyah && currentKey in state.progress.rememberedKeys
            OutlinedButton(
                onClick = onRemembered,
                enabled = !state.loading && !lastAlreadyRemembered,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (lastAlreadyRemembered) {
                        "Surah complete — well done"
                    } else {
                        "Remembered"
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Add the Hafalan ayah widget to your home screen for a quiet daily reminder. " +
                    "Tap the widget to listen right away — it won’t open the app. " +
                    "No streaks, no pressure — just the next line.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.surahPickerOpen) {
        ModalBottomSheet(
            onDismissRequest = onDismissSurahPicker,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SurahPicker(
                surahs = SurahCatalog.all,
                selected = state.surah.number,
                onSelected = onSurahSelected,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressScreen(
    progress: ProgressSummary,
    onBack: () -> Unit,
) {
    val started = progress.perSurah.filter { it.rememberedCount > 0 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Remembered ayahs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "%.1f%% of the Quran",
                        progress.overallPercent * 100f,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${progress.overallRemembered} / ${ProgressSummary.TOTAL_QURAN_AYAHS} ayahs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.overallPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = if (started.isEmpty()) {
                        "Mark Remembered on an ayah to start tracking."
                    } else {
                        "By surah"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
            items(started, key = { it.surah.number }) { row ->
                SurahProgressRow(row)
            }
        }
    }
}

@Composable
fun AyahArabicText(
    text: String,
    script: ScriptEdition,
    modifier: Modifier = Modifier,
    style: TextStyle,
) {
    val color = style.color
    if (script.usesTajweedMarkup && TajweedMarkup.looksLikeMarkup(text)) {
        Text(
            text = TajweedMarkup.toAnnotatedString(text, color),
            modifier = modifier,
            style = style,
        )
    } else {
        Text(
            text = if (TajweedMarkup.looksLikeMarkup(text)) {
                TajweedMarkup.plainText(text)
            } else {
                text
            },
            modifier = modifier,
            style = style,
        )
    }
}

@Composable
private fun ScriptEditionChips(
    selected: ScriptEdition,
    onSelect: (ScriptEdition) -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        selectedLabelColor = MaterialTheme.colorScheme.onBackground,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScriptEdition.entries.forEach { edition ->
            FilterChip(
                selected = selected == edition,
                onClick = { onSelect(edition) },
                label = { Text(edition.label) },
                colors = chipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == edition,
                    borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                ),
            )
        }
    }
}

@Composable
private fun SurahProgressRow(row: SurahProgress) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${row.surah.number}. ${row.surah.nameTransliterated}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format(
                    Locale.US,
                    "%d/%d · %.0f%%",
                    row.rememberedCount,
                    row.surah.ayahCount,
                    row.percent * 100f,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { row.percent.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SurahPicker(
    surahs: List<SurahInfo>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Pick a surah",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "Start small. You can change anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(surahs, key = { it.number }) { surah ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(surah.number) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${surah.number}. ${surah.nameTransliterated}",
                        color = if (surah.number == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = surah.nameArabic,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
