package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordRelayChannel(
	@Required @SerialName( "server-id" ) val serverId: String = "",
	@Required @SerialName( "channel-id" ) val channelId: String = "",
	@Required val webhook: DiscordWebhook = DiscordWebhook(),
)
