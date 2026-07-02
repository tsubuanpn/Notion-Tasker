package com.notiontasks.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeBlock(
    val id: String,
    val type: String, // "task" (タスク) または "life" (生活)
    val title: String,
    val associatedId: String? = null, // type == "task" の場合にタスク ID を格納する
    val startTime: Int, // 深夜零時からの経過分 (例: 540 = 09:00)
    val endTime: Int, // 深夜零時からの経過分 (例: 600 = 10:00)
    val color: String, // 16進数のカラー文字列 (例: "#4CAF50")
    val date: String // "yyyy-MM-dd"
)

@Serializable
data class LifeActivity(
    val id: String,
    val name: String,
    val durationMinutes: Int, // デフォルトの所要時間（分）
    val color: String, // 16進数のカラー文字列 (例: "#FF9800")
    val defaultStartTime: Int? = null, // 深夜零時からのデフォルトの開始時間（分）（ない場合は null）
    val defaultEndTime: Int? = null // 深夜零時からのデフォルトの終了時間（分）（ない場合は null）
)
