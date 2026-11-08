package com.notiontasks.app.ui.viewmodel

import com.notiontasks.app.data.model.TaskModel
import com.notiontasks.app.data.remote.dto.NotionOptionInfo

/**
 * Uses the saved Notion metadata when it is available. Older app versions did
 * not persist category metadata, so their local task cache is a safe fallback
 * for rebuilding the filter choices after a process restart.
 */
internal fun categoryOptionsForTaskUi(
    savedOptions: List<NotionOptionInfo>,
    cachedTasks: List<TaskModel>,
): List<NotionOptionInfo> {
    if (savedOptions.isNotEmpty()) return savedOptions

    return cachedTasks.asSequence()
        .map { task -> task.category.trim() to task.categoryColor.orEmpty() }
        .filter { (name, _) -> name.isNotEmpty() }
        .distinctBy { (name, _) -> name }
        .map { (name, color) -> NotionOptionInfo(name = name, color = color) }
        .toList()
}
