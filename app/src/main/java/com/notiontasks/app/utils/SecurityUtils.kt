@file:Suppress("DEPRECATION")
package com.notiontasks.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityUtils {
    private const val PREFS_NAME = "notion_tasks_secure_prefs"

    fun getSecurePreferences(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val mainKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return try {
            createPrefs(appContext, mainKey)
        } catch (e: Exception) {
            // 復号失敗時に認証情報を含む設定を消去しない。安全な復旧はユーザーが
            // 明示的に設定をリセットする操作に限定する。
            throw IllegalStateException("暗号化された設定を読み込めません。", e)
        }
    }

    private fun createPrefs(context: Context, mainKey: MasterKey): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
