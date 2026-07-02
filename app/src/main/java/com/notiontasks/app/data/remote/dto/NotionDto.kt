@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
@file:Suppress("unused")

package com.notiontasks.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NotionQueryResponse(
    @SerialName("results") val results: List<NotionPage>
)

@Serializable
data class NotionPage(
    @SerialName("id") val id: String,
    @SerialName("properties") val properties: Map<String, JsonElement>
)

@Serializable
data class NotionDatabaseResponse(
    @SerialName("id") val id: String,
    @SerialName("properties") val properties: Map<String, NotionPropertyMeta>
)

@Serializable
data class NotionPropertyMeta(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String = "",
    @SerialName("select") val select: NotionSelectMeta? = null,
    @SerialName("status") val status: NotionStatusMeta? = null,
    @SerialName("title") val title: JsonElement? = null,
    @SerialName("date") val date: JsonElement? = null
)

@Serializable
data class NotionSelectMeta(
    @SerialName("options") val options: List<NotionOptionInfo> = emptyList()
)

@Serializable
data class NotionStatusMeta(
    @SerialName("options") val options: List<NotionOptionInfo> = emptyList()
)

@Serializable
data class NotionOptionInfo(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String,
    @SerialName("color") val color: String = ""
)

@Serializable
data class TitleProperty(
    @SerialName("title") val title: List<RichTextObject>
)

@Serializable
data class RichTextObject(
    @SerialName("plain_text") val plainText: String? = null,
    @SerialName("text") val text: TextContent? = null
)

@Serializable
data class TextContent(
    @SerialName("content") val content: String
)

@Serializable
data class StatusProperty(
    @SerialName("status") val status: StatusValue? = null
)

@Serializable
data class StatusValue(
    @SerialName("name") val name: String
)

@Serializable
data class SelectProperty(
    @SerialName("select") val select: SelectValue? = null
)

@Serializable
data class SelectValue(
    @SerialName("name") val name: String
)

@Serializable
data class DateProperty(
    @SerialName("date") val date: DateValue? = null
)

@Serializable
data class DateValue(
    @SerialName("start") val start: String? = null
)

// リクエスト用の DTO

@Serializable
data class NotionUpdateRequest(
    @SerialName("properties") val properties: Map<String, JsonElement>
)

@Serializable
data class PropertyUpdate(
    @SerialName("status") val status: StatusValue? = null,
    @SerialName("select") val select: SelectValue? = null,
    @SerialName("date") val date: DateValue? = null,
    @SerialName("title") val title: List<RichTextObject>? = null
)

@Serializable
data class NotionCreateRequest(
    @SerialName("parent") val parent: DatabaseParent,
    @SerialName("properties") val properties: Map<String, PropertyUpdate>
)

@Serializable
data class DatabaseParent(
    @SerialName("database_id") val databaseId: String
)

