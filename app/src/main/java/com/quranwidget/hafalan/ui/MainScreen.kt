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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranwidget.hafalan.MainUiState
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.data.SurahInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onChooseSurah: () -> Unit,
    onDismissSurahPicker: () -> Unit,
    onSurahSelected: (Int) -> Unit,
    onRemembered: () -> Unit,
    onPlay: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hafalan") },
                actions = {
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
                    Text(
                        text = state.ayahText.ifBlank { "…" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPlay)
                            .padding(vertical = 16.dp),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp,
                            lineHeight = 52.sp,
                        ),
                        textAlign = TextAlign.Center,
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

            Spacer(Modifier.height(32.dp))
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
            OutlinedButton(
                onClick = onRemembered,
                enabled = !state.loading &&
                    state.hafalan.ayahNumber < state.surah.ayahCount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.hafalan.ayahNumber >= state.surah.ayahCount) {
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
                    "Tap the widget to listen. No streaks, no pressure — just the next line.",
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
