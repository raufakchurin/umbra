package ru.myit.vlevpn.domain.repository

data class ImportSummary(
    val importedCount: Int,
    val skippedCount: Int,
    val message: String,
)

interface ServerImportRepository {
    suspend fun importFromInput(input: String): ImportSummary
}
