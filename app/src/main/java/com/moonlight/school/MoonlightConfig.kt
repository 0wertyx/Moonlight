package com.moonlight.school

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * The persistence contract is independent of the APK version.
 * Never rename these stores or reset them on an application update.
 * WebView authentication remains in Android's own private storage.
 */
object MoonlightConfig {
    const val SCHEMA_VERSION = 1
    const val PERSONAL_STORE = "moonlight_local"
    const val SNAPSHOT_STORE = "moonlight_snapshot"
    private const val META_STORE = "moonlight_config_meta"
    private const val SCHEMA_KEY = "schema_version"

    fun personal(context: Context): SharedPreferences =
        context.getSharedPreferences(PERSONAL_STORE, Context.MODE_PRIVATE)

    fun snapshot(context: Context): SharedPreferences =
        context.getSharedPreferences(SNAPSHOT_STORE, Context.MODE_PRIVATE)

    private fun meta(context: Context): SharedPreferences =
        context.getSharedPreferences(META_STORE, Context.MODE_PRIVATE)

    fun version(context: Context): Int = meta(context).getInt(SCHEMA_KEY, 0)

    @Synchronized
    fun migrate(context: Context): Boolean {
        val metadata = meta(context)
        val existing = version(context)
        if (existing > SCHEMA_VERSION) return false
        if (existing == SCHEMA_VERSION) return true

        // v0 -> v1: adopt existing stores in place. Only add a separate schema
        // marker; leave all existing and unknown keys and all data untouched.
        // Future migrations must be additive or copy verified values before
        // committing their own schema marker. Never clear preferences here.
        val target = ConfigMigration.next(existing, SCHEMA_VERSION) ?: return false
        return metadata.edit().putInt(SCHEMA_KEY, target).commit()
    }
}

internal object ConfigMigration {
    /** Initial migration adopts existing stores; unknown versions are not reset. */
    fun next(from: Int, current: Int): Int? = when {
        from == current && current == 1 -> 1
        from == 0 && current == 1 -> 1
        else -> null
    }
}

class MoonlightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!MoonlightConfig.migrate(this)) {
            Log.w("MoonlightConfig", "Config schema not migrated; existing data retained")
        }
    }
}
