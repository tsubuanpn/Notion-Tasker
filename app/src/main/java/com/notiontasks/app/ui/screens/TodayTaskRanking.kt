package com.notiontasks.app.ui.screens

import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.utils.JapanTime

/** Today で代表表示する進行中タスクを、日付の優先順で一意に選ぶ。 */
internal fun representativeInProgressTask(
    tasks: List<TaskModel>,
    inProgressStatus: String,
): TaskModel? = tasks.asSequence()
    .filter { it.status.trim() == inProgressStatus.trim() }
    .sortedWith(
        compareBy<TaskModel> {
            JapanTime.projectedDateKey(it.scheduledDate)
                ?: JapanTime.projectedDateKey(it.dueDate)
                ?: "9999-12-31"
        }
            .thenBy { JapanTime.projectedDateKey(it.scheduledDate) ?: "9999-12-31" }
            .thenBy { JapanTime.projectedDateKey(it.dueDate) ?: "9999-12-31" }
            .thenBy { it.id },
    )
    .firstOrNull()
