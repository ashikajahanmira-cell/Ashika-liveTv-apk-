package com.ashika.livetv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.content.Intent
import android.app.AlertDialog
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val playlist =
        "https://raw.githubusercontent.com/abusaeeidx/Mrgify-BDIX-IPTV/main/playlist.m3u"

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var search: EditText

    private val channels = mutableListOf<Pair<String, String>>()
    private val favorites = mutableSetOf<String>()

    private var showingFavorites = false
    private var searchQuery = ""

    private val bg = Color.rgb(8, 13, 25)
    private val blue = Color.rgb(30, 100, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 10)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val menuButton = makeButton("☰", 28f) {
            drawer.visibility =
                if (drawer.visibility == View.VISIBLE) View.GONE
                else View.VISIBLE
        }

        val title = TextView(this).apply {
            text = "ASHIKA LIVE TV"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
            layoutParams = LinearLayout.LayoutParams(
                0, -2, 1f
            )
        }

        header.addView(menuButton)
        header.addView(title)

        status = TextView(this).apply {
            text = "Loading channels..."
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(5, 15, 5, 15)
        }

        search = EditText(this).apply {
            hint = "Search channels"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val scroll = ScrollView(this)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        content.addView(header)
        content.addView(status)
        content.addView(search)

        content.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(content)

        // Sidebar
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 20, 16, 16)
            setBackgroundColor(Color.rgb(13, 24, 43))
            visibility = View.GONE
            elevation = 15f
        }

        val drawerParams = FrameLayout.LayoutParams(
            dp(280),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.START
        )

        val brand = TextView(this).apply {
            text = "ASHIKA\nLIVE TV"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 30, 10, 30)
        }

        drawer.addView(brand)

        addMenuItem("🏠   Home") {
            showingFavorites = false
            search.setText("")
            drawer.visibility = View.GONE
            showChannels("")
        }

        addMenuItem("📺   Live TV") {
            showingFavorites = false
            drawer.visibility = View.GONE
            showChannels(searchQuery)
        }

        addMenuItem("❤️   Favorites") {
            showingFavorites = true
            drawer.visibility = View.GONE
            showChannels(searchQuery)
        }

        addMenuItem("🗂️   Categories") {
            drawer.visibility = View.GONE
            AlertDialog.Builder(this)
                .setTitle("Categories")
                .setMessage(
                    "Total channels: ${channels.size}\n\n" +
                    "Category filtering will be available " +
                    "when playlist category data is added."
                )
                .setPositiveButton("OK", null)
                .show()
        }

        addMenuItem("🔄   Refresh Playlist") {
            drawer.visibility = View.GONE
            channels.clear()
            loadChannels()
        }

        addMenuItem("⚙️   Settings") {
            drawer.visibility = View.GONE
            AlertDialog.Builder(this)
                .setTitle("Settings")
                .setMessage(
                    "Ashika Live TV\n\n" +
                    "Theme: Dark Navy\n" +
                    "Playlist: Online IPTV"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        addMenuItem("ℹ️   About Ashika Live TV") {
            drawer.visibility = View.GONE
            AlertDialog.Builder(this)
                .setTitle("About")
                .setMessage(
                    "ASHIKA LIVE TV\n\n" +
                    "Your personal live TV player."
                )
                .setPositiveButton("OK", null)
                .show()
        }

        root.addView(drawer, drawerParams)

        drawer.setOnClickListener {
            // Keep clicks inside drawer
        }

        root.setOnClickListener {
            if (drawer.visibility == View.VISIBLE) {
                drawer.visibility = View.GONE
            }
        }

        // Allow content to receive normal touches
        content.setOnClickListener {
            drawer.visibility = View.GONE
        }

        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int,
                count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int,
                before: Int, count: Int
            ) {
                searchQuery = s.toString()
                showChannels(searchQuery)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadChannels()
    }

    private fun addMenuItem(
        text: String,
        action: () -> Unit
    ) {
        val item = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(14, 17, 10, 17)
            isClickable = true
            setBackgroundColor(Color.TRANSPARENT)

            setOnClickListener {
                action()
            }
        }

        drawer.addView(
            item,
            LinearLayout.LayoutParams(-1, -2)
        )

        val line = View(this).apply {
            setBackgroundColor(Color.rgb(38, 55, 78))
        }

        drawer.addView(
            line,
            LinearLayout.LayoutParams(-1, dp(1))
        )
    }

    private fun makeButton(
        text: String,
        size: Float,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 10)
            isClickable = true
            setOnClickListener { action() }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun loadChannels() {
        status.text = "Loading channels..."

        thread {
            try {
                val connection =
                    URL(playlist).openConnection() as HttpURLConnection

                connection.connectTimeout = 20000
                connection.readTimeout = 30000

                val data = connection.inputStream.bufferedReader().use {
                    it.readLines()
                }

                connection.disconnect()

                val loaded = mutableListOf<Pair<String, String>>()
                var name = "Live Channel"

                data.forEach { line ->
                    when {
                        line.startsWith("#EXTINF") ->
                            name = line.substringAfterLast(",").trim()

                        line.startsWith("http://") ||
                        line.startsWith("https://") -> {
                            loaded.add(name to line.trim())
                            name = "Live Channel"
                        }
                    }
                }

                runOnUiThread {
                    channels.clear()
                    channels.addAll(loaded)
                    status.text = "${channels.size} Channels Loaded"
                    showChannels(searchQuery)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Playlist loading failed"
                }
            }
        }
    }

    private fun showChannels(query: String) {
        list.removeAllViews()

        val filtered = channels.filter {
            it.first.contains(query, true) &&
            (!showingFavorites || favorites.contains(it.second))
        }

        filtered.forEach { channel ->

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 5, 12, 5)
                setBackgroundColor(Color.rgb(18, 27, 45))
            }

            val name = TextView(this).apply {
                text = "▶   ${channel.first}"
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(8, 15, 8, 15)
                layoutParams = LinearLayout.LayoutParams(
                    0, -2, 1f
                )

                setOnClickListener {
                    val intent = Intent(
                        this@MainActivity,
                        PlayerActivity::class.java
                    )
                    intent.putExtra("url", channel.second)
                    intent.putExtra("name", channel.first)
                    startActivity(intent)
                }
            }

            val favorite = TextView(this).apply {
                text = if (favorites.contains(channel.second))
                    "❤️" else "♡"

                textSize = 23f
                setTextColor(Color.WHITE)
                setPadding(12, 10, 12, 10)

                setOnClickListener {
                    if (favorites.contains(channel.second)) {
                        favorites.remove(channel.second)
                    } else {
                        favorites.add(channel.second)
                    }

                    showChannels(query)
                }
            }

            item.addView(name)
            item.addView(favorite)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 5, 0, 5)

            list.addView(item, params)
        }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (showingFavorites)
                    "No favorite channels yet"
                else "No channels found"

                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(20, 35, 20, 35)
            }
            list.addView(empty)
        }
    }
}
