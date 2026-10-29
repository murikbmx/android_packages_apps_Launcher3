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

package com.android.launcher3.allapps

import android.app.Dialog as PlatformDialog
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.Window.FEATURE_NO_TITLE
import android.view.WindowManager.LayoutParams
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.pm.UserCache
import com.android.launcher3.popup.DrawerAppShortcuts
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.CustomAppTitles
import com.android.launcher3.util.DrawerFolders
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.Dialog
import com.android.launcher3.views.DialogScope
import com.android.launcher3.views.DialogTheme
import com.android.launcher3.views.DialogViewModel
import com.android.launcher3.views.ListenerView

/**
 * Dialogs to edit apps and the folders of the app drawer, and the screen to pick the apps of a
 * folder.
 */
object DrawerFolderDialogs {

    /**
     * Shows the dialog to edit the app: its title, and its folders when shown in the app drawer.
     */
    @JvmStatic
    fun showAppEditDialog(
        target: ActivityContext,
        itemInfo: ItemInfo,
        component: ComponentName,
        originalView: View?,
    ) {
        val context = target.asContext()
        val user = itemInfo.user
        val app = ComponentKey(component, user)
        val folders =
            if (DrawerAppShortcuts.isDrawerApp(target, itemInfo)) {
                DrawerFolders.getFolders(context)
            } else {
                null
            }
        val icon =
            ((originalView as? BubbleTextView)?.icon
                    ?: (itemInfo as? ItemInfoWithIcon)?.newIcon(context))
                ?.let { it.toBitmap(it.intrinsicWidth, it.intrinsicHeight) }
                ?.asImageBitmap()
        val initialTitle = itemInfo.title?.toString() ?: ""

        Dialog(
                target,
                AppEditViewModel(
                    target,
                    icon,
                    initialTitle,
                    folders,
                    folders?.filter { app in it.apps }?.map { it.id }?.toSet() ?: emptySet(),
                ) { title, folderIds, newFolderTitle ->
                    if (title != initialTitle) {
                        // An empty title resets it to the app label
                        CustomAppTitles.set(context, component, user, title)
                    }
                    if (folders != null) {
                        DrawerFolders.setAppFolders(context, app, folderIds)
                        if (newFolderTitle != null) {
                            DrawerFolders.createFolder(context, newFolderTitle, listOf(app))
                        }
                    }
                },
            )
            .show()
    }

    /** Shows the dialog to rename the app drawer folder, add apps to it or delete it. */
    @JvmStatic
    fun showFolderEditDialog(target: ActivityContext, folderIcon: FolderIcon) {
        val context = target.asContext()
        val folderId = DrawerFolders.fromItemId(folderIcon.mInfo.id) ?: return
        val initialTitle = folderIcon.mInfo.title?.toString() ?: ""
        val renameFolder = { title: String ->
            if (title != initialTitle) DrawerFolders.renameFolder(context, folderId, title)
        }

        Dialog(
                target,
                FolderEditViewModel(
                    target,
                    folderIcon.previewBitmap()?.asImageBitmap(),
                    initialTitle,
                    onSave = renameFolder,
                    onAddApps = { title ->
                        renameFolder(title)
                        showAppPicker(target, folderId)
                    },
                    onDelete = { DrawerFolders.deleteFolder(context, folderId) },
                ),
            )
            .show()
    }

    /** Shows the screen to pick the apps of the app drawer folder. */
    @JvmStatic
    fun showAppPicker(target: ActivityContext, folderId: Int) {
        val context = target.asContext()
        val folder = DrawerFolders.getFolder(context, folderId) ?: return
        val userCache = UserCache.INSTANCE.get(context)
        // The apps of the drawer tabs, private space apps can't be in folders
        val apps =
            target.activityComponent.appsStore.apps
                .filter { it.componentName != null && !userCache.getUserInfo(it.user).isPrivate }
                .sortedWith(AppInfoComparator(context))
        AppPicker(target, folder, apps).show()
    }

    /** Returns the preview of the folder icon, without its title. */
    private fun FolderIcon.previewBitmap(): Bitmap? {
        if (width == 0 || height == 0) return null
        val bounds = Rect().also(::getPreviewBounds)
        if (!bounds.intersect(0, 0, width, height)) return null
        return renderBitmap(bounds.width(), bounds.height()) { canvas ->
            canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            draw(canvas)
        }
    }
}

/** Draws the drawable in a bitmap of the given size, keeping its bounds. */
private fun Drawable.toBitmap(width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val oldBounds = copyBounds()
    return renderBitmap(width, height) { canvas ->
        setBounds(0, 0, width, height)
        draw(canvas)
        bounds = oldBounds
    }
}

/**
 * Renders [draw] in a bitmap. It's recorded in a [Picture] rather than drawn on a software canvas,
 * as the launcher icons are hardware bitmaps that software canvases can't draw.
 */
private fun renderBitmap(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap {
    val picture = Picture()
    draw(picture.beginRecording(width, height))
    picture.endRecording()
    return Bitmap.createBitmap(picture)
}

/** Model for the dialog to edit an app. [folders] is null outside of the app drawer. */
private class AppEditViewModel(
    activityContext: ActivityContext,
    val icon: ImageBitmap?,
    initialTitle: String,
    val folders: List<DrawerFolders.Folder>?,
    appFolderIds: Set<Int>,
    onSave: (title: String, folderIds: Set<Int>, newFolderTitle: String?) -> Unit,
) :
    DialogViewModel<AppEditViewModel>(
        title = activityContext.asContext().getString(R.string.edit_app_dialog_title),
        content = { viewModel -> AppEditView(viewModel) },
        neutralButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_neutral_button),
        positiveButton = activityContext.asContext().getString(R.string.edit_dialog_done),
        onPositiveButtonClick = { viewModel ->
            val newFolderTitle =
                viewModel.newFolderTitle?.text?.trim()?.ifEmpty {
                    activityContext.asContext().getString(R.string.drawer_folder_default_title)
                }
            onSave(viewModel.titleField.text.trim(), viewModel.folderIds, newFolderTitle)
            true
        },
    ) {
    var titleField by mutableStateOf(TextFieldValue(initialTitle))
    var folderIds by mutableStateOf(appFolderIds)
    // The title of the folder to create for the app, null until the user asks for a new folder
    var newFolderTitle by mutableStateOf<TextFieldValue?>(null)
}

/** Model for the dialog to edit an app drawer folder. */
private class FolderEditViewModel(
    activityContext: ActivityContext,
    val icon: ImageBitmap?,
    initialTitle: String,
    onSave: (String) -> Unit,
    val onAddApps: (String) -> Unit,
    val onDelete: () -> Unit,
) :
    DialogViewModel<FolderEditViewModel>(
        title = activityContext.asContext().getString(R.string.edit_folder_dialog_title),
        content = { viewModel -> FolderEditView(viewModel) },
        neutralButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_neutral_button),
        positiveButton = activityContext.asContext().getString(R.string.edit_dialog_done),
        onPositiveButtonClick = { viewModel ->
            onSave(viewModel.titleField.text.trim())
            true
        },
    ) {
    var titleField by mutableStateOf(TextFieldValue(initialTitle))
}

@Composable
private fun DialogScope.AppEditView(viewModel: AppEditViewModel) {
    EditView(viewModel.icon) {
        TitleField(
            value = viewModel.titleField,
            onValueChange = { viewModel.titleField = it },
            label = stringResource(R.string.edit_app_dialog_label),
        )

        val folders = viewModel.folders ?: return@EditView
        Column(
            modifier =
                Modifier.padding(top = 8.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState())
        ) {
            folders.forEach { folder ->
                val checked = folder.id in viewModel.folderIds
                EditRow(
                    icon = R.drawable.ic_drawer_folder,
                    text = folder.title,
                    modifier =
                        Modifier.toggleable(value = checked, role = Role.Checkbox) {
                            viewModel.folderIds =
                                if (it) viewModel.folderIds + folder.id
                                else viewModel.folderIds - folder.id
                        },
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }

            val newFolderTitle = viewModel.newFolderTitle
            if (newFolderTitle == null) {
                EditRow(
                    icon = R.drawable.ic_drawer_folder_new,
                    text = stringResource(R.string.drawer_folder_new),
                    modifier =
                        Modifier.clickable { viewModel.newFolderTitle = TextFieldValue() },
                )
            } else {
                val focusRequester = remember { FocusRequester() }
                TitleField(
                    value = newFolderTitle,
                    onValueChange = { viewModel.newFolderTitle = it },
                    label = stringResource(R.string.drawer_folder_new),
                    modifier = Modifier.padding(top = 8.dp).focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        }
    }
}

@Composable
private fun DialogScope.FolderEditView(viewModel: FolderEditViewModel) {
    EditView(viewModel.icon) {
        TitleField(
            value = viewModel.titleField,
            onValueChange = { viewModel.titleField = it },
            label = stringResource(R.string.edit_folder_dialog_label),
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            EditRow(
                icon = R.drawable.ic_drawer_folder_new,
                text = stringResource(R.string.drawer_folder_add_apps),
                modifier =
                    Modifier.clickable {
                        dismiss(animate = true)
                        viewModel.onAddApps(viewModel.titleField.text.trim())
                    },
            )
            EditRow(
                icon = R.drawable.ic_drawer_folder_delete,
                text = stringResource(R.string.drawer_folder_delete),
                modifier =
                    Modifier.clickable {
                        dismiss(animate = true)
                        viewModel.onDelete()
                    },
            )
        }
    }
}

/** Content of the edit dialogs: the icon, next to the [fields]. */
@Composable
private fun EditView(icon: ImageBitmap?, fields: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                color = DialogTheme.colorScheme.surfaceContainerHighest,
            ) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.padding(8.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) { fields() }
    }
}

@Composable
private fun TitleField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
private fun EditRow(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = DialogTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            text = text,
            style = DialogTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/**
 * Full screen list of the apps, to pick the ones of an app drawer folder. The picked apps are saved
 * when it's closed, and the folder is deleted if none are left.
 */
private class AppPicker(
    private val activityContext: ActivityContext,
    private val folder: DrawerFolders.Folder,
    private val apps: List<AppInfo>,
) {
    private val checked = mutableStateListOf<ComponentKey>().apply { addAll(folder.apps) }

    fun show() {
        val context = activityContext.asContext()
        // A full screen theme, as dialog themes limit the width of the window
        val dialog = PlatformDialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        var listener: ListenerView? = null

        with(dialog.window!!) {
            requestFeature(FEATURE_NO_TITLE)
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            decorView.setViewTreeLifecycleOwner(activityContext)
            decorView.setViewTreeSavedStateRegistryOwner(activityContext)
        }

        val content =
            ComposeView(context).apply {
                setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
                setContent { AppPickerView(folder.title, apps, checked, onBack = dialog::dismiss) }
            }
        dialog.setContentView(content)
        dialog.setOnShowListener {
            // Closed along with the other floating views, such as when going home
            listener =
                ListenerView(context, AbstractFloatingView.TYPE_DIALOG_LISTENER)
                    .apply { setListener { dialog.dismiss() } }
                    .also(activityContext.dragLayer::addView)
        }
        dialog.setOnDismissListener {
            listener?.let(activityContext.dragLayer::removeView)
            content.disposeComposition()
            if (checked.toSet() != folder.apps.toSet()) {
                DrawerFolders.setFolderApps(context, folder.id, checked.toList())
            }
        }
        dialog.show()
    }
}

@Composable
private fun AppPickerView(
    title: String,
    apps: List<AppInfo>,
    checked: MutableList<ComponentKey>,
    onBack: () -> Unit,
) {
    DialogTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DialogTheme.colorScheme.surface) {
            Column(modifier = Modifier.systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_drawer_folder_back),
                            contentDescription = stringResource(R.string.drawer_folder_back),
                        )
                    }
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        text = title,
                        style = DialogTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(apps, key = { "${it.componentName}#${it.user}" }) { app ->
                        AppPickerRow(app, checked)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(app: AppInfo, checked: MutableList<ComponentKey>) {
    val context = LocalContext.current
    val iconSize = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon = remember(app) { app.newIcon(context).toBitmap(iconSize, iconSize)?.asImageBitmap() }
    val key = remember(app) { ComponentKey(app.componentName, app.user) }
    val isChecked = key in checked

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = isChecked, role = Role.Checkbox) {
                    if (it) checked.add(key) else checked.remove(key)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Text(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            text = app.title?.toString() ?: "",
            style = DialogTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Checkbox(checked = isChecked, onCheckedChange = null)
    }
}
