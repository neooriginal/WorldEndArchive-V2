package com.worldendarchive.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File

data class Row(val id: Long, val title: String, val category: String, val summary: String, val kind: String)

data class Entry(
    val id: Long, val slug: String, val lang: String, val title: String, val category: String, val kind: String,
    val summary: String, val body: String, val warnings: List<String>, val importance: Int,
    val sourceTitle: String, val sourceUrl: String?, val license: String, val otherLangId: Long?,
)

/** The prebuilt archive shipped inside the APK (assets/archive.db), copied to app storage on first start. */
class Archive private constructor(private val db: SQLiteDatabase) {

    val info: Map<String, String> by lazy {
        db.rawQuery("SELECT k, v FROM info", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
    }

    fun count(lang: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM entries WHERE lang = ?", arrayOf(lang)).use { it.moveToFirst(); it.getInt(0) }

    fun categories(lang: String): List<Pair<String, Int>> =
        db.rawQuery("SELECT category, COUNT(*) FROM entries WHERE lang = ? GROUP BY category ORDER BY 2 DESC", arrayOf(lang)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) }
        }

    private fun rows(sql: String, args: Array<String>): List<Row> = db.rawQuery(sql, args).use { c ->
        buildList { while (c.moveToNext()) add(Row(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))) }
    }

    private val cols = "e.id, e.title, e.category, e.summary, e.kind"

    fun search(text: String, lang: String, limit: Int = 60): List<Row> {
        val terms = Regex("[\\p{L}\\p{N}]+").findAll(text).map { it.value }.take(8).toList()
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" ") { "$it*" }
        // FTS4 has no built-in ranking: titles that contain the words first, then by importance
        val like = "%" + terms.joinToString("%") + "%"
        return rows(
            "SELECT $cols FROM entries_fts f JOIN entries e ON e.id = f.docid WHERE entries_fts MATCH ? AND e.lang = ? " +
                "ORDER BY CASE WHEN e.title LIKE ? THEN 0 ELSE 1 END, e.importance DESC LIMIT $limit",
            arrayOf(match, lang, like),
        )
    }

    fun byCategory(cat: String, lang: String, limit: Int = 300): List<Row> = rows(
        "SELECT $cols FROM entries e WHERE e.lang = ? AND e.category = ? ORDER BY e.importance DESC, e.title LIMIT $limit",
        arrayOf(lang, cat),
    )

    fun essentials(lang: String, limit: Int = 40): List<Row> = rows(
        "SELECT $cols FROM entries e WHERE e.lang = ? ORDER BY e.importance DESC, " +
            "CASE WHEN e.kind = 'howto' THEN 0 ELSE 1 END, e.title LIMIT $limit",
        arrayOf(lang),
    )

    fun entry(id: Long): Entry? = db.rawQuery(
        "SELECT id, slug, lang, title, category, kind, summary, body, warnings, importance, source_title, source_url, license " +
            "FROM entries WHERE id = ?", arrayOf(id.toString()),
    ).use { c ->
        if (!c.moveToFirst()) return null
        val slug = c.getString(1)
        val other = db.rawQuery("SELECT id FROM entries WHERE slug = ? AND id != ? LIMIT 1", arrayOf(slug, id.toString()))
            .use { o -> if (o.moveToFirst()) o.getLong(0) else null }
        val warnings = runCatching {
            val a = JSONArray(c.getString(8) ?: "[]"); List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
        Entry(c.getLong(0), slug, c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6),
            c.getString(7), warnings, c.getInt(9), c.getString(10) ?: "", c.getString(11), c.getString(12) ?: "", other)
    }

    companion object {
        fun open(ctx: Context): Archive {
            val file = ctx.getDatabasePath("archive.db")
            val prefs = ctx.getSharedPreferences("archive", Context.MODE_PRIVATE)
            val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
            if (!file.exists() || prefs.getLong("copied_for", -1) != version) {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                ctx.assets.open("archive.db").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
                tmp.renameTo(file)
                prefs.edit().putLong("copied_for", version).apply()
            }
            return Archive(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
        }
    }
}
