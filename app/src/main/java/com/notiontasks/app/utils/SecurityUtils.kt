@file:Suppress("DEPRECATION")
package com.notiontasks.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Xml
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

object SecurityUtils {
    private const val PREFS_NAME = "notion_tasks_secure_prefs"

    sealed interface SecurePreferencesAccess {
        data class Available(val preferences: SharedPreferences) : SecurePreferencesAccess
        data object Unavailable : SecurePreferencesAccess
    }

    fun getSecurePreferences(context: Context): SharedPreferences {
        return try {
            val appContext = context.applicationContext
            validatePreferenceFile(appContext)
            val mainKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            createPrefs(appContext, mainKey).also(::validateReadablePreferences)
        } catch (e: Exception) {
            // 復号失敗時に認証情報を含む設定を消去しない。安全な復旧はユーザーが
            // 明示的に設定をリセットする操作に限定する。
            throw IllegalStateException("暗号化された設定を読み込めません。", e)
        }
    }

    /**
     * Returns an explicit unavailable state instead of allowing an encrypted preference
     * failure to crash the UI before a recovery choice can be displayed.
     */
    fun accessSecurePreferences(context: Context): SecurePreferencesAccess =
        openSecurePreferences { getSecurePreferences(context) }

    internal fun openSecurePreferences(
        opener: () -> SharedPreferences,
    ): SecurePreferencesAccess = try {
        SecurePreferencesAccess.Available(opener())
    } catch (_: Exception) {
        SecurePreferencesAccess.Unavailable
    }

    /**
     * Explicitly removes only the encrypted settings and its app keystore key.
     * Callers must obtain a user confirmation before invoking this operation.
     */
    fun resetSecurePreferences(context: Context): Result<Unit> = runCatching {
        val appContext = context.applicationContext
        appContext.deleteSharedPreferences(PREFS_NAME)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    /**
     * SharedPreferences reports malformed XML asynchronously and otherwise converts it
     * to an empty map. Validate the encrypted file first so a broken file cannot look
     * like an unconfigured connection and leave the app on a blank launch screen.
     */
    private fun validatePreferenceFile(context: Context) {
        val preferenceFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$PREFS_NAME.xml",
        )
        if (!preferenceFile.exists()) return

        FileInputStream(preferenceFile).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                parser.next()
            }
        }
    }

    private fun validateReadablePreferences(preferences: SharedPreferences) {
        // EncryptedSharedPreferences decrypts keys and values while materialising all.
        // Reading the complete map catches keystore/cipher failures before Compose starts.
        preferences.all
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
