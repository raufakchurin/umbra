package ru.myit.vlevpn.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RuntimeRequestStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun write(request: StartProxyRequest): String = withContext(Dispatchers.IO) {
        requestDir.mkdirs()
        clearStaleFiles()
        val file = File.createTempFile(REQUEST_PREFIX, REQUEST_SUFFIX, requestDir)
        file.writeText(request.encodeForService())
        file.absolutePath
    }

    suspend fun read(path: String): StartProxyRequest = withContext(Dispatchers.IO) {
        val file = resolveRequestFile(path)
        decodeStartProxyRequest(file.readText())
    }

    suspend fun clear(path: String) = withContext(Dispatchers.IO) {
        runCatching { resolveRequestFile(path).delete() }
    }

    private val requestDir: File
        get() = File(context.cacheDir, "runtime-requests")

    private fun clearStaleFiles() {
        val cutoff = System.currentTimeMillis() - REQUEST_TTL_MILLIS
        requestDir.listFiles()
            ?.filter { it.name.startsWith(REQUEST_PREFIX) && it.lastModified() < cutoff }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun resolveRequestFile(path: String): File {
        val directory = requestDir.canonicalFile
        val file = File(path).canonicalFile
        require(file.parentFile?.canonicalFile == directory) {
            "Runtime request path is outside app cache"
        }
        return file
    }

    private companion object {
        const val REQUEST_PREFIX = "start-request-"
        const val REQUEST_SUFFIX = ".json"
        const val REQUEST_TTL_MILLIS = 10 * 60 * 1000L
    }
}
