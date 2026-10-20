/*
 * Copyright (C) 2019-2024 The LineageOS Project
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
package com.android.launcher3.lineage.trust;

import static com.android.launcher3.lineage.trust.db.TrustComponent.Kind.HIDDEN;
import static com.android.launcher3.lineage.trust.db.TrustComponent.Kind.PROTECTED;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.security.Flags;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AppFilter;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.lineage.trust.db.TrustComponent;
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;
import com.android.launcher3.util.Executors;

import java.util.List;

public class TrustAppsActivity extends Activity implements
        TrustAppsAdapter.Listener,
        LoadTrustComponentsTask.Callback,
        UpdateItemTask.UpdateCallback {

    private static final String TAG = "TrustAppsActivity";
    private static final String KEY_TRUST_ONBOARDING = "pref_trust_onboarding";
    private static final int REQUEST_APP_LOCK = 1;
    // How long an app stays unlocked after leaving it, the second one is the system default.
    private static final long[] RELOCK_DELAYS_MS = {0, 5_000, 30_000, 60_000};

    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingView;
    private ProgressBar mProgressBar;

    private TrustDatabaseHelper mDbHelper;
    private TrustAppsAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setupEdgeToEdge();
        setContentView(R.layout.activity_hidden_apps);
        mRecyclerView = findViewById(R.id.hidden_apps_list);
        mLoadingView = findViewById(R.id.hidden_apps_loading);
        mLoadingView.setVisibility(View.VISIBLE);
        mProgressBar = findViewById(R.id.hidden_apps_progress_bar);

        final boolean hasSecureKeyguard = LineageUtils.hasSecureKeyguard(this);
        mAdapter = new TrustAppsAdapter(this, hasSecureKeyguard);
        mDbHelper = TrustDatabaseHelper.getInstance(this);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);

        showOnBoarding(false);

        loadList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_trust_apps, menu);
        menu.findItem(R.id.menu_trust_relock).setVisible(Flags.appLockApis());
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_trust_help) {
            showOnBoarding(true);
            return true;
        } else if (id == R.id.menu_trust_relock) {
            showRelockDialog();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onHiddenItemChanged(@NonNull TrustComponent component) {
        new UpdateItemTask(mDbHelper, this, HIDDEN).execute(component);
    }

    @Override
    public void onProtectedItemChanged(@NonNull TrustComponent component) {
        final String pkgName = component.getPackageName();
        final boolean protect = component.isProtected();
        if (!isAppLockSupported(pkgName)) {
            new UpdateItemTask(mDbHelper, this, PROTECTED).execute(component);
            return;
        }

        // App Lock replaces the launcher-side protection, so drop the legacy entry and let
        // the system authenticate the user before it changes the App Lock state.
        if (!protect) {
            new UpdateItemTask(mDbHelper, this, PROTECTED).execute(component);
        }
        Executors.ORDERED_BG_EXECUTOR.execute(() -> {
            PendingIntent pendingIntent = null;
            try {
                pendingIntent = getPackageManager()
                        .getEnableAppLockIntentForPackage(pkgName, protect);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to get App Lock intent for " + pkgName, e);
            }
            final PendingIntent result = pendingIntent;
            runOnUiThread(() -> startAppLockIntent(result));
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_APP_LOCK) {
            // The user may have cancelled the authentication, show the actual state.
            loadList();
        }
    }

    private boolean isAppLockSupported(@NonNull String pkgName) {
        if (!Flags.appLockApis()) {
            return false;
        }
        try {
            return getPackageManager().getApplicationInfo(pkgName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO))
                    .isAppLockSupported;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void startAppLockIntent(@Nullable PendingIntent pendingIntent) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (pendingIntent == null) {
            loadList();
            return;
        }
        try {
            startIntentSenderForResult(pendingIntent.getIntentSender(), REQUEST_APP_LOCK,
                    null, 0, 0, 0, ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            .toBundle());
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Failed to start App Lock intent", e);
            loadList();
        }
    }

    private void showRelockDialog() {
        final String[] entries = {
                getString(R.string.trust_apps_relock_immediately),
                getString(R.string.trust_apps_relock_5_seconds),
                getString(R.string.trust_apps_relock_30_seconds),
                getString(R.string.trust_apps_relock_1_minute),
        };
        final long current = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.APP_LOCK_GRACE_PERIOD, RELOCK_DELAYS_MS[1]);
        int checked = 1;
        for (int i = 0; i < RELOCK_DELAYS_MS.length; i++) {
            if (RELOCK_DELAYS_MS[i] == current) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.trust_apps_relock_title)
                .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                    Settings.Secure.putLong(getContentResolver(),
                            Settings.Secure.APP_LOCK_GRACE_PERIOD, RELOCK_DELAYS_MS[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadList() {
        new LoadTrustComponentsTask(mDbHelper, getPackageManager(), new AppFilter(this), this)
                .execute();
    }

    @Override
    public void onUpdated(boolean result) {
        LauncherAppState.INSTANCE.get(this).getModel().reloadIfActive("TrustApps");
    }

    @Override
    public void onLoadListProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onLoadCompleted(List<TrustComponent> result) {
        mLoadingView.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        mAdapter.update(result);
    }

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    // Apply the insets paddings to the view.
                    v.setPadding(insets.left, insets.top, insets.right, insets.bottom);

                    // Return CONSUMED if you don't want the window insets to keep being
                    // passed down to descendant views.
                    return WindowInsetsCompat.CONSUMED;
                });
    }

    private void showOnBoarding(boolean forceShow) {
        SharedPreferences preferenceManager = LauncherPrefs.getPrefs(this);
        if (!forceShow && preferenceManager.getBoolean(KEY_TRUST_ONBOARDING, false)) {
            return;
        }

        preferenceManager.edit()
                .putBoolean(KEY_TRUST_ONBOARDING, true)
                .apply();

        new AlertDialog.Builder(this)
                .setView(R.layout.dialog_trust_welcome)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
