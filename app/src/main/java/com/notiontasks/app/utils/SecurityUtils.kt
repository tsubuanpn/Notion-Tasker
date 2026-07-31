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
            e.printStackTrace()
            // 破損している可能性があるため、一度削除して再作成を試みる
            try {
                appContext.deleteSharedPreferences(PREFS_NAME)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            createPrefs(appContext, mainKey)
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
