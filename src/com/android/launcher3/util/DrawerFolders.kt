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
import android.util.Log
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores the folders of the app drawer. They are independent of the home screen folders: each one
 * has a title and the apps it holds, which are shown in the folder instead of the app list.
 */
object DrawerFolders {
    private const val TAG = "DrawerFolders"
    private const val PREFS_NAME = "drawer_folders"
    private const val KEY_FOLDERS = "folders"
    private const val KEY_SEPARATOR = "|"

    private const val JSON_ID = "id"
    private const val JSON_TITLE = "title"
    private const val JSON_APPS = "apps"

    // Item ids of the drawer folders, far from the ids of the home screen items and containers
    private const val ITEM_ID_OFFSET = -1_000_000

    /** A folder of the app drawer. */
    data class Folder(val id: Int, val title: String, val apps: List<ComponentKey>)

    private val listeners = mutableSetOf<Runnable>()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the item id of the folder's FolderInfo. */
    @JvmStatic fun toItemId(folderId: Int) = ITEM_ID_OFFSET - folderId

    /** Returns the id of the folder of the FolderInfo, or null if it isn't a drawer folder. */
    @JvmStatic
    fun fromItemId(itemId: Int): Int? = (ITEM_ID_OFFSET - itemId).takeIf { it > 0 }

    /** Returns the folders, in the order they were created. */
    @JvmStatic
    fun getFolders(context: Context): List<Folder> {
        val json = prefs(context).getString(KEY_FOLDERS, null) ?: return emptyList()
        val userCache = UserCache.INSTANCE.get(context)
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val folder = array.getJSONObject(i)
                val apps = folder.getJSONArray(JSON_APPS)
                Folder(
                    folder.getInt(JSON_ID),
                    folder.getString(JSON_TITLE),
                    (0 until apps.length()).mapNotNull { parseKey(userCache, apps.getString(it)) },
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Unable to parse the drawer folders", e)
            emptyList()
        }
    }

    /** Returns the folder with the [id], or null if it doesn't exist. */
    @JvmStatic
    fun getFolder(context: Context, id: Int): Folder? = getFolders(context).find { it.id == id }

    /** Creates a folder holding the [apps] and returns its id. */
    @JvmStatic
    fun createFolder(context: Context, title: String, apps: Collection<ComponentKey>): Int {
        val folders = getFolders(context)
        val id = (folders.maxOfOrNull { it.id } ?: 0) + 1
        save(context, folders + Folder(id, title.trim(), apps.distinct()))
        return id
    }

    /**
     * Renames the folder. [notify] is false when the caller already shows the new title, so the
     * app drawer isn't rebuilt.
     */
    @JvmStatic
    @JvmOverloads
    fun renameFolder(context: Context, id: Int, title: String, notify: Boolean = true) {
        update(context, notify) { if (it.id == id) it.copy(title = title.trim()) else it }
    }

    /**
     * Sets the apps of the folder, deleting it if there are none. [notify] is false when the caller
     * already shows the change, and calls [notifyChanged] later.
     */
    @JvmStatic
    @JvmOverloads
    fun setFolderApps(
        context: Context,
        id: Int,
        apps: Collection<ComponentKey>,
        notify: Boolean = true,
    ) {
        update(context, notify) { if (it.id == id) it.copy(apps = apps.distinct()) else it }
    }

    /** Adds the app to the folders with the [folderIds] and removes it from the others. */
    @JvmStatic
    fun setAppFolders(context: Context, app: ComponentKey, folderIds: Set<Int>) {
        update(context) {
            when {
                it.id in folderIds && app !in it.apps -> it.copy(apps = it.apps + app)
                it.id !in folderIds && app in it.apps -> it.copy(apps = it.apps - app)
                else -> it
            }
        }
    }

    /** Deletes the folder, its apps go back to the app list. */
    @JvmStatic
    fun deleteFolder(context: Context, id: Int) {
        save(context, getFolders(context).filter { it.id != id })
    }

    /** Calls [onChange] on the main thread whenever the folders change. */
    @JvmStatic
    fun addChangeListener(onChange: Runnable): SafeCloseable {
        synchronized(listeners) { listeners.add(onChange) }
        return SafeCloseable { synchronized(listeners) { listeners.remove(onChange) } }
    }

    /** Calls the change listeners, such as to rebuild the app drawer. */
    @JvmStatic
    fun notifyChanged() {
        val toNotify = synchronized(listeners) { listeners.toList() }
        MAIN_EXECUTOR.execute { toNotify.forEach(Runnable::run) }
    }

    private fun update(context: Context, notify: Boolean = true, transform: (Folder) -> Folder) {
        save(context, getFolders(context).map(transform), notify)
    }

    private fun save(context: Context, folders: List<Folder>, notify: Boolean = true) {
        val userCache = UserCache.INSTANCE.get(context)
        val array = JSONArray()
        // Empty folders are removed, like the home screen ones
        folders
            .filter { it.apps.isNotEmpty() }
            .forEach { folder ->
                array.put(
                    JSONObject()
                        .put(JSON_ID, folder.id)
                        .put(JSON_TITLE, folder.title)
                        .put(JSON_APPS, JSONArray(folder.apps.map { keyToString(userCache, it) }))
                )
            }
        prefs(context).edit().putString(KEY_FOLDERS, array.toString()).apply()

        if (notify) notifyChanged()
    }

    private fun keyToString(userCache: UserCache, key: ComponentKey) =
        "${userCache.getSerialNumberForUser(key.user)}$KEY_SEPARATOR" +
            key.componentName.flattenToString()

    private fun parseKey(userCache: UserCache, string: String): ComponentKey? {
        val serial = string.substringBefore(KEY_SEPARATOR).toLongOrNull() ?: return null
        val component =
            ComponentName.unflattenFromString(string.substringAfter(KEY_SEPARATOR))
                ?: return null
        val user = userCache.getUserForSerialNumber(serial)
        // Skip the apps of removed profiles, unknown serial numbers resolve to the main user
        if (userCache.getSerialNumberForUser(user) != serial) return null
        return ComponentKey(component, user)
    }
}
