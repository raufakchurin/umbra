package ru.myit.vlevpn.data.subscription

data class SubscriptionMetadata(
    val title: String = "",
    val updateIntervalHours: Int = 0,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireAtMillis: Long = 0L,
    val supportUrl: String = "",
    val webPageUrl: String = "",
    val announce: String = "",
    val updateAlways: Boolean = false,
    val providerId: String = "",
    val providerDomainHash: String = "",
    val extraKeysAddUrl: String = "",
)

data class SubscriptionFetchResult(
    val body: String,
    val metadata: SubscriptionMetadata = SubscriptionMetadata(),
)
