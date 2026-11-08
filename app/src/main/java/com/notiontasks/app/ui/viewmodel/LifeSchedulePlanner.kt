package com.notiontasks.app.ui.viewmodel

import com.notiontasks.app.data.model.TimeBlock
import com.notiontasks.app.data.model.TimeBlockOrigin
import com.notiontasks.app.utils.JapanTime
import java.time.LocalDate

/** Pure ownership rules for life-activity generated schedule blocks. */
internal object LifeSchedulePlanner {
    fun isManagedPresetBlock(block: TimeBlock, presetId: String): Boolean =
        block.origin == TimeBlockOrigin.PRESET && block.presetId == presetId && block.type == "life"

    /** A present or future preset block may be removed only before it has started today. */
    fun canRemoveForDeletedPreset(
        block: TimeBlock,
        presetId: String,
        today: LocalDate,
        nowMinutes: Int,
    ): Boolean {
        if (!isManagedPresetBlock(block, presetId)) return false
        val blockDate = JapanTime.parseCalendarDate(block.date) ?: return false
        if (blockDate.isBefore(today)) return false
        return blockDate.isAfter(today) || block.startTime > nowMinutes
    }

    fun needsPresetUpdate(block: TimeBlock, name: String, startTime: Int, endTime: Int, color: String): Boolean =
        block.title != name || block.startTime != startTime || block.endTime != endTime || block.color != color
}
