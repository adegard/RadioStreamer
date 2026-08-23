package com.adegard.radiostreamer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adegard.radiostreamer.data.Station

class StationAdapter(
    private val onPlayClick: (Station) -> Unit,
    private val onDeleteClick: (Station) -> Unit,
) : RecyclerView.Adapter<StationAdapter.Holder>() {

    private val items = mutableListOf<Station>()
    private var currentUrl: String? = null
    private var playing: Boolean = false

    fun submit(list: List<Station>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setCurrent(url: String?, playing: Boolean) {
        currentUrl = url
        this.playing = playing
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.stationName)
        val url: TextView = v.findViewById(R.id.stationUrl)
        val toggle: ImageButton = v.findViewById(R.id.btnPlayPause)
        val delete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_station, parent, false)
        )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val station = items[position]
        holder.name.text = station.name
        holder.url.text = station.url
        val active = station.url == currentUrl
        holder.toggle.setImageResource(
            if (active && playing) R.drawable.ic_pause else R.drawable.ic_play
        )
        holder.itemView.isSelected = active
        holder.toggle.setOnClickListener { onPlayClick(station) }
        holder.delete.setOnClickListener { onDeleteClick(station) }
    }
}
