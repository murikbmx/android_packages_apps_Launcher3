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
import com.android.systemui.shared.recents.model.Task

/**
 * Apps locked in recents by pulling their card down. Locked apps are skipped by "Clear all" and
 * can only be removed by swiping their card away.
 */
object RecentsLockedTasks {
    private const val PREFS = "recents_locked_tasks"
    private const val KEY = "locked"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyOf(task: Task): String? {
        val pkg = task.key.packageName ?: return null
        return "$pkg/${task.key.userId}"
    }

    private fun locked(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    @JvmStatic
    fun isLocked(context: Context, task: Task?): Boolean {
        val key = task?.let { keyOf(it) } ?: return false
        return key in locked(context)
    }

    @JvmStatic
    fun isLocked(context: Context, taskView: TaskView): Boolean =
        taskView.taskContainers.any { isLocked(context, it.task) }

    /** Toggles the lock of all apps in [taskView]; returns the new state. */
    @JvmStatic
    fun toggle(context: Context, taskView: TaskView): Boolean {
        val lock = !isLocked(context, taskView)
        val set = locked(context).toMutableSet()
        taskView.taskContainers.mapNotNull { keyOf(it.task) }.forEach {
            if (lock) set.add(it) else set.remove(it)
        }
        prefs(context).edit().putStringSet(KEY, set).apply()
        return lock
    }

    /** Drops the lock of all apps in [taskView], e.g. once it was swiped away by hand. */
    @JvmStatic
    fun unlock(context: Context, taskView: TaskView) {
        val set = locked(context).toMutableSet()
        if (set.removeAll(taskView.taskContainers.mapNotNull { keyOf(it.task) }.toSet())) {
            prefs(context).edit().putStringSet(KEY, set).apply()
        }
    }
}
