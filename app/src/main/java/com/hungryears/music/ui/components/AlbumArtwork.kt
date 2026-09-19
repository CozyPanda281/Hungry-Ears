package com.hungryears.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.hungryears.music.R

@Composable
fun AlbumArtwork(
    albumId: Long?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val baseModifier = modifier.size(size).clip(MaterialTheme.shapes.small)
    val uri = albumId?.let { "content://media/external/audio/albumart/$it" }
    if (uri == null) {
        Box(
            modifier = baseModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_library_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = baseModifier,
            error = painterResource(R.drawable.ic_library_music),
        )
    }
}
