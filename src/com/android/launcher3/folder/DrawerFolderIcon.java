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

package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.views.ActivityContext;

/** Icon of an app drawer folder, aligned with the app icons of the drawer. */
public class DrawerFolderIcon extends FolderIcon {

    public DrawerFolderIcon(Context context) {
        super(context);
    }

    public DrawerFolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Inflates the icon of the drawer folder along with the folder it opens. */
    public static DrawerFolderIcon inflate(ActivityContext activityContext, ViewGroup group,
            FolderInfo folderInfo) {
        DrawerFolder folder = DrawerFolder.fromXml(activityContext);
        DrawerFolderIcon icon = (DrawerFolderIcon) inflateIcon(R.layout.drawer_folder_icon,
                activityContext, group, folderInfo);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.setFolder(folder);
        return icon;
    }

    @Override
    protected int getPreviewIconFlags() {
        // Same as the apps of the drawer, not the home screen
        return LauncherPrefs.ALLAPPS_THEMED_ICONS.get(getContext()) ? BitmapInfo.FLAG_THEMED : 0;
    }

    @Override
    protected boolean shouldCenterIcon() {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Center the preview on the app icons of the drawer and put the title where theirs are
        DeviceProfile dp = mActivity.getDeviceProfile();
        int previewAreaSize = dp.getWorkspaceProfile().getIconSizePx();
        int appIconSize = dp.getAllAppsProfile().getIconSizePx();
        int drawablePadding = dp.getAllAppsProfile().getIconDrawablePaddingPx();
        Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
        int textHeight = (int) Math.ceil(fm.bottom - fm.top);

        int appIconTop = (MeasureSpec.getSize(heightMeasureSpec) - appIconSize - drawablePadding
                - textHeight) / 2;
        int paddingTop = appIconTop + (appIconSize - previewAreaSize) / 2;
        setPadding(getPaddingLeft(), paddingTop, getPaddingRight(), getPaddingBottom());
        ((FrameLayout.LayoutParams) mFolderName.getLayoutParams()).topMargin =
                appIconTop + appIconSize + drawablePadding - paddingTop;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
