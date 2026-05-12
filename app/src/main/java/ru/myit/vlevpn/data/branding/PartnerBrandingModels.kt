package ru.myit.vlevpn.data.branding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PartnerBrandingResponse(
    val items: List<PartnerBrandingItem> = emptyList(),
)

@Serializable
data class PartnerBrandingItem(
    @SerialName("provider_id") val providerId: String,
    val priority: Int,
    @SerialName("use_image") val useImage: Boolean,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_sha256") val imageSha256: String? = null,
    @SerialName("image_size_bytes") val imageSizeBytes: Int = 0,
    @SerialName("blur_percent") val blurPercent: Int = 35,
    @SerialName("accent_color") val accentColor: String,
    @SerialName("background_color") val backgroundColor: String,
    @SerialName("updated_at") val updatedAt: String,
)
