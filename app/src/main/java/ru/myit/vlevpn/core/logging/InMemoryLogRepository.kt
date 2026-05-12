package ru.myit.vlevpn.core.logging

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryLogRepository @Inject constructor() : LogRepository {
    private val secretKeys = setOf("password", "pass", "credential", "id", "uuid", "email")
    private val _entries = MutableStateFlow<List<RuntimeLogEntry>>(emptyList())
    private val _generatedConfigPreview = MutableStateFlow<String?>(null)

    override val entries: StateFlow<List<RuntimeLogEntry>> = _entries
    override val generatedConfigPreview: StateFlow<String?> = _generatedConfigPreview

    override fun add(level: LogLevel, message: String) {
        _entries.update { old ->
            (old + RuntimeLogEntry(System.currentTimeMillis(), level, sanitizeText(message))).takeLast(300)
        }
    }

    override fun setGeneratedConfigPreview(configJson: String?) {
        _generatedConfigPreview.value = configJson?.let(::sanitizeJsonLikeText)
    }

    override fun clear() {
        _entries.value = emptyList()
        _generatedConfigPreview.value = null
    }

    private fun sanitizeText(value: String): String {
        var result = value
        secretKeys.forEach { key ->
            result = result.replace(
                Regex("(?i)(\"?$key\"?\\s*[:=]\\s*\")([^\"\\s,}]+)(\")"),
                "\$1***\$3",
            )
        }
        return result
    }

    private fun sanitizeJsonLikeText(value: String): String {
        var result = value
        secretKeys.forEach { key ->
            result = result.replace(
                Regex("(?i)(\"$key\"\\s*:\\s*\")([^\"]*)(\")"),
                "\$1***\$3",
            )
        }
        return result
    }
}
