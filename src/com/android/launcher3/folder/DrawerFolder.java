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

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.DrawerFolderDialogs;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.IModelWriter;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DrawerFolders;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Folder of the app drawer. It looks and opens like the home screen folders, but its apps behave
 * like the other apps of the drawer, and it's saved in {@link DrawerFolders} instead of the home
 * screen database.
 */
public class DrawerFolder extends Folder {

    private static final long GREY_OUT_DURATION = 300;
    // Size of the badge to remove an app, relative to the app icon
    private static final float REMOVE_BADGE_SCALE = 0.4f;

    private IModelWriter mModelWriter;
    private ImageButton mRemoveButton;

    // Whether tapping the apps removes them from the folder
    private boolean mRemoveMode;
    // Whether apps were removed since the folder was opened, the drawer is rebuilt once it's closed
    private boolean mAppsRemoved;

    // The app list greyed out behind the open folder
    private View mGreyedOutView;
    private ValueAnimator mGreyOutAnimator;
    private float mGreyOutProgress;
    private final Paint mGreyOutPaint = new Paint();
    private final ColorMatrix mGreyOutMatrix = new ColorMatrix();

    public DrawerFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressLint("InflateParams")
    static DrawerFolder fromXml(ActivityContext activityContext) {
        return (DrawerFolder) activityContext.getLayoutInflater()
                .inflate(R.layout.drawer_folder, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findViewById(R.id.folder_add_apps).setOnClickListener(v -> {
            Integer folderId = DrawerFolders.fromItemId(mInfo.id);
            if (folderId == null) return;
            close(true);
            DrawerFolderDialogs.showAppPicker(mActivityContext, folderId);
        });
        mRemoveButton = findViewById(R.id.folder_remove_apps);
        mRemoveButton.setOnClickListener(v -> setRemoveMode(!mRemoveMode));
    }

    @Override
    public void animateOpen() {
        updateRemoveButton();
        super.animateOpen();
        if (isOpen()) {
            greyOutAppList(true /* greyOut */, true /* animate */);
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        setRemoveMode(false);
        super.handleClose(animate);
        greyOutAppList(false /* greyOut */, animate);
    }

    @Override
    protected void onCloseComplete() {
        if (mAppsRemoved) {
            mAppsRemoved = false;
            DrawerFolders.notifyChanged();
        }
    }

    @Override
    LauncherDelegate getAnimationLauncherDelegate() {
        // The home screen animation would dim the scrim, which is the background of the drawer
        return LauncherDelegate.withoutLauncher(mActivityContext);
    }

    @Override
    void prepareItemView(BubbleTextView icon) {
        // Themed like the apps of the drawer, not the home screen
        icon.setThemeIconsOverride(LauncherPrefs.ALLAPPS_THEMED_ICONS.get(getContext()));
    }

    @Override
    protected IModelWriter getModelWriter() {
        if (mModelWriter == null) {
            mModelWriter = new DrawerFolderModelWriter(getContext());
        }
        return mModelWriter;
    }

    @Override
    protected Rect getFolderBoundingBox() {
        // The workspace is hidden behind the app drawer, so the folder can use the whole screen
        BaseDragLayer<?> dragLayer = mActivityContext.getDragLayer();
        Rect insets = mActivityContext.getDeviceProfile().getInsets();
        int margin = getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        return new Rect(insets.left + margin, insets.top + margin,
                dragLayer.getWidth() - insets.right - margin,
                dragLayer.getHeight() - insets.bottom - margin);
    }

    @Override
    protected int getMinItemCount() {
        return 1;
    }

    @Override
    void replaceFolderWithFinalItem() {
        // Drawer folders may hold a single app, they are only removed once they are empty
    }

    @Override
    public boolean isDropEnabled() {
        // Apps are added from the add apps screen, not by dragging them into the folder
        return false;
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        if (mRemoveMode) return true;
        // Same as the other apps of the drawer: shows the shortcuts of the app and drags it to the
        // home screen, closing the folder once the drag starts
        DragController dragController = mActivityContext.getDragController();
        DragController.DragListener listener = new DragController.DragListener() {
            @Override
            public void onDragStart(DragObject dragObject, DragOptions options) {
                dragController.removeDragListener(this);
                close(true);
            }

            @Override
            public void onDragEnd() {
                dragController.removeDragListener(this);
            }
        };
        dragController.addDragListener(listener);
        boolean handled = mActivityContext.getAllAppsItemLongClickListener().onLongClick(v);
        if (!dragController.isDragging()) {
            dragController.removeDragListener(listener);
        }
        return handled;
    }

    /** The last app can't be removed, the folder is deleted from its dialog instead. */
    private void updateRemoveButton() {
        // Invisible rather than gone to keep the folder name centered
        mRemoveButton.setVisibility(getItemCount() > 1 ? VISIBLE : INVISIBLE);
    }

    private void setRemoveMode(boolean removeMode) {
        if (mRemoveMode == removeMode) return;
        mRemoveMode = removeMode;
        mRemoveButton.setImageResource(removeMode
                ? R.drawable.ic_drawer_folder_done : R.drawable.ic_drawer_folder_remove);
        for (View icon : getIconsInReadingOrder()) {
            applyRemoveMode(icon);
        }
    }

    private void applyRemoveMode(View icon) {
        icon.getOverlay().clear();
        if (!mRemoveMode) {
            icon.setOnClickListener(mActivityContext.getItemOnClickListener());
            return;
        }
        if (icon instanceof BubbleTextView btv) {
            Drawable badge = getContext().getDrawable(R.drawable.ic_drawer_folder_remove_badge);
            Rect iconBounds = new Rect();
            btv.getIconBounds(iconBounds);
            int size = (int) (iconBounds.width() * REMOVE_BADGE_SCALE);
            int right = Math.min(iconBounds.right + size / 3, icon.getWidth());
            int top = Math.max(iconBounds.top - size / 3, 0);
            badge.setBounds(right - size, top, right, top + size);
            icon.getOverlay().add(badge);
        }
        icon.setOnClickListener(this::removeApp);
    }

    private void removeApp(View icon) {
        if (!(icon.getTag() instanceof ItemInfo info) || getItemCount() <= 1) return;
        icon.getOverlay().clear();
        icon.setOnClickListener(mActivityContext.getItemOnClickListener());
        removeFolderContent(true /* animate */, info);

        Integer folderId = DrawerFolders.fromItemId(mInfo.id);
        if (folderId != null) {
            List<ComponentKey> apps = new ArrayList<>();
            for (ItemInfo item : mInfo.getContents()) {
                apps.add(new ComponentKey(item.getTargetComponent(), item.user));
            }
            // The folder already shows the change, the drawer is rebuilt once it's closed
            DrawerFolders.setFolderApps(getContext(), folderId, apps, false /* notify */);
            mAppsRemoved = true;
        }

        updateRemoveButton();
        if (getItemCount() <= 1) {
            setRemoveMode(false);
        }
    }

    /** Desaturates the app list behind the open folder. */
    private void greyOutAppList(boolean greyOut, boolean animate) {
        if (greyOut && mActivityContext instanceof Launcher launcher) {
            View view = launcher.getAppsView().getActiveRecyclerView();
            if (mGreyedOutView != null && mGreyedOutView != view) {
                // Restore the list of another drawer tab
                mGreyedOutView.setLayerType(LAYER_TYPE_NONE, null);
            }
            mGreyedOutView = view;
        }
        View view = mGreyedOutView;
        if (view == null) return;

        if (mGreyOutAnimator != null) {
            mGreyOutAnimator.cancel();
            mGreyOutAnimator = null;
        }
        float progress = greyOut ? 1f : 0f;
        if (!animate) {
            setGreyOutProgress(view, progress);
            return;
        }
        mGreyOutAnimator = ValueAnimator.ofFloat(mGreyOutProgress, progress)
                .setDuration(GREY_OUT_DURATION);
        mGreyOutAnimator.addUpdateListener(
                a -> setGreyOutProgress(view, (float) a.getAnimatedValue()));
        mGreyOutAnimator.start();
    }

    private void setGreyOutProgress(View view, float progress) {
        mGreyOutProgress = progress;
        if (progress <= 0f) {
            // The view is kept, as greying out starts from 0 and must still be undone on close
            view.setLayerType(LAYER_TYPE_NONE, null);
            return;
        }
        mGreyOutMatrix.setSaturation(1f - progress);
        mGreyOutPaint.setColorFilter(new ColorMatrixColorFilter(mGreyOutMatrix));
        if (view.getLayerType() == LAYER_TYPE_HARDWARE) {
            view.setLayerPaint(mGreyOutPaint);
        } else {
            view.setLayerType(LAYER_TYPE_HARDWARE, mGreyOutPaint);
        }
    }
}
