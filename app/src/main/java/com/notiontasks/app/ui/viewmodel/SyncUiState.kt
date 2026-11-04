package com.notiontasks.app.ui.viewmodel

sealed interface SyncUiState {
    data object NotConfigured : SyncUiState
    data object Syncing : SyncUiState
    data class Synced(val lastSyncedAtMillis: Long) : SyncUiState
    data class PendingChanges(val count: Int) : SyncUiState
    data class Failed(val message: String, val retryable: Boolean) : SyncUiState
}
