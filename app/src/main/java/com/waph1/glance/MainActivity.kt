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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()
    private lateinit var rootLayout: ConstraintLayout
    private var hiddenApps: MutableSet<String> = mutableSetOf()

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

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

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
        waveSideBar.visibility = if (showSidebar) View.VISIBLE else View.GONE
        waveSideBar.isHapticEnabled = sidebarHaptics

        setupKeyboardListener(rootLayout, waveSideBar)

        waveSideBar.onLetterSelected = { letter ->
            val index = if (letter == "#") {
                // Find first app that does NOT start with a letter
                allApps.indexOfFirst { !it.label.first().isLetter() }
            } else {
                allApps.indexOfFirst { it.label.startsWith(letter, ignoreCase = true) }
            }
            
            if (index != -1) {
                (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
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
        val hideOption = if (isHidden) "Unhide App" else "Hide App"
        val options = arrayOf("App Info", hideOption)

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

        container.addView(label)
        container.addView(seekBar)
        container.addView(autoKeyboardSwitch)
        container.addView(showSidebarSwitch)
        container.addView(hapticsSwitch)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                prefs.edit()
                    .putInt("opacity", seekBar.progress)
                    .putBoolean("auto_keyboard", autoKeyboardSwitch.isChecked)
                    .putBoolean("show_sidebar", showSidebarSwitch.isChecked)
                    .putBoolean("sidebar_haptics", hapticsSwitch.isChecked)
                    .apply()
                
                // Apply settings immediately
                val waveSideBar = findViewById<WaveSideBar>(R.id.waveSideBar)
                waveSideBar.visibility = if (showSidebarSwitch.isChecked) View.VISIBLE else View.GONE
                waveSideBar.isHapticEnabled = hapticsSwitch.isChecked
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
        val filtered = if (query.isEmpty()) {
            // If query is empty, show all apps EXCEPT hidden ones
            allApps.filter { !hiddenApps.contains(it.packageName) }
        } else {
            // If query is NOT empty, show all matching apps (INCLUDING hidden ones)
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        adapter.submitList(filtered)
    }
}
