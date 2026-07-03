package com.blissless.oni_extension_template

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.iterator

/**
 * ContentProvider queried by the Oni main app.
 *
 * Query URI (`chapter` is REQUIRED for the image-URL return format):
 *   content://com.blissless.oni_extension_template.provider/scrape
 *     ?manga=<title>&anilistId=<id>&chapter=<number-or-title>
 *
 * Returns a single-row MatrixCursor whose "data" column holds the JSON string
 * produced by [serializeResult]. See [TemplateScraper] for the expected
 * return shapes.
 */
class ScraperProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.blissless.oni_extension_template.provider"
        const val PATH_SCRAPE = "scrape"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SCRAPE")
        private const val CODE_SCRAPES = 1
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_SCRAPE, CODE_SCRAPES)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        when (uriMatcher.match(uri)) {
            CODE_SCRAPES -> {
                val mangaName = uri.getQueryParameter("manga")
                val anilistId = uri.getQueryParameter("anilistId")
                val chapter   = uri.getQueryParameter("chapter")
                val cursor = MatrixCursor(arrayOf("data"))

                try {
                    val result = TemplateScraper.scrape(
                        context!!, mangaName, anilistId, chapter
                    )
                    val json = serializeResult(result)
                    cursor.addRow(arrayOf(json))
                } catch (e: Exception) {
                    cursor.addRow(arrayOf(
                        "{\"error\":\"Scraping failed: " +
                                "${e.message?.replace("\"", "\\\"")}\"}"
                    ))
                }
                return cursor
            }
        }
        return null
    }

    /**
     * Serializes the scraper result to JSON. Supports:
     *   - Map<String, *>  -> {"key": value, ...}  (totalChapters/chapter/error/...)
     *   - List<*>         -> ["...", "..."]       (flat list — legacy)
     *
     * Nested values may be Map, List, JSONArray, JSONObject, null, or any
     * primitive (Int, Long, String, Boolean, etc.) accepted by
     * [JSONObject.put].
     */
    private fun serializeResult(result: Any): String {
        return when (result) {
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((key, value) in result) {
                    when (value) {
                        is Map<*, *> -> obj.put(key.toString(), JSONObject(value as Map<*, *>))
                        is List<*> -> {
                            val arr = JSONArray()
                            for (item in value) arr.put(item)
                            obj.put(key.toString(), arr)
                        }
                        is JSONArray -> obj.put(key.toString(), value)
                        is JSONObject -> obj.put(key.toString(), value)
                        null -> obj.put(key.toString(), JSONObject.NULL)
                        else -> obj.put(key.toString(), value)
                    }
                }
                obj.toString()
            }
            is List<*> -> {
                val arr = JSONArray()
                for (item in result) arr.put(item)
                arr.toString()
            }
            else -> result.toString()
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selectionArgs: Array<String>?): Int = 0
}
