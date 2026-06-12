package com.waph1.glance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var apps = listOf<AppInfo>()
    var iconSizePercent: Int = 100
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var favoriteApps: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var showOnlyFavorites: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submitList(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameView: TextView = itemView.findViewById(R.id.appName)
        private val starView: ImageView = itemView.findViewById(R.id.appFavoriteStar)

        fun bind(app: AppInfo) {
            val density = itemView.context.resources.displayMetrics.density
            val lpIcon = iconView.layoutParams as ViewGroup.MarginLayoutParams
            val lpName = nameView.layoutParams as ViewGroup.MarginLayoutParams

            if (iconSizePercent <= 0) {
                iconView.visibility = View.GONE
                lpName.marginStart = 0
            } else {
                iconView.visibility = View.VISIBLE
                iconView.setImageDrawable(app.icon)

                val sizeDp = 48f * (iconSizePercent / 100f)
                val sizePx = (sizeDp * density).toInt()
                lpIcon.width = sizePx
                lpIcon.height = sizePx
                iconView.layoutParams = lpIcon

                lpName.marginStart = (16 * density).toInt()
            }
            nameView.layoutParams = lpName

            // Bind favorite star
            starView.visibility = if (favoriteApps.contains(app.packageName) && !showOnlyFavorites) View.VISIBLE else View.GONE

            nameView.text = app.label
            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener {
                onAppLongClick(app)
                true
            }
        }
    }
}
