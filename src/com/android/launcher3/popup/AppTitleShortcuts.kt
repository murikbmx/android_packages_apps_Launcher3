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
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.allapps.DrawerFolderDialogs
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.util.CustomAppTitles
import com.android.launcher3.views.ActivityContext

/**
 * Long-press shortcuts to edit an app, giving it a custom title or putting it in app drawer
 * folders, and to reset its title to the app label.
 */
object AppTitleShortcuts {

    @JvmField
    val RENAME_APP =
        SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
            if (itemInfo.renamableComponent() == null) null
            else RenameApp(target, itemInfo, originalView)
        }

    @JvmField
    val RESET_APP_TITLE =
        SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
            val component = itemInfo.renamableComponent() ?: return@Factory null
            if (CustomAppTitles.get(target.asContext(), component, itemInfo.user) == null) null
            else ResetAppTitle(target, itemInfo, originalView)
        }

    /** Only apps can be renamed, not deep shortcuts, widgets or folders. */
    private fun ItemInfo.renamableComponent(): ComponentName? =
        if (itemType == Favorites.ITEM_TYPE_APPLICATION && this is ItemInfoWithIcon) {
            targetComponent
        } else {
            null
        }

    private class RenameApp<T : ActivityContext>(
        target: T,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<T>(R.drawable.ic_rename_app, R.string.rename_app, target, itemInfo,
        originalView) {

        override fun onClick(view: View) {
            val component = mItemInfo.renamableComponent() ?: return
            dismissTaskMenuView()
            DrawerFolderDialogs.showAppEditDialog(mTarget, mItemInfo, component, mOriginalView)
        }
    }

    private class ResetAppTitle<T : ActivityContext>(
        target: T,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<T>(R.drawable.ic_reset_app_title, R.string.reset_app_title, target,
        itemInfo, originalView) {

        override fun onClick(view: View) {
            val component = mItemInfo.renamableComponent() ?: return
            dismissTaskMenuView()
            CustomAppTitles.set(mTarget.asContext(), component, mItemInfo.user, null)
        }
    }
}
