package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/guild#guild-member-object
@Serializable
data class Member(
    @SerialName("user") val user: User? = null,
    @SerialName("nick") val nick: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @Required @SerialName("roles") val roles: List<String>,
    @Required @SerialName("joined_at") val joinedAt: String,
    @SerialName("premium_since") val premiumSince: String? = null,
    @Required @SerialName("deaf") val isDeaf: Boolean,
    @Required @SerialName("mute") val isMute: Boolean,
    @Required @SerialName("flags") val flags: Int,
    @SerialName("pending") val isPending: Boolean? = null,
    @SerialName("permissions") val permissions: String? = null,
    @SerialName("communication_disabled_until") val communicationDisabledUntil: String? = null,
    @SerialName("avatar_decoration_data") val avatarDecorationData: AvatarDecorationData? = null,
)
