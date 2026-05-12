package ru.myit.vlevpn.runtime

sealed interface RuntimeState {
    data object Idle : RuntimeState
    data object PreparingVpnPermission : RuntimeState
    data object StartingForeground : RuntimeState
    data object BuildingConfig : RuntimeState
    data object EstablishingVpn : RuntimeState
    data object StartingNativeCore : RuntimeState
    data object VerifyingConnection : RuntimeState
    data class Running(val connectedAtMillis: Long) : RuntimeState
    data object Stopping : RuntimeState
    data class Error(val message: String) : RuntimeState
}
