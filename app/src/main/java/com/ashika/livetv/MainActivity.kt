package com.ashika.livetv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import android.content.Intent
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val bg = Color.rgb(7, 12, 25)
    private val panel = Color.rgb(16, 27, 47)
    private val blue = Color.rgb(38, 112, 255)
    private val red = Color.rgb(245, 65, 85)
    private val white = Color.WHITE
    private val muted = Color.rgb(155, 170, 195)

    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var channelList: LinearLayout
    private lateinit var categoryList: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var drawer: LinearLayout

    private val channels =
        mutableListOf<Triple<String, String, String>>()

    private val playlistChannels =
        mutableListOf<Triple<String, String, String>>()

    private val favorites = mutableSetOf<String>()
    private val history = mutableListOf<String>()

    private var channelsListener: ListenerRegistration? = null
    private var playlistListener: ListenerRegistration? = null

    private var selectedCategory = "All"
    private var page = "home"
    private var query = ""

    private var lastPlaylistUrl = ""
    private var playlistRequestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("ashika_data", MODE_PRIVATE)

        favorites.addAll(
            prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        )

        history.addAll(
            prefs.getString("history", "")
                ?.split("\n")?.filter { it.isNotBlank() }
                ?: emptyList()
        )

        buildHome()
        loadChannels()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 16): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun label(
        text: String,
        size: Float,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun button(
        text: String,
        action: () -> Unit
    ): TextView {
        return label(text, 15f, white, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(blue, 14)
            setOnClickListener { action() }
        }
    }

    private fun buildHome() {
        val outer = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val menu = label("☰", 27f).apply {
            setPadding(dp(8), dp(8), dp(15), dp(8))
            setOnClickListener { showDrawer() }
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        brand.addView(label("ASHIKA", 23f, white, true))
        brand.addView(label("LIVE TELEVISION", 9f, muted, true))

        val live = label("● LIVE", 12f, Color.rgb(255, 90, 105), true)
            .apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(0x332F5BFF, 20)
            }

        header.addView(menu)
        header.addView(brand)
        header.addView(live)

        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(25, 77, 160),
                    Color.rgb(13, 35, 76),
                    Color.rgb(29, 20, 57)
                )
            ).apply { cornerRadius = dp(22).toFloat() }
        }

        hero.addView(label("WELCOME TO", 11f, 0xFFBFD6FF.toInt(), true))
        hero.addView(
            label("ASHIKA LIVE TV", 25f, white, true).apply {
                setPadding(0, dp(7), 0, dp(5))
            }
        )
        hero.addView(
            label("Your entertainment. One destination.", 12f, 0xFFD4E2FF.toInt())
        )

        val heroButton = button("▶   Explore Channels") {
            page = "home"
            selectedCategory = "All"
            renderCategories()
            renderChannels()
        }

        val heroParams = LinearLayout.LayoutParams(-2, -2)
        heroParams.topMargin = dp(17)
        hero.addView(heroButton, heroParams)

        val heroParamsOuter = LinearLayout.LayoutParams(-1, -2)
        heroParamsOuter.topMargin = dp(18)
        root.addView(hero, heroParamsOuter)

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, dp(10))
        }

        section.addView(
            label("Discover Channels", 19f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val refresh = label("↻ Refresh", 13f, 0xFF82B5FF.toInt(), true)
        refresh.setOnClickListener {
            lastPlaylistUrl = ""
            loadChannels()
        }
        section.addView(refresh)

        root.addView(section)

        search = EditText(this).apply {
            hint = "⌕   Search channels..."
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(panel, 14)
        }

        root.addView(search)

        categoryList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(categoryList)
        }

        val catParams = LinearLayout.LayoutParams(-1, -2)
        catParams.topMargin = dp(13)
        root.addView(categoryScroll, catParams)

        status = label("Connecting to Firebase...", 12f, muted)
        status.setPadding(0, dp(13), 0, dp(8))
        root.addView(status)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(channelList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        outer.addView(root)

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(20))
            background = rounded(Color.rgb(13, 24, 44), 0)
            elevation = dp(20).toFloat()
            visibility = View.GONE
        }

        outer.addView(
            drawer,
            FrameLayout.LayoutParams(
                dp(285),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
        )

        drawer.setOnClickListener { }

        setContentView(outer)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                query = s.toString()
                renderChannels()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showDrawer() {
        drawer.removeAllViews()

        drawer.addView(label("ASHIKA", 25f, white, true))
        drawer.addView(label("PREMIUM LIVE TV", 10f, muted, true))

        addDrawerItem("🏠   Home") {
            page = "home"
            selectedCategory = "All"
            renderCategories()
            renderChannels()
        }

        addDrawerItem("📺   All Channels") {
            page = "home"
            selectedCategory = "All"
            renderCategories()
            renderChannels()
        }

        addDrawerItem("❤️   My Favorites") {
            page = "favorites"
            renderChannels()
        }

        addDrawerItem("🕘   Recently Watched") {
            page = "history"
            renderChannels()
        }

        addDrawerItem("🔄   Refresh Channels") {
            lastPlaylistUrl = ""
            loadChannels()
        }

        addDrawerItem("⚙️   Settings") {
            AlertDialog.Builder(this)
                .setTitle("Ashika Live TV Settings")
                .setMessage(
                    "Premium Dark Theme\n" +
                    "Source: Firebase + M3U Playlist\n" +
                    "Channels: ${channels.size}"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        addDrawerItem("ℹ️   About") {
            AlertDialog.Builder(this)
                .setTitle("Ashika Live TV")
                .setMessage("Your personal entertainment destination.")
                .setPositiveButton("OK", null)
                .show()
        }

        drawer.visibility = View.VISIBLE
    }

    private fun addDrawerItem(
        text: String,
        action: () -> Unit
    ) {
        val item = label(text, 15f, white, true).apply {
            setPadding(dp(12), dp(17), dp(8), dp(17))
            setOnClickListener {
                drawer.visibility = View.GONE
                action()
            }
        }

        drawer.addView(item)

        val line = View(this).apply {
            setBackgroundColor(0xFF25344D.toInt())
        }

        drawer.addView(
            line,
            LinearLayout.LayoutParams(-1, dp(1))
        )
    }

    // FIREBASE CHANNELS + PLAYLIST

    private fun loadChannels() {
        channelsListener?.remove()
        playlistListener?.remove()

        channelsListener = null
        playlistListener = null

        status.text = "Connecting to Firebase..."

        val db = FirebaseFirestore.getInstance()

        // Individual channels from Admin Panel

        channelsListener = db.collection("channels")
            .whereEqualTo("enabled", true)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    status.text = "Firebase error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val loaded =
                    mutableListOf<Triple<String, String, String>>()

                snapshot.documents.forEach { doc ->

                    val name = doc.getString("name")
                    val url = doc.getString("url")
                    val category = doc.getString("category") ?: "Other"

                    if (!name.isNullOrBlank() && !url.isNullOrBlank()) {
                        loaded.add(Triple(name, url, category))
                    }
                }

                channels.clear()
                channels.addAll(loaded)

                updateCombinedChannels()
            }

        // Playlist URL from Admin Panel

        playlistListener = db.collection("settings")
            .document("playlist")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    status.text = "Playlist settings error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    lastPlaylistUrl = ""
                    playlistRequestId++
                    playlistChannels.clear()
                    updateCombinedChannels()
                    return@addSnapshotListener
                }

                val enabled = snapshot.getBoolean("enabled") == true
                val playlistUrl = snapshot.getString("url")?.trim().orEmpty()

                if (!enabled || playlistUrl.isBlank()) {
                    lastPlaylistUrl = ""
                    playlistRequestId++
                    playlistChannels.clear()
                    updateCombinedChannels()
                    return@addSnapshotListener
                }

                if (playlistUrl != lastPlaylistUrl) {
                    lastPlaylistUrl = playlistUrl
                    downloadPlaylist(playlistUrl)
                }
            }
    }

    private fun updateCombinedChannels() {
        val combined =
            mutableListOf<Triple<String, String, String>>()

        val usedUrls = mutableSetOf<String>()

        // Manual Firebase channels first

        channels.forEach { channel ->
            if (usedUrls.add(channel.second)) {
                combined.add(channel)
            }
        }

        // Then M3U playlist channels

        playlistChannels.forEach { channel ->
            if (usedUrls.add(channel.second)) {
                combined.add(channel)
            }
        }

        val oldCategory = selectedCategory

        channels.clear()
        channels.addAll(combined)

        if (
            selectedCategory != "All" &&
            channels.none { it.third == selectedCategory }
        ) {
            selectedCategory = "All"
        }

        status.text = "${channels.size} CHANNELS AVAILABLE"

        renderCategories()
        renderChannels()
    }

    // DOWNLOAD M3U PLAYLIST IN BACKGROUND

    private fun downloadPlaylist(playlistUrl: String) {
        playlistRequestId++
        val requestId = playlistRequestId

        status.text = "Downloading M3U playlist..."

        thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(playlistUrl)
                    .openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent",
                    "AshikaLiveTV/1.0"
                )

                connection.connect()

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    throw Exception("HTTP error: $responseCode")
                }

                val content = BufferedReader(
                    InputStreamReader(connection.inputStream, Charsets.UTF_8)
                ).use { reader ->
                    reader.readText()
                }

                val parsed = parseM3U(content)

                runOnUiThread {
                    if (isFinishing || requestId != playlistRequestId) {
                        return@runOnUiThread
                    }

                    playlistChannels.clear()
                    playlistChannels.addAll(parsed)

                    updateCombinedChannels()

                    status.text =
                        "${channels.size} CHANNELS AVAILABLE | " +
                        "${parsed.size} PLAYLIST CHANNELS"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || requestId != playlistRequestId) {
                        return@runOnUiThread
                    }

                    status.text =
                        "Playlist error: ${e.message ?: "Download failed"}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    // M3U PARSER

    private fun parseM3U(
        content: String
    ): List<Triple<String, String, String>> {

        val result = mutableListOf<Triple<String, String, String>>()

        var currentName = ""
        var currentCategory = "Other"

        content.lineSequence().forEach { rawLine ->

            val line = rawLine.trim()

            if (line.isBlank()) return@forEach

            if (line.startsWith("#EXTINF", ignoreCase = true)) {

                val commaIndex = line.lastIndexOf(',')

                val displayName = if (commaIndex >= 0) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    ""
                }

                val tvgName = Regex(
                    """tvg-name\s*=\s*["']([^"']*)["']""",
                    RegexOption.IGNORE_CASE
                ).find(line)?.groupValues?.get(1)

                val groupTitle = Regex(
                    """group-title\s*=\s*["']([^"']*)["']""",
                    RegexOption.IGNORE_CASE
                ).find(line)?.groupValues?.get(1)

                currentName = tvgName
                    ?.takeIf { it.isNotBlank() }
                    ?: displayName
                    .ifBlank { "Unknown Channel" }

                currentCategory = groupTitle
                    ?.takeIf { it.isNotBlank() }
                    ?: "Other"

            } else if (
                !line.startsWith("#") &&
                (line.startsWith("http://", true) ||
                 line.startsWith("https://", true))
            ) {

                if (currentName.isNotBlank()) {
                    result.add(
                        Triple(
                            currentName,
                            line,
                            currentCategory
                        )
                    )
                }

                currentName = ""
                currentCategory = "Other"
            }
        }

        return result
    }

    private fun renderCategories() {
        categoryList.removeAllViews()

        val categories = listOf("All") +
            channels.map { it.third }.distinct().sorted()

        categories.forEach { category ->

            val selected = selectedCategory == category

            val chip = label(
                category,
                12f,
                if (selected) white else muted,
                true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(10), dp(15), dp(10))
                background = rounded(
                    if (selected) blue else panel,
                    20
                )

                setOnClickListener {
                    selectedCategory = category
                    page = "home"
                    renderCategories()
                    renderChannels()
                }
            }

            val params = LinearLayout.LayoutParams(-2, -2)
            params.setMargins(0, 0, dp(8), 0)
            categoryList.addView(chip, params)
        }
    }

    private fun renderChannels() {
        channelList.removeAllViews()

        val filtered = when (page) {

            "favorites" -> channels.filter {
                favorites.contains(it.second)
            }

            "history" -> history.mapNotNull { url ->
                channels.find { it.second == url }
            }

            else -> channels.filter {
                selectedCategory == "All" ||
                it.third == selectedCategory
            }

        }.filter {
            it.first.contains(query, true)
        }

        filtered.forEach { channel ->
            channelList.addView(createChannelCard(channel))
        }

        if (filtered.isEmpty()) {

            val empty = label(
                if (page == "favorites") "No favorites yet"
                else if (page == "history") "No recently watched channels"
                else "No channels found",
                14f,
                muted
            ).apply {
                gravity = Gravity.CENTER
                setPadding(10, dp(35), 10, dp(35))
            }

            channelList.addView(empty)
        }
    }

    private fun createChannelCard(
        channel: Triple<String, String, String>
    ): View {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(13), dp(10), dp(13))
            background = rounded(panel, 16)
        }

        val logo = TextView(this).apply {
            text = "▶"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(
                Color.rgb(28, 65, 120), 13
            )
        }

        card.addView(
            logo,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(5), 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        info.addView(
            label(channel.first, 14f, white, true)
        )

        info.addView(
            label(channel.third, 10f, muted).apply {
                setPadding(0, dp(4), 0, 0)
            }
        )

        card.addView(info)

        val favorite = label(
            if (favorites.contains(channel.second)) "♥" else "♡",
            25f,
            red,
            true
        ).apply {

            setPadding(dp(10), dp(8), dp(8), dp(8))

            setOnClickListener {

                if (!favorites.add(channel.second)) {
                    favorites.remove(channel.second)
                }

                prefs.edit()
                    .putStringSet("favorites", favorites.toSet())
                    .apply()

                renderChannels()
            }
        }

        card.addView(favorite)

        card.setOnClickListener {

            history.remove(channel.second)
            history.add(0, channel.second)

            while (history.size > 30) {
                history.removeAt(history.lastIndex)
            }

            prefs.edit()
                .putString("history", history.joinToString("\n"))
                .apply()

            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("url", channel.second)
            intent.putExtra("name", channel.first)
            startActivity(intent)
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 0, 0, dp(10))
        card.layoutParams = params

        return card
    }

    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) {
            drawer.visibility = View.GONE
        } else if (page != "home") {
            page = "home"
            renderChannels()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        playlistRequestId++

        channelsListener?.remove()
        playlistListener?.remove()

        channelsListener = null
        playlistListener = null

        super.onDestroy()
    }
}
