package com.blissless.oni_extension_template

import android.content.Context

/**
 * Template scraper — implement your site's scraping logic here.
 *
 * Called by [ScraperProvider] with the three query parameters received from
 * the Oni main app:
 *   - `mangaName`  — English and/or Romaji title (from the `manga` URI param)
 *   - `anilistId`  — AniList ID (from the `anilistId` URI param; may be null)
 *   - `chapter`    — Chapter to fetch (from the `chapter` URI param). Accepts
 *                    a plain number (`"38"`), a decimal (`"1.5"`), a version
 *                    suffix (`"12v2"`), or a chapter title (`"Episode 38"`).
 *                    **Required** for the image-URL return format documented
 *                    in the repo README.
 *
 * Return a `Map<String, *>` (preferred) or a `List<*>` (legacy flat list).
 * [ScraperProvider.serializeResult] handles the JSON conversion either way.
 *
 * Reference implementation: https://github.com/Suntrax/mangadotnet-extension
 */
object TemplateScraper {

    fun scrape(
        context: Context,
        mangaName: String?,
        anilistId: String?,
        chapter: String?
    ): Any {
        // TODO: Implement your scraping logic here.

        // Example returning a single chapter with image URLs:
        // return mapOf(
        //     "totalChapters" to 105,
        //     "chapter" to mapOf(
        //         "number" to chapter,
        //         "title"  to "Episode 1",
        //         "group"  to "Scanlation Group",
        //         "images" to listOf(
        //             "https://example.com/chapters/.../001.webp",
        //             "https://example.com/chapters/.../002.webp"
        //         )
        //     )
        // )

        // Example returning an error. When the error is "chapter not found",
        // include totalChapters so the UI can show the valid range:
        // return mapOf(
        //     "totalChapters" to 105,
        //     "error" to "Chapter '$chapter' not found. Available range: 1–105."
        // )

        return mapOf("error" to "TemplateScraper.scrape() not implemented.")
    }
}
