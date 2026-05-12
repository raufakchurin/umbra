package ru.myit.vlevpn.data.subscription

import ru.myit.vlevpn.domain.model.ServerProfile

data class ParsedSubscription(
    val profiles: List<ServerProfile>,
    val skippedItems: List<String> = emptyList(),
    val providerId: String = "",
    val providerDomainHash: String = "",
) {
    val importedCount: Int = profiles.size
    val skippedCount: Int = skippedItems.size
}
