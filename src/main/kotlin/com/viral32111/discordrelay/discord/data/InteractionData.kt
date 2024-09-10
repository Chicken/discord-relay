package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/interactions/receiving-and-responding#interaction-object-interaction-data
@Serializable
data class InteractionData(
    @Required @SerialName("name") val name: String,
    @Required @SerialName("type") val type: Int,
    @SerialName("options") val options: List<ApplicationCommandInteractionDataOption>? = null,
)
