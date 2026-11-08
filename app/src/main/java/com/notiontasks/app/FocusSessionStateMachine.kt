package com.notiontasks.app

/** Pure state transition rules for the Pomodoro phase cycle. */
internal object FocusSessionStateMachine {
    data class PhaseTransition(
        val nextMode: String,
        val completedWorkSessions: Int,
    )

    fun completePhase(currentMode: String, completedWorkSessions: Int): PhaseTransition {
        if (currentMode != "work") return PhaseTransition("work", completedWorkSessions)

        val updatedCount = completedWorkSessions + 1
        val nextMode = if (updatedCount % LONG_BREAK_INTERVAL == 0) "longBreak" else "shortBreak"
        return PhaseTransition(nextMode, updatedCount)
    }

    private const val LONG_BREAK_INTERVAL = 4
}
