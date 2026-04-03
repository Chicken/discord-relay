package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.DiscordBot
import com.viral32111.discordrelay.events.PlayerCompleteAdvancementCallback
import com.viral32111.discordrelay.events.PlayerDeathCallback
import com.viral32111.discordrelay.events.PlayerJoinCallback
import com.viral32111.discordrelay.events.PlayerLeaveCallback
import com.viral32111.discordrelay.helper.getColor
import com.viral32111.discordrelay.helper.getText

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.loader.api.FabricLoader
import me.drex.vanish.api.VanishAPI
import me.drex.vanish.api.VanishEvents

fun registerCallbackListeners( coroutineScope: CoroutineScope, configuration: Configuration ) {
	val avatarUrl = configuration.thirdParty.avatarUrl
	val hasVanish = FabricLoader.getInstance().isModLoaded("melius-vanish");

	ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
		DiscordRelay.LOGGER.debug( "Sending server offline message..." )

		coroutineScope.launch {
			DiscordBot.sendEmbedMessage(
				authorName = "The server has stopped",
				color = 0xFF0000,
				wait = false
			)
		}
	}

	ServerMessageEvents.CHAT_MESSAGE.register { message, player, _ ->
		if ( hasVanish && VanishAPI.isVanished(player) ) return@register
		DiscordRelay.LOGGER.debug( "Relaying chat message '${ message.content.string }' for player '${ player.name.string } (${ player.uuidAsString })...'" )

		coroutineScope.launch {
			DiscordBot.sendTextMessage(
				content = message.content.string,
				avatarUrl = avatarUrl.format( player.uuidAsString ),
				userName = player.displayName?.string ?: player.name.string
			)
		}
	}

	PlayerJoinCallback.EVENT.register { player ->
		if ( hasVanish && VanishAPI.isVanished(player) ) return@register
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		DiscordRelay.LOGGER.debug( "Relaying join message for player '${ player.name.string } (${ player.uuidAsString })...'" )

		coroutineScope.launch {
			DiscordBot.sendEmbedMessage(
				authorName = "${ player.displayName?.string ?: player.name.string } joined",
				authorIconUrl = playerAvatarUrl,
				color = 0xFFFFFF
			)
		}
	}

	PlayerLeaveCallback.EVENT.register { player ->
		if ( hasVanish && VanishAPI.isVanished(player) ) return@register
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		DiscordRelay.LOGGER.debug( "Relaying leave message for player '${ player.name.string } (${ player.uuidAsString })...'" )

		coroutineScope.launch {
			DiscordBot.sendEmbedMessage(
				authorName = "${ player.displayName?.string ?: player.name.string } left",
				authorIconUrl = playerAvatarUrl,
				color = 0xFFFFFF
			)
		}
	}

	if ( hasVanish ) {
		VanishEvents.VANISH_EVENT.register { player, vanish ->
			val playerAvatarUrl = avatarUrl.format( player.uuidAsString )
			if ( vanish ) {
				coroutineScope.launch {
					DiscordBot.sendEmbedMessage(
						authorName = "${ player.displayName?.string ?: player.name.string } left",
						authorIconUrl = playerAvatarUrl,
						color = 0xFFFFFF
					)
				}
			} else {
				coroutineScope.launch {
					DiscordBot.sendEmbedMessage(
						authorName = "${ player.displayName?.string ?: player.name.string } joined",
						authorIconUrl = playerAvatarUrl,
						color = 0xFFFFFF
					)
				}
			}
		}
	}

	PlayerDeathCallback.EVENT.register { player, damageSource ->
		if ( hasVanish && VanishAPI.isVanished(player) ) return@register
		val deathMessage = damageSource.getDeathMessage( player ).string
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		DiscordRelay.LOGGER.debug( "Relaying death message '$deathMessage' for player '${ player.name.string } (${ player.uuidAsString })...'" )

		coroutineScope.launch {
			DiscordBot.sendEmbedMessage(
				authorName = deathMessage,
				authorIconUrl = playerAvatarUrl,
				color = 0xFFFFAA
			)
		}
	}

	PlayerCompleteAdvancementCallback.EVENT.register { player, advancement, shouldAnnounceToChat ->
		if ( hasVanish && VanishAPI.isVanished(player) ) return@register
		if ( !shouldAnnounceToChat ) return@register

		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )
		val advancementTitle = advancement.display?.get()?.title?.string
		val advancementDescription = advancement.display?.get()?.description?.string
		val advancementText = advancement.getText() ?: "gained the achievement"
		val advancementColor = advancement.getColor()

		DiscordRelay.LOGGER.debug( "Relaying advancement '$advancementTitle' completion message for player '${ player.name.string } (${ player.uuidAsString })...'" )

		coroutineScope.launch {
			DiscordBot.sendEmbedMessage(
				authorName = "${ player.displayName?.string ?: player.name.string } $advancementText ${ advancementTitle ?: "Unknown" }",
				authorIconUrl = playerAvatarUrl,
				description = if ( !advancementDescription.isNullOrBlank() ) advancementDescription else null,
				color = advancementColor ?: 0xFFFFFF
			)
		}
	}
}
