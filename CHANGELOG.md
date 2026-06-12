# Changelog

## v1.0
This major release transforms Glance from a standalone overlay drawer utility into a fully functional system Home Screen launcher.

### Added
- System Home Screen launcher support (registered `android.intent.category.HOME` in manifest).
- Translucent system wallpaper rendering option (`Theme.Glance.Wallpaper`).
- Wallpaper dimming support integrated with the background opacity settings slider.
- "Show only favorites on startup" option in settings.
- "Enable double tap gesture" option to toggle the sidebar toggle gesture in settings.
- Custom back button interception to clear active search queries or prevent accidental home screen exits.

### Changed
- Replaced touch-disable behavior with `scrollVerticallyBy` redirection in `ScrollControlLayoutManager` to avoid layout range measurement loops.
- Prevented calling `finish()` on launching external apps when running as the default system launcher.

## v0.9 (Pre-release)
Initial set of customized overlay capabilities.

### Added
- Moveable Search Bar (top/bottom constraint switching).
- Icon size customization slider (from 0% text-only up to 100% original size).
- Context menu options (long-press) to add/remove apps from Favorites or Hide them.
- Favorites filtering via double-tap gesture on the WaveSideBar.
- Option to view hidden apps in a filtered list.
- Search queries query apps globally across custom lists (Favorites and Hidden views) and show indicators.
- Stack-from-bottom layout alignment for Favorites and Hidden views to optimize reachability.
