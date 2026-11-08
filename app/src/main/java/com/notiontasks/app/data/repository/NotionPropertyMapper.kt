package com.notiontasks.app.data.repository

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Defensive parsing boundary for dynamic Notion property payloads. */
internal fun Map<String, JsonElement>.getTitleText(propertyName: String): String? =
    runCatching {
        get(propertyName)?.jsonObject?.get("title")?.jsonArray
            ?.mapNotNull { it.jsonObject["plain_text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
    }.getOrNull()

internal fun Map<String, JsonElement>.getRichTextText(propertyName: String): String? =
    runCatching {
        get(propertyName)?.jsonObject?.get("rich_text")?.jsonArray
            ?.mapNotNull { it.jsonObject["plain_text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
    }.getOrNull()

internal fun Map<String, JsonElement>.getStatusText(propertyName: String): String? =
    runCatching { get(propertyName)?.jsonObject?.get("status")?.jsonObject?.get("name")?.jsonPrimitive?.content }.getOrNull()

internal fun Map<String, JsonElement>.getSelectText(propertyName: String): String? =
    runCatching { get(propertyName)?.jsonObject?.get("select")?.jsonObject?.get("name")?.jsonPrimitive?.content }.getOrNull()

internal fun Map<String, JsonElement>.getDateValue(propertyName: String): String? =
    runCatching { get(propertyName)?.jsonObject?.get("date")?.jsonObject?.get("start")?.jsonPrimitive?.content }.getOrNull()

internal fun Map<String, JsonElement>.getStatusColor(propertyName: String): String? =
    runCatching { get(propertyName)?.jsonObject?.get("status")?.jsonObject?.get("color")?.jsonPrimitive?.content }.getOrNull()

internal fun Map<String, JsonElement>.getSelectColor(propertyName: String): String? =
    runCatching { get(propertyName)?.jsonObject?.get("select")?.jsonObject?.get("color")?.jsonPrimitive?.content }.getOrNull()
