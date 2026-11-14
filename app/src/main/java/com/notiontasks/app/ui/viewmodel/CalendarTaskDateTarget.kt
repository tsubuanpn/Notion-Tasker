package com.notiontasks.app.ui.viewmodel

/** Which task dates receive the selected calendar day when opening the add dialog. */
enum class CalendarTaskDateTarget(val preferenceValue: String, val label: String) {
    DUE_DATE("due_date", "締め切り"),
    SCHEDULED_DATE("scheduled_date", "予定日"),
    BOTH("both", "締め切りと予定日の両方");

    fun initialDates(selectedDate: String?): CalendarTaskDates {
        val date = selectedDate.orEmpty()
        return CalendarTaskDates(
            dueDate = if (this != SCHEDULED_DATE) date else "",
            scheduledDate = if (this != DUE_DATE) date else "",
        )
    }

    companion object {
        fun fromPreference(value: String?): CalendarTaskDateTarget =
            entries.firstOrNull { it.preferenceValue == value } ?: SCHEDULED_DATE
    }
}

data class CalendarTaskDates(val dueDate: String = "", val scheduledDate: String = "")
