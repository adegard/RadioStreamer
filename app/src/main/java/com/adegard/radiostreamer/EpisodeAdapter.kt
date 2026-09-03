package com.adegard.radiostreamer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adegard.radiostreamer.data.Episode

class EpisodeAdapter(
    private val onPlayClick: (Episode) -> Unit,
) : RecyclerView.Adapter<EpisodeAdapter.Holder>() {

    private val items = mutableListOf<Episode>()
    private var currentAudioUrl: String? = null
    private var playing = false

    fun submit(list: List<Episode>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setCurrent(url: String?, playing: Boolean) {
        currentAudioUrl = url
        this.playing = playing
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.episodeTitle)
        val date: TextView = v.findViewById(R.id.episodeDate)
        val duration: TextView = v.findViewById(R.id.episodeDuration)
        val play: ImageButton = v.findViewById(R.id.btnEpisodePlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ep = items[position]
        holder.title.text = ep.title
        holder.date.text = ep.pubDate
        holder.duration.text = ep.duration ?: ""
        val active = ep.audioUrl == currentAudioUrl
        holder.play.setImageResource(if (active && playing) R.drawable.ic_pause else R.drawable.ic_play)
        holder.play.setOnClickListener { onPlayClick(ep) }
    }
}
