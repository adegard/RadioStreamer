package com.adegard.radiostreamer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Station(val id: Long, val name: String, val url: String)

class StationStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("stations", Context.MODE_PRIVATE)

    fun load(): List<Station> {
        prefs.getString(KEY, null)?.let { return parse(it) }
        StationBackup.read(context)?.let { json ->
            val restored = parse(json)
            if (restored.isNotEmpty()) {
                saveToPrefs(restored)
                return restored
            }
        }
        return defaults()
    }

    fun save(stations: List<Station>) {
        val json = toJson(stations)
        prefs.edit().putString(KEY, json).apply()
        StationBackup.write(context, json)
    }

    fun add(name: String, url: String): Station {
        val list = load().toMutableList()
        val station = Station(System.currentTimeMillis(), name.trim(), url.trim())
        list.add(station)
        save(list)
        return station
    }

    fun remove(id: Long) {
        save(load().filterNot { it.id == id })
    }

    private fun saveToPrefs(stations: List<Station>) {
        prefs.edit().putString(KEY, toJson(stations)).apply()
    }

    private fun parse(json: String): List<Station> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Station(o.getLong("id"), o.getString("name"), o.getString("url"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun toJson(stations: List<Station>): String {
        val arr = JSONArray()
        stations.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("url", s.url))
        }
        return arr.toString()
    }

    private fun defaults(): List<Station> = DEFAULT_STATIONS.mapIndexed { i, (name, url) ->
        Station((i + 1).toLong(), name, url)
    }

    companion object {
        private const val KEY = "station_list_json"

        private val DEFAULT_STATIONS = listOf(
            "BBC World Service" to "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            "BBC Radio 1" to "https://stream.live.vc.bbcmedia.co.uk/bbc_radio_one",
            "FIP (Paris)" to "https://icecast.radiofrance.fr/fip-midfi.mp3",
            "Deutschlandfunk" to "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3",
            "KEXP Seattle" to "https://kexp-mp3-128.streamguys1.com/kexp128.mp3",
            "WFMU New York" to "https://stream0.wfmu.org/freeform-128k",
            "SomaFM Groove Salad" to "https://ice1.somafm.com/groovesalad-128-mp3",
            "Radio Paradise" to "https://stream.radioparadise.com/aac-320",
            "Radio Swiss Jazz" to "https://stream.srg-ssr.ch/m/rsj/mp3_128",
            "Jazz24" to "https://live.wostreaming.net/direct/ppm-jazz24mp3-ibc1"
        )
    }
}
