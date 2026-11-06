package com.notiontasks.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskStatusTransitionsTest {
    @Test
    fun cyclesOnlyTheThreeMappedStatusesInOrder() {
        assertEquals("進行中", nextSupportedTaskStatus("未着手", "未着手", "進行中", "完了"))
        assertEquals("完了", nextSupportedTaskStatus("進行中", "未着手", "進行中", "完了"))
        assertEquals("未着手", nextSupportedTaskStatus("完了", "未着手", "進行中", "完了"))
    }

    @Test
    fun doesNotOverwriteAnUnsupportedStatus() {
        assertNull(nextSupportedTaskStatus("保留", "未着手", "進行中", "完了"))
    }
}
