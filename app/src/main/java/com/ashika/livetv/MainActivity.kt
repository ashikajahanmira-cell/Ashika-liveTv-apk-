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
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.speech.RecognizerIntent
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val bg = Color.rgb(8, 5, 20)
    private val panel = Color.rgb(25, 15, 45)
    private val purple = Color.rgb(151, 71, 255)
    private val purpleLight = Color.rgb(190, 130, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(175, 160, 200)
    private val red = Color.rgb(255, 75, 120)

    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var channelList: LinearLayout
    private lateinit var categoryList: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var drawer: LinearLayout
    private lateinit var connectionStatus: TextView

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                runOnUiThread {
                    updateConnectionStatus(isInternetAvailable())
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    updateConnectionStatus(isInternetAvailable())
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val connected =
                    capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    ) &&
                    capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )

                runOnUiThread {
                    updateConnectionStatus(connected)
                }
            }
        }

    private val channels =
        mutableListOf<Triple<String, String, String>>()

    private val manualChannels =
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
    private var playlistRequestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("ashika_data", MODE_PRIVATE)

        favorites.addAll(
            prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        )

        history.addAll(
            prefs.getString("history", "")
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )

        buildHome()
        startInternetMonitoring()
        loadChannels()

        if (prefs.getBoolean("pin_enabled", false)) {
            showAppLock()
        }
    }

    // BASIC HELPERS

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int = 16,
        stroke: Int = 0
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()

            if (stroke != 0) {
                setStroke(dp(1), stroke)
            }
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

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun button(
        text: String,
        action: () -> Unit
    ): TextView {

        return label(text, 14f, white, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(purple, 15)
            setOnClickListener { action() }
        }
    }

    // INTERNET CONNECTION MONITORING

    private fun startInternetMonitoring() {

        connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE)
                as ConnectivityManager

        updateConnectionStatus(isInternetAvailable())

        try {
            val request = NetworkRequest.Builder()
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .build()

            connectivityManager?.registerNetworkCallback(
                request,
                networkCallback
            )

            networkCallbackRegistered = true

        } catch (e: Exception) {
            updateConnectionStatus(isInternetAvailable())
        }
    }

    private fun isInternetAvailable(): Boolean {

        return try {
            val manager = connectivityManager
                ?: getSystemService(CONNECTIVITY_SERVICE)
                    as ConnectivityManager

            val network = manager.activeNetwork ?: return false

            val capabilities =
                manager.getNetworkCapabilities(network)
                    ?: return false

            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) &&
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )

        } catch (e: Exception) {
            false
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {

        if (!::connectionStatus.isInitialized) return

        if (connected) {
            connectionStatus.text = "● ONLINE"
            connectionStatus.setTextColor(
                Color.rgb(60, 230, 140)
            )
        } else {
            connectionStatus.text = "● OFFLINE"
            connectionStatus.setTextColor(
                Color.rgb(255, 75, 120)
            )
        }
    }

    // HOME UI

    private fun buildHome() {

        val outer = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(12), dp(17), dp(8))
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

        brand.addView(
            label("ASHIKA", 24f, purpleLight, true)
        )

        brand.addView(
            label("LIVE TELEVISION • PREMIUM", 9f, muted, true)
        )

        connectionStatus = label(
            "● CHECKING",
            12f,
            purpleLight,
            true
        ).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(panel, 20, purple)
        }

        header.addView(menu)
        header.addView(brand)
        header.addView(connectionStatus)

        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(23), dp(20), dp(20))

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(95, 28, 175),
                    Color.rgb(44, 15, 85),
                    Color.rgb(15, 8, 35)
                )
            ).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), purple)
            }
        }

        hero.addView(
            label(
                "WELCOME TO PREMIUM ENTERTAINMENT",
                10f,
                purpleLight,
                true
            )
        )

        hero.addView(
            label("ASHIKA LIVE TV", 27f, white, true).apply {
                setPadding(0, dp(8), 0, dp(5))
            }
        )

        hero.addView(
            label(
                "Your entertainment. One destination.",
                12f,
                muted
            )
        )

        val explore = button("▶   EXPLORE CHANNELS") {
            page = "home"
            selectedCategory = "All"
            renderCategories()
            renderChannels()
        }

        val heroParams = LinearLayout.LayoutParams(-2, -2)
        heroParams.topMargin = dp(17)

        hero.addView(explore, heroParams)

        val heroOuter = LinearLayout.LayoutParams(-1, -2)
        heroOuter.topMargin = dp(18)

        root.addView(hero, heroOuter)

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, dp(10))
        }

        section.addView(
            label("Discover Channels", 19f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val refresh = label(
            "↻ Refresh",
            13f,
            purpleLight,
            true
        )

        refresh.setOnClickListener {
            loadChannels()
        }

        section.addView(refresh)
        root.addView(section)

        // SEARCH + VOICE SEARCH

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        search = EditText(this).apply {
            hint = "⌕  Search channels..."
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = rounded(panel, 14, purple)
        }

        searchRow.addView(
            search,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val mic = button("🎤") {
            startVoiceSearch()
        }

        val micParams = LinearLayout.LayoutParams(
            dp(52),
            dp(48)
        )

        micParams.marginStart = dp(8)

        searchRow.addView(mic, micParams)
        root.addView(searchRow)

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

        status = label(
            "Connecting to Firebase...",
            12f,
            muted
        )

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

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        // BOTTOM NAVIGATION

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(5))
            background = rounded(panel, 18, purple)
        }

        val navItems = listOf(
            "⌂\nHome",
            "♥\nFavorites",
            "◷\nHistory",
            "⚙\nSettings"
        )

        navItems.forEachIndexed { index, title ->

            val item = label(
                title,
                12f,
                purpleLight,
                true
            ).apply {

                gravity = Gravity.CENTER

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(5)
                )

                layoutParams =
                    LinearLayout.LayoutParams(0, -2, 1f)

                setOnClickListener {

                    when (index) {

                        0 -> {
                            page = "home"
                            selectedCategory = "All"
                            renderCategories()
                            renderChannels()
                        }

                        1 -> {
                            page = "favorites"
                            renderChannels()
                        }

                        2 -> {
                            page = "history"
                            renderChannels()
                        }

                        3 -> showSettings()
                    }
                }
            }

            bottom.addView(item)
        }

        val bottomParams = LinearLayout.LayoutParams(-1, -2)
        bottomParams.topMargin = dp(8)

        root.addView(bottom, bottomParams)

        outer.addView(root)

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(20))
            background = rounded(
                Color.rgb(18, 9, 35),
                0,
                purple
            )
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

        search.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    query = s.toString()
                    renderChannels()
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        renderCategories()
        renderChannels()
    }

    // VOICE SEARCH

    private fun startVoiceSearch() {

        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Say a channel name"
            )

            startActivityForResult(intent, 501)

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Voice search is not available",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Legacy activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 501 && resultCode == RESULT_OK) {

            val results = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

            val spoken = results?.firstOrNull().orEmpty()

            search.setText(spoken)
            search.setSelection(search.text.length)
        }
    }

    // DRAWER

    private fun showDrawer() {

        drawer.removeAllViews()

        drawer.addView(
            label("ASHIKA", 25f, purpleLight, true)
        )

        drawer.addView(
            label("NEON PURPLE PREMIUM", 10f, muted, true)
        )

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

        addDrawerItem("🔐   PIN Lock Settings") {
            showPinSettings()
        }

        addDrawerItem("🛠   Admin Panel") {
            showAdminPanel()
        }

        addDrawerItem("🔄   Refresh Channels") {
            loadChannels()
        }

        addDrawerItem("⚙️   Settings") {
            showSettings()
        }

        addDrawerItem("ℹ️   About") {

            AlertDialog.Builder(this)
                .setTitle("Ashika Live TV")
                .setMessage(
                    "Neon Purple Premium Edition\n" +
                    "Firebase + Multiple M3U Playlists\n" +
                    "Channels: ${channels.size}\n" +
                    "Internet: ${
                        if (isInternetAvailable()) "Online"
                        else "Offline"
                    }"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        drawer.visibility = View.VISIBLE
    }

    private fun addDrawerItem(
        text: String,
        action: () -> Unit
    ) {

        val item = label(
            text,
            15f,
            white,
            true
        ).apply {

            setPadding(
                dp(12),
                dp(17),
                dp(8),
                dp(17)
            )

            setOnClickListener {
                drawer.visibility = View.GONE
                action()
            }
        }

        drawer.addView(item)

        drawer.addView(
            View(this).apply {
                setBackgroundColor(0xFF392052.toInt())
            },
            LinearLayout.LayoutParams(-1, dp(1))
        )
    }

    // SETTINGS

    private fun showSettings() {

        AlertDialog.Builder(this)
            .setTitle("Ashika Premium Settings")
            .setMessage(
                "Theme: Neon Purple\n" +
                "Channels: ${channels.size}\n" +
                "Favorites: ${favorites.size}\n" +
                "Internet: ${
                    if (isInternetAvailable()) "ONLINE"
                    else "OFFLINE"
                }\n" +
                "PIN Lock: ${
                    if (prefs.getBoolean("pin_enabled", false))
                        "ON"
                    else
                        "OFF"
                }\n\n" +
                "Player and Picture-in-Picture settings " +
                "will be configured in PlayerActivity."
            )
            .setPositiveButton("PIN Settings") { _, _ ->
                showPinSettings()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // PIN SETTINGS

    private fun showPinSettings() {

        val input = EditText(this).apply {
            hint = "Enter new 4-8 digit PIN"
            inputType = 2
            setTextColor(white)
            setHintTextColor(muted)
        }

        val box = LinearLayout(this).apply {
            setPadding(
                dp(22),
                dp(10),
                dp(22),
                dp(5)
            )
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Set App PIN")
            .setMessage(
                "Create a PIN for app lock and Admin Panel."
            )
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val pin = input.text.toString()

                if (!pin.matches(Regex("\\d{4,8}"))) {
                    input.error = "Enter 4 to 8 digits"
                    return@setOnClickListener
                }

                prefs.edit()
                    .putString("app_pin", pin)
                    .putBoolean("pin_enabled", true)
                    .apply()

                Toast.makeText(
                    this,
                    "PIN lock enabled",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAppLock() {

        val input = EditText(this).apply {
            hint = "Enter your PIN"
            inputType = 2
            setTextColor(white)
            setHintTextColor(muted)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("🔐 Ashika App Lock")
            .setMessage("Enter your PIN to continue.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Unlock", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val savedPin =
                    prefs.getString("app_pin", "")

                if (input.text.toString() == savedPin) {
                    dialog.dismiss()
                } else {
                    input.error = "Incorrect PIN"
                }
            }
        }

        dialog.show()
    }

    // ADMIN PANEL

    private fun showAdminPanel() {

        val savedPin = prefs.getString("app_pin", null)

        if (savedPin.isNullOrBlank()) {

            AlertDialog.Builder(this)
                .setTitle("Admin Panel Locked")
                .setMessage(
                    "Set an app PIN before opening the Admin Panel."
                )
                .setPositiveButton("Set PIN") { _, _ ->
                    showPinSettings()
                }
                .setNegativeButton("Cancel", null)
                .show()

            return
        }

        val input = EditText(this).apply {
            hint = "Admin PIN"
            inputType = 2
            setTextColor(white)
            setHintTextColor(muted)
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Authentication")
            .setMessage("Enter your app PIN.")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->

                if (input.text.toString() == savedPin) {
                    openAdminOptions()
                } else {
                    Toast.makeText(
                        this,
                        "Incorrect PIN",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAdminOptions() {

        val options = arrayOf(
            "Add Channel",
            "Refresh Channels",
            "View Channel Count"
        )

        AlertDialog.Builder(this)
            .setTitle("ASHIKA ADMIN PANEL")
            .setItems(options) { _, which ->

                when (which) {

                    0 -> addAdminChannel()

                    1 -> loadChannels()

                    2 -> AlertDialog.Builder(this)
                        .setMessage(
                            "Total channels: ${channels.size}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .show()
    }

    private fun addAdminChannel() {

        val name = EditText(this).apply {
            hint = "Channel name"
            setTextColor(white)
        }

        val url = EditText(this).apply {
            hint = "Stream URL"
            setTextColor(white)
        }

        val category = EditText(this).apply {
            hint = "Category (Bangla, Hindi...)"
            setTextColor(white)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(5),
                dp(20),
                dp(5)
            )

            addView(name)
            addView(url)
            addView(category)
        }

        AlertDialog.Builder(this)
            .setTitle("Add New Channel")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->

                val channelName =
                    name.text.toString().trim()

                val streamUrl =
                    url.text.toString().trim()

                val channelCategory =
                    category.text.toString()
                        .trim()
                        .ifBlank { "Other" }

                if (
                    channelName.isBlank() ||
                    !(
                        streamUrl.startsWith("https://") ||
                        streamUrl.startsWith("http://")
                    )
                ) {

                    Toast.makeText(
                        this,
                        "Enter a channel name and valid HTTP(S) URL",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                FirebaseFirestore.getInstance()
                    .collection("channels")
                    .add(
                        hashMapOf(
                            "name" to channelName,
                            "url" to streamUrl,
                            "category" to channelCategory,
                            "enabled" to true
                        )
                    )
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Channel added successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {

                        Toast.makeText(
                            this,
                            "Firebase error: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // FIREBASE + PLAYLISTS

    private fun loadChannels() {

        channelsListener?.remove()
        playlistListener?.remove()

        channelsListener = null
        playlistListener = null

        playlistRequestId++

        status.text = "Connecting to Firebase..."

        val db = FirebaseFirestore.getInstance()

        channelsListener = db.collection("channels")
            .whereEqualTo("enabled", true)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    status.text =
                        "Firebase error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                manualChannels.clear()

                snapshot.documents.forEach { doc ->

                    val name = doc.getString("name")
                    val url = doc.getString("url")
                    val category =
                        doc.getString("category") ?: "Other"

                    if (
                        !name.isNullOrBlank() &&
                        !url.isNullOrBlank()
                    ) {
                        manualChannels.add(
                            Triple(name, url, category)
                        )
                    }
                }

                updateCombinedChannels()
            }

        playlistListener = db.collection("settings")
            .document("playlist")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    status.text =
                        "Playlist settings error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {

                    playlistRequestId++
                    playlistChannels.clear()
                    updateCombinedChannels()

                    return@addSnapshotListener
                }

                val items =
                    mutableListOf<Pair<String, String>>()

                val saved =
                    snapshot.get("playlists") as? List<*>

                if (saved != null) {

                    saved.forEach { item ->

                        val data = item as? Map<*, *>
                            ?: return@forEach

                        val name =
                            data["name"] as? String ?: "Playlist"

                        val url =
                            data["url"] as? String ?: ""

                        val enabled =
                            data["enabled"] == true

                        if (enabled && url.isNotBlank()) {
                            items.add(name to url)
                        }
                    }

                } else {

                    val oldUrl =
                        snapshot.getString("url")
                            ?.trim()
                            .orEmpty()

                    val enabled =
                        snapshot.getBoolean("enabled") == true

                    if (enabled && oldUrl.isNotBlank()) {
                        items.add("Old Playlist" to oldUrl)
                    }
                }

                downloadMultiplePlaylists(items)
            }
    }

    private fun updateCombinedChannels() {

        val combined =
            mutableListOf<Triple<String, String, String>>()

        val used = mutableSetOf<String>()

        manualChannels.forEach {
            if (used.add(it.second)) {
                combined.add(it)
            }
        }

        playlistChannels.forEach {
            if (used.add(it.second)) {
                combined.add(it)
            }
        }

        channels.clear()
        channels.addAll(combined)

        if (
            selectedCategory != "All" &&
            channels.none {
                it.third == selectedCategory
            }
        ) {
            selectedCategory = "All"
        }

        status.text =
            "${channels.size} CHANNELS AVAILABLE"

        renderCategories()
        renderChannels()
    }

    private fun downloadMultiplePlaylists(
        playlistItems: List<Pair<String, String>>
    ) {

        playlistRequestId++

        val requestId = playlistRequestId

        if (playlistItems.isEmpty()) {

            playlistChannels.clear()
            updateCombinedChannels()

            return
        }

        playlistChannels.clear()
        updateCombinedChannels()

        status.text = "Downloading playlists..."

        thread {

            val all =
                mutableListOf<Triple<String, String, String>>()

            val errors = mutableListOf<String>()

            playlistItems.forEach { playlist ->

                var connection: HttpURLConnection? = null

                try {

                    connection = URL(playlist.second)
                        .openConnection() as HttpURLConnection

                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.requestMethod = "GET"

                    connection.setRequestProperty(
                        "User-Agent",
                        "AshikaLiveTV/1.0"
                    )

                    connection.connect()

                    if (connection.responseCode !in 200..299) {
                        throw Exception(
                            "HTTP ${connection.responseCode}"
                        )
                    }

                    val content = connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }

                    val parsed = parseM3U(content)

                    parsed.forEach {

                        val cat = if (it.third == "Other") {
                            playlist.first
                        } else {
                            "${playlist.first} • ${it.third}"
                        }

                        all.add(
                            Triple(it.first, it.second, cat)
                        )
                    }

                    if (parsed.isEmpty()) {
                        errors.add(
                            "${playlist.first}: Empty playlist"
                        )
                    }

                } catch (e: Exception) {

                    errors.add(
                        "${playlist.first}: ${e.message}"
                    )

                } finally {
                    connection?.disconnect()
                }
            }

            runOnUiThread {

                if (
                    isFinishing ||
                    requestId != playlistRequestId
                ) {
                    return@runOnUiThread
                }

                playlistChannels.clear()
                playlistChannels.addAll(all)

                updateCombinedChannels()

                if (errors.isNotEmpty()) {
                    status.text =
                        "${channels.size} CHANNELS | " +
                        "${errors.size} PLAYLIST ERRORS"
                }
            }
        }
    }

    private fun parseM3U(
        content: String
    ): List<Triple<String, String, String>> {

        val result =
            mutableListOf<Triple<String, String, String>>()

        var currentName = ""
        var currentCategory = "Other"

        content.lineSequence().forEach { raw ->

            val line = raw.trim()

            if (line.isBlank()) return@forEach

            if (line.startsWith("#EXTINF", true)) {

                val comma = line.lastIndexOf(',')

                val displayName = if (comma >= 0) {
                    line.substring(comma + 1).trim()
                } else {
                    ""
                }

                val tvgName = Regex(
                    """tvg-name\s*=\s*["']([^"']*)["']""",
                    RegexOption.IGNORE_CASE
                ).find(line)?.groupValues?.get(1)

                val group = Regex(
                    """group-title\s*=\s*["']([^"']*)["']""",
                    RegexOption.IGNORE_CASE
                ).find(line)?.groupValues?.get(1)

                currentName =
                    tvgName?.takeIf { it.isNotBlank() }
                        ?: displayName.ifBlank {
                            "Unknown Channel"
                        }

                currentCategory =
                    group?.takeIf { it.isNotBlank() }
                        ?: "Other"

            } else if (
                !line.startsWith("#") &&
                (
                    line.startsWith("http://", true) ||
                    line.startsWith("https://", true)
                )
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

    // CATEGORIES

    private fun renderCategories() {

        categoryList.removeAllViews()

        val categories = listOf("All") +
            channels.map { it.third }.distinct().sorted()

        categories.forEach { category ->

            val selected =
                selectedCategory == category

            val chip = label(
                category,
                12f,
                if (selected) white else muted,
                true
            ).apply {

                gravity = Gravity.CENTER

                setPadding(
                    dp(15),
                    dp(10),
                    dp(15),
                    dp(10)
                )

                background = rounded(
                    if (selected) purple else panel,
                    20,
                    if (selected) purpleLight else 0
                )

                setOnClickListener {

                    selectedCategory = category
                    page = "home"

                    renderCategories()
                    renderChannels()
                }
            }

            val params =
                LinearLayout.LayoutParams(-2, -2)

            params.marginEnd = dp(8)

            categoryList.addView(chip, params)
        }
    }

    // CHANNELS

    private fun renderChannels() {

        channelList.removeAllViews()

        val filtered = when (page) {

            "favorites" -> channels.filter {
                favorites.contains(it.second)
            }

            "history" -> history.mapNotNull { url ->
                channels.find {
                    it.second == url
                }
            }

            else -> channels.filter {
                selectedCategory == "All" ||
                it.third == selectedCategory
            }

        }.filter {
            it.first.contains(query, true)
        }

        filtered.forEach {
            channelList.addView(createChannelCard(it))
        }

        if (filtered.isEmpty()) {

            channelList.addView(
                label(
                    when (page) {
                        "favorites" -> "♡  No favorites yet"
                        "history" -> "No recent channels"
                        else -> "No channels found"
                    },
                    14f,
                    muted
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        10,
                        dp(35),
                        10,
                        dp(35)
                    )
                }
            )
        }
    }

    // CHANNEL CARD

    private fun createChannelCard(
        channel: Triple<String, String, String>
    ): View {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(13),
                dp(13),
                dp(10),
                dp(13)
            )

            background = rounded(
                panel,
                17,
                0xFF57318A.toInt()
            )
        }

        val logo = TextView(this).apply {
            text = "▶"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(white)

            background = rounded(
                Color.rgb(85, 35, 145),
                13,
                purpleLight
            )
        }

        card.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            )
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(5), 0)

            layoutParams =
                LinearLayout.LayoutParams(0, -2, 1f)
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
            if (favorites.contains(channel.second)) "♥"
            else "♡",
            25f,
            red,
            true
        ).apply {

            setPadding(
                dp(10),
                dp(8),
                dp(8),
                dp(8)
            )

            setOnClickListener {

                if (!favorites.add(channel.second)) {
                    favorites.remove(channel.second)
                }

                prefs.edit()
                    .putStringSet(
                        "favorites",
                        favorites.toSet()
                    )
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
                .putString(
                    "history",
                    history.joinToString("\n")
                )
                .apply()

            val intent = Intent(
                this,
                PlayerActivity::class.java
            )

            intent.putExtra("url", channel.second)
            intent.putExtra("name", channel.first)

            startActivity(intent)
        }

        val params =
            LinearLayout.LayoutParams(-1, -2)

        params.bottomMargin = dp(10)

        card.layoutParams = params

        return card
    }

    // BACK BUTTON + EXIT CONFIRMATION

    @Deprecated("Legacy back callback")
    override fun onBackPressed() {

        if (drawer.visibility == View.VISIBLE) {

            drawer.visibility = View.GONE

        } else if (page != "home") {

            page = "home"
            selectedCategory = "All"

            renderCategories()
            renderChannels()

        } else {

            AlertDialog.Builder(this)
                .setTitle("Exit Ashika Live TV?")
                .setMessage(
                    "আপনি কি Ashika Live TV থেকে বের হতে চান?"
                )
                .setPositiveButton("Yes") { _, _ ->
                    finish()
                }
                .setNegativeButton("No", null)
                .setCancelable(true)
                .show()
        }
    }

    // CLEANUP

    override fun onDestroy() {

        playlistRequestId++

        channelsListener?.remove()
        playlistListener?.remove()

        channelsListener = null
        playlistListener = null

        if (networkCallbackRegistered) {
            try {
                connectivityManager?.unregisterNetworkCallback(
                    networkCallback
                )
            } catch (e: Exception) {
                // Callback already unregistered
            }

            networkCallbackRegistered = false
        }

        connectivityManager = null

        super.onDestroy()
    }
}
