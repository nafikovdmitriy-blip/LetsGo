package com.harrystv.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.harrystv.iptv.R
import com.harrystv.iptv.api.M3uAndXmltvRepository
import com.harrystv.iptv.api.XtreamApiService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class EpgChannelRow(
    val streamId: Int,
    val channelName: String,
    val logoUrl: String?,
    val epgChannelId: String?,
    val streamUrl: String,
    var nowTitle: String = "Loading EPG...",
    var nextTitle: String = "—",
    var laterTitle: String = "—"
)

class EpgGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg_guide)

        val session = SessionManager(this)
        val rv = findViewById<RecyclerView>(R.id.rvEpgRows)
        rv.layoutManager = LinearLayoutManager(this)

        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val now = System.currentTimeMillis()
        findViewById<TextView>(R.id.tvSlot1).text = "NOW (${timeFmt.format(Date(now))})"
        findViewById<TextView>(R.id.tvSlot2).text = "NEXT (${timeFmt.format(Date(now + 3600_000L))})"
        findViewById<TextView>(R.id.tvSlot3).text = "LATER (${timeFmt.format(Date(now + 7200_000L))})"

        lifecycleScope.launch {
            try {
                val xmltvUrl = session.getXmltvUrl()
                val xmltvMap = if (xmltvUrl.isNotEmpty()) {
                    try {
                        M3uAndXmltvRepository.loadXmltvEpg(xmltvUrl)
                    } catch (_: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

                val rows = if (session.getLoginMode() == "M3U") {
                    var channels = M3uAndXmltvRepository.cachedM3uChannels
                    if (channels.isEmpty()) {
                        channels = M3uAndXmltvRepository.loadM3uPlaylist(session.getM3uUrl())
                    }
                    channels.take(150).mapIndexed { idx, ch ->
                        val progs = xmltvMap[ch.tvgId] ?: emptyList()
                        val upcoming = progs.filter { it.endMillis >= now }.sortedBy { it.startMillis }
                        EpgChannelRow(
                            streamId = idx,
                            channelName = ch.name,
                            logoUrl = ch.logoUrl,
                            epgChannelId = ch.tvgId,
                            streamUrl = ch.streamUrl,
                            nowTitle = upcoming.getOrNull(0)?.title ?: "Live Stream",
                            nextTitle = upcoming.getOrNull(1)?.title ?: "—",
                            laterTitle = upcoming.getOrNull(2)?.title ?: "—"
                        )
                    }
                } else {
                    val api = XtreamApiService.create(session.getServerUrl())
                    val streams = api.getLiveStreams(session.getUsername(), session.getPassword()).take(150)
                    streams.map { s ->
                        val progs = xmltvMap[s.epgChannelId] ?: emptyList()
                        val upcoming = progs.filter { it.endMillis >= now }.sortedBy { it.startMillis }
                        EpgChannelRow(
                            streamId = s.streamId,
                            channelName = s.name,
                            logoUrl = s.streamIcon,
                            epgChannelId = s.epgChannelId,
                            streamUrl = "${session.getServerUrl()}/live/${session.getUsername()}/${session.getPassword()}/${s.streamId}.m3u8",
                            nowTitle = upcoming.getOrNull(0)?.title ?: "Press OK to Watch",
                            nextTitle = upcoming.getOrNull(1)?.title ?: "—",
                            laterTitle = upcoming.getOrNull(2)?.title ?: "—"
                        )
                    }
                }

                rv.adapter = EpgRowAdapter(rows) { row ->
                    val intent = Intent(this@EpgGuideActivity, PlayerActivity::class.java)
                    intent.putExtra("STREAM_URL", row.streamUrl)
                    intent.putExtra("TITLE", "${row.channelName} — ${row.nowTitle}")
                    intent.putExtra("STREAM_ID", row.streamId)
                    intent.putExtra("IS_LIVE", true)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Toast.makeText(this@EpgGuideActivity, "EPG Load Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    class EpgRowAdapter(
        private val rows: List<EpgChannelRow>,
        private val onClick: (EpgChannelRow) -> Unit
    ) : RecyclerView.Adapter<EpgRowAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivLogo: ImageView = view.findViewById(R.id.ivEpgLogo)
            val tvChannel: TextView = view.findViewById(R.id.tvEpgChannel)
            val tvNow: TextView = view.findViewById(R.id.tvProgNow)
            val tvNext: TextView = view.findViewById(R.id.tvProgNext)
            val tvLater: TextView = view.findViewById(R.id.tvProgLater)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_epg_row, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            holder.tvChannel.text = row.channelName
            holder.tvNow.text = row.nowTitle
            holder.tvNext.text = row.nextTitle
            holder.tvLater.text = row.laterTitle

            if (!row.logoUrl.isNullOrBlank()) {
                holder.ivLogo.visibility = View.VISIBLE
                holder.ivLogo.load(row.logoUrl)
            } else {
                holder.ivLogo.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(row) }
        }

        override fun getItemCount(): Int = rows.size
    }

    companion object {
        fun decodeBase64IfNeeded(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return try {
                String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                raw
            }
        }
    }
}
