package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.utils.JapanTime
import java.time.LocalDate

/** Labels and selection rules shared by the task list and Home shortcuts. */
internal object TaskPeriod {
    const val TODAY = "今日"
    const val THIS_WEEK = "今週"
    const val UNASSIGNED = "未割当"

    val all = listOf(TODAY, THIS_WEEK, UNASSIGNED)
}

/**
 * Returns open tasks that match at least one selected period.
 *
 * A task may have both a scheduled date and a due date; either date matching the
 * selected boundary makes it eligible. Dates are compared as Japan calendar dates.
 */
internal fun filterOpenTasksByPeriod(
    tasks: List<TaskModel>,
    completedStatus: String,
    periods: Set<String>,
    today: String = JapanTime.todayIso(),
): List<TaskModel> = tasks.filter { task ->
    task.status.trim() != completedStatus.trim() && task.matchesTaskPeriods(periods, today)
}

internal fun TaskModel.matchesTaskPeriods(periods: Set<String>, today: String): Boolean {
    if (periods.isEmpty()) return true

    val weekEnd = JapanTime.endOfWeek(LocalDate.parse(today)).toString()
    val scheduled = JapanTime.projectedDateKey(scheduledDate)
    val due = JapanTime.projectedDateKey(dueDate)
    val dates = listOfNotNull(scheduled, due)
    return (TaskPeriod.TODAY in periods && dates.any { it <= today }) ||
        (TaskPeriod.THIS_WEEK in periods && dates.any { it <= weekEnd }) ||
        (TaskPeriod.UNASSIGNED in periods && scheduledDate.isNullOrBlank() && dueDate.isNullOrBlank())
}

internal fun taskPeriodSortKey(task: TaskModel): String =
    JapanTime.projectedDateKey(task.scheduledDate)
        ?: JapanTime.projectedDateKey(task.dueDate)
        ?: "9999-12-31"
