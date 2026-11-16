package com.harrystv.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.harrystv.iptv.model.CategoryItem
import kotlinx.coroutines.launch

data class DisplayEntry(
    val key: String,
    val id: Int,
    val title: String,
    val iconUrl: String?,
    val extension: String,
    val directUrl: String? = null,
    val isSeries: Boolean = false
)

class BrowserActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var api: XtreamApiService? = null
    private var mode: String = "LIVE"
    private var fullList: List<DisplayEntry> = emptyList()
    private lateinit var itemAdapter: EntryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        session = SessionManager(this)
        if (session.getLoginMode() == "XTREAM") {
            api = XtreamApiService.create(session.getServerUrl())
        }
        mode = intent.getStringExtra("MODE") ?: "LIVE"

        findViewById<TextView>(R.id.tvBrowserHeader).text = "$mode CATEGORIES"

        val rvCategories = findViewById<RecyclerView>(R.id.rvCategories)
        val rvItems = findViewById<RecyclerView>(R.id.rvItems)
        val etSearch = findViewById<EditText>(R.id.etSearch)

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvItems.layoutManager = LinearLayoutManager(this)

        itemAdapter = EntryAdapter(
            items = emptyList(),
            favorites = session.getFavorites(),
            onClick = { entry -> handleItemClick(entry) },
            onLongClick = { entry ->
                val added = session.toggleFavorite(entry.key)
                itemAdapter.updateFavorites(session.getFavorites())
                Toast.makeText(
                    this,
                    if (added) "Added to Favorites ★" else "Removed from Favorites",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        rvItems.adapter = itemAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.lowercase() ?: ""
                itemAdapter.update(fullList.filter { it.title.lowercase().contains(q) })
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        if (session.getLoginMode() == "M3U") {
            loadM3uCategories(rvCategories)
        } else {
            loadXtreamCategories(rvCategories)
        }
    }

    private fun handleItemClick(entry: DisplayEntry) {
        if (entry.isSeries && session.getLoginMode() == "XTREAM") {
            val intent = Intent(this, SeriesDetailActivity::class.java)
            intent.putExtra("SERIES_ID", entry.id)
            intent.putExtra("SERIES_NAME", entry.title)
            startActivity(intent)
            return
        }

        val streamUrl = entry.directUrl ?: when (mode) {
            "LIVE" -> "${session.getServerUrl()}/live/${session.getUsername()}/${session.getPassword()}/${entry.id}.m3u8"
            "VOD" -> "${session.getServerUrl()}/movie/${session.getUsername()}/${session.getPassword()}/${entry.id}.${entry.extension}"
            else -> "${session.getServerUrl()}/series/${session.getUsername()}/${session.getPassword()}/${entry.id}.${entry.extension}"
        }

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("STREAM_URL", streamUrl)
        intent.putExtra("TITLE", entry.title)
        intent.putExtra("STREAM_ID", entry.id)
        intent.putExtra("IS_LIVE", mode == "LIVE")
        startActivity(intent)
    }

    private fun loadM3uCategories(rvCategories: RecyclerView) {
        lifecycleScope.launch {
            try {
                var channels = M3uAndXmltvRepository.cachedM3uChannels
                if (channels.isEmpty()) {
                    channels = M3uAndXmltvRepository.loadM3uPlaylist(session.getM3uUrl())
                }
                val groups = channels.map { it.groupTitle }.distinct()
                val cats = listOf(CategoryItem("ALL", "All M3U Channels")) +
                        groups.map { CategoryItem(it, it) }

                rvCategories.adapter = CategoryAdapter(cats) { cat ->
                    val filtered = if (cat.categoryId == "ALL") channels else channels.filter { it.groupTitle == cat.categoryId }
                    fullList = filtered.mapIndexed { idx, ch ->
                        DisplayEntry(
                            key = "M3U_${ch.streamUrl}",
                            id = idx,
                            title = ch.name,
                            iconUrl = ch.logoUrl,
                            extension = "m3u8",
                            directUrl = ch.streamUrl
                        )
                    }
                    if (mode == "FAVORITES") {
                        val favs = session.getFavorites()
                        fullList = fullList.filter { favs.contains(it.key) }
                    }
                    itemAdapter.update(fullList)
                }

                fullList = channels.mapIndexed { idx, ch ->
                    DisplayEntry("M3U_${ch.streamUrl}", idx, ch.name, ch.logoUrl, "m3u8", ch.streamUrl)
                }
                if (mode == "FAVORITES") {
                    val favs = session.getFavorites()
                    fullList = fullList.filter { favs.contains(it.key) }
                }
                itemAdapter.update(fullList)
            } catch (e: Exception) {
                Toast.makeText(this@BrowserActivity, "Failed to load M3U playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadXtreamCategories(rvCategories: RecyclerView) {
        val service = api ?: return
        lifecycleScope.launch {
            try {
                val effectiveMode = if (mode == "FAVORITES") "LIVE" else mode
                val cats: List<CategoryItem> = when (effectiveMode) {
                    "LIVE" -> service.getLiveCategories(session.getUsername(), session.getPassword())
                    "VOD" -> service.getVodCategories(session.getUsername(), session.getPassword())
                    else -> service.getSeriesCategories(session.getUsername(), session.getPassword())
                }
                val allCats = listOf(CategoryItem("ALL", "All Channels / Titles")) + cats
                rvCategories.adapter = CategoryAdapter(allCats) { cat ->
                    loadXtreamItems(if (cat.categoryId == "ALL") null else cat.categoryId)
                }
                loadXtreamItems(null)
            } catch (e: Exception) {
                Toast.makeText(this@BrowserActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadXtreamItems(categoryId: String?) {
        val service = api ?: return
        lifecycleScope.launch {
            try {
                val effectiveMode = if (mode == "FAVORITES") "LIVE" else mode
                var entries = when (effectiveMode) {
                    "LIVE" -> service.getLiveStreams(session.getUsername(), session.getPassword(), categoryId = categoryId)
                        .map { DisplayEntry("LIVE_${it.streamId}", it.streamId, it.name, it.streamIcon, "m3u8") }
                    "VOD" -> service.getVodStreams(session.getUsername(), session.getPassword(), categoryId = categoryId)
                        .map { DisplayEntry("VOD_${it.streamId}", it.streamId, it.name, it.streamIcon, it.containerExtension ?: "mp4") }
                    else -> service.getSeries(session.getUsername(), session.getPassword(), categoryId = categoryId)
                        .map { DisplayEntry("SERIES_${it.seriesId}", it.seriesId, it.name, it.cover, "mp4", isSeries = true) }
                }
                if (mode == "FAVORITES") {
                    val favs = session.getFavorites()
                    entries = entries.filter { favs.contains(it.key) }
                }
                fullList = entries
                itemAdapter.update(fullList)
            } catch (e: Exception) {
                Toast.makeText(this@BrowserActivity, "Failed to load streams", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class CategoryAdapter(
        private val items: List<CategoryItem>,
        private val onClick: (CategoryItem) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {
        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tv: TextView = view.findViewById(R.id.tvTitle)
            val iv: ImageView = view.findViewById(R.id.ivIcon)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.iv.visibility = View.GONE
            holder.tv.text = item.categoryName
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = items.size
    }

    class EntryAdapter(
        private var items: List<DisplayEntry>,
        private var favorites: Set<String>,
        private val onClick: (DisplayEntry) -> Unit,
        private val onLongClick: (DisplayEntry) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {
        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tv: TextView = view.findViewById(R.id.tvTitle)
            val iv: ImageView = view.findViewById(R.id.ivIcon)
            val star: TextView = view.findViewById(R.id.tvStar)
        }
        fun update(newItems: List<DisplayEntry>) {
            items = newItems
            notifyDataSetChanged()
        }
        fun updateFavorites(newFavs: Set<String>) {
            favorites = newFavs
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tv.text = item.title
            holder.star.visibility = if (favorites.contains(item.key)) View.VISIBLE else View.GONE
            if (!item.iconUrl.isNullOrBlank()) {
                holder.iv.visibility = View.VISIBLE
                holder.iv.load(item.iconUrl)
            } else {
                holder.iv.visibility = View.GONE
            }
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
        override fun getItemCount(): Int = items.size
    }
}
