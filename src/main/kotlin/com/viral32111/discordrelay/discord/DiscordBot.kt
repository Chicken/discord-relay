package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.config.Configuration
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.allowedMentions
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import me.drex.vanish.api.VanishAPI
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.PlayerConfigEntry
import net.minecraft.server.PlayerManager
import net.minecraft.server.WhitelistEntry
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting
import java.util.*

object DiscordBot {
	private lateinit var kord: Kord
	private lateinit var configuration: Configuration
	private lateinit var playerManager: PlayerManager

	private val coroutineScope = CoroutineScope( Dispatchers.IO )
	private var loginJob: Job? = null

	private val hasVanish by lazy { FabricLoader.getInstance().isModLoaded( "melius-vanish" ) }

	suspend fun initialize( config: Configuration, pm: PlayerManager ) {
		configuration = config
		playerManager = pm

		kord = Kord( config.discord.application.token )

		registerEventHandlers()
		registerSlashCommands()
	}

	private fun registerEventHandlers() {
		kord.on<ReadyEvent> {
			DiscordRelay.LOGGER.info( "Discord bot ready as ${ self.username }!" )

			sendEmbedMessage(
				authorName = "The server has started",
				color = 0x00FF00
			)
		}

		kord.on<MessageCreateEvent> {
			val author = message.author ?: return@on
			if ( author.isBot ) return@on
			if ( message.channelId != Snowflake( configuration.discord.relay.channelId ) ) return@on
			if ( message.content.isBlank() ) return@on

			val member = message.getAuthorAsMember()
			val memberRoleColor = member.roles
                .toList()
                .filter { it.color.rgb != 0 }
                .maxByOrNull { it.rawPosition }
				?.color

			val style = if ( memberRoleColor != null )
				Style.EMPTY.withColor( memberRoleColor.rgb )
			else
				Style.EMPTY.withColor( TextColor.fromFormatting( Formatting.GREEN ) )

			val displayName = member.nickname ?: author.globalName ?: author.username

			val chatMessage: Text = Text.literal( "" )
				.append(
					Text.literal( "(Discord) " )
						.setStyle( Style.EMPTY.withColor( TextColor.fromFormatting( Formatting.BLUE ) ) )
						.append( Text.literal( displayName ).setStyle( style ) )
				)
				.append( Text.literal( ": " ) )
				.append( Text.literal( message.content ) )

			playerManager.broadcast( chatMessage, false )
		}

		kord.on<ChatInputCommandInteractionCreateEvent> {
			val commandName = interaction.command.rootName
			DiscordRelay.LOGGER.debug( "Received slash command interaction: '$commandName'" )

			when ( commandName ) {
				"whitelist" -> {
					val username = interaction.command.strings[ "username" ]
					val response = interaction.deferEphemeralResponse()

					if ( username == null ) {
						response.respond { content = "Please provide a username!" }
						return@on
					}

					val optional: Optional<PlayerConfigEntry> = playerManager.server.getApiServices().nameToIdCache().findByName( username )

					if ( optional.isEmpty ) {
						response.respond { content = "Invalid username!" }
					} else {
						val profile = optional.get()
						if ( playerManager.whitelist.isAllowed( profile ) ) {
							response.respond { content = "$username is already on the whitelist!" }
						} else {
							playerManager.whitelist.add( WhitelistEntry( profile ) )
							response.respond { content = "Added $username to the whitelist!" }
						}
					}
				}

				"list" -> {
					val response = interaction.deferEphemeralResponse()

					val playerList = playerManager.playerList
						.filter { !hasVanish || !VanishAPI.isVanished( it ) }
						.map { player ->
							val displayName = player.displayName?.string
							if ( displayName.isNullOrEmpty() || displayName == player.name.string ) {
								player.name.string
							} else {
								"$displayName (${ player.name.string })"
							}
						}

					val playersOnline = when {
						playerList.isEmpty() -> ""
						playerList.size == 1 -> playerList[ 0 ]
						playerList.size == 2 -> "${ playerList[ 0 ] } and ${ playerList[ 1 ] }"
						else -> playerList.dropLast( 1 ).joinToString( ", " ) + ", and " + playerList.last()
					}

					response.respond {
						content = "${ playerList.size }/${ playerManager.maxPlayerCount } players online${ if ( playersOnline.isNotEmpty() ) ": $playersOnline" else "" }"
					}
				}
			}
		}
	}

	private suspend fun registerSlashCommands() {
		kord.createGlobalChatInputCommand( "whitelist", "Whitelist yourself" ) {
			string( "username", "The Minecraft username to whitelist" ) { required = true }
		}
		kord.createGlobalChatInputCommand( "list", "Get a list of the currently online players" )
	}

	fun start() {
		loginJob = coroutineScope.launch {
			@OptIn( PrivilegedIntent::class )
			kord.login {
				intents += Intent.Guilds
				intents += Intent.GuildMembers
				intents += Intent.GuildMessages
				intents += Intent.MessageContent
			}
		}
	}

	suspend fun shutdown() {
		if ( ::kord.isInitialized ) {
			kord.shutdown()
		}
		loginJob?.cancel()
	}

	suspend fun sendTextMessage( content: String, avatarUrl: String, userName: String ) {
		if ( !::kord.isInitialized ) return

		val webhookId = Snowflake( configuration.discord.relay.webhook.identifier )
		val webhookToken = configuration.discord.relay.webhook.token
		val threadId = configuration.discord.relay.webhook.threadId?.let { Snowflake( it ) }

		kord.rest.webhook.executeWebhook( webhookId, webhookToken, wait = true, threadId = threadId ) {
			this.content = content
			this.username = userName
			this.avatarUrl = avatarUrl
			allowedMentions {  }
		}
	}

	suspend fun sendEmbedMessage(
		authorName: String,
		authorIconUrl: String? = null,
		description: String? = null,
		color: Int = 0xFFFFFF,
		wait: Boolean = true
	) {
		if ( !::kord.isInitialized ) return

		val webhookId = Snowflake( configuration.discord.relay.webhook.identifier )
		val webhookToken = configuration.discord.relay.webhook.token
		val threadId = configuration.discord.relay.webhook.threadId?.let { Snowflake( it ) }

		kord.rest.webhook.executeWebhook( webhookId, webhookToken, wait = wait, threadId = threadId ) {
			embed {
				author {
					name = authorName
					icon = authorIconUrl
				}
				if ( description != null ) this.description = description
				this.color = Color( color )
			}
			allowedMentions { }
		}
	}
}
