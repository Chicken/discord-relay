package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/interactions/receiving-and-responding#interaction-object-application-command-interaction-data-option-structure
@Serializable
data class ApplicationCommandInteractionDataOption(
    // We only receive string so other value types are not implemented
    @SerialName("value") val value: String? = null,
)
