package com.adegard.radiostreamer.data

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Podcast(
    val id: Long,
    val title: String,
    val feedUrl: String,
    val imageUrl: String? = null
)

class PodcastStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("podcasts", Context.MODE_PRIVATE)

    fun load(): List<Podcast> {
        prefs.getString(KEY, null)?.let { return parse(it) }
        PodcastBackup.read(context)?.let { json ->
            val restored = parse(json)
            if (restored.isNotEmpty()) {
                saveToPrefs(restored)
                return restored
            }
        }
        return emptyList()
    }

    fun save(podcasts: List<Podcast>) {
        val json = toJson(podcasts)
        prefs.edit().putString(KEY, json).apply()
        PodcastBackup.write(context, json)
    }

    fun add(title: String, feedUrl: String, imageUrl: String?): Podcast {
        val list = load().toMutableList()
        val podcast = Podcast(System.currentTimeMillis(), title.trim(), feedUrl.trim(), imageUrl)
        list.add(podcast)
        save(list)
        return podcast
    }

    fun remove(id: Long) {
        save(load().filterNot { it.id == id })
    }

    private fun saveToPrefs(podcasts: List<Podcast>) {
        prefs.edit().putString(KEY, toJson(podcasts)).apply()
    }

    private fun parse(json: String): List<Podcast> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Podcast(
                o.getLong("id"),
                o.getString("title"),
                o.getString("feedUrl"),
                if (o.has("imageUrl")) o.getString("imageUrl") else null
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun toJson(podcasts: List<Podcast>): String {
        val arr = JSONArray()
        podcasts.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("feedUrl", p.feedUrl)
                p.imageUrl?.let { put("imageUrl", it) }
            })
        }
        return arr.toString()
    }

    companion object {
        private const val KEY = "podcast_list_json"
    }
}
