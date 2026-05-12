package ru.myit.vlevpn.quick

sealed interface QuickCommand {
    data object Connect : QuickCommand
    data object Disconnect : QuickCommand
    data object Toggle : QuickCommand
    data class Import(val input: String) : QuickCommand
    data class Add(val input: String) : QuickCommand
    data class RoutingAdd(val packages: Set<String>, val apply: Boolean) : QuickCommand
}
