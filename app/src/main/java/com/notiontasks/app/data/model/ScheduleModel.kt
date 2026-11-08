package com.notiontasks.app.data.model

import kotlinx.serialization.Serializable

/** Provenance is explicit so old or manually edited blocks are never regenerated implicitly. */
object TimeBlockOrigin {
    const val MANUAL = "manual"
    const val PRESET = "preset"
    const val LEGACY = "legacy"
}

@Serializable
data class TimeBlock(
    val id: String,
    val type: String, // "task" (タスク) または "life" (生活)
    val title: String,
    val associatedId: String? = null, // type == "task" の場合にタスク ID を格納する
    val startTime: Int, // 深夜零時からの経過分 (例: 540 = 09:00)
    val endTime: Int, // 深夜零時からの経過分 (例: 600 = 10:00)
    val color: String, // 16進数のカラー文字列 (例: "#4CAF50")
    val date: String, // "yyyy-MM-dd"
    /** manual, preset, or legacy/unknown. Existing data migrates to legacy conservatively. */
    val origin: String = TimeBlockOrigin.MANUAL,
    /** The life-activity preset that generated this block, if [origin] is preset. */
    val presetId: String? = null,
    /** Monotonic generation marker for idempotent preset regeneration. */
    val presetGeneration: Long = 0L,
)

@Serializable
data class LifeActivity(
    val id: String,
    val name: String,
    val durationMinutes: Int, // デフォルトの所要時間（分）
    val color: String, // 16進数のカラー文字列 (例: "#FF9800")
    val defaultStartTime: Int? = null, // 深夜零時からのデフォルトの開始時間（分）（ない場合は null）
    val defaultEndTime: Int? = null, // 深夜零時からのデフォルトの終了時間（分）（ない場合は null）
    val sortOrder: Int = 0, // 表示順序
)
