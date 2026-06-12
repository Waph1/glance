package com.waph1.glance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()
    private lateinit var rootLayout: ConstraintLayout
    private var hiddenApps: MutableSet<String> = mutableSetOf()
    private var favoriteApps: MutableSet<String> = mutableSetOf()
    private var showOnlyHidden: Boolean = false
    private var showOnlyFavorites: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Custom Search UI
        setContentView(R.layout.activity_main)
        setupUI()
    }



    private fun setupUI() {
        rootLayout = findViewById(R.id.rootLayout)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val recyclerView = findViewById<RecyclerView>(R.id.appsRecyclerView)
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        // Load opacity
        val savedOpacity = prefs.getInt("opacity", 128)
        updateBackgroundOpacity(savedOpacity)

        // Load hidden apps
        hiddenApps = prefs.getStringSet("hidden_apps", emptySet())?.toMutableSet() ?: mutableSetOf()

        // Load favorite apps
        favoriteApps = prefs.getStringSet("favorite_apps", emptySet())?.toMutableSet() ?: mutableSetOf()

        // Load show only hidden apps
        showOnlyHidden = prefs.getBoolean("show_only_hidden", false)

        // Load show only favorites on startup
        showOnlyFavorites = prefs.getBoolean("start_only_favorites", false)

        settingsButton.setOnClickListener {
            showOpacityDialog()
        }

        adapter = AppAdapter(
            onAppClick = { app ->
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    finish()
                }
            },
            onAppLongClick = { app ->
                showHideAppDialog(app, searchEditText.text.toString())
            }
        )

        val layoutManager = ScrollControlLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSidebarAndScrollState()
        }

        val savedIconSize = prefs.getInt("icon_size", 100)
        adapter.iconSizePercent = savedIconSize
        adapter.favoriteApps = favoriteApps
        adapter.showOnlyFavorites = showOnlyFavorites

        // Setup Wave Side Bar
        val waveSideBar = findViewById<WaveSideBar>(R.id.waveSideBar)
        
        // Fix height to initial measured height to avoid compression by keyboard
        // We wait for the first layout pass, then lock the height.
        waveSideBar.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (waveSideBar.height > 0) {
                    val params = waveSideBar.layoutParams
                    params.height = waveSideBar.height
                    waveSideBar.layoutParams = params
                    waveSideBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })

        // Apply settings
        val showSidebar = prefs.getBoolean("show_sidebar", true)
        val sidebarHaptics = prefs.getBoolean("sidebar_haptics", true)
        val searchBottom = prefs.getBoolean("search_bottom", false)
        waveSideBar.visibility = if (showSidebar) View.VISIBLE else View.GONE
        waveSideBar.isHapticEnabled = sidebarHaptics
        updateSearchBarPosition(searchBottom)

        setupKeyboardListener(rootLayout, waveSideBar)

        waveSideBar.onLetterSelected = { letter ->
            val index = if (letter == "#") {
                // Find first app that does NOT start with a letter
                allApps.indexOfFirst { it.label.isNotEmpty() && !it.label.first().isLetter() }
            } else {
                allApps.indexOfFirst { it.label.startsWith(letter, ignoreCase = true) }
            }
            
            if (index != -1) {
                (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
            }
        }

        waveSideBar.onDoubleTap = {
            val enableDoubleTap = prefs.getBoolean("enable_double_tap", true)
            if (enableDoubleTap) {
                showOnlyFavorites = !showOnlyFavorites
                val query = searchEditText.text.toString()
                filterApps(query)
            }
        }

        // Load apps asynchronously to avoid blocking UI thread
        Thread {
            // Sort: Letters first (A-Z), then others (numbers, symbols)
            allApps = loadInstalledApps().sortedWith(Comparator { a, b ->
                val aFirst = a.label.firstOrNull() ?: ' '
                val bFirst = b.label.firstOrNull() ?: ' '
                
                val aIsLetter = aFirst.isLetter()
                val bIsLetter = bFirst.isLetter()
                
                if (aIsLetter && !bIsLetter) {
                    -1 // a comes first
                } else if (!aIsLetter && bIsLetter) {
                    1 // b comes first
                } else {
                    // Both are letters or both are not letters, sort alphabetically
                    a.label.compareTo(b.label, ignoreCase = true)
                }
            })
            
            runOnUiThread {
                filterApps(searchEditText.text.toString())
            }
        }.start()

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Focus search bar automatically ONLY if enabled
        val autoKeyboard = prefs.getBoolean("auto_keyboard", false) // Default false
        if (autoKeyboard) {
            searchEditText.requestFocus()
            // Show keyboard explicitly might be needed
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showHideAppDialog(app: AppInfo, currentQuery: String) {
        val isHidden = hiddenApps.contains(app.packageName)
        val isFavorite = favoriteApps.contains(app.packageName)
        val hideOption = if (isHidden) "Unhide App" else "Hide App"
        val favoriteOption = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
        val options = arrayOf("App Info", hideOption, favoriteOption)

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // App Info
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${app.packageName}")
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    1 -> {
                        // Hide/Unhide
                        if (isHidden) {
                            hiddenApps.remove(app.packageName)
                        } else {
                            hiddenApps.add(app.packageName)
                        }
                        // Save to prefs
                        getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .edit()
                            .putStringSet("hidden_apps", hiddenApps)
                            .apply()
                        
                        // Refresh list
                        filterApps(currentQuery)
                    }
                    2 -> {
                        // Add/Remove Favorite
                        if (isFavorite) {
                            favoriteApps.remove(app.packageName)
                        } else {
                            favoriteApps.add(app.packageName)
                        }
                        // Save to prefs
                        getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .edit()
                            .putStringSet("favorite_apps", favoriteApps)
                            .apply()
                        
                        // Refresh adapter
                        adapter.favoriteApps = favoriteApps
                    }
                }
            }
            .show()
    }

    private fun showOpacityDialog() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentOpacity = prefs.getInt("opacity", 128)
        val currentAutoKeyboard = prefs.getBoolean("auto_keyboard", false)
        val currentShowSidebar = prefs.getBoolean("show_sidebar", true)
        val currentHaptics = prefs.getBoolean("sidebar_haptics", true)
        val currentSearchBottom = prefs.getBoolean("search_bottom", false)
        val currentIconSize = prefs.getInt("icon_size", 100)
        val currentShowOnlyHidden = prefs.getBoolean("show_only_hidden", false)
        val currentStartOnlyFavorites = prefs.getBoolean("start_only_favorites", false)
        val currentEnableDoubleTap = prefs.getBoolean("enable_double_tap", true)

        // Using a simple linear layout for the dialog content
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        val label = TextView(this).apply {
            text = "Background Opacity"
            textSize = 18f
            setPadding(0, 0, 0, 30)
        }
        
        val seekBar = SeekBar(this).apply {
            max = 255
            progress = currentOpacity
        }

        val iconSizeLabel = TextView(this).apply {
            text = "Icon Size: $currentIconSize%"
            textSize = 18f
            setPadding(0, 40, 0, 20)
        }

        val iconSizeSeekBar = SeekBar(this).apply {
            max = 100
            progress = currentIconSize
        }

        iconSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                iconSizeLabel.text = "Icon Size: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val autoKeyboardSwitch = android.widget.Switch(this).apply {
            text = "Auto-open Keyboard"
            isChecked = currentAutoKeyboard
            setPadding(0, 50, 0, 0)
        }

        val showSidebarSwitch = android.widget.Switch(this).apply {
            text = "Show Sidebar"
            isChecked = currentShowSidebar
            setPadding(0, 30, 0, 0)
        }

        val hapticsSwitch = android.widget.Switch(this).apply {
            text = "Sidebar Haptics"
            isChecked = currentHaptics
            setPadding(0, 30, 0, 0)
        }

        val searchBottomSwitch = android.widget.Switch(this).apply {
            text = "Search bar at bottom"
            isChecked = currentSearchBottom
            setPadding(0, 30, 0, 0)
        }

        val showOnlyHiddenSwitch = android.widget.Switch(this).apply {
            text = "Show only hidden apps"
            isChecked = currentShowOnlyHidden
            setPadding(0, 30, 0, 0)
        }

        container.addView(label)
        container.addView(seekBar)
        container.addView(iconSizeLabel)
        container.addView(iconSizeSeekBar)
        container.addView(autoKeyboardSwitch)
        val startOnlyFavoritesSwitch = android.widget.Switch(this).apply {
            text = "Show only favorites on startup"
            isChecked = currentStartOnlyFavorites
            setPadding(0, 30, 0, 0)
        }

        val enableDoubleTapSwitch = android.widget.Switch(this).apply {
            text = "Enable double tap gesture"
            isChecked = currentEnableDoubleTap
            setPadding(0, 30, 0, 0)
        }

        container.addView(showSidebarSwitch)
        container.addView(hapticsSwitch)
        container.addView(searchBottomSwitch)
        container.addView(showOnlyHiddenSwitch)
        container.addView(startOnlyFavoritesSwitch)
        container.addView(enableDoubleTapSwitch)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                prefs.edit()
                    .putInt("opacity", seekBar.progress)
                    .putBoolean("auto_keyboard", autoKeyboardSwitch.isChecked)
                    .putBoolean("show_sidebar", showSidebarSwitch.isChecked)
                    .putBoolean("sidebar_haptics", hapticsSwitch.isChecked)
                    .putBoolean("search_bottom", searchBottomSwitch.isChecked)
                    .putInt("icon_size", iconSizeSeekBar.progress)
                    .putBoolean("show_only_hidden", showOnlyHiddenSwitch.isChecked)
                    .putBoolean("start_only_favorites", startOnlyFavoritesSwitch.isChecked)
                    .putBoolean("enable_double_tap", enableDoubleTapSwitch.isChecked)
                    .apply()
                
                // Apply settings immediately
                val waveSideBar = findViewById<WaveSideBar>(R.id.waveSideBar)
                waveSideBar.visibility = if (showSidebarSwitch.isChecked) View.VISIBLE else View.GONE
                waveSideBar.isHapticEnabled = hapticsSwitch.isChecked
                updateSearchBarPosition(searchBottomSwitch.isChecked)
                adapter.iconSizePercent = iconSizeSeekBar.progress
                showOnlyHidden = showOnlyHiddenSwitch.isChecked
                val query = findViewById<EditText>(R.id.searchEditText)?.text?.toString() ?: ""
                filterApps(query)
                updateSidebarAndScrollState()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Revert to original if cancelled
                updateBackgroundOpacity(currentOpacity)
            }
            .create()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateBackgroundOpacity(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.show()
    }

    private fun updateBackgroundOpacity(alpha: Int) {
        // Black background with variable alpha
        val color = Color.argb(alpha, 0, 0, 0)
        rootLayout.setBackgroundColor(color)
    }

    private fun updateSearchBarPosition(searchBottom: Boolean) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)

        val margin16 = (16 * resources.displayMetrics.density).toInt()
        val margin8 = (8 * resources.displayMetrics.density).toInt()

        if (searchBottom) {
            // Search bar at the bottom
            constraintSet.clear(R.id.searchContainer, ConstraintSet.TOP)
            constraintSet.connect(
                R.id.searchContainer,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                margin16
            )

            // appsRecyclerView constraints
            constraintSet.clear(R.id.appsRecyclerView, ConstraintSet.TOP)
            constraintSet.clear(R.id.appsRecyclerView, ConstraintSet.BOTTOM)
            constraintSet.connect(
                R.id.appsRecyclerView,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                margin16
            )
            constraintSet.connect(
                R.id.appsRecyclerView,
                ConstraintSet.BOTTOM,
                R.id.searchContainer,
                ConstraintSet.TOP,
                margin8
            )

            // waveSideBar constraints
            constraintSet.clear(R.id.waveSideBar, ConstraintSet.TOP)
            constraintSet.clear(R.id.waveSideBar, ConstraintSet.BOTTOM)
            constraintSet.connect(
                R.id.waveSideBar,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                margin16
            )
            constraintSet.connect(
                R.id.waveSideBar,
                ConstraintSet.BOTTOM,
                R.id.searchContainer,
                ConstraintSet.TOP,
                margin8
            )
        } else {
            // Search bar at the top
            constraintSet.clear(R.id.searchContainer, ConstraintSet.BOTTOM)
            constraintSet.connect(
                R.id.searchContainer,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                margin16
            )

            // appsRecyclerView constraints
            constraintSet.clear(R.id.appsRecyclerView, ConstraintSet.TOP)
            constraintSet.clear(R.id.appsRecyclerView, ConstraintSet.BOTTOM)
            constraintSet.connect(
                R.id.appsRecyclerView,
                ConstraintSet.TOP,
                R.id.searchContainer,
                ConstraintSet.BOTTOM,
                margin8
            )
            constraintSet.connect(
                R.id.appsRecyclerView,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                0
            )

            // waveSideBar constraints
            constraintSet.clear(R.id.waveSideBar, ConstraintSet.TOP)
            constraintSet.clear(R.id.waveSideBar, ConstraintSet.BOTTOM)
            constraintSet.connect(
                R.id.waveSideBar,
                ConstraintSet.TOP,
                R.id.searchContainer,
                ConstraintSet.BOTTOM,
                0
            )
            constraintSet.connect(
                R.id.waveSideBar,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                0
            )
        }

        constraintSet.applyTo(rootLayout)
    }


    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(intent, 0)
        return apps.mapNotNull { resolveInfo ->
            try {
                val packageName = resolveInfo.activityInfo.packageName
                // Filter out our own app to avoid confusion
                if (packageName == this.packageName) return@mapNotNull null
                
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                AppInfo(label, packageName, icon)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun setupKeyboardListener(rootView: View, waveSideBar: View) {
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            // 0.15 ratio is enough to determine if keyboard is open
            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard is open
                waveSideBar.visibility = View.GONE
            } else {
                // Keyboard is closed
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val showSidebar = prefs.getBoolean("show_sidebar", true)
                if (showSidebar) {
                    waveSideBar.visibility = View.VISIBLE
                } else {
                    waveSideBar.visibility = View.GONE
                }
            }
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isNotEmpty()) {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        } else {
            if (showOnlyFavorites) {
                allApps.filter { favoriteApps.contains(it.packageName) }
            } else if (showOnlyHidden) {
                allApps.filter { hiddenApps.contains(it.packageName) }
            } else {
                allApps.filter { !hiddenApps.contains(it.packageName) }
            }
        }
        
        adapter.showOnlyFavorites = showOnlyFavorites && query.isEmpty()

        val recyclerView = findViewById<RecyclerView>(R.id.appsRecyclerView)
        (recyclerView.layoutManager as? LinearLayoutManager)?.stackFromEnd = (query.isEmpty() && (showOnlyFavorites || showOnlyHidden))
        adapter.submitList(filtered)
    }

    private fun updateSidebarAndScrollState() {
        val recyclerView = findViewById<RecyclerView>(R.id.appsRecyclerView) ?: return
        val waveSideBar = findViewById<WaveSideBar>(R.id.waveSideBar) ?: return
        val layoutManager = recyclerView.layoutManager as? ScrollControlLayoutManager ?: return
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        val contentHeight = recyclerView.computeVerticalScrollRange()
        val viewHeight = recyclerView.height
        val exceeds = contentHeight > viewHeight

        layoutManager.isScrollEnabled = exceeds
        recyclerView.overScrollMode = if (exceeds) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER

        val showSidebarPref = prefs.getBoolean("show_sidebar", true)
        waveSideBar.alpha = if (showSidebarPref && exceeds) 1f else 0f
    }

    class ScrollControlLayoutManager(context: Context) : LinearLayoutManager(context) {
        var isScrollEnabled = true
        override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
            return if (isScrollEnabled) {
                super.scrollVerticallyBy(dy, recycler, state)
            } else {
                0
            }
        }
    }
}
