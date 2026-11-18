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

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fixed-duration interpolators that follow the curve of a damped spring, so spring-like motion can
 * be used with regular [android.animation.ValueAnimator]s. The curve is normalized to settle
 * exactly at 1 when the input reaches 1.
 */
object SpringInterpolators {

    /**
     * Under-damped spring (dampingRatio < 1) that overshoots once and settles. [settle] is
     * dampingRatio * naturalFrequency in units of the animation duration; larger settles sooner.
     */
    @JvmStatic
    @JvmOverloads
    fun underDamped(dampingRatio: Float, settle: Float = 7f): Interpolator {
        val zeta = dampingRatio.toDouble()
        val omega = settle / zeta
        val omegaD = omega * sqrt(1 - zeta * zeta)
        return Interpolator { t ->
            if (t >= 1f) {
                1f
            } else if (t <= 0f) {
                0f
            } else {
                val e = exp(-zeta * omega * t)
                (1 - e * (cos(omegaD * t) + zeta * omega / omegaD * sin(omegaD * t))).toFloat()
            }
        }
    }

    /** Critically damped spring: fast start, smooth settle, never overshoots. */
    @JvmStatic
    fun criticallyDamped(omega: Float): Interpolator {
        val w = omega.toDouble()
        val end = 1 - (1 + w) * exp(-w)
        return Interpolator { t ->
            if (t >= 1f) {
                1f
            } else if (t <= 0f) {
                0f
            } else {
                ((1 - (1 + w * t) * exp(-w * t)) / end).toFloat()
            }
        }
    }
}
