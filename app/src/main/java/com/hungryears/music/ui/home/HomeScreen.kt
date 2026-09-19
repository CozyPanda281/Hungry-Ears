package com.hungryears.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hungryears.music.R
import com.hungryears.music.ui.components.EmptyState
import com.hungryears.music.ui.components.ScreenHeader
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        modifier = modifier,
    )
}

@Composable
fun HomeScreenContent(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HungryEarsSpacing.xl),
        ) {
            ScreenHeader(title = stringResource(R.string.home_title))
            if (state.continueListening.isNotEmpty() ||
                state.madeForYou.isNotEmpty() ||
                state.favouriteArtists.isNotEmpty() ||
                state.recentlyAdded.isNotEmpty()
            ) {
                // Personalized sections arrive in PHASE 7 (recommendation engine).
            } else {
                EmptyState(
                    icon = Icons.Filled.Home,
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }
    }
}