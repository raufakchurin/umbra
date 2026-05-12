package ru.myit.vlevpn.runtime

import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import libXray.DialerController
import libXray.LibXray
import ru.myit.vlevpn.core.logging.LogLevel
import ru.myit.vlevpn.core.logging.LogRepository

@Singleton
class LibXrayRuntime @Inject constructor(
    private val protectBridge: VpnProtectBridge,
    private val logs: LogRepository,
) : XrayRuntime {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var environment: XrayEnvironment? = null
    private var initialized = false

    override suspend fun init(environment: XrayEnvironment) = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@LibXrayRuntime.environment = environment
            if (!initialized) {
                val controller = DialerController { fd -> protectBridge.protect(fd.toInt()) }
                LibXray.registerDialerController(controller)
                LibXray.registerListenerController(controller)
                LibXray.initDns(controller, "1.1.1.1:53")
                initialized = true
                logs.add(LogLevel.INFO, "libXray initialized: ${decodeStringResponse(LibXray.xrayVersion())}")
            }
        }
    }

    override suspend fun start(configJson: String, tunFd: Int?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val env = requireNotNull(environment) { "Xray environment is not initialized" }
            stopIfRunning()
            if (tunFd != null) {
                LibXray.setTunFd(tunFd)
            }
            val request = LibXray.newXrayRunFromJSONRequest(
                env.filesDirPath,
                "",
                configJson,
            )
            decodeUnitResponse(LibXray.runXrayFromJSON(request))
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        mutex.withLock {
            stopIfRunning()
        }
    }

    override suspend fun queryStats(): XrayStats = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val response = decodeStringResponse(
                    LibXray.queryStats(Base64.encodeToString(XrayMetrics.URL.toByteArray(), Base64.NO_WRAP)),
                )
                parseStats(response)
            }.getOrElse {
                XrayStats()
            }
        }
    }

    private fun stopIfRunning() {
        if (LibXray.getXrayState()) {
            decodeUnitResponse(LibXray.stopXray())
        }
    }

    private fun decodeStringResponse(base64: String): String {
        val obj = decodeResponseObject(base64)
        if (obj["success"]?.jsonPrimitive?.content != "true") {
            val error = obj["error"]?.jsonPrimitive?.content.orEmpty()
            throw IllegalStateException(error.ifBlank { "libXray call failed" })
        }
        return obj["data"]?.jsonPrimitive?.content.orEmpty()
    }

    private fun decodeUnitResponse(base64: String) {
        val obj = decodeResponseObject(base64)
        if (obj["success"]?.jsonPrimitive?.content != "true") {
            val error = obj["error"]?.jsonPrimitive?.content.orEmpty()
            throw IllegalStateException(error.ifBlank { "libXray call failed" })
        }
    }

    private fun decodeResponseObject(base64: String) =
        json.parseToJsonElement(
            String(Base64.decode(base64, Base64.DEFAULT)),
        ).jsonObject

    private fun parseStats(payload: String): XrayStats {
        val counters = mutableListOf<Pair<String, Long>>()
        collectCounters(json.parseToJsonElement(payload), "", counters)
        val stats = counters.filter { (name, _) -> name.startsWith("stats.", ignoreCase = true) }
        val proxyStats = stats.filter { (name, _) ->
            name.contains("outbound.proxy", ignoreCase = true) ||
                name.contains("outbound>>>proxy", ignoreCase = true)
        }
        val source = proxyStats.ifEmpty { stats }
        return XrayStats(
            uplinkBytes = source
                .filter { (name, _) -> name.contains("uplink", ignoreCase = true) }
                .sumOf { (_, value) -> value },
            downlinkBytes = source
                .filter { (name, _) -> name.contains("downlink", ignoreCase = true) }
                .sumOf { (_, value) -> value },
        )
    }

    private fun collectCounters(element: JsonElement, path: String, out: MutableList<Pair<String, Long>>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                collectCounters(value, if (path.isBlank()) key else "$path.$key", out)
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                collectCounters(value, "$path[$index]", out)
            }
            is JsonPrimitive -> element.longOrNull?.let { out += path to it }
        }
    }
}
