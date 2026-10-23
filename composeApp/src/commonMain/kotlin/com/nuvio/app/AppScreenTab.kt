package com.nuvio.app

import com.nuvio.app.core.ui.NativeNavigationTab

enum class AppScreenTab {
    Home,
    Search,
    Discover,
    Library,
    Calendar,
    LiveTv,
    Settings,
    ;

    companion object {
        fun fromName(name: String): AppScreenTab =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Home
    }
}

internal fun AppScreenTab.toNativeNavigationTab(): NativeNavigationTab = when (this) {
    AppScreenTab.Home -> NativeNavigationTab.Home
    AppScreenTab.Search -> NativeNavigationTab.Search
    AppScreenTab.Discover -> NativeNavigationTab.Discover
    AppScreenTab.Calendar -> NativeNavigationTab.Calendar
    AppScreenTab.Library -> NativeNavigationTab.Library
    AppScreenTab.LiveTv -> NativeNavigationTab.LiveTv
    AppScreenTab.Settings -> NativeNavigationTab.Settings
}

internal fun NativeNavigationTab.toAppScreenTab(): AppScreenTab = when (this) {
    NativeNavigationTab.Home -> AppScreenTab.Home
    NativeNavigationTab.Search -> AppScreenTab.Search
    NativeNavigationTab.Discover -> AppScreenTab.Discover
    NativeNavigationTab.Calendar -> AppScreenTab.Calendar
    NativeNavigationTab.Library -> AppScreenTab.Library
    NativeNavigationTab.LiveTv -> AppScreenTab.LiveTv
    NativeNavigationTab.Settings -> AppScreenTab.Settings
}
