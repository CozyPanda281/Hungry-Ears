package com.hungryears.music.data.local.db

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The FTS5 index for local search (SEARCH_SYSTEM.md §2).
 *
 * Room cannot model FTS5 virtual tables, so this is created and maintained outside Room: an
 * external-content table over `tracks` plus triggers that keep it in sync with inserts, updates
 * and deletes. `tracks_fts` is never queried by Room-generated code — `SearchDao` uses a raw
 * query and the ordering is decided in `domain/search`.
 *
 * External-content columns are named after the corresponding `tracks` columns, which FTS5 requires
 * when it reads the content table. Because the platform SQLite is compiled without FTS5, the
 * database must run on [androidx.sqlite.driver.bundled.BundledSQLiteDriver] (configured in
 * [HungryEarsDatabase]).
 */
internal object FtsIndex {

    const val TABLE = "tracks_fts"
    private const val PREFS = "hungry_ears_fts"
    private const val KEY_SCHEMA = "tracks_fts_schema"

    /** Bump to force the index to be dropped and rebuilt when the schema here changes. */
    private const val SCHEMA_VERSION = 2

    private val DROPS = listOf(
        "DROP TRIGGER IF EXISTS tracks_fts_ai",
        "DROP TRIGGER IF EXISTS tracks_fts_ad",
        "DROP TRIGGER IF EXISTS tracks_fts_au",
        "DROP TABLE IF EXISTS $TABLE",
    )

    private val DDL = listOf(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(
            title, artist, album_title, genre,
            content='tracks',
            content_rowid='id',
            tokenize='unicode61 remove_diacritics 2'
        )
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS tracks_fts_ai AFTER INSERT ON tracks BEGIN
            INSERT INTO $TABLE(rowid, title, artist, album_title, genre)
            VALUES (new.id, new.title, new.artist, new.album_title, new.genre);
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS tracks_fts_ad AFTER DELETE ON tracks BEGIN
            INSERT INTO $TABLE($TABLE, rowid, title, artist, album_title, genre)
            VALUES ('delete', old.id, old.title, old.artist, old.album_title, old.genre);
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS tracks_fts_au AFTER UPDATE ON tracks BEGIN
            INSERT INTO $TABLE($TABLE, rowid, title, artist, album_title, genre)
            VALUES ('delete', old.id, old.title, old.artist, old.album_title, old.genre);
            INSERT INTO $TABLE(rowid, title, artist, album_title, genre)
            VALUES (new.id, new.title, new.artist, new.album_title, new.genre);
        END
        """.trimIndent(),
    )

    fun buildIfNeeded(context: Context, db: SupportSQLiteDatabase) = build(context, db::execSQL)

    fun buildIfNeeded(context: Context, connection: SQLiteConnection) =
        build(context) { sql -> connection.execute(sql) }

    /**
     * Ensures the schema exists and, the first time this schema generation is seen, drops any
     * stale index and rebuilds it from `tracks`. Afterwards the triggers keep it in sync.
     */
    private fun build(context: Context, execute: (String) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val needsRebuild = prefs.getInt(KEY_SCHEMA, 0) != SCHEMA_VERSION
        if (needsRebuild) DROPS.forEach(execute)
        DDL.forEach(execute)
        if (needsRebuild) {
            execute("INSERT INTO $TABLE($TABLE) VALUES('rebuild')")
            prefs.edit().putInt(KEY_SCHEMA, SCHEMA_VERSION).apply()
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }
}
