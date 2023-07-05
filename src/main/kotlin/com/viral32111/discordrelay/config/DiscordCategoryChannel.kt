package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordCategoryChannel(
	@Required @SerialName( "id" ) val identifier: String = "",
	@SerialName( "name" ) val name: String = "Minecraft (%s)"
)
