package com.notiontasks.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notiontasks.app.data.model.TaskModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskListCardAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusControl_describesTheActualNextStateAction_andKeepsTouchTarget() {
        val task = TaskModel(
            id = "task",
            title = "テストタスク",
            status = "未着手",
            category = "学習",
            dueDate = null,
            scheduledDate = null,
        )

        composeRule.setContent {
            MaterialTheme {
                TaskListCard(
                    task = task,
                    isCompleted = false,
                    onEdit = {},
                    onCycle = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("テストタスクを次の状態へ変更")
            .assertHeightIsAtLeast(48.dp)
    }
}
