package com.harrystv.iptv.ui

import android.content.Intent
import android.os.Bundle
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
import com.harrystv.iptv.api.XtreamApiService
import com.harrystv.iptv.model.EpisodeItem
import kotlinx.coroutines.launch

class SeriesDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_detail)

        val session = SessionManager(this)
        val seriesId = intent.getIntExtra("SERIES_ID", 0)
        val fallbackName = intent.getStringExtra("SERIES_NAME") ?: "Series"

        val ivCover = findViewById<ImageView>(R.id.ivSeriesCover)
        val tvTitle = findViewById<TextView>(R.id.tvSeriesTitle)
        val tvPlot = findViewById<TextView>(R.id.tvSeriesPlot)
        val rvEpisodes = findViewById<RecyclerView>(R.id.rvEpisodes)
        rvEpisodes.layoutManager = LinearLayoutManager(this)
        tvTitle.text = fallbackName

        lifecycleScope.launch {
            try {
                val api = XtreamApiService.create(session.getServerUrl())
                val resp = api.getSeriesInfo(session.getUsername(), session.getPassword(), seriesId = seriesId)
                tvTitle.text = resp.info?.name ?: fallbackName
                tvPlot.text = resp.info?.plot ?: ""
                if (!resp.info?.cover.isNullOrBlank()) {
                    ivCover.load(resp.info?.cover)
                }

                val allEpisodes = mutableListOf<Pair<String, EpisodeItem>>()
                resp.episodes?.toSortedMap()?.forEach { (seasonNum, epList) ->
                    epList.forEach { ep ->
                        allEpisodes.add("S${seasonNum.padStart(2, '0')}E${ep.episodeNum.toString().padStart(2, '0')}" to ep)
                    }
                }

                rvEpisodes.adapter = EpisodeAdapter(allEpisodes) { label, ep ->
                    val ext = ep.containerExtension ?: "mp4"
                    val url = "${session.getServerUrl()}/series/${session.getUsername()}/${session.getPassword()}/${ep.id}.$ext"
                    val intent = Intent(this@SeriesDetailActivity, PlayerActivity::class.java)
                    intent.putExtra("STREAM_URL", url)
                    intent.putExtra("TITLE", "${tvTitle.text} - $label: ${ep.title ?: ""}")
                    intent.putExtra("IS_LIVE", false)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Toast.makeText(this@SeriesDetailActivity, "Failed to load episodes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class EpisodeAdapter(
        private val items: List<Pair<String, EpisodeItem>>,
        private val onClick: (String, EpisodeItem) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.VH>() {
        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tv: TextView = view.findViewById(R.id.tvTitle)
            val iv: ImageView = view.findViewById(R.id.ivIcon)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (label, ep) = items[position]
            holder.iv.visibility = View.GONE
            holder.tv.text = "$label  •  ${ep.title ?: "Episode ${ep.episodeNum}"}"
            holder.itemView.setOnClickListener { onClick(label, ep) }
        }
        override fun getItemCount(): Int = items.size
    }
}
