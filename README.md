# Glance

Glance is a lightweight, efficient launcher utility designed to give you quick access to your installed applications. It features a sleek, alphabetical sidebar for fast navigation and a powerful settings system for deep personalization.

## Features

- **Alphabetical Wave Sidebar**: Navigate the app list by letter using a fluid, responsive sidebar with interactive scale feedback.
- **Search Bar Positioning**: Toggle the search bar position between the top and bottom of the screen via settings.
- **Icon Size Customization**: Adjust icon size from 0% to 100%. At 0%, icons are completely removed and text margins are optimized for a clean, text-only list.
- **Favorites System**: Long-press any app to add it to favorites. A star icon appears next to favorite apps in the main list.
- **Favorites-Only View**: Double-tap on the sidebar to toggle between the full list and a favorites-only view. In favorites-only mode, the star icons are hidden for a cleaner layout.
- **Hidden Apps**: Hide apps from the main list via the long-press menu. Toggle "Show only hidden apps" in settings to manage and launch them.
- **Global Search**: Type in the search bar to query all installed apps globally, overriding active filters (favorites-only/hidden-only) and displaying stars on favorites. Clearing the search automatically restores the active filter.
- **Bottom Alignment**: Custom views (Favorites-only and Hidden-only) stack apps from the bottom of the screen up, while preserving alphabetical (A-Z) ordering.
- **Dynamic Scroll and Sidebar Control**: If the current list fits completely on the screen, scrolling and overscroll stretch animations are automatically disabled, and the sidebar fades out while remaining functional for the double-tap gesture.
- **Startup Customization**: Configure Glance to display only favorite apps on startup.
- **Gesture Control**: Toggle the double-tap gesture on the sidebar on or off in settings to prevent accidental triggers.
- **Background Opacity**: Customize launcher background opacity.

## Recommended Usage

Glance is optimized to be used as a quick-access overlay launcher:
- **Gestures**: Bind Glance to launch on a swipe gesture (e.g., swipe up from the bottom).
- **Physical Keys**: Map it to an extra hardware button or accessibility shortcut for instant access from any screen.

## Technical Details

- **Language**: Kotlin
- **UI Structure**: XML layouts, custom views (WaveSideBar), ConstraintLayout configurations
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle (Kotlin DSL)

## Build and Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/Waph1/glance.git
   ```
2. Open the project in Android Studio.
3. Build the debug APK or run the application directly on your device.
