package com.ashika.livetv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val playlist =
        "https://raw.githubusercontent.com/abusaeeidx/Mrgify-BDIX-IPTV/main/playlist.m3u"

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val channels = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 10)
            setBackgroundColor(Color.rgb(8, 13, 25))
        }

        val title = TextView(this).apply {
            text = "ASHIKA LIVE TV"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "Loading channels..."
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(5, 15, 5, 15)
        }

        val search = EditText(this).apply {
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

        root.addView(title)
        root.addView(status)
        root.addView(search)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showChannels(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadChannels()
    }

    private fun loadChannels() {
        thread {
            try {
                val connection = URL(playlist).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 30000

                val data = connection.inputStream.bufferedReader().use {
                    it.readLines()
                }
                connection.disconnect()

                var name = "Live Channel"

                data.forEach { line ->
                    when {
                        line.startsWith("#EXTINF") ->
                            name = line.substringAfterLast(",").trim()

                        line.startsWith("http://") || line.startsWith("https://") -> {
                            channels.add(name to line.trim())
                            name = "Live Channel"
                        }
                    }
                }

                runOnUiThread {
                    status.text = "${channels.size} Channels Loaded"
                    showChannels("")
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

        channels.filter {
            it.first.contains(query, true)
        }.forEach { channel ->

            val item = TextView(this).apply {
                text = "▶   ${channel.first}"
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(18, 20, 18, 20)
                setBackgroundColor(Color.rgb(18, 27, 45))

                setOnClickListener {
                    val intent = android.content.Intent(
                        this@MainActivity,
                        PlayerActivity::class.java
                    )
                    intent.putExtra("url", channel.second)
                    intent.putExtra("name", channel.first)
                    startActivity(intent)
                }
            }

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 5, 0, 5)
            list.addView(item, params)
        }
    }
}
