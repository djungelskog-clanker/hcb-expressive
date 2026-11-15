package lol.djungelskog.hcbx.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { System, Light, Dark }

/** User preferences, backed by SharedPreferences but observable from Compose. */
object Settings {
    private lateinit var prefs: android.content.SharedPreferences

    var themeMode by mutableStateOf(ThemeMode.System); private set
    var dynamicColor by mutableStateOf(true); private set
    var hideBalances by mutableStateOf(false); private set
    var biometricLock by mutableStateOf(false); private set
    var secureScreen by mutableStateOf(false); private set
    var haptics by mutableStateOf(true); private set
    var lastOrg by mutableStateOf<String?>(null); private set

    fun init(c: Context) {
        prefs = c.getSharedPreferences("hcbx_settings", Context.MODE_PRIVATE)
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "System")!!) }.getOrDefault(ThemeMode.System)
        dynamicColor = prefs.getBoolean("dynamic", true)
        hideBalances = prefs.getBoolean("hide", false)
        biometricLock = prefs.getBoolean("bio", false)
        secureScreen = prefs.getBoolean("secure", false)
        haptics = prefs.getBoolean("haptics", true)
        lastOrg = prefs.getString("org", null)
    }

    fun setTheme(v: ThemeMode) { themeMode = v; prefs.edit().putString("theme", v.name).apply() }
    fun setDynamic(v: Boolean) { dynamicColor = v; prefs.edit().putBoolean("dynamic", v).apply() }
    fun setHide(v: Boolean) { hideBalances = v; prefs.edit().putBoolean("hide", v).apply() }
    fun setBio(v: Boolean) { biometricLock = v; prefs.edit().putBoolean("bio", v).apply() }
    fun setSecure(v: Boolean) { secureScreen = v; prefs.edit().putBoolean("secure", v).apply() }
    fun setHaptics(v: Boolean) { haptics = v; prefs.edit().putBoolean("haptics", v).apply() }
    fun setLastOrg(v: String?) { lastOrg = v; prefs.edit().putString("org", v).apply() }
}
