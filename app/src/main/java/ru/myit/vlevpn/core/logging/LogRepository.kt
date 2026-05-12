package ru.myit.vlevpn.core.logging

import kotlinx.coroutines.flow.StateFlow

data class RuntimeLogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val message: String,
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

interface LogRepository {
    val entries: StateFlow<List<RuntimeLogEntry>>
    val generatedConfigPreview: StateFlow<String?>

    fun add(level: LogLevel, message: String)
    fun setGeneratedConfigPreview(configJson: String?)
    fun clear()
}
