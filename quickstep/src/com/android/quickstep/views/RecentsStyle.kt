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

package com.android.quickstep.views

import android.content.Context
import android.content.res.Configuration
import com.android.launcher3.LauncherPrefs

/** Layout style of the recent apps screen, chosen in home settings. */
object RecentsStyle {
    /** Default AOSP horizontal carousel. */
    const val AOSP = 2
    /** Overlapping card stack. */
    const val STACK = 3

    @JvmStatic
    fun get(context: Context): Int =
        if (LauncherPrefs.get(context).get(LauncherPrefs.RECENTS_STYLE).toIntOrNull() == STACK) STACK
        else AOSP

    /** Spring entry / scroll animations, only used by the default style. */
    @JvmStatic
    fun isSpringAnimEnabled(context: Context): Boolean =
        try {
            LauncherPrefs.get(context).get(LauncherPrefs.RECENTS_SPRING_ANIM)
        } catch (ignored: IllegalStateException) {
            false
        }

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
