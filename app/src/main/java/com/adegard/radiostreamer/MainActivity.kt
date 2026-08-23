package com.adegard.radiostreamer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adegard.radiostreamer.data.Station
import com.adegard.radiostreamer.data.StationStore
import com.adegard.radiostreamer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {

    private lateinit var store: StationStore
    private lateinit var adapter: StationAdapter

    private lateinit var emptyView: TextView
    private lateinit var miniPlayer: View
    private lateinit var miniTitle: TextView
    private lateinit var miniToggle: ImageButton

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var currentUrl: String? = null
    private var isPlaying: Boolean = false

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
            Toast.makeText(
                this@MainActivity,
                R.string.stream_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = StationStore(this)
        adapter = StationAdapter(
            onPlayClick = ::onStationPlayClicked,
            onDeleteClick = ::onStationDeleteClicked
        )

        val recycler = findViewById<RecyclerView>(R.id.recyclerStations)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        emptyView = findViewById(R.id.emptyView)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniTitle = findViewById(R.id.miniTitle)
        miniToggle = findViewById(R.id.miniToggle)

        setSupportActionBar(findViewById(R.id.toolbar))

        miniToggle.setOnClickListener { togglePlayPause() }

        requestPermissionsIfNeeded()
        reloadStations()
        connectController()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                showAddDialog()
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

    private fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    private fun onStationDeleteClicked(station: Station) {
        AlertDialog.Builder(this)
            .setTitle(station.name)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteStation(station) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteStation(station: Station) {
        store.remove(station.id)
        if (station.url == currentUrl) {
            controller?.stop()
            currentUrl = null
            refreshPlaybackUi()
        }
        reloadStations()
    }

    private fun showAddDialog() {
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
                if (!url.contains("://")) url = "http://" + url
                store.add(name, url)
                reloadStations()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reloadStations() {
        val stations = store.load()
        adapter.submit(stations)
        emptyView.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshPlaybackUi() {
        adapter.setCurrent(currentUrl, isPlaying)
        miniPlayer.visibility = if (currentUrl != null) View.VISIBLE else View.GONE
        val name = store.load().firstOrNull { it.url == currentUrl }?.name.orEmpty()
        miniTitle.text = name
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
