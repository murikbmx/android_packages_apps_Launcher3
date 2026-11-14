/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.uioverrides.touchcontrollers

import android.content.Context
import android.view.MotionEvent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE
import com.android.launcher3.Utilities.EDGE_NAV_BAR
import com.android.launcher3.Utilities.boundToRange
import com.android.launcher3.Utilities.debugLog
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.TouchController
import com.android.quickstep.views.RecentsLockedTasks
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import java.util.function.Consumer

/** Touch controller which handles pulling task view cards down to lock them in recents. */
class TaskViewLaunchTouchController<CONTAINER>
@JvmOverloads
constructor(
    private val container: CONTAINER,
    private val onAnimationCreatedCallback: Consumer<AnimatorPlaybackController>? = null,
) : TouchController, SingleAxisSwipeDetector.Listener
    where CONTAINER : Context, CONTAINER : RecentsViewContainer {
    private val recentsView: RecentsView<*, *> = container.getOverviewPanel()
    private val detector: SingleAxisSwipeDetector =
        SingleAxisSwipeDetector(
            container as Context,
            this,
            recentsView.pagedOrientationHandler.upDownSwipeDirection,
        )
    private val isRtl = isRtl(container.resources)
    private val downDirection = recentsView.pagedOrientationHandler.getDownDirection(isRtl)

    private var taskBeingDragged: TaskView? = null
    private var isDragging = false
    private var verticalFactor: Int = 0
    private var canInterceptTouch = false

    private fun canInterceptTouch(ev: MotionEvent): Boolean =
        when {
            // Don't intercept swipes on the nav bar, as user might be trying to go home during a
            // task dismiss animation.
            (ev.edgeFlags and EDGE_NAV_BAR) != 0 -> {
                debugLog(TAG, "Not intercepting edge swipe on nav bar.")
                false
            }

            !recentsView.shouldSwipeDownLaunchTaskView(taskBeingDragged) -> {
                // Already logged in RecentsViewUtils.
                false
            }

            else -> true
        }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (
            (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) &&
                !isDragging
        ) {
            clearState()
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            canInterceptTouch = onActionDown(ev)
            if (!canInterceptTouch) {
                clearState()
                return false
            }
        }
        // Ignore other actions if touch intercepting has not been enabled in an ACTION_DOWN event.
        if (!canInterceptTouch) {
            return false
        }
        onControllerTouchEvent(ev)
        val downDirectionIsNegative = downDirection == SingleAxisSwipeDetector.DIRECTION_NEGATIVE
        val wasInitialTouchDown =
            (downDirectionIsNegative && !detector.wasInitialTouchPositive()) ||
                (!downDirectionIsNegative && detector.wasInitialTouchPositive())
        return detector.isDraggingState && wasInitialTouchDown
    }

    override fun onControllerTouchEvent(ev: MotionEvent) = detector.onTouchEvent(ev)

    private fun onActionDown(ev: MotionEvent): Boolean {
        taskBeingDragged =
            recentsView.taskViews
                .firstOrNull {
                    recentsView.isTaskViewVisible(it) && container.dragLayer.isEventOverView(it, ev)
                }
                ?.also {
                    verticalFactor =
                        recentsView.pagedOrientationHandler.getTaskDragDisplacementFactor(isRtl)
                }
        if (!canInterceptTouch(ev)) {
            return false
        }
        detector.setDetectableScrollConditions(downDirection, /* ignoreSlop= */ false)
        return true
    }

    // Pulling a card down locks / unlocks its app in recents instead of launching it.
    private var lockThreshold = 0f
    private var lockArmed = false
    private var returnAnimator: ValueAnimator? = null

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        val taskBeingDragged = taskBeingDragged ?: return
        debugLog(TAG, "Handling lock drag.")
        returnAnimator?.cancel()
        isDragging = true
        lockArmed = false
        lockThreshold =
            recentsView.pagedOrientationHandler.getSecondaryDimension(taskBeingDragged).toFloat() *
                LOCK_THRESHOLD_FRACTION
    }

    override fun onDrag(displacement: Float): Boolean {
        val taskBeingDragged = taskBeingDragged ?: return true
        val pull = maxOf(0f, displacement * verticalFactor)
        // Rubber band: the card follows the finger less and less.
        val maxPull = lockThreshold / RESISTANCE * 1.5f
        val translation = boundToRange(pull, 0f, maxPull) * RESISTANCE
        setPullTranslation(taskBeingDragged, translation * verticalFactor)
        val armed = translation >= lockThreshold * RESISTANCE
        if (armed != lockArmed) {
            lockArmed = armed
            taskBeingDragged.performHapticFeedback(
                if (armed) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                else HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
            )
        }
        return true
    }

    override fun onDragEnd(velocity: Float) {
        val taskBeingDragged = taskBeingDragged ?: return clearState()
        if (lockArmed) {
            RecentsLockedTasks.toggle(container, taskBeingDragged)
            taskBeingDragged.refreshLockState()
        }
        val from =
            taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)
        returnAnimator =
            ValueAnimator.ofFloat(from, 0f).apply {
                duration = RETURN_DURATION
                interpolator = EMPHASIZED_DECELERATE
                addUpdateListener {
                    setPullTranslation(taskBeingDragged, it.animatedValue as Float)
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            returnAnimator = null
                        }
                    }
                )
                start()
            }
        clearState()
    }

    /** Moves the card, and the live app surface with it when the card is a live tile. */
    private fun setPullTranslation(taskView: TaskView, value: Float) {
        taskView.secondaryDismissTranslationProperty.setValue(taskView, value)
        if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
            recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value = value
            }
            recentsView.redrawLiveTile()
        }
    }

    private fun clearState() {
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
        taskBeingDragged = null
        isDragging = false
        lockArmed = false
    }

    companion object {
        private const val TAG = "TaskViewLaunchTouchController"
        private const val LOCK_THRESHOLD_FRACTION: Float = 0.25f
        private const val RESISTANCE: Float = 0.4f
        private const val RETURN_DURATION: Long = 300
    }
}
