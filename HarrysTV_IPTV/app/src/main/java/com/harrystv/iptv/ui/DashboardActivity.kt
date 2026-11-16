package com.harrystv.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.harrystv.iptv.R
import com.harrystv.iptv.api.M3uAndXmltvRepository
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val session = SessionManager(this)
        val tvClock = findViewById<TextView>(R.id.tvClock)
        val tvFooter = findViewById<TextView>(R.id.tvAccountFooter)

        tvClock.text = SimpleDateFormat("hh:mm a  |  MMM dd, yyyy", Locale.getDefault()).format(Date())
        val modeInfo = if (session.getLoginMode() == "M3U") {
            "Mode: M3U Playlist"
        } else {
            "User: ${session.getUsername()}  •  Server: ${session.getServerUrl()}"
        }
        tvFooter.text = "Profile: ${session.getProfileName()}   •   $modeInfo"

        val tileLive = findViewById<View>(R.id.tileLiveTv)
        val tileMovies = findViewById<View>(R.id.tileMovies)
        val tileSeries = findViewById<View>(R.id.tileSeries)
        val tileEpg = findViewById<View>(R.id.tileEpgGuide)
        val tileFavorites = findViewById<View>(R.id.tileFavorites)
        val btnSwitch = findViewById<Button>(R.id.btnSwitchUser)

        tileLive.requestFocus()

        tileLive.setOnClickListener { openBrowser("LIVE") }
        tileMovies.setOnClickListener { openBrowser("VOD") }
        tileSeries.setOnClickListener { openBrowser("SERIES") }
        tileFavorites.setOnClickListener { openBrowser("FAVORITES") }
        tileEpg.setOnClickListener {
            startActivity(Intent(this, EpgGuideActivity::class.java))
        }

        btnSwitch.setOnClickListener {
            session.clear()
            M3uAndXmltvRepository.cachedM3uChannels = emptyList()
            M3uAndXmltvRepository.cachedXmltvPrograms = emptyMap()
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("switch_user", true)
            startActivity(intent)
            finish()
        }
    }

    private fun openBrowser(mode: String) {
        val intent = Intent(this, BrowserActivity::class.java)
        intent.putExtra("MODE", mode)
        startActivity(intent)
    }
}
