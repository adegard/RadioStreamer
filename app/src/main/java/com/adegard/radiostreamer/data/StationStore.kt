package com.adegard.radiostreamer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Station(val id: Long, val name: String, val url: String)

class StationStore(context: Context) {

    private val prefs = context.getSharedPreferences("stations", Context.MODE_PRIVATE)

    fun load(): List<Station> {
        val raw = prefs.getString(KEY, null) ?: return defaults().also { save(it) }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Station(o.getLong("id"), o.getString("name"), o.getString("url"))
            }
        } catch (_: Exception) {
            defaults()
        }
    }

    fun save(stations: List<Station>) {
        val arr = JSONArray()
        stations.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("url", s.url))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
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

    private fun defaults(): List<Station> = DEFAULT_STATIONS.mapIndexed { i, (name, url) ->
        Station((i + 1).toLong(), name, url)
    }

    companion object {
        private const val KEY = "station_list_json"

        private val DEFAULT_STATIONS = listOf(
            "Radio Swiss Jazz" to "https://stream.srg-ssr.ch/m/rsj/mp3_128",
            "Radio Swiss Classic" to "https://stream.srg-ssr.ch/m/rsc/mp3_128",
            "Radio Swiss Pop" to "https://stream.srg-ssr.ch/m/rsp/mp3_128",
            "Smooth Jazz Florida" to "https://us4.internet-radio.com/proxy/smoothjazz?mp=/stream",
            "Cafe del Mar Radio" to "https://streams.cafedelmarradio.com:8443/cafedelmarradio",
            "Chilltrax" to "https://ais-sa2.cdnstream1.com/2445_128.mp3",
            "Ambient Sleeping Pill" to "http://radio.stereoscenic.com/asp-h",
            "Deep House Lounge" to "http://198.58.98.83:8006/stream",
            "BBC Radio 3" to "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_three",
            "BBC Radio 6 Music" to "http://stream.live.vc.bbcmedia.co.uk/bbc_6music",
            "NPR Classical" to "https://npr-ice.streamguys1.com/classical.mp3",
            "Jazz24" to "https://live.wostreaming.net/direct/ppm-jazz24mp3-ibc1",
            "The Jazz Groove (East)" to "https://jazzgroove-east.streamguys1.com/jazzgroove-east.mp3",
            "The Jazz Groove (West)" to "https://jazzgroove-west.streamguys1.com/jazzgroove-west.mp3",
            "Radio Paradise (Main Mix)" to "https://stream.radioparadise.com/aac-320",
            "Radio Paradise (Rock Mix)" to "https://stream.radioparadise.com/rock-320",
            "Radio Paradise (Mellow Mix)" to "https://stream.radioparadise.com/mellow-320",
            "Radio Paradise (World Mix)" to "https://stream.radioparadise.com/world-320",
            "Lounge Radio" to "http://stream.loungemusic.es:8000/lounge128.mp3",
            "Ibiza Sonica" to "http://s1.sonicabroadcast.com:8000/stream",
            "France Info" to "https://stream.radiofrance.fr/franceinfo/franceinfo_hifi.m3u8",
            "France Culture" to "https://stream.radiofrance.fr/franceculture/franceculture_hifi.m3u8"
        )
    }
}
