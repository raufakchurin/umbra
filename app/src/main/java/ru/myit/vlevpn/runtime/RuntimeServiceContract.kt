package ru.myit.vlevpn.runtime

import android.content.Context
import android.content.Intent

object RuntimeServiceContract {
    const val ACTION_STATE = "ru.myit.vlevpn.runtime.STATE"
    const val ACTION_STATS = "ru.myit.vlevpn.runtime.STATS"
    const val ACTION_LOG = "ru.myit.vlevpn.runtime.LOG"

    const val EXTRA_REQUEST_PATH = "request_path"
    const val EXTRA_STATE = "state"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_CONNECTED_AT = "connected_at"
    const val EXTRA_UPLINK = "uplink"
    const val EXTRA_DOWNLINK = "downlink"
    const val EXTRA_LOG_LEVEL = "log_level"
    const val EXTRA_LOG_MESSAGE = "log_message"

    const val STATE_IDLE = "idle"
    const val STATE_BUILDING_CONFIG = "building_config"
    const val STATE_ESTABLISHING_VPN = "establishing_vpn"
    const val STATE_STARTING_NATIVE_CORE = "starting_native_core"
    const val STATE_VERIFYING_CONNECTION = "verifying_connection"
    const val STATE_RUNNING = "running"
    const val STATE_STOPPING = "stopping"
    const val STATE_ERROR = "error"

    fun stateIntent(context: Context, state: RuntimeState): Intent =
        Intent(ACTION_STATE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_STATE, state.serviceName())
            .apply {
                when (state) {
                    is RuntimeState.Error -> putExtra(EXTRA_MESSAGE, state.message)
                    is RuntimeState.Running -> putExtra(EXTRA_CONNECTED_AT, state.connectedAtMillis)
                    else -> Unit
                }
            }

    fun statsIntent(context: Context, stats: RuntimeStats): Intent =
        Intent(ACTION_STATS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_UPLINK, stats.uplinkBytes)
            .putExtra(EXTRA_DOWNLINK, stats.downlinkBytes)

    fun logIntent(context: Context, level: String, message: String): Intent =
        Intent(ACTION_LOG)
            .setPackage(context.packageName)
            .putExtra(EXTRA_LOG_LEVEL, level)
            .putExtra(EXTRA_LOG_MESSAGE, message)
}

fun Intent.toRuntimeState(): RuntimeState? =
    when (getStringExtra(RuntimeServiceContract.EXTRA_STATE)) {
        RuntimeServiceContract.STATE_IDLE -> RuntimeState.Idle
        RuntimeServiceContract.STATE_BUILDING_CONFIG -> RuntimeState.BuildingConfig
        RuntimeServiceContract.STATE_ESTABLISHING_VPN -> RuntimeState.EstablishingVpn
        RuntimeServiceContract.STATE_STARTING_NATIVE_CORE -> RuntimeState.StartingNativeCore
        RuntimeServiceContract.STATE_VERIFYING_CONNECTION -> RuntimeState.VerifyingConnection
        RuntimeServiceContract.STATE_RUNNING -> RuntimeState.Running(
            getLongExtra(RuntimeServiceContract.EXTRA_CONNECTED_AT, System.currentTimeMillis()),
        )
        RuntimeServiceContract.STATE_STOPPING -> RuntimeState.Stopping
        RuntimeServiceContract.STATE_ERROR -> RuntimeState.Error(
            getStringExtra(RuntimeServiceContract.EXTRA_MESSAGE).orEmpty().ifBlank { "Runtime error" },
        )
        else -> null
    }

private fun RuntimeState.serviceName(): String =
    when (this) {
        RuntimeState.Idle -> RuntimeServiceContract.STATE_IDLE
        RuntimeState.BuildingConfig -> RuntimeServiceContract.STATE_BUILDING_CONFIG
        RuntimeState.EstablishingVpn -> RuntimeServiceContract.STATE_ESTABLISHING_VPN
        RuntimeState.StartingNativeCore -> RuntimeServiceContract.STATE_STARTING_NATIVE_CORE
        RuntimeState.VerifyingConnection -> RuntimeServiceContract.STATE_VERIFYING_CONNECTION
        is RuntimeState.Running -> RuntimeServiceContract.STATE_RUNNING
        RuntimeState.Stopping -> RuntimeServiceContract.STATE_STOPPING
        is RuntimeState.Error -> RuntimeServiceContract.STATE_ERROR
        RuntimeState.PreparingVpnPermission,
        RuntimeState.StartingForeground,
        -> error("State is owned by the UI process")
    }
