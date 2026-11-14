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
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.icons.FastBitmapDrawable

/**
 * Spring-like app open / close transitions from home screen icons: the window grows out of the
 * icon on a spring curve and the icon "lands" with a small bounce when the app closes into it.
 */
object AppSpringAnimations {
    /** Duration of the opening window animation. */
    const val OPEN_DURATION = 450L

    /** Base curve of the opening window (size, y, corners). */
    @JvmField val OPEN_INTERPOLATOR: Interpolator = SpringInterpolators.criticallyDamped(7f)

    /** Horizontal curve of the opening window; slightly faster than y to move along an arc. */
    @JvmField val OPEN_X_INTERPOLATOR: Interpolator = SpringInterpolators.criticallyDamped(9f)

    private const val LAND_SCALE = 0.9f
    private const val LAND_IN_DURATION = 90L
    private const val LAND_OUT_DURATION = 420L
    private val LAND_OUT_INTERPOLATOR = SpringInterpolators.underDamped(0.5f)

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        try {
            LauncherPrefs.get(context).get(LauncherPrefs.APP_SPRING_ANIM)
        } catch (ignored: IllegalStateException) {
            false
        }

    /** Plays the landing bounce on [view] after an app closed into its icon. */
    @JvmStatic
    fun playLanding(view: BubbleTextView) {
        val icon = view.icon ?: return
        if (!icon.isAnimationEnabled || !view.isAttachedToWindow || icon.isPressed) return
        icon.scaleAnimation?.cancel()
        val down = ObjectAnimator.ofFloat(icon, FastBitmapDrawable.SCALE, LAND_SCALE).apply {
            duration = LAND_IN_DURATION
            interpolator = Interpolators.DECELERATE_QUINT
        }
        val up = ObjectAnimator.ofFloat(icon, FastBitmapDrawable.SCALE, 1f).apply {
            duration = LAND_OUT_DURATION
            interpolator = LAND_OUT_INTERPOLATOR
        }
        // scaleAnimation must stay an ObjectAnimator so that pressing cancels the bounce.
        up.addListener(AnimatorListeners.forEndCallback(Runnable {
            if (icon.scaleAnimation === up) icon.scaleAnimation = null
        }))
        down.addListener(AnimatorListeners.forSuccessCallback(Runnable {
            if (icon.scaleAnimation === down) {
                icon.scaleAnimation = up
                up.start()
            }
        }))
        icon.scaleAnimation = down
        down.start()
    }
}
