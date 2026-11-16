package com.harrystv.iptv.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.harrystv.iptv.R
import com.harrystv.iptv.api.M3uAndXmltvRepository
import com.harrystv.iptv.api.XtreamApiService
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private var isM3uMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (session.hasSession() && !intent.getBooleanExtra("switch_user", false)) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val btnModeXtream = findViewById<Button>(R.id.btnModeXtream)
        val btnModeM3u = findViewById<Button>(R.id.btnModeM3u)
        val layoutXtream = findViewById<LinearLayout>(R.id.layoutXtreamFields)
        val layoutM3u = findViewById<LinearLayout>(R.id.layoutM3uFields)

        val etProfile = findViewById<EditText>(R.id.etProfileName)
        val etUser = findViewById<EditText>(R.id.etUsername)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val etUrl = findViewById<EditText>(R.id.etServerUrl)
        val etM3u = findViewById<EditText>(R.id.etM3uUrl)
        val etXmltv = findViewById<EditText>(R.id.etXmltvUrl)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        fun updateTabStyles() {
            if (isM3uMode) {
                layoutXtream.visibility = View.GONE
                layoutM3u.visibility = View.VISIBLE
                btnModeM3u.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366F1"))
                btnModeM3u.setTextColor(Color.WHITE)
                btnModeXtream.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E1B4B"))
                btnModeXtream.setTextColor(Color.parseColor("#94A3B8"))
            } else {
                layoutXtream.visibility = View.VISIBLE
                layoutM3u.visibility = View.GONE
                btnModeXtream.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366F1"))
                btnModeXtream.setTextColor(Color.WHITE)
                btnModeM3u.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E1B4B"))
                btnModeM3u.setTextColor(Color.parseColor("#94A3B8"))
            }
        }

        btnModeXtream.setOnClickListener {
            isM3uMode = false
            updateTabStyles()
        }

        btnModeM3u.setOnClickListener {
            isM3uMode = true
            updateTabStyles()
        }

        btnLogin.setOnClickListener {
            val profile = etProfile.text.toString().ifBlank { "Harry's Playlist" }
            val customXmltv = etXmltv.text.toString().trim()

            btnLogin.isEnabled = false
            btnLogin.text = "CONNECTING..."

            lifecycleScope.launch {
                try {
                    if (isM3uMode) {
                        val m3uUrl = etM3u.text.toString().trim()
                        if (m3uUrl.isEmpty()) {
                            Toast.makeText(this@LoginActivity, "Enter an M3U playlist URL", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val channels = M3uAndXmltvRepository.loadM3uPlaylist(m3uUrl)
                        if (channels.isEmpty()) {
                            Toast.makeText(this@LoginActivity, "No channels found in M3U", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        session.saveM3uCredentials(profile, m3uUrl, customXmltv)
                        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                        finish()
                    } else {
                        val user = etUser.text.toString().trim()
                        val pass = etPass.text.toString().trim()
                        val url = etUrl.text.toString().trim()
                        if (user.isEmpty() || pass.isEmpty() || url.isEmpty()) {
                            Toast.makeText(this@LoginActivity, "Fill all Xtream Codes fields", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val api = XtreamApiService.create(url)
                        val resp = api.authenticate(user, pass)
                        if (resp.userInfo?.status != null) {
                            session.saveXtreamCredentials(
                                profile,
                                url,
                                user,
                                pass,
                                resp.userInfo.expDate ?: "Unlimited",
                                customXmltv
                            )
                            startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Invalid Xtream credentials", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    btnLogin.isEnabled = true
                    btnLogin.text = "CONNECT"
                }
            }
        }
    }
}
