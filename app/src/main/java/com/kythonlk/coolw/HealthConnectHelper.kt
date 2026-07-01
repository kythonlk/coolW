package com.kythonlk.coolw

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
object HealthConnectHelper {

    val stepsReadPermission: String
        get() = HealthPermission.getReadPermission(StepsRecord::class)

    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasStepsPermission(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val client = HealthConnectClient.getOrCreate(context)
        return client.permissionController.getGrantedPermissions().contains(stepsReadPermission)
    }

    suspend fun readTodaySteps(context: Context): Int? {
        if (!isAvailable(context)) return null
        if (!hasStepsPermission(context)) return null

        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
            )
        )

        return response.records.sumOf { it.count.toInt() }
    }

    fun sourceLabel(source: String): String = when (source) {
        CoolWPrefs.SOURCE_HEALTH_CONNECT -> "HEALTH CONNECT"
        CoolWPrefs.SOURCE_SENSOR -> "DEVICE SENSOR"
        else -> "NO DATA"
    }
}
