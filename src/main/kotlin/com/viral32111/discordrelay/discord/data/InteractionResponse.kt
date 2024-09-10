package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/interactions/receiving-and-responding#interaction-response-object

@Serializable
object InteractionResponseType {
    const val Message = 4
}

@Serializable
data class InteractionResponse(
    @Required val type: Int,
    val data: InteractionResponseData? = null
)

@Serializable
object InteractionResponseFlags {
    const val Ephemeral = 64
}

@Serializable
data class InteractionResponseData(
    val content: String? = null,
    val flags: Int? = null,
)

class InteractionTextResponseBuilder {
    var content: String? = null
    private var flags: Int? = null

    fun hidden() { flags = InteractionResponseFlags.Ephemeral }

    fun build() = InteractionResponse( InteractionResponseType.Message, InteractionResponseData( content, flags ) )
}
