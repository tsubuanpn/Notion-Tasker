package com.notiontasks.app.utils

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** App-wide calendar rules: Japan Standard Time and Monday-start calendar weeks. */
object JapanTime {
    val zoneId: ZoneId = ZoneId.of("Asia/Tokyo")

    fun today(clock: Clock = Clock.system(zoneId)): LocalDate = LocalDate.now(clock.withZone(zoneId))

    fun now(clock: Clock = Clock.system(zoneId)) = java.time.ZonedDateTime.now(clock.withZone(zoneId))

    fun todayIso(clock: Clock = Clock.system(zoneId)): String = today(clock).toString()

    fun startOfWeek(date: LocalDate = today()): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun endOfWeek(date: LocalDate = today()): LocalDate = startOfWeek(date).plusDays(6)

    /**
     * Projects a Notion date value to a calendar day in Japan without changing the stored source value.
     * Date-only values keep their stated calendar day; offset-bearing timestamps are converted to JST.
     */
    fun projectNotionDate(value: String?): LocalDate? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null

        return runCatching { LocalDate.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zoneId).toLocalDate() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(raw).withZoneSameInstant(zoneId).toLocalDate() }.getOrNull()
            ?: runCatching { Instant.parse(raw).atZone(zoneId).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
    }

    fun projectedDateKey(value: String?): String? = projectNotionDate(value)?.toString()
}
