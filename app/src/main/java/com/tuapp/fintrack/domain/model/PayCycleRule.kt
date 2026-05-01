package com.tuapp.fintrack.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
sealed class PayCycleRule {

    @Serializable
    @SerialName("DayOfMonthRule")
    data class DayOfMonthRule(val day: Int) : PayCycleRule()

    @Serializable
    @SerialName("LastDayOfMonthRule")
    data object LastDayOfMonthRule : PayCycleRule()

    @Serializable
    @SerialName("SpecificDateRule")
    data class SpecificDateRule(val dateMs: Long) : PayCycleRule()

    @Serializable
    @SerialName("WeeklyRule")
    data class WeeklyRule(val dayOfWeekAnchor: Int) : PayCycleRule()

    @Serializable
    @SerialName("BiweeklyRule")
    data class BiweeklyRule(val anchorDateMs: Long, val dayOfWeek: Int) : PayCycleRule()
}

fun PayCycleRule.toJson(): String = json.encodeToString(PayCycleRule.serializer(), this)

fun String.toPayCycleRule(): PayCycleRule = json.decodeFromString(PayCycleRule.serializer(), this)
