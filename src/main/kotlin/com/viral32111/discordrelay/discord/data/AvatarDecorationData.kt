package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/user#avatar-decoration-data-object
@Serializable
data class AvatarDecorationData(
    @Required @SerialName("asset") val asset: String,
    @Required @SerialName("sku_id") val skuId: String
)