package com.example.expressapp.log

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogLevel { INFO, WARN, ERROR }

data class QueryLog(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val nu: String,
    val message: String
) {
    val timeFormatted: String
        get() = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
