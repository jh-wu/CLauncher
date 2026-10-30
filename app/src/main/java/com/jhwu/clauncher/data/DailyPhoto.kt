package com.jhwu.clauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset

/** A downloaded photo of the day and the credit its license requires us to show. */
data class DailyPhoto(val date: String, val title: String, val credit: String, val file: File)

/**
 * Wikimedia Commons' "Picture of the day", via the Wikimedia featured-content feed.
 * Freely licensed and needs no API key. The feed also features news, history and wildlife
 * pictures, so only landscape-shaped scenery is used; other days fall back to the most recent
 * scenic one.
 */
object DailyPhotoRepository {
    private const val TAG = "DailyPhoto"
    private const val FEED_URL = "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/"
    private const val USER_AGENT = "CLauncher/1.0 (https://github.com/jh-wu/CLauncher)"
    private const val TARGET_WIDTH = 1920
    private const val DAYS_TO_LOOK_BACK = 30

    private val SCENIC = listOf(
        "landscape", "mountain", "lake", "valley", "sunset", "sunrise", "beach", "coast", "view of",
        "panorama", "forest", "fog", "aurora", "northern lights", "snow", "winter", "river", "hills",
        "fields", "glacier", "island", "desert", "dunes", "waterfall", "meadow", "fjord", "sea", "alps",
        "blue hour", "national park", "vineyard", "canyon", "clouds",
    )
    private val UNSUITABLE = listOf(
        "war", "airstrike", "attack", "damage", "destroyed", "killed", "victim", "massacre", "holocaust",
        "auschwitz", "military", "soldier", "explosion", "disaster", "flood", "fire", "protest", "funeral",
        "memorial", "nude",
    )

    private val _photo = MutableStateFlow<DailyPhoto?>(null)
    val photo: StateFlow<DailyPhoto?> = _photo.asStateFlow()
    private val mutex = Mutex()

    private fun imageFile(context: Context) = File(context.filesDir, "daily_photo.jpg")
    private fun infoFile(context: Context) = File(context.filesDir, "daily_photo.json")

    /** Loads the last downloaded photo, then downloads today's if it hasn't been yet. */
    suspend fun refresh(context: Context) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (_photo.value == null) _photo.value = loadSaved(context)
            val today = LocalDate.now(ZoneOffset.UTC)
            if (_photo.value?.date == today.toString()) return@withContext
            try {
                download(context, today)?.let { _photo.value = it }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't fetch the photo of the day", e)
            }
        }
    }

    private fun loadSaved(context: Context): DailyPhoto? {
        val info = infoFile(context)
        val image = imageFile(context)
        if (!info.exists() || !image.exists()) return null
        return runCatching {
            val json = JSONObject(info.readText())
            DailyPhoto(json.getString("date"), json.getString("title"), json.getString("credit"), image)
        }.getOrNull()
    }

    private fun download(context: Context, today: LocalDate): DailyPhoto? {
        for (daysBack in 0 until DAYS_TO_LOOK_BACK) {
            val day = today.minusDays(daysBack.toLong())
            val feed = JSONObject(fetchText(FEED_URL + "%d/%02d/%02d".format(day.year, day.monthValue, day.dayOfMonth)))
            val image = feed.optJSONObject("image") ?: continue
            val original = image.getJSONObject("image")
            val width = original.getInt("width")
            val height = original.getInt("height")
            if (width < height * 1.3) continue // Not a landscape photo; try the day before.
            val text = image.optJSONObject("description")?.optString("text").orEmpty().lowercase()
            val words = text.split(Regex("[^\\p{L}]+")).toSet()
            // Scenic terms match word prefixes ("mountain" in "mountains"); unsuitable ones whole words only.
            val scenic = SCENIC.any { term -> if (' ' in term) term in text else words.any { it.startsWith(term) } }
            val unsuitable = UNSUITABLE.any { term -> term in words }
            if (!scenic || unsuitable) continue

            val temp = File(context.filesDir, "daily_photo.tmp")
            val thumbnail = image.optJSONObject("thumbnail")?.optString("source").orEmpty()
            // Originals can be 50+ MB, so ask Wikimedia for a TV-sized thumbnail when the original is larger.
            val sized = thumbnail.replace(Regex("/\\d+px-"), "/${TARGET_WIDTH}px-")
            val candidates = buildList {
                if (width > TARGET_WIDTH && sized != thumbnail) add(sized)
                add(original.getString("source"))
            }
            if (candidates.none { runCatching { downloadTo(it, temp) }.isSuccess }) continue
            temp.renameTo(imageFile(context))

            val title = image.optJSONObject("description")?.optString("text")?.takeIf { it.isNotBlank() }
                ?: image.optString("title").removePrefix("File:").substringBeforeLast('.')
            val artist = image.optJSONObject("artist")?.optString("text")
                ?.split(Regex("\\s+"))?.joinToString(" ")?.trim()?.takeIf { it.isNotBlank() }
            val license = image.optJSONObject("license")?.optString("type")?.takeIf { it.isNotBlank() }
            val credit = listOfNotNull(artist, license, "Wikimedia Commons").joinToString(" · ")
            infoFile(context).writeText(
                JSONObject()
                    .put("date", today.toString())
                    .put("title", title.lines().first().take(140))
                    .put("credit", credit)
                    .toString(),
            )
            return loadSaved(context)
        }
        return null
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", USER_AGENT)
    }

    private fun fetchText(url: String): String {
        val connection = open(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode} for $url")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }
}
