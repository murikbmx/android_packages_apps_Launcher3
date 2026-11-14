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

package com.android.launcher3.anim

import android.animation.ObjectAnimator
import android.content.Context
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.icons.FastBitmapDrawable

/** Icon shrinks while pressed and springs back on release, instead of growing. */
object IconPressAnimations {
    private const val PRESSED_SCALE = 0.88f
    private const val PRESS_DURATION = 120L
    private const val RELEASE_DURATION = 450L

    private val RELEASE_INTERPOLATOR = SpringInterpolators.underDamped(0.55f)

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        try {
            LauncherPrefs.get(context).get(LauncherPrefs.ICON_PRESS_ANIM)
        } catch (ignored: IllegalStateException) {
            false
        }

    /**
     * Replaces the default press feedback that [FastBitmapDrawable] just started for a pressed
     * state change.
     */
    @JvmStatic
    fun animatePressedChanged(icon: FastBitmapDrawable) {
        if (!icon.isAnimationEnabled) return
        val pressed = icon.isPressed
        val endScale = if (pressed) PRESSED_SCALE else 1f
        icon.scaleAnimation?.cancel()
        if (!icon.isVisible) {
            icon.scaleAnimation = null
            FastBitmapDrawable.SCALE.set(icon, endScale)
            if (pressed) {
                // Keep getAnimatedScale() in sync with the drawn scale.
                icon.scaleAnimation = ObjectAnimator.ofFloat(icon, FastBitmapDrawable.SCALE,
                    endScale).setDuration(0)
            }
            return
        }
        icon.scaleAnimation =
            ObjectAnimator.ofFloat(icon, FastBitmapDrawable.SCALE, endScale).apply {
                if (pressed) {
                    duration = PRESS_DURATION
                    interpolator = Interpolators.DECELERATE_QUINT
                } else {
                    duration = RELEASE_DURATION
                    interpolator = RELEASE_INTERPOLATOR
                }
                start()
            }
    }
}
