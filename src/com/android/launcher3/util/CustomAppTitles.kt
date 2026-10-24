/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.UserHandle
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.tasks.PackageUpdatedTask
import com.android.launcher3.pm.UserCache

/**
 * Stores the titles users give to apps, keyed by launcher activity and user. The title replaces
 * the app label everywhere the launcher shows it: home screen, app drawer, search and Recents.
 */
object CustomAppTitles {
    private const val PREFS_NAME = "custom_app_titles"
    private const val KEY_SEPARATOR = "|"

    // Hold the listeners strongly, SharedPreferences only keeps weak references to them
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun userPrefix(context: Context, user: UserHandle) =
        "${UserCache.INSTANCE.get(context).getSerialNumberForUser(user)}$KEY_SEPARATOR"

    private fun key(context: Context, component: ComponentName, user: UserHandle) =
        userPrefix(context, user) + component.flattenToString()

    /** Returns the custom title of the activity, or null if it wasn't renamed. */
    @JvmStatic
    fun get(context: Context, component: ComponentName, user: UserHandle): String? =
        prefs(context).getString(key(context, component, user), null)

    /**
     * Returns the custom title of the package's only renamed activity, or null if none or several
     * of its activities were renamed. Used when the exact launcher activity is unknown.
     */
    @JvmStatic
    fun getForPackage(context: Context, packageName: String, user: UserHandle): String? {
        val prefix = userPrefix(context, user) + packageName + "/"
        return prefs(context).all.filterKeys { it.startsWith(prefix) }.values.singleOrNull()
            as? String
    }

    /** Sets the custom title of the activity, or resets it to the app label if [title] is empty. */
    @JvmStatic
    fun set(context: Context, component: ComponentName, user: UserHandle, title: String?) {
        val newTitle = title?.trim()
        val editor = prefs(context).edit()
        if (newTitle.isNullOrEmpty()) {
            editor.remove(key(context, component, user))
        } else {
            editor.putString(key(context, component, user), newTitle)
        }
        editor.apply()

        // Reload the titles of the package's home screen and app drawer items
        LauncherAppState.getInstance(context)
            .model
            .enqueueModelUpdateTask(
                PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, component.packageName)
            )
    }

    /** Calls [onChange] whenever a custom title is set or reset. */
    @JvmStatic
    fun addChangeListener(context: Context, onChange: Runnable): SafeCloseable {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange.run() }
        synchronized(listeners) { listeners.add(listener) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return SafeCloseable {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            synchronized(listeners) { listeners.remove(listener) }
        }
    }
}
