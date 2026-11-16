package com.harrystv.iptv.ui

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("harrys_tv_session", Context.MODE_PRIVATE)

    fun saveXtreamCredentials(
        profileName: String,
        serverUrl: String,
        username: String,
        password: String,
        expDate: String,
        customXmltvUrl: String = ""
    ) {
        prefs.edit()
            .putString("login_mode", "XTREAM")
            .putString("profile_name", profileName)
            .putString("server_url", serverUrl.trimEnd('/'))
            .putString("username", username)
            .putString("password", password)
            .putString("exp_date", expDate)
            .putString("xmltv_url", customXmltvUrl.ifBlank { "${serverUrl.trimEnd('/')}/xmltv.php?username=$username&password=$password" })
            .apply()
    }

    fun saveM3uCredentials(profileName: String, m3uUrl: String, customXmltvUrl: String) {
        prefs.edit()
            .putString("login_mode", "M3U")
            .putString("profile_name", profileName)
            .putString("m3u_url", m3uUrl.trim())
            .putString("xmltv_url", customXmltvUrl.trim())
            .apply()
    }

    fun getLoginMode(): String = prefs.getString("login_mode", "XTREAM") ?: "XTREAM"
    fun getServerUrl(): String = prefs.getString("server_url", "") ?: ""
    fun getUsername(): String = prefs.getString("username", "") ?: ""
    fun getPassword(): String = prefs.getString("password", "") ?: ""
    fun getM3uUrl(): String = prefs.getString("m3u_url", "") ?: ""
    fun getXmltvUrl(): String = prefs.getString("xmltv_url", "") ?: ""
    fun getProfileName(): String = prefs.getString("profile_name", "My Playlist") ?: "My Playlist"
    fun getExpDate(): String = prefs.getString("exp_date", "Active") ?: "Active"

    fun hasSession(): Boolean {
        return if (getLoginMode() == "M3U") {
            getM3uUrl().isNotEmpty()
        } else {
            getServerUrl().isNotEmpty() && getUsername().isNotEmpty()
        }
    }

    fun toggleFavorite(streamKey: String): Boolean {
        val current = getFavorites().toMutableSet()
        val added = if (current.contains(streamKey)) {
            current.remove(streamKey)
            false
        } else {
            current.add(streamKey)
            true
        }
        prefs.edit().putStringSet("favorites", current).apply()
        return added
    }

    fun getFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun clear() = prefs.edit().clear().apply()
}
