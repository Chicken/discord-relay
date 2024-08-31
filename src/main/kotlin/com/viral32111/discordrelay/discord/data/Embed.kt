package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/message#embed-object

@Serializable
data class Embed(
	@SerialName( "description" ) val description: String? = null,
	@SerialName( "color" ) val color: Int? = null,
	@SerialName( "author" ) val author: EmbedAuthor? = null,
)

@Serializable
data class EmbedAuthor(
	@Required @SerialName( "name" ) val name: String,
	@SerialName( "icon_url" ) val iconUrl: String? = null
)

data class EmbedBuilder(
	var description: String? = null,
	var color: Int? = null,
	var author: EmbedAuthor? = null,
) {
	fun build() = Embed( description, color, author )
}
