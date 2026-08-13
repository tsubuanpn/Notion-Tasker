package com.notiontasks.app.utils

import com.notiontasks.app.data.model.TimeBlock
import java.util.Locale

/**
 * タイムスケジュールをテキスト形式でエクスポートするためのユーティリティ
 */
object ScheduleExporter {

    /**
     * 指定された日付のタイムブロックリストを読みやすいテキスト形式に変換します。
     * 
     * @param dateDisplayName 表示用の日付文字列 (例: "2026年08月13日 (木)")
     * @param blocks 変換対象のタイムブロックリスト
     * @return 整形されたスケジュールテキスト
     */
    fun exportToText(dateDisplayName: String, blocks: List<TimeBlock>): String {
        val sb = StringBuilder()
        sb.append("【$dateDisplayName のスケジュール】\n\n")

        if (blocks.isEmpty()) {
            sb.append("本日の予定はありません。")
        } else {
            // 開始時間順にソートして出力
            blocks.sortedBy { it.startTime }.forEach { block ->
                val startTime = formatMinutes(block.startTime)
                val endTime = formatMinutes(block.endTime)
                sb.append("$startTime - $endTime : ${block.title}\n")
            }
        }
        
        sb.append("\n-- NotionTasker より出力 --")
        return sb.toString()
    }

    /**
     * 分単位の数値を "HH:mm" 形式の文字列に変換します。
     */
    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }
}
