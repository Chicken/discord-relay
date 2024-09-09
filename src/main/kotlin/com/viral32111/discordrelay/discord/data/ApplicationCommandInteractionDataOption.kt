package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/interactions/receiving-and-responding#interaction-object-application-command-interaction-data-option-structure
@Serializable
data class ApplicationCommandInteractionDataOption(
    @Required @SerialName("name") val name: String,
    @Required @SerialName("type") val type: Int,
    @SerialName("value") val value: String? = null,
    @SerialName("options") val options: List<ApplicationCommandInteractionDataOption>? = null,
    @SerialName("focused") val isFocused: Boolean? = null
)