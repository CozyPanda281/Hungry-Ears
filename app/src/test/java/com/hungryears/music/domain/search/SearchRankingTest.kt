package com.hungryears.music.domain.search

import com.hungryears.music.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `lowercases and trims`() {
        assertEquals("hello world", TextNormalizer.normalize("  Hello   World  "))
    }

    @Test
    fun `strips diacritics`() {
        assertEquals("cafe creme", TextNormalizer.normalize("Café Crème"))
        assertEquals("bjork", TextNormalizer.normalize("Björk"))
    }

    @Test
    fun `tokenizes normalized text`() {
        assertEquals(listOf("one", "direction"), TextNormalizer.tokenize("  One   Direction "))
    }

    @Test
    fun `blank text yields no tokens`() {
        assertTrue(TextNormalizer.tokenize("   ").isEmpty())
        assertEquals("", TextNormalizer.normalize("   "))
    }
}

class SearchRankingTest {

    private fun track(
        id: Long,
        title: String,
        artist: String = "Some Artist",
        album: String = "Some Album",
        genre: String = "Some Genre",
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        albumTitle = album,
        albumId = null,
        genre = genre,
        durationMs = 0L,
        trackNumber = 0,
        year = 0,
        folderPath = "",
        fileName = "$title.mp3",
        sizeBytes = 0L,
        dateAdded = 0L,
        dateModifiedMs = 0L,
        contentUri = "content://$id",
        container = "mp3",
        isSupported = true,
    )

    private class Signals(
        private val plays: Map<Long, Int> = emptyMap(),
        private val favourites: Set<Long> = emptySet(),
    ) : SearchSignals {
        override fun playCount(trackId: Long): Int = plays[trackId] ?: 0
        override fun isFavourite(trackId: Long): Boolean = trackId in favourites
    }

    @Test
    fun `empty or blank query returns nothing`() {
        val tracks = listOf(track(1, "History"))
        assertTrue(SearchRanking.rankTracks("", tracks).isEmpty())
        assertTrue(SearchRanking.rankTracks("   ", tracks).isEmpty())
    }

    @Test
    fun `unmatched query returns nothing`() {
        assertTrue(SearchRanking.rankTracks("zzzzzz", listOf(track(1, "History"))).isEmpty())
    }

    @Test
    fun `exact title outranks prefix and contains`() {
        val exact = track(1, "History")
        val prefix = track(2, "History of Rap")
        val contains = track(3, "Ancient History")
        val ranked = SearchRanking.rankTracks("history", listOf(contains, prefix, exact))
        assertEquals(listOf(1L, 2L, 3L), ranked.map { it.id })
    }

    @Test
    fun `combined title plus artist outranks partial artist`() {
        val combined = track(1, "History", artist = "One Direction")
        val partialArtist = track(2, "What Makes You Beautiful", artist = "One Direction")
        val ranked = SearchRanking.rankTracks("history one direction", listOf(partialArtist, combined))
        assertEquals(1L, ranked.first().id)
    }

    @Test
    fun `exact artist outranks partial title`() {
        val artistHit = track(1, "Viva la Vida", artist = "Coldplay")
        val titleHit = track(2, "Coldplay Tribute", artist = "Cover Band")
        val ranked = SearchRanking.rankTracks("coldplay", listOf(titleHit, artistHit))
        assertEquals(1L, ranked.first().id)
    }

    @Test
    fun `partial token coverage lowers but does not remove a track`() {
        val exactArtist = track(1, "Yellow", artist = "Coldplay")
        val partialArtist = track(2, "Fix You", artist = "Coldplay")
        val exactScore = SearchRanking.scoreTrack("coldplay", exactArtist)
        val partialScore = SearchRanking.scoreTrack("coldplay yellow", partialArtist)
        assertTrue(partialScore > 0)
        assertTrue(partialScore < exactScore)
    }

    @Test
    fun `diacritics are folded for matching`() {
        val score = SearchRanking.scoreTrack("cafe", track(1, "Café"))
        assertEquals(SearchRanking.EXACT_TITLE, score)
    }

    @Test
    fun `leading articles do not block matching`() {
        val beatles = track(1, "Hey Jude", artist = "The Beatles")
        assertEquals(SearchRanking.EXACT_ARTIST, SearchRanking.scoreTrack("beatles", beatles))
        assertEquals(SearchRanking.EXACT_ARTIST, SearchRanking.scoreTrack("the beatles", beatles))
        assertTrue(SearchRanking.scoreTrack("the", beatles) > 0)
    }

    @Test
    fun `history boost is capped`() {
        val noPlays = track(1, "Song", artist = "Band")
        val somePlays = track(2, "Song", artist = "Band")
        val manyPlays = track(3, "Song", artist = "Band")
        val signals = Signals(plays = mapOf(2L to 2, 3L to 1000))
        assertEquals(SearchRanking.EXACT_ARTIST, SearchRanking.scoreTrack("band", noPlays, signals))
        assertEquals(
            SearchRanking.EXACT_ARTIST + SearchRanking.HISTORY_CAP,
            SearchRanking.scoreTrack("band", manyPlays, signals),
        )
        assertTrue(
            SearchRanking.scoreTrack("band", somePlays, signals) >
                SearchRanking.scoreTrack("band", noPlays, signals),
        )
    }

    @Test
    fun `favourite bonus is applied once and clamped`() {
        val favourite = track(1, "Song", artist = "Band")
        val plain = track(2, "Song", artist = "Band")
        val signals = Signals(favourites = setOf(1L))
        assertEquals(
            SearchRanking.EXACT_ARTIST + SearchRanking.FAVOURITE_BONUS,
            SearchRanking.scoreTrack("band", favourite, signals),
        )
        assertEquals(SearchRanking.EXACT_ARTIST, SearchRanking.scoreTrack("band", plain, signals))
    }

    @Test
    fun `historical boost dominates an otherwise identical track`() {
        val never = track(1, "Song", artist = "Band")
        val popular = track(2, "Song", artist = "Band")
        val signals = Signals(plays = mapOf(2L to 3))
        val ranked = SearchRanking.rankTracks("band", listOf(never, popular), signals)
        assertEquals(2L, ranked.first().id)
    }

    @Test
    fun `equal scores break ties by title ascending`() {
        val zebra = track(1, "Zebra", artist = "Band")
        val apple = track(2, "Apple", artist = "Band")
        val ranked = SearchRanking.rankTracks("band", listOf(zebra, apple))
        assertEquals(listOf(2L, 1L), ranked.map { it.id })
    }

    @Test
    fun `ranking is deterministic across repeated calls`() {
        val tracks = listOf(
            track(1, "History", artist = "One Direction"),
            track(2, "History of Rap"),
            track(3, "Ancient History"),
        )
        val first = SearchRanking.rankTracks("history", tracks).map { it.id }
        val second = SearchRanking.rankTracks("history", tracks.reversed()).map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun `name scoring ranks exact above prefix above substring`() {
        assertEquals(SearchRanking.NAME_EXACT, SearchRanking.scoreName("rock", "Rock"))
        assertEquals(SearchRanking.NAME_PREFIX, SearchRanking.scoreName("roc", "Rock"))
        assertEquals(SearchRanking.NAME_CONTAINS, SearchRanking.scoreName("rock", "Classic Rock"))
        assertEquals(SearchRanking.NAME_PARTIAL, SearchRanking.scoreName("classic zzz", "Classic Rock"))
    }
}
