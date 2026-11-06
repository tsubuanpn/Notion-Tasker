package com.notiontasks.app.ui.viewmodel

/** Returns the next mapped status, or null for a status that this app does not manage. */
internal fun nextSupportedTaskStatus(
    currentStatus: String,
    unstartedStatus: String,
    inProgressStatus: String,
    completedStatus: String,
): String? {
    val supported = listOf(unstartedStatus, inProgressStatus, completedStatus)
        .map(String::trim)
    if (supported.any(String::isBlank) || supported.distinct().size != supported.size) return null

    val currentIndex = supported.indexOf(currentStatus.trim())
    return supported.getOrNull((currentIndex + 1) % supported.size).takeIf { currentIndex >= 0 }
}
