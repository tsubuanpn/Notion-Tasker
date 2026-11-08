package com.notiontasks.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSessionStateMachineTest {
    @Test
    fun `work completion increments count and chooses a short break before the fourth session`() {
        assertEquals(
            FocusSessionStateMachine.PhaseTransition("shortBreak", 3),
            FocusSessionStateMachine.completePhase("work", 2),
        )
    }

    @Test
    fun `fourth work completion chooses a long break`() {
        assertEquals(
            FocusSessionStateMachine.PhaseTransition("longBreak", 4),
            FocusSessionStateMachine.completePhase("work", 3),
        )
    }

    @Test
    fun `break completion returns to work without incrementing the completed work count`() {
        assertEquals(
            FocusSessionStateMachine.PhaseTransition("work", 4),
            FocusSessionStateMachine.completePhase("shortBreak", 4),
        )
    }
}
