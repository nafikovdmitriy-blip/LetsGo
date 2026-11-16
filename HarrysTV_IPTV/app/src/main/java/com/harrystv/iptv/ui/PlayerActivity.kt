package com.harrystv.iptv.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.harrystv.iptv.R
import com.harrystv.iptv.api.XtreamApiService
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var osdBox: LinearLayout
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { osdBox.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        val title = intent.getStringExtra("TITLE") ?: "Live Stream"
        val streamId = intent.getIntExtra("STREAM_ID", 0)
        val isLive = intent.getBooleanExtra("IS_LIVE", false)

        osdBox = findViewById(R.id.osdInfoBox)
        findViewById<TextView>(R.id.tvChannelOverlay).text = title
        val tvSub = findViewById<TextView>(R.id.tvEpgOsdSub)
        playerView = findViewById(R.id.playerView)

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(streamUrl))
            exo.prepare()
            exo.playWhenReady = true
        }

        val session = SessionManager(this)
        if (isLive && streamId > 0 && session.getLoginMode() == "XTREAM") {
            lifecycleScope.launch {
                try {
                    val api = XtreamApiService.create(session.getServerUrl())
                    val epg = api.getShortEpg(session.getUsername(), session.getPassword(), streamId = streamId)
                    val list = epg.epgListings ?: emptyList()
                    if (list.isNotEmpty()) {
                        val nowTitle = EpgGuideActivity.decodeBase64IfNeeded(list[0].title)
                        val nextTitle = if (list.size > 1) EpgGuideActivity.decodeBase64IfNeeded(list[1].title) else ""
                        tvSub.text = if (nextTitle.isNotEmpty()) "Now: $nowTitle  |  Next: $nextTitle" else "Now: $nowTitle"
                    }
                } catch (_: Exception) {
                }
            }
        }

        showOsdTemporarily()
    }

    private fun showOsdTemporarily() {
        osdBox.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_INFO) {
            playerView.showController()
            showOsdTemporarily()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        hideHandler.removeCallbacks(hideRunnable)
        player?.release()
        player = null
    }
}
