package com.adegard.radiostreamer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adegard.radiostreamer.data.Podcast

class PodcastAdapter(
    private val onPlayClick: (Podcast) -> Unit,
    private val onDeleteClick: (Podcast) -> Unit,
) : RecyclerView.Adapter<PodcastAdapter.Holder>() {

    private val items = mutableListOf<Podcast>()

    fun submit(list: List<Podcast>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.podcastTitle)
        val url: TextView = v.findViewById(R.id.podcastUrl)
        val play: ImageButton = v.findViewById(R.id.btnPodcastPlay)
        val delete: ImageButton = v.findViewById(R.id.btnPodcastDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_podcast, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val podcast = items[position]
        holder.title.text = podcast.title
        holder.url.text = podcast.feedUrl
        holder.play.setOnClickListener { onPlayClick(podcast) }
        holder.delete.setOnClickListener { onDeleteClick(podcast) }
    }
}
