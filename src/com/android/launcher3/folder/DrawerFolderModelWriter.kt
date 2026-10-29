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

package com.android.launcher3.folder

import android.content.Context
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.ui.LauncherUiStateNotifier
import com.android.launcher3.ui.NoOpLauncherUiStateNotifier
import com.android.launcher3.util.DrawerFolders
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * Writer of a [DrawerFolder]. Drawer folders and their apps aren't home screen items, so nothing is
 * written to the home screen database, only the folder title is saved in [DrawerFolders].
 */
class DrawerFolderModelWriter(private val context: Context) : IModelWriter {

    override fun <T> scheduleTransaction(
        onComplete: ((success: Boolean, result: T?) -> Unit)?,
        block: (TransactionContext) -> T,
    ) {}

    override fun suspendWrites() {}

    override fun resumeWrites(
        pendingTransaction: Consumer<TransactionContext>?,
        discardPending: Boolean,
    ) {}

    @Deprecated("Temporary interface for ui notification")
    override fun getNotifier(): LauncherUiStateNotifier = NoOpLauncherUiStateNotifier()

    override fun addOrMoveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {}

    override fun moveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {}

    override fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int) {}

    override fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {}

    override fun updateItemInDatabase(item: ItemInfo) {
        if (item !is FolderInfo) return
        val folderId = DrawerFolders.fromItemId(item.id) ?: return
        // The folder already shows the new title, no need to rebuild the app drawer
        DrawerFolders.renameFolder(context, folderId, item.title?.toString() ?: "", notify = false)
    }

    override fun addItemToDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {}

    override fun addItemsToDatabase(items: List<ItemInfo>) {}

    override fun deleteItemFromDatabase(item: ItemInfo, reason: String?) {}

    override fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?) {}

    override fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?) {}

    override fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo) {}

    override fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: LauncherWidgetHolder?,
        reason: String?,
    ) {}
}
