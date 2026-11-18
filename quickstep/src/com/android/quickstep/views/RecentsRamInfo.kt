/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep.views

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import java.util.Locale
import kotlin.math.roundToInt

/** Free / total memory line shown under the recents actions, and the "freed" toast. */
object RecentsRamInfo {
    private const val GB = 1024.0 * 1024.0 * 1024.0
    private const val MB = 1024.0 * 1024.0
    private const val FREED_DELAY_MS = 1500L
    private const val MIN_FREED_MB = 20

    /** Marketed memory sizes, used to round the total reported by the kernel. */
    private val MARKETING_SIZES_GB = intArrayOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32)

    private val handler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        try {
            LauncherPrefs.get(context).get(LauncherPrefs.RECENTS_RAM_INFO)
        } catch (ignored: IllegalStateException) {
            false
        }

    private fun memoryInfo(context: Context): ActivityManager.MemoryInfo? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    }

    private fun formatGb(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f", bytes / GB)

    private fun totalGb(totalBytes: Long): String {
        val raw = totalBytes / GB
        val marketed = MARKETING_SIZES_GB.firstOrNull { it >= raw }
        return marketed?.toString() ?: raw.roundToInt().toString()
    }

    /** "X GB free of Y GB", or null when memory info is unavailable. */
    @JvmStatic
    fun freeText(context: Context): String? {
        val info = memoryInfo(context) ?: return null
        return context.getString(
            R.string.recents_ram_info,
            formatGb(info.availMem),
            totalGb(info.totalMem),
        )
    }

    /**
     * Called when "Clear all" is tapped: measures free memory now and again once the cleared apps
     * are gone, then shows how much was freed. Recents closes after clearing, so this is a toast.
     */
    @JvmStatic
    fun onClearAll(context: Context) {
        if (!isEnabled(context)) return
        val appContext = context.applicationContext
        val before = memoryInfo(appContext)?.availMem ?: return
        handler.postDelayed(
            {
                val after = memoryInfo(appContext)?.availMem ?: return@postDelayed
                val freed = after - before
                val text =
                    when {
                        freed >= GB / 10 ->
                            appContext.getString(R.string.recents_ram_freed, formatGb(freed))
                        freed >= MIN_FREED_MB * MB ->
                            appContext.getString(
                                R.string.recents_ram_freed_mb,
                                (freed / MB).roundToInt(),
                            )
                        else -> freeText(appContext)
                    } ?: return@postDelayed
                Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
            },
            FREED_DELAY_MS,
        )
    }
}
