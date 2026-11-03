package com.notiontasks.app.data.remote

import com.notiontasks.app.data.remote.dto.NotionQueryResponse
import com.notiontasks.app.data.remote.dto.NotionUpdateRequest
import com.notiontasks.app.data.remote.dto.NotionCreateRequest
import com.notiontasks.app.data.remote.dto.NotionPage
import com.notiontasks.app.data.remote.dto.NotionDatabaseResponse
import com.notiontasks.app.data.remote.dto.NotionDatabaseInfo
import com.notiontasks.app.data.remote.dto.NotionQueryRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

interface NotionApi {

    @GET("v1/databases/{database_id}")
    suspend fun getDatabaseDataSources(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2025-09-03",
        @Path("database_id") databaseId: String,
    ): NotionDatabaseInfo

    @GET("v1/data_sources/{data_source_id}")
    suspend fun getDataSource(
        @Header("Authorization") token: String,
        @Header("Notion-Version") version: String = "2025-09-03",
        @Path("data_source_id") dataSourceId: String,
    ): NotionDatabaseResponse

    @POST("v1/data_sources/{data_source_id}/query")
    suspend fun queryDatabase(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2025-09-03",
        @Path("data_source_id") dataSourceId: String,
        @Body request: NotionQueryRequest = NotionQueryRequest(),
    ): NotionQueryResponse

    @PATCH("v1/pages/{page_id}")
    suspend fun updatePage(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2025-09-03",
        @Path("page_id") pageId: String,
        @Body request: NotionUpdateRequest
    ): NotionPage

    @POST("v1/pages")
    suspend fun createPage(
        @Header("Authorization") token: String, // Bearer <トークン>
        @Header("Notion-Version") version: String = "2025-09-03",
        @Body request: NotionCreateRequest
    ): NotionPage
}
