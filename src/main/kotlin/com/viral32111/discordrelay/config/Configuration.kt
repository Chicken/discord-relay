package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
	@Required val discord: Discord = Discord(),
	@Required val http: HTTP = HTTP(),
	@Required @SerialName( "third-party" ) val thirdParty: ThirdParty = ThirdParty()
)
