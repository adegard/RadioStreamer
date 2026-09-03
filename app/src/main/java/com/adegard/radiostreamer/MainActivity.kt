package com.adegard.radiostreamer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adegard.radiostreamer.data.*
import com.adegard.radiostreamer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var stationStore: StationStore
    private lateinit var stationAdapter: StationAdapter
    private lateinit var podcastStore: PodcastStore
    private lateinit var podcastAdapter: PodcastAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    private lateinit var radioContainer: View
    private lateinit var podcastContainer: View
    private lateinit var podcastListLevel: View
    private lateinit var episodeListLevel: View
    private lateinit var emptyView: View
    private lateinit var emptyPodcastsView: View
    private lateinit var emptyEpisodesView: View
    private lateinit var miniPlayer: View
    private lateinit var miniTitle: TextView
    private lateinit var miniToggle: ImageButton
    private lateinit var searchPodcasts: EditText
    private lateinit var episodePodcastTitle: TextView

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var currentUrl: String? = null
    private var isPlaying = false

    private var currentPodcastId: Long? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            refreshPlaybackUi()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentUrl = mediaItem?.localConfiguration?.uri?.toString()
            refreshPlaybackUi()
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@MainActivity, R.string.stream_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stationStore = StationStore(this)
        podcastStore = PodcastStore(this)

        stationAdapter = StationAdapter(
            onPlayClick = ::onStationPlayClicked,
            onDeleteClick = ::onStationDeleteClicked
        )
        podcastAdapter = PodcastAdapter(
            onPlayClick = ::onPodcastPlayClicked,
            onDeleteClick = ::onPodcastDeleteClicked
        )
        episodeAdapter = EpisodeAdapter(
            onPlayClick = ::onEpisodePlayClicked
        )

        radioContainer = findViewById(R.id.radioContainer)
        podcastContainer = findViewById(R.id.podcastContainer)
        podcastListLevel = findViewById(R.id.podcastListLevel)
        episodeListLevel = findViewById(R.id.episodeListLevel)
        emptyView = findViewById(R.id.emptyView)
        emptyPodcastsView = findViewById(R.id.emptyPodcastsView)
        emptyEpisodesView = findViewById(R.id.emptyEpisodesView)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniTitle = findViewById(R.id.miniTitle)
        miniToggle = findViewById(R.id.miniToggle)
        searchPodcasts = findViewById(R.id.searchPodcasts)
        episodePodcastTitle = findViewById(R.id.episodePodcastTitle)

        val recyclerStations = findViewById<RecyclerView>(R.id.recyclerStations)
        recyclerStations.layoutManager = LinearLayoutManager(this)
        recyclerStations.adapter = stationAdapter

        val recyclerPodcasts = findViewById<RecyclerView>(R.id.recyclerPodcasts)
        recyclerPodcasts.layoutManager = LinearLayoutManager(this)
        recyclerPodcasts.adapter = podcastAdapter

        val recyclerEpisodes = findViewById<RecyclerView>(R.id.recyclerEpisodes)
        recyclerEpisodes.layoutManager = LinearLayoutManager(this)
        recyclerEpisodes.adapter = episodeAdapter

        setSupportActionBar(findViewById(R.id.toolbar))
        miniToggle.setOnClickListener { togglePlayPause() }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { showPodcastList() }

        searchPodcasts.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                val all = podcastStore.load()
                val filtered = if (query.isBlank()) all
                else all.filter { it.title.contains(query, ignoreCase = true) }
                podcastAdapter.submit(filtered)
                emptyPodcastsView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
            .setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_radio -> {
                        radioContainer.visibility = View.VISIBLE
                        podcastContainer.visibility = View.GONE
                        title = getString(R.string.app_name)
                        true
                    }
                    R.id.nav_podcasts -> {
                        radioContainer.visibility = View.GONE
                        podcastContainer.visibility = View.VISIBLE
                        showPodcastList()
                        title = getString(R.string.tab_podcasts)
                        true
                    }
                    else -> false
                }
            }

        requestPermissionsIfNeeded()
        reloadStations()
        reloadPodcasts()
        connectController()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                if (podcastContainer.visibility == View.VISIBLE) showAddPodcastDialog()
                else showAddStationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }

    // ---- Radio ----

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                currentUrl = c.currentMediaItem?.localConfiguration?.uri?.toString()
                isPlaying = c.isPlaying
                refreshPlaybackUi()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.player_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onStationPlayClicked(station: Station) {
        val c = controller ?: return
        if (currentUrl == station.url) {
            if (c.isPlaying) c.pause() else c.play()
        } else {
            c.setMediaItem(MediaItem.fromUri(station.url))
            c.prepare()
            c.play()
        }
    }

    private fun onStationDeleteClicked(station: Station) {
        AlertDialog.Builder(this)
            .setTitle(station.name)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                stationStore.remove(station.id)
                if (station.url == currentUrl) {
                    controller?.stop(); currentUrl = null; refreshPlaybackUi()
                }
                reloadStations()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddStationDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_station, null)
        val nameEdit = view.findViewById<TextView>(R.id.editName)
        val urlEdit = view.findViewById<TextView>(R.id.editUrl)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_station)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = nameEdit.text.toString().trim()
                var url = urlEdit.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(this, R.string.fields_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!url.contains("://")) url = "http://$url"
                stationStore.add(name, url)
                reloadStations()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reloadStations() {
        val stations = stationStore.load()
        stationAdapter.submit(stations)
        emptyView.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---- Podcasts ----

    private fun onPodcastPlayClicked(podcast: Podcast) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, R.string.adding_podcast, Toast.LENGTH_SHORT).show()
            val feed = PodcastFetcher.fetchFeed(podcast.feedUrl)
            if (feed != null) {
                showEpisodeList(podcast.title, feed.episodes)
            } else {
                Toast.makeText(this@MainActivity, R.string.podcast_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onPodcastDeleteClicked(podcast: Podcast) {
        AlertDialog.Builder(this)
            .setTitle(podcast.title)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                podcastStore.remove(podcast.id)
                if (podcastListLevel.visibility == View.VISIBLE) reloadPodcasts()
                else showPodcastList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onEpisodePlayClicked(episode: Episode) {
        val c = controller ?: return
        c.setMediaItem(MediaItem.fromUri(episode.audioUrl))
        c.prepare()
        c.play()
        episodeAdapter.setCurrent(episode.audioUrl, true)
    }

    private fun showAddPodcastDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_station, null)
        val nameEdit = view.findViewById<TextView>(R.id.editName)
        val urlEdit = view.findViewById<TextView>(R.id.editUrl)
        nameEdit.visibility = View.GONE
        view.findViewById<TextView>(R.id.labelName)?.visibility = View.GONE
        urlEdit.hint = getString(R.string.podcast_url_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_podcast)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val input = urlEdit.text.toString().trim()
                if (input.isEmpty()) {
                    Toast.makeText(this, R.string.fields_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (looksLikeUrl(input)) addPodcastByUrl(input)
                else searchAndAddPodcast(input)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun looksLikeUrl(s: String): Boolean {
        return s.contains("://") || s.endsWith(".xml") || s.endsWith(".rss") ||
                s.contains("/feed") || s.contains("podcast") && s.contains(".")
    }

    private fun addPodcastByUrl(url: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, R.string.adding_podcast, Toast.LENGTH_SHORT).show()
            var feedUrl = url
            var feed = PodcastFetcher.fetchFeed(url)
            if (feed == null) {
                val discovered = PodcastFetcher.discoverRssUrl(url)
                if (discovered != null) {
                    feedUrl = discovered
                    feed = PodcastFetcher.fetchFeed(discovered)
                }
            }
            if (feed != null && feed.episodes.isNotEmpty()) {
                val podcast = podcastStore.add(feed.title, feedUrl, feed.imageUrl)
                reloadPodcasts()
                showEpisodeList(feed.title, feed.episodes)
                Toast.makeText(this@MainActivity, R.string.podcast_added, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.podcast_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchAndAddPodcast(query: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, R.string.adding_podcast, Toast.LENGTH_SHORT).show()
            val results = PodcastFetcher.searchITunes(query)
            if (results.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.podcast_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = results.map { "${it.name}\n${it.artistName}" }.toTypedArray()
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.select_podcast)
                .setItems(names) { _, which ->
                    val r = results[which]
                    addPodcastByUrl(r.feedUrl!!)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun reloadPodcasts() {
        val podcasts = podcastStore.load()
        podcastAdapter.submit(podcasts)
        emptyPodcastsView.visibility = if (podcasts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showPodcastList() {
        podcastListLevel.visibility = View.VISIBLE
        episodeListLevel.visibility = View.GONE
        currentPodcastId = null
        reloadPodcasts()
    }

    private fun showEpisodeList(podcastTitle: String, episodes: List<Episode>) {
        episodePodcastTitle.text = podcastTitle
        episodeAdapter.submit(episodes)
        episodeAdapter.setCurrent(currentUrl, isPlaying)
        episodeListLevel.visibility = View.VISIBLE
        podcastListLevel.visibility = View.GONE
        emptyEpisodesView.visibility = if (episodes.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---- Shared ----

    private fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    private fun refreshPlaybackUi() {
        stationAdapter.setCurrent(currentUrl, isPlaying)
        episodeAdapter.setCurrent(currentUrl, isPlaying)
        miniPlayer.visibility = if (currentUrl != null) View.VISIBLE else View.GONE
        val stationName = stationStore.load().firstOrNull { it.url == currentUrl }?.name
        miniTitle.text = stationName ?: currentUrl?.substringAfterLast("/") ?: ""
        miniToggle.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT < 29) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}
