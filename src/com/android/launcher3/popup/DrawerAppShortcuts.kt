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

package com.android.launcher3.popup

import android.content.ComponentName
import android.view.View
import android.widget.Toast
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.SecondaryDropTarget
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.views.ActivityContext

/** Long-press shortcuts to hide and uninstall apps, only shown in the app drawer. */
object DrawerAppShortcuts {

    @JvmField
    val HIDE_APP =
        SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
            if (!isDrawerApp(target, itemInfo)) null else HideApp(target, itemInfo, originalView)
        }

    @JvmField
    val UNINSTALL_APP =
        SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
            if (!isDrawerApp(target, itemInfo)) return@Factory null
            // System apps that can't be uninstalled have no uninstall target
            val component =
                SecondaryDropTarget.getUninstallTarget(target.asContext(), itemInfo)
                    ?: return@Factory null
            UninstallApp(target, itemInfo, originalView, component)
        }

    /**
     * Apps of the app drawer, its folders, search results and suggestions. Private space apps
     * already have their own shortcuts.
     */
    @JvmStatic
    fun isDrawerApp(target: ActivityContext, itemInfo: ItemInfo): Boolean =
        target is Launcher &&
            (itemInfo.container == Favorites.CONTAINER_ALL_APPS ||
                itemInfo.container == Favorites.CONTAINER_ALL_APPS_PREDICTION) &&
            itemInfo.targetComponent != null &&
            !UserCache.INSTANCE.get(target.asContext()).getUserInfo(itemInfo.user).isPrivate

    private class HideApp<T : ActivityContext>(
        target: T,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<T>(R.drawable.ic_hide_app, R.string.hide_app, target, itemInfo,
        originalView) {

        override fun onClick(view: View) {
            val packageName = mItemInfo.targetComponent?.packageName ?: return
            val context = mTarget.asContext()
            dismissTaskMenuView()
            // Same as hiding the app from the Hidden & Protected apps screen, where it can be shown
            // again
            MODEL_EXECUTOR.execute {
                TrustDatabaseHelper.getInstance(context).addHiddenApp(packageName)
                MAIN_EXECUTOR.execute {
                    LauncherAppState.getInstance(context).model.reloadIfActive("HideApp")
                    Toast.makeText(context, R.string.hide_app_done, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private class UninstallApp<T : ActivityContext>(
        target: T,
        itemInfo: ItemInfo,
        originalView: View,
        private val component: ComponentName,
    ) :
        SystemShortcut<T>(
            R.drawable.ic_uninstall_no_shadow,
            R.string.uninstall_drop_target_label,
            target,
            itemInfo,
            originalView,
        ) {

        override fun onClick(view: View) {
            dismissTaskMenuView()
            // Opens the system confirmation dialog
            SecondaryDropTarget.performUninstall(view.context, component, mItemInfo)
        }
    }
}
