package com.hungryears.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.hungryears.music.R

@Composable
fun TopDestination.IconContent() {
    val tint = LocalContentColor.current
    val description = stringResource(labelRes)
    when (this) {
        TopDestination.Home -> Icon(Icons.Filled.Home, contentDescription = description, tint = tint)
        TopDestination.Search -> Icon(Icons.Filled.Search, contentDescription = description, tint = tint)
        TopDestination.Library -> Icon(
            painterResource(R.drawable.ic_library_music),
            contentDescription = description,
            tint = tint,
        )
        TopDestination.Playlists -> Icon(
            painterResource(R.drawable.ic_playlist),
            contentDescription = description,
            tint = tint,
        )
    }
}