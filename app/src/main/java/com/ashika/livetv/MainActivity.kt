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
import android.text.InputType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    // FIREBASE ADMIN UID
    private val ADMIN_UID = "qVzYexoF4wcm7IBByOoA9yKeDy82"

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

    // BASIC ADMIN CHECK

    private fun isAdmin(): Boolean {
        return FirebaseAuth.getInstance()
            .currentUser?.uid == ADMIN_UID
    }

    private fun requireAdmin(): Boolean {

        if (isAdmin()) return true

        Toast.makeText(
            this,
            "Admin login required. Please login again.",
            Toast.LENGTH_LONG
        ).show()

        return false
    }

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

    // INTERNET CONNECTION

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
            connectionStatus.setTextColor(red)
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

        brand.addView(label("ASHIKA", 24f, purpleLight, true))
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

        val refresh = label("↻ Refresh", 13f, purpleLight, true)

        refresh.setOnClickListener {
            loadChannels()
        }

        section.addView(refresh)
        root.addView(section)

        // SEARCH

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

        val micParams = LinearLayout.LayoutParams(dp(52), dp(48))
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

            val item = label(title, 12f, purpleLight, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
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
            background = rounded(Color.rgb(18, 9, 35), 0, purple)
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

        drawer.addView(label("ASHIKA", 25f, purpleLight, true))
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
                        if (isInternetAvailable()) "Online" else "Offline"
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

        val item = label(text, 15f, white, true).apply {
            setPadding(dp(12), dp(17), dp(8), dp(17))
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
                    if (isInternetAvailable()) "ONLINE" else "OFFLINE"
                }\n" +
                "PIN Lock: ${
                    if (prefs.getBoolean("pin_enabled", false)) "ON" else "OFF"
                }\n\n" +
                "Player and Picture-in-Picture settings " +
                "are configured in PlayerActivity."
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
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(white)
            setHintTextColor(muted)
        }

        val box = LinearLayout(this).apply {
            setPadding(dp(22), dp(10), dp(22), dp(5))
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Set App PIN")
            .setMessage("Create a PIN for app lock and Admin Panel.")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

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
            inputType = InputType.TYPE_CLASS_NUMBER
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val savedPin = prefs.getString("app_pin", "")

                    if (input.text.toString() == savedPin) {
                        dialog.dismiss()
                    } else {
                        input.error = "Incorrect PIN"
                    }
                }
        }

        dialog.show()
    }

    // ADMIN LOGIN

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
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(white)
            setHintTextColor(muted)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Admin Authentication")
            .setMessage("Enter your app PIN.")
            .setView(input)
            .setPositiveButton("Continue", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    if (input.text.toString() == savedPin) {
                        dialog.dismiss()
                        showFirebaseAdminLogin()
                    } else {
                        input.error = "Incorrect PIN"
                    }
                }
        }

        dialog.show()
    }

    private fun showFirebaseAdminLogin() {

        val email = EditText(this).apply {
            hint = "Admin Email"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
        }

        val password = EditText(this).apply {
            hint = "Admin Password"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(5))
            addView(email)
            addView(password)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("🔐 Firebase Admin Login")
            .setMessage("Enter your Firebase Admin Email and Password.")
            .setView(box)
            .setPositiveButton("Login", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            val loginButton =
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            loginButton.setOnClickListener {

                val adminEmail = email.text.toString().trim()
                val adminPassword = password.text.toString()

                if (adminEmail.isBlank()) {
                    email.error = "Enter Admin Email"
                    return@setOnClickListener
                }

                if (adminPassword.isBlank()) {
                    password.error = "Enter Admin Password"
                    return@setOnClickListener
                }

                loginButton.isEnabled = false
                loginButton.text = "Signing in..."

                FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(
                        adminEmail,
                        adminPassword
                    )
                    .addOnCompleteListener { task ->

                        if (task.isSuccessful) {

                            val user =
                                FirebaseAuth.getInstance().currentUser

                            if (user?.uid == ADMIN_UID) {

                                dialog.dismiss()

                                Toast.makeText(
                                    this,
                                    "Admin Login Successful",
                                    Toast.LENGTH_SHORT
                                ).show()

                                openAdminOptions()

                            } else {

                                FirebaseAuth.getInstance().signOut()

                                loginButton.isEnabled = true
                                loginButton.text = "Login"

                                Toast.makeText(
                                    this,
                                    "Access Denied: Admin UID does not match",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                        } else {

                            loginButton.isEnabled = true
                            loginButton.text = "Login"

                            Toast.makeText(
                                this,
                                "Login Failed: ${
                                    task.exception?.message ?: "Unknown error"
                                }",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }

        dialog.show()
    }

    // ADMIN SESSION VERIFICATION + MENU

    private fun openAdminOptions() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null || user.uid != ADMIN_UID) {
            Toast.makeText(
                this,
                "Admin authentication required",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        user.reload().addOnCompleteListener { task ->

            val verifiedUser = FirebaseAuth.getInstance().currentUser

            if (
                !task.isSuccessful ||
                verifiedUser == null ||
                verifiedUser.uid != ADMIN_UID
            ) {
                FirebaseAuth.getInstance().signOut()

                Toast.makeText(
                    this,
                    "Admin session invalid. Login again.",
                    Toast.LENGTH_LONG
                ).show()

                return@addOnCompleteListener
            }

            val options = arrayOf(
                "📊 Admin Dashboard",
                "➕ Add Channel",
                "📺 Manage Channels",
                "📂 Manage M3U Playlists",
                "🔄 Refresh Channels",
                "🚪 Admin Logout"
            )

            AlertDialog.Builder(this)
                .setTitle("ASHIKA ADMIN PANEL")
                .setItems(options) { _, which ->

                    when (which) {
                        0 -> showAdminDashboard()
                        1 -> addAdminChannel()
                        2 -> showChannelManager()
                        3 -> showPlaylistManager()
                        4 -> loadChannels()
                        5 -> {
                            FirebaseAuth.getInstance().signOut()

                            Toast.makeText(
                                this,
                                "Admin Logged Out",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .show()
        }
    }

    // ADMIN DASHBOARD

    private fun showAdminDashboard() {

        if (!requireAdmin()) return

        val db = FirebaseFirestore.getInstance()

        db.collection("channels")
            .get()
            .addOnSuccessListener { snapshot ->

                val total = snapshot.size()
                val enabled = snapshot.documents.count {
                    it.getBoolean("enabled") == true
                }
                val disabled = total - enabled

                db.collection("settings")
                    .document("playlist")
                    .get()
                    .addOnSuccessListener { playlistDoc ->

                        val playlists =
                            getPlaylistMaps(playlistDoc)

                        AlertDialog.Builder(this)
                            .setTitle("📊 ADMIN DASHBOARD")
                            .setMessage(
                                "ASHIKA LIVE TV\n\n" +
                                "Total Firebase Channels: $total\n" +
                                "Enabled Channels: $enabled\n" +
                                "Disabled Channels: $disabled\n" +
                                "M3U Playlists: ${playlists.size}\n" +
                                "Loaded Channels: ${channels.size}\n\n" +
                                "Admin UID: Verified"
                            )
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Manage Channels") { _, _ ->
                                showChannelManager()
                            }
                            .show()
                    }
                    .addOnFailureListener {
                        showError("Dashboard error: ${it.message}")
                    }
            }
            .addOnFailureListener {
                showError("Dashboard error: ${it.message}")
            }
    }

    // ADD CHANNEL

    private fun addAdminChannel() {

        if (!requireAdmin()) return

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
            setPadding(dp(20), dp(5), dp(20), dp(5))
            addView(name)
            addView(url)
            addView(category)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add New Channel")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val channelName = name.text.toString().trim()
                    val streamUrl = url.text.toString().trim()
                    val channelCategory =
                        category.text.toString().trim().ifBlank { "Other" }

                    if (channelName.isBlank()) {
                        name.error = "Enter channel name"
                        return@setOnClickListener
                    }

                    if (!isValidHttpUrl(streamUrl)) {
                        url.error = "Enter a valid HTTP(S) URL"
                        return@setOnClickListener
                    }

                    if (!requireAdmin()) return@setOnClickListener

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
                            dialog.dismiss()
                            Toast.makeText(
                                this,
                                "Channel added successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            showError("Firebase error: ${it.message}")
                        }
                }
        }

        dialog.show()
    }

    // CHANNEL MANAGER

    private fun showChannelManager() {

        if (!requireAdmin()) return

        FirebaseFirestore.getInstance()
            .collection("channels")
            .get()
            .addOnSuccessListener { snapshot ->

                val documents = snapshot.documents

                if (documents.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No Firebase channels found",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val names = documents.map { doc ->
                    val name = doc.getString("name") ?: "Unnamed"
                    val category = doc.getString("category") ?: "Other"
                    val enabled = doc.getBoolean("enabled") == true

                    "$name | $category | ${if (enabled) "ON" else "OFF"}"
                }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("📺 MANAGE CHANNELS")
                    .setItems(names) { _, index ->
                        showChannelActions(documents[index].id)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
            .addOnFailureListener {
                showError("Channel loading error: ${it.message}")
            }
    }

    private fun showChannelActions(documentId: String) {

        if (!requireAdmin()) return

        val options = arrayOf(
            "✏️ Edit Channel",
            "🗑 Delete Channel"
        )

        AlertDialog.Builder(this)
            .setTitle("Channel Management")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editAdminChannel(documentId)
                    1 -> deleteAdminChannel(documentId)
                }
            }
            .show()
    }

    private fun editAdminChannel(documentId: String) {

        if (!requireAdmin()) return

        val ref = FirebaseFirestore.getInstance()
            .collection("channels")
            .document(documentId)

        ref.get().addOnSuccessListener { doc ->

            if (!doc.exists()) {
                showError("Channel not found")
                return@addOnSuccessListener
            }

            val name = EditText(this).apply {
                hint = "Channel name"
                setText(doc.getString("name") ?: "")
                setTextColor(white)
            }

            val url = EditText(this).apply {
                hint = "Stream URL"
                setText(doc.getString("url") ?: "")
                setTextColor(white)
            }

            val category = EditText(this).apply {
                hint = "Category"
                setText(doc.getString("category") ?: "Other")
                setTextColor(white)
            }

            val enabled = CheckBox(this).apply {
                text = "Channel Enabled"
                setTextColor(white)
                isChecked = doc.getBoolean("enabled") == true
            }

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(name)
                addView(url)
                addView(category)
                addView(enabled)
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("✏️ Edit Channel")
                .setView(box)
                .setPositiveButton("Update", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener {

                        val newName = name.text.toString().trim()
                        val newUrl = url.text.toString().trim()
                        val newCategory = category.text.toString().trim()

                        if (newName.isBlank()) {
                            name.error = "Enter channel name"
                            return@setOnClickListener
                        }

                        if (!isValidHttpUrl(newUrl)) {
                            url.error = "Enter a valid URL"
                            return@setOnClickListener
                        }

                        if (!requireAdmin()) return@setOnClickListener

                        ref.update(
                            mapOf(
                                "name" to newName,
                                "url" to newUrl,
                                "category" to newCategory.ifBlank { "Other" },
                                "enabled" to enabled.isChecked
                            )
                        )
                            .addOnSuccessListener {
                                dialog.dismiss()
                                Toast.makeText(
                                    this,
                                    "Channel updated successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener {
                                showError("Update failed: ${it.message}")
                            }
                    }
            }

            dialog.show()
        }
            .addOnFailureListener {
                showError("Could not load channel: ${it.message}")
            }
    }

    private fun deleteAdminChannel(documentId: String) {

        if (!requireAdmin()) return

        AlertDialog.Builder(this)
            .setTitle("Delete Channel?")
            .setMessage(
                "এই Channel-টি Firebase থেকে স্থায়ীভাবে মুছে যাবে।"
            )
            .setPositiveButton("Delete") { _, _ ->

                if (!requireAdmin()) return@setPositiveButton

                FirebaseFirestore.getInstance()
                    .collection("channels")
                    .document(documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Channel deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        showError("Delete failed: ${it.message}")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // PLAYLIST MANAGER

    private fun showPlaylistManager() {

        if (!requireAdmin()) return

        FirebaseFirestore.getInstance()
            .collection("settings")
            .document("playlist")
            .get()
            .addOnSuccessListener { doc ->

                val playlists = getPlaylistMaps(doc)

                val options = arrayOf(
                    "➕ Add New Playlist",
                    "✏️ Edit Playlist",
                    "🗑 Delete Playlist",
                    "📋 View Playlists"
                )

                AlertDialog.Builder(this)
                    .setTitle("📂 M3U PLAYLIST MANAGER")
                    .setItems(options) { _, which ->

                        when (which) {
                            0 -> addM3UPlaylist()
                            1 -> selectPlaylistForEdit(playlists)
                            2 -> selectPlaylistForDelete(playlists)
                            3 -> viewM3UPlaylists(playlists)
                        }
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
            .addOnFailureListener {
                showError("Playlist error: ${it.message}")
            }
    }

    private fun getPlaylistMaps(
        doc: DocumentSnapshot
    ): MutableList<MutableMap<String, Any>> {

        val result = mutableListOf<MutableMap<String, Any>>()

        val saved = doc.get("playlists") as? List<*>

        if (saved != null) {

            saved.forEach { item ->

                val data = item as? Map<*, *> ?: return@forEach

                val name = data["name"] as? String ?: return@forEach
                val url = data["url"] as? String ?: return@forEach

                result.add(
                    mutableMapOf(
                        "name" to name,
                        "url" to url,
                        "enabled" to (data["enabled"] == true)
                    )
                )
            }

        } else {

            val oldUrl = doc.getString("url").orEmpty()

            if (oldUrl.isNotBlank()) {
                result.add(
                    mutableMapOf(
                        "name" to "Old Playlist",
                        "url" to oldUrl,
                        "enabled" to (doc.getBoolean("enabled") == true)
                    )
                )
            }
        }

        return result
    }

    private fun savePlaylistMaps(
        playlists: List<MutableMap<String, Any>>,
        successMessage: String
    ) {

        if (!requireAdmin()) return

        FirebaseFirestore.getInstance()
            .collection("settings")
            .document("playlist")
            .set(mapOf("playlists" to playlists))
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    successMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                showError("Playlist save failed: ${it.message}")
            }
    }

    private fun addM3UPlaylist() {

        if (!requireAdmin()) return

        val name = EditText(this).apply {
            hint = "Playlist name"
            setTextColor(white)
        }

        val url = EditText(this).apply {
            hint = "M3U URL"
            setTextColor(white)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(name)
            addView(url)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("➕ Add M3U Playlist")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val playlistName = name.text.toString().trim()
                    val playlistUrl = url.text.toString().trim()

                    if (playlistName.isBlank()) {
                        name.error = "Enter playlist name"
                        return@setOnClickListener
                    }

                    if (!isValidHttpUrl(playlistUrl)) {
                        url.error = "Enter valid playlist URL"
                        return@setOnClickListener
                    }

                    if (!requireAdmin()) return@setOnClickListener

                    val ref = FirebaseFirestore.getInstance()
                        .collection("settings")
                        .document("playlist")

                    ref.get().addOnSuccessListener { doc ->

                        val playlists = getPlaylistMaps(doc)

                        playlists.add(
                            mutableMapOf(
                                "name" to playlistName,
                                "url" to playlistUrl,
                                "enabled" to true
                            )
                        )

                        savePlaylistMaps(
                            playlists,
                            "Playlist added successfully"
                        )

                        dialog.dismiss()
                    }
                        .addOnFailureListener {
                            showError("Could not load playlists: ${it.message}")
                        }
                }
        }

        dialog.show()
    }

    private fun viewM3UPlaylists(
        playlists: List<MutableMap<String, Any>>
    ) {

        if (playlists.isEmpty()) {
            Toast.makeText(
                this,
                "No playlists found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val details = playlists.mapIndexed { index, item ->
            "${index + 1}. ${item["name"]}\n" +
            "${item["url"]}\n" +
            "Status: ${if (item["enabled"] == true) "Enabled" else "Disabled"}"
        }.joinToString("\n\n")

        AlertDialog.Builder(this)
            .setTitle("Saved M3U Playlists")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun selectPlaylistForEdit(
        playlists: MutableList<MutableMap<String, Any>>
    ) {

        if (playlists.isEmpty()) {
            Toast.makeText(
                this,
                "No playlists found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val names = playlists.map {
            it["name"].toString()
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Playlist to Edit")
            .setItems(names) { _, index ->
                editM3UPlaylist(playlists, index)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editM3UPlaylist(
        playlists: MutableList<MutableMap<String, Any>>,
        index: Int
    ) {

        if (!requireAdmin()) return

        val item = playlists[index]

        val name = EditText(this).apply {
            hint = "Playlist name"
            setText(item["name"].toString())
            setTextColor(white)
        }

        val url = EditText(this).apply {
            hint = "Playlist URL"
            setText(item["url"].toString())
            setTextColor(white)
        }

        val enabled = CheckBox(this).apply {
            text = "Playlist Enabled"
            setTextColor(white)
            isChecked = item["enabled"] == true
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(name)
            addView(url)
            addView(enabled)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Playlist")
            .setView(box)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val newName = name.text.toString().trim()
                    val newUrl = url.text.toString().trim()

                    if (newName.isBlank()) {
                        name.error = "Enter playlist name"
                        return@setOnClickListener
                    }

                    if (!isValidHttpUrl(newUrl)) {
                        url.error = "Enter valid playlist URL"
                        return@setOnClickListener
                    }

                    if (!requireAdmin()) return@setOnClickListener

                    playlists[index] = mutableMapOf(
                        "name" to newName,
                        "url" to newUrl,
                        "enabled" to enabled.isChecked
                    )

                    savePlaylistMaps(
                        playlists,
                        "Playlist updated successfully"
                    )

                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun selectPlaylistForDelete(
        playlists: MutableList<MutableMap<String, Any>>
    ) {

        if (playlists.isEmpty()) {
            Toast.makeText(
                this,
                "No playlists found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val names = playlists.map {
            it["name"].toString()
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Playlist to Delete")
            .setItems(names) { _, index ->

                AlertDialog.Builder(this)
                    .setTitle("Delete Playlist?")
                    .setMessage(
                        "Are you sure you want to delete " +
                        "${playlists[index]["name"]}?"
                    )
                    .setPositiveButton("Delete") { _, _ ->

                        if (!requireAdmin()) {
                            return@setPositiveButton
                        }

                        playlists.removeAt(index)

                        savePlaylistMaps(
                            playlists,
                            "Playlist deleted successfully"
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // FIREBASE + PLAYLIST LOADING

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
                    status.text = "Firebase error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                manualChannels.clear()

                snapshot.documents.forEach { doc ->

                    val name = doc.getString("name")
                    val url = doc.getString("url")
                    val category = doc.getString("category") ?: "Other"

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
                    status.text = "Playlist settings error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    playlistRequestId++
                    playlistChannels.clear()
                    updateCombinedChannels()
                    return@addSnapshotListener
                }

                val items = mutableListOf<Pair<String, String>>()

                val saved = snapshot.get("playlists") as? List<*>

                if (saved != null) {

                    saved.forEach { item ->

                        val data = item as? Map<*, *>
                            ?: return@forEach

                        val name = data["name"] as? String ?: "Playlist"
                        val url = data["url"] as? String ?: ""
                        val enabled = data["enabled"] == true

                        if (enabled && url.isNotBlank()) {
                            items.add(name to url)
                        }
                    }

                } else {

                    val oldUrl = snapshot.getString("url")
                        ?.trim()
                        .orEmpty()

                    val enabled = snapshot.getBoolean("enabled") == true

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
            channels.none { it.third == selectedCategory }
        ) {
            selectedCategory = "All"
        }

        status.text = "${channels.size} CHANNELS AVAILABLE"

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
                        throw Exception("HTTP ${connection.responseCode}")
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

                        all.add(Triple(it.first, it.second, cat))
                    }

                    if (parsed.isEmpty()) {
                        errors.add("${playlist.first}: Empty playlist")
                    }

                } catch (e: Exception) {
                    errors.add("${playlist.first}: ${e.message}")
                } finally {
                    connection?.disconnect()
                }
            }

            runOnUiThread {

                if (isFinishing || requestId != playlistRequestId) {
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
                        ?: displayName.ifBlank { "Unknown Channel" }

                currentCategory =
                    group?.takeIf { it.isNotBlank() } ?: "Other"

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

    // CATEGORY FILTER

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

            val params = LinearLayout.LayoutParams(-2, -2)
            params.marginEnd = dp(8)

            categoryList.addView(chip, params)
        }
    }

    // CHANNEL LIST

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
                    setPadding(10, dp(35), 10, dp(35))
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

            setPadding(dp(13), dp(13), dp(10), dp(13))

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
        params.bottomMargin = dp(10)
        card.layoutParams = params

        return card
    }

    // COMMON HELPERS

    private fun isValidHttpUrl(value: String): Boolean {
        return value.startsWith("https://", true) ||
            value.startsWith("http://", true)
    }

    private fun showError(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // BACK BUTTON + EXIT

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
