package com.notiontasks.app.data.remote

import com.notiontasks.app.data.remote.dto.NotionQueryResponse
import com.notiontasks.app.data.remote.dto.NotionUpdateRequest
import com.notiontasks.app.data.remote.dto.NotionCreateRequest
import com.notiontasks.app.data.remote.dto.NotionPage
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

interface NotionApi {

    @GET("v1/databases/{database_id}")
    suspend fun getDatabase(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("database_id") databaseId: String
    ): NotionDatabaseResponse

    @POST("v1/databases/{database_id}/query")
    suspend fun queryDatabase(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("database_id") databaseId: String,
        @Body filter: Map<String, String> = emptyMap()
    ): NotionQueryResponse

    @PATCH("v1/pages/{page_id}")
    suspend fun updatePage(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("page_id") pageId: String,
        @Body request: NotionUpdateRequest
    ): NotionPage

    @POST("v1/pages")
    suspend fun createPage(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2022-06-28",
        @Body request: NotionCreateRequest
    ): NotionPage
}
