package com.hungryears.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hungryears.music.R
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Folder
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun AlbumRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val songs = pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HungryEarsSpacing.xs),
    ) {
        AlbumArtwork(
            albumId = album.albumId,
            contentDescription = album.displayTitle,
            size = 56.dp,
        )
        Spacer(modifier = Modifier.width(HungryEarsSpacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.displayArtist} · $songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val songs = pluralStringResource(R.plurals.track_count, artist.trackCount, artist.trackCount)
    MediaGlyphRow(
        initials = artist.displayName.take(1).uppercase(),
        title = artist.displayName,
        subtitle = songs,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun GenreRow(genre: Genre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val songs = pluralStringResource(R.plurals.track_count, genre.trackCount, genre.trackCount)
    MediaGlyphRow(
        iconRes = R.drawable.ic_library_music,
        title = genre.displayName,
        subtitle = songs,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun FolderRow(folder: Folder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val songs = pluralStringResource(R.plurals.track_count, folder.trackCount, folder.trackCount)
    MediaGlyphRow(
        iconRes = R.drawable.ic_playlist,
        title = folder.displayName,
        subtitle = songs,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val songs = pluralStringResource(R.plurals.track_count, playlist.trackCount, playlist.trackCount)
    MediaGlyphRow(
        iconRes = R.drawable.ic_playlist,
        title = playlist.displayName,
        subtitle = songs,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MediaGlyphRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    initials: String? = null,
    iconRes: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HungryEarsSpacing.xs),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (initials != null) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(HungryEarsSpacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
