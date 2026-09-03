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

object PodcastBackup {

    private const val FILE_NAME = "podcasts.json"
    private const val SUB_DIR = "RadioStreamer"

    fun write(context: Context, json: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                deleteExisting(context)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR
                    )
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    SUB_DIR
                )
                dir.mkdirs()
                File(dir, FILE_NAME).writeText(json)
            }
        } catch (_: Exception) {
        }
    }

    fun read(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR + "/"
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(FILE_NAME, relPath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } else null
                }
            } else {
                val f = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    SUB_DIR + "/" + FILE_NAME
                )
                if (f.exists()) f.readText() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteExisting(context: Context) {
        val resolver = context.contentResolver
        val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR + "/"
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(FILE_NAME, relPath),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                )
                resolver.delete(uri, null, null)
            }
        }
    }
}
