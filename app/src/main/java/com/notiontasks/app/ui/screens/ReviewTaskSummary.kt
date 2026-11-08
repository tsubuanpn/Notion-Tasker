package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.utils.JapanTime

/**
 * 選択期間の予定と、期間開始前から未完了の持ち越しを同じ振り返り対象として返す。
 */
internal fun reviewPeriodTasks(
    tasks: List<TaskModel>,
    completedStatus: String,
    startDate: String,
    endDate: String,
): List<TaskModel> = tasks.filter { task ->
    val dates = listOfNotNull(
        JapanTime.projectedDateKey(task.scheduledDate),
        JapanTime.projectedDateKey(task.dueDate),
    )
    val isPlannedInPeriod = dates.any { it in startDate..endDate }
    val isOutstandingCarryover = task.status.trim() != completedStatus.trim() && dates.any { it < startDate }
    isPlannedInPeriod || isOutstandingCarryover
}

internal fun carryoverTaskCount(
    tasks: List<TaskModel>,
    completedStatus: String,
    startDate: String,
): Int = tasks.count { task ->
    task.status.trim() != completedStatus.trim() &&
        listOfNotNull(
            JapanTime.projectedDateKey(task.scheduledDate),
            JapanTime.projectedDateKey(task.dueDate),
        ).any { it < startDate }
}
