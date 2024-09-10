package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.WebSocketCloseCode
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.Gateway
import com.viral32111.discordrelay.discord.data.Guild
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.time.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.drex.vanish.api.VanishAPI
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.PlayerManager
import net.minecraft.server.WhitelistEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting
import java.io.IOException
import java.net.URI
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.pow
import kotlin.random.Random

class Gateway( private val configuration: Configuration, private val playerManager: PlayerManager ) {

	// Dedicated scope for our coroutines to run on
	private val coroutineScope = CoroutineScope( Dispatchers.IO )

	// The underlying WebSocket connection
	private var webSocket: WebSocket? = null

	// Job & completable for heartbeating in the background
	private var heartbeatJob: Job? = null
	private var heartbeatAcknowledgementConfirmation: CompletableDeferred<Unit>? = null

	// Last known sequence number for heartbeating & session resuming
	private var sequenceNumber: Int? = null

	// Used for session resuming
	private var sessionIdentifier: String? = null
	private var resumeBaseUrl: String? = null
	private var shouldResume = false

	// Number of times we've reconnected
	private var reconnectCount = 0
	private var alreadyReconnecting = false

	// The bot ID & server roles, set during ready process
	private var myIdentifier: String? = null
	private var serverRoles: Map<String, Guild.Role>? = null

	// Server closure
	private var serverClosed = false;

	private var hasVanish = FabricLoader.getInstance().isModLoaded("melius-vanish");

	/**
	 * Opens a WebSocket connection to the given URL, closing any existing connections beforehand.
	 * Ideally wait for closure confirmation after calling this.
	 * @param baseUrl The base URL of the WebSocket. Should not include any parameters.
	 * @param version The gateway version to include in the parameters.
	 * @return The underlying WebSocket.
	 * @see awaitClosure
	 */
	suspend fun open( baseUrl: String, version: Int = configuration.discord.api.version ) {
		val url = URI.create( "$baseUrl?v=$version&encoding=json" )

		if ( webSocket != null && !webSocket!!.isOutputClosed ) {
			DiscordRelay.LOGGER.info( "Closing existing WebSocket connection..." )
			close( WebSocketCloseCode.GoingAway, "Closing existing connection." )
		}

		DiscordRelay.LOGGER.info( "Opening new WebSocket connection to '$url'..." )
		webSocket = HTTP.startWebSocketConnection( url, configuration.http.timeoutSeconds, Listener() )
	}

	/**
	 * Closes the underlying WebSocket connection.
	 * @param code The WebSocket close code. https://www.rfc-editor.org/rfc/rfc6455.html#section-7.4.1
	 * @param reason The human-readable reason for closing the connection.
	 */
	suspend fun close( code: Int = WebSocketCloseCode.Normal, reason: String = "Unknown.", serverClosing: Boolean = false) {
		try {
			DiscordRelay.LOGGER.info( "Closing WebSocket connection with code $code & reason '$reason'" )
			if ( serverClosing ) serverClosed = true;
			webSocket?.sendClose( code, reason )?.await()
		} catch ( exception: IOException ) {
			DiscordRelay.LOGGER.error( "Cannot close WebSocket connection! (${ exception.message })" )
			webSocket?.abort()
		}
	}

	// Starts heartbeating in the background
	private fun startHeartbeating( webSocket: WebSocket, interval: Long ) {
		if ( heartbeatJob != null ) {
			DiscordRelay.LOGGER.debug( "Cancelling existing background heartbeating job..." )
			heartbeatJob?.cancel()
		}

		DiscordRelay.LOGGER.debug( "Starting new background heartbeating job..." )
		heartbeatJob = coroutineScope.launch {
			DiscordRelay.LOGGER.debug( "HEARTBEAT 1" )
			heartbeatLoop( webSocket, interval )
			DiscordRelay.LOGGER.debug( "HEARTBEAT 2" )
		}
	}

	// Sends heartbeats on an interval - https://discord.com/developers/docs/topics/gateway#sending-heartbeats
	private suspend fun heartbeatLoop( webSocket: WebSocket, regularInterval: Long ) {
		DiscordRelay.LOGGER.debug( "HEARTBEAT 3" )
		val initialInterval = ( regularInterval * Random.nextFloat() ).toLong()
		DiscordRelay.LOGGER.debug( "Waiting $initialInterval milliseconds for the initial heartbeat..." )
		DiscordRelay.LOGGER.debug( "HEARTBEAT 4" )
		delay( initialInterval )
		DiscordRelay.LOGGER.debug( "HEARTBEAT 5" )

		sendHeartbeat( webSocket, this.sequenceNumber )
		DiscordRelay.LOGGER.debug( "HEARTBEAT 6" )

		while ( !webSocket.isOutputClosed ) {
			DiscordRelay.LOGGER.debug( "Waiting $regularInterval milliseconds for the next heartbeat..." )
			delay( regularInterval )

			DiscordRelay.LOGGER.debug( "HEARTBEAT 7" )
			sendHeartbeat( webSocket, this.sequenceNumber )
			DiscordRelay.LOGGER.debug( "HEARTBEAT 8" )
		}
	}

	// Sends a heartbeat event to Discord - https://discord.com/developers/docs/topics/gateway-events#heartbeat
	private suspend fun sendHeartbeat( webSocket: WebSocket, sequenceNumber: Int? ) {
		heartbeatAcknowledgementConfirmation = CompletableDeferred()

		DiscordRelay.LOGGER.debug( "Sending heartbeat at sequence number $sequenceNumber..." )
		sendEvent( webSocket, Gateway.Event.OperationCode.Heartbeat, JsonPrimitive( sequenceNumber ) )

		try {
			withTimeout( Duration.ofSeconds( configuration.discord.gateway.heartbeatTimeoutSeconds ) ) {
				DiscordRelay.LOGGER.debug( "Waiting for heartbeat acknowledgement..." )
				heartbeatAcknowledgementConfirmation?.await()
				DiscordRelay.LOGGER.debug( "Received heartbeat acknowledgement!" )
			}
		} catch ( exception: TimeoutCancellationException ) {
			DiscordRelay.LOGGER.warn( "Timed out while waiting for a heartbeat acknowledgement!" )

			DiscordRelay.LOGGER.debug( "Closing WebSocket connection..." )
			close( WebSocketCloseCode.GoingAway, "No heartbeat acknowledgement." )
			tryReconnect( true )
		}
	}

	// Sends an identify event to Discord - https://discord.com/developers/docs/topics/gateway#identifying
	private suspend fun sendIdentify( webSocket: WebSocket ) {
		DiscordRelay.LOGGER.debug( "IDENTIFY" )
		val libraryName = "viral32111's discord relay/${ Version.discordRelay() } (https://github.com/viral32111/discord-relay)"

		// Just to be sure
		this.sessionIdentifier = null
		this.resumeBaseUrl = null
		DiscordRelay.LOGGER.debug( "Reset session identifier & resume base URL." )

		DiscordRelay.LOGGER.debug( "Sending identify..." )
		sendEvent( webSocket, Gateway.Event.OperationCode.Identify, JSON.encodeToJsonElement( Gateway.Event.Data.Identify(
			applicationToken = configuration.discord.application.token,
			intents = Gateway.Event.Data.Identify.Intents.Guilds or
					Gateway.Event.Data.Identify.Intents.GuildMessages or
					Gateway.Event.Data.Identify.Intents.MessageContent,
			connectionProperties = Gateway.Event.Data.Identify.ConnectionProperties(
				operatingSystemName = configuration.http.userAgentPrefix,
				browserName = libraryName,
				deviceName = libraryName
			)
		) ) )
	}

	// Sends a resume event to Discord - https://discord.com/developers/docs/topics/gateway#resuming
	private suspend fun sendResume( webSocket: WebSocket, sessionIdentifier: String, sequenceNumber: Int ) {
		DiscordRelay.LOGGER.debug( "RESUME" )
		DiscordRelay.LOGGER.debug( "Sending resume for session '$sessionIdentifier'..." )
		sendEvent( webSocket, Gateway.Event.OperationCode.Resume, JSON.encodeToJsonElement( Gateway.Event.Data.Resume(
			applicationToken = configuration.discord.application.token,
			sessionIdentifier = sessionIdentifier,
			sequenceNumber = sequenceNumber
		) ) )
	}

	// Sends an event to Discord - https://discord.com/developers/docs/topics/gateway-events#send-events
	private suspend fun sendEvent( webSocket: WebSocket, operationCode: Int, data: JsonElement? ) {
		if ( webSocket.isOutputClosed ) {
			DiscordRelay.LOGGER.warn( "WebSocket output closed when attempting to send data?!" )
			close( WebSocketCloseCode.GoingAway, "WebSocket output closed.")
			tryReconnect( true )
			return
		}

		val jsonPayload = JSON.encodeToString( Gateway.Event(
			operationCode = operationCode,
			data = data
		) )

		DiscordRelay.LOGGER.debug( "Sending JSON payload '$jsonPayload' over WebSocket..." )
		webSocket.sendText( jsonPayload, true )?.await()
	}

	// https://discord.com/developers/docs/topics/gateway-events#hello
	private fun handleHelloEvent( webSocket: WebSocket, data: Gateway.Event.Data.Hello ) {
		val sessionIdentifier = this.sessionIdentifier
		val sequenceNumber = this.sequenceNumber

		DiscordRelay.LOGGER.debug( "HELLO 1" )
		startHeartbeating( webSocket, data.heartbeatInterval )
		DiscordRelay.LOGGER.debug( "HELLO 2" )

		coroutineScope.launch {
			DiscordRelay.LOGGER.debug( "HELLO 3" )
			if ( shouldResume && sessionIdentifier != null && sequenceNumber != null ) {
				DiscordRelay.LOGGER.debug( "HELLO 4/1" )
				sendResume( webSocket, sessionIdentifier, sequenceNumber )
				DiscordRelay.LOGGER.debug( "HELLO 5/1" )
				shouldResume = false
			} else {
				DiscordRelay.LOGGER.debug( "HELLO 4/2" )
				sendIdentify( webSocket )
				DiscordRelay.LOGGER.debug( "HELLO 5/2" )
			}
		}
	}

	// https://discord.com/developers/docs/topics/gateway-events#reconnect
	private fun handleReconnectEvent() {
		DiscordRelay.LOGGER.debug( "We need to reconnect!" )

		coroutineScope.launch {
			close( WebSocketCloseCode.GoingAway, "Told to reconnect." )

			DiscordRelay.LOGGER.debug( "Reconnecting as instructed..." )
			tryReconnect( true )
		}
	}

	private fun handleInvalidSessionEvent( shouldResume: Boolean ) {
		DiscordRelay.LOGGER.warn( "Our session is invalid!" )

		coroutineScope.launch {
			close( WebSocketCloseCode.GoingAway, "Session is invalid." )

			DiscordRelay.LOGGER.debug( "Reconnecting due to invalid session..." )
			tryReconnect( shouldResume )
		}
	}

	private fun handleHeartbeat( webSocket: WebSocket ) {
		val sequenceNumber = this.sequenceNumber
		DiscordRelay.LOGGER.debug( "Heartbeat requested at sequence number '$sequenceNumber'." )

		coroutineScope.launch {
			sendHeartbeat( webSocket, sequenceNumber )
		}
	}

	private fun handleHeartbeatAcknowledgement() {
		DiscordRelay.LOGGER.debug( "Heartbeat acknowledged." )
		heartbeatAcknowledgementConfirmation?.complete( Unit )
	}

	// https://discord.com/developers/docs/topics/gateway-events#ready
	private fun handleReadyEvent( data: Gateway.Event.Data.Ready ) {
		DiscordRelay.LOGGER.info( "Ready as '${ data.user.username }#${ data.user.discriminator }' (${ data.user.identifier })." )
		myIdentifier = data.user.identifier

		sessionIdentifier = data.sessionIdentifier
		resumeBaseUrl = data.resumeUrl
		DiscordRelay.LOGGER.debug( "Set session identifier to '$sessionIdentifier' & resume base URL to '$resumeBaseUrl'." )
	}

	// https://discord.com/developers/docs/topics/gateway-events#message-create
	private fun handleMessageCreate( message: Gateway.Event.Data.MessageCreate ) {
		DiscordRelay.LOGGER.debug( "Received message '${ message.content }' (${ message.identifier }) in channel ${ message.channelIdentifier } from '@${ message.author.username }' (${ message.author.identifier })." )

		if ( message.channelIdentifier != configuration.discord.relay.channelId ) {
			DiscordRelay.LOGGER.debug( "Ignoring non-relay channel message (${ message.identifier }) from '@${ message.author.username }' (${ message.author.identifier })." )
			return
		}

		if ( message.content.isBlank() ) {
			DiscordRelay.LOGGER.debug( "Ignoring empty message (${ message.identifier }) from '@${ message.author.username }' (${ message.author.identifier })." )
			return
		}

		if ( message.author.isBot == true || message.author.isSystem == true ) {
			DiscordRelay.LOGGER.debug( "Ignoring bot/system message '${ message.content }' (${ message.identifier }) from '@${ message.author.username }' (${ message.author.identifier })." )
			return
		}

		if ( message.author.identifier == myIdentifier ) {
			DiscordRelay.LOGGER.debug( "Ignoring my message '${ message.content }' (${ message.identifier }) from '@${ message.author.username }' (${ message.author.identifier })." )
			return
		}

		DiscordRelay.LOGGER.debug( "Relaying Discord message '${ message.content }' (${ message.identifier}) from '@${ message.author.username }' (${ message.author.identifier })..." )

		val memberRoleColor = getMemberRoleColor( message.member )
		val playerStyle = getStyleOrDefault( memberRoleColor )
		DiscordRelay.LOGGER.debug( "Member role color is '$memberRoleColor' & player style is '${ playerStyle.color?.rgb }'" )

		val chatMessage: Text = Text.literal( "" )
			.append( Text.literal( "(Discord) " ).setStyle( Style.EMPTY.withColor( TextColor.fromFormatting( Formatting.BLUE ) ) )
			.append( Text.literal( message.member?.displayName ?: message.author.displayName ?: message.author.username ).setStyle( playerStyle ) ) )
			.append( Text.literal( ": " ) )
			.append( Text.literal( message.content ) )

		playerManager.broadcast( chatMessage, false )
	}

	// https://discord.com/developers/docs/topics/gateway-events#guild-create
	private fun handleGuildCreate( guild: Gateway.Event.Data.GuildCreate ) {
		if ( guild.identifier != configuration.discord.relay.serverId ) {
			DiscordRelay.LOGGER.debug( "Ignoring guild create event for guild '${ guild.name }' (${ guild.identifier })." )
			return
		}

		serverRoles = guild.roles.associateBy { it.identifier }
	}

	// https://discord.com/developers/docs/topics/gateway-events#interaction-create
	private fun handleInteractionCreate( interaction: Gateway.Event.Data.InteractionCreate) {
		DiscordRelay.LOGGER.debug( "Received interaction '${interaction.data?.name}' (${interaction.identifier})" )

		if(interaction.type != 2) return // Only handle slash command interactions (for now)

		if(interaction.data?.name == "whitelist") {
			val username = interaction.data.options?.get(0)?.value

			coroutineScope.launch {
				val gameProfile = playerManager.server.userCache?.findByName(username)

				if(username == null || gameProfile?.isEmpty == true || gameProfile == null) {
					API.respondToInteractionWithText(
						interaction.identifier,
						interaction.token,
					) {
						content = "Invalid username!"
						hidden()
					}
				} else {
					if(playerManager.whitelist.get(gameProfile.get()) != null) {
						API.respondToInteractionWithText(
							interaction.identifier,
							interaction.token,
						) {
							content = "$username is already on the whitelist!"
							hidden()
						}
					} else {
						playerManager.whitelist.add(WhitelistEntry(gameProfile.get()))

						API.respondToInteractionWithText(
							interaction.identifier,
							interaction.token,
						) {
							content = "Added $username to the whitelist!"
							hidden()
						}
					}
				}
			}
		}

		if(interaction.data?.name == "list") {
			val playerList = playerManager.playerList.filter { !hasVanish || !VanishAPI.isVanished(it) }.map { player: ServerPlayerEntity ->
				if (player.displayName?.string.isNullOrEmpty() || player.displayName?.string == player.name.string) {
					player.name.string
				} else {
					"${player.displayName?.string} (${player.name.string})"
				}
			}
			val playersOnline = when {
				playerList.isEmpty() -> ""
				playerList.size == 1 -> playerList[0]
				playerList.size == 2 -> "${playerList[0]} and ${playerList[1]}"
				else -> playerList.dropLast(1).joinToString(", ") + ", and " + playerList.last()
			}

			coroutineScope.launch {
				API.respondToInteractionWithText(
					interaction.identifier,
					interaction.token,
				) {
					content = "${playerList.size}/${playerManager.maxPlayerCount} players online${if(playersOnline != "") {": $playersOnline"} else {""}}"
					hidden()
				}
			}
		}
	}

	private fun getMemberRoleColor( member: Guild.Member? ): Int? = member?.roleIdentifiers
		?.map { serverRoles?.get( it ) ?: return null }
		?.maxByOrNull { it.position }
		?.color

	private fun getStyleOrDefault( rgb: Int?, formatting: Formatting = Formatting.GREEN ): Style =
		if ( rgb != null ) Style.EMPTY.withColor( rgb ) else Style.EMPTY.withColor( TextColor.fromFormatting( formatting ) )

	private fun processMessage( webSocket: WebSocket, message: String ) {
		val event = JSON.decodeFromString<Gateway.Event>( message )
		DiscordRelay.LOGGER.debug( "Received JSON payload '$message' from the WebSocket." )

		if ( event.sequenceNumber != null ) {
			DiscordRelay.LOGGER.debug( "Incremented sequence number from $sequenceNumber to ${ event.sequenceNumber }." )
			sequenceNumber = event.sequenceNumber
		}

		when ( event.operationCode ) {
			Gateway.Event.OperationCode.Hello -> {
				if ( event.data == null ) throw IllegalStateException( "Received Gateway hello operation '${ event.name }' without data" )
				handleHelloEvent( webSocket, JSON.decodeFromJsonElement<Gateway.Event.Data.Hello>( event.data ) )
			}
			Gateway.Event.OperationCode.Reconnect -> handleReconnectEvent()
			Gateway.Event.OperationCode.InvalidSession -> {
				if ( event.data == null ) throw IllegalStateException( "Received Gateway invalid session operation '${ event.name }' without data" )
				handleInvalidSessionEvent( JSON.decodeFromJsonElement<Boolean>( event.data ) )
			}
			Gateway.Event.OperationCode.Heartbeat -> handleHeartbeat( webSocket )
			Gateway.Event.OperationCode.HeartbeatAcknowledgement -> handleHeartbeatAcknowledgement()

			// https://discord.com/developers/docs/topics/gateway-events#receive-events
			Gateway.Event.OperationCode.Dispatch -> {
				if ( event.data == null ) throw IllegalStateException( "Received Gateway event '${ event.name }' without data" )

				when ( event.name ) {
					Gateway.Event.Name.Ready -> handleReadyEvent( JSON.decodeFromJsonElement<Gateway.Event.Data.Ready>( event.data ) )
					Gateway.Event.Name.MessageCreate -> handleMessageCreate( JSON.decodeFromJsonElement<Gateway.Event.Data.MessageCreate>( event.data ) )
					Gateway.Event.Name.GuildCreate -> handleGuildCreate( JSON.decodeFromJsonElement<Gateway.Event.Data.GuildCreate>( event.data ) )
					Gateway.Event.Name.InteractionCreate -> handleInteractionCreate( JSON.decodeFromJsonElement<Gateway.Event.Data.InteractionCreate>( event.data ) )

					else -> DiscordRelay.LOGGER.debug( "Ignoring Gateway event '${ event.name }' with data '${ JSON.encodeToString( event.data ) }'." )
				}
			}

			else -> DiscordRelay.LOGGER.debug( "Ignoring Gateway operation ${ event.operationCode } with data '${ JSON.encodeToString( event.data ) }'." )
		}
	}

	private suspend fun tryReconnect( shouldResume: Boolean, force: Boolean = false ) {
		if ( serverClosed ) {
			DiscordRelay.LOGGER.debug( "Server closed, not reconnecting..." )
			return
		}
		if ( alreadyReconnecting && !force ) {
			DiscordRelay.LOGGER.debug( "Already reconnecting, skipping..." )
			return
		}
		alreadyReconnecting = true
		val duration = ( 2.0.pow( reconnectCount ) * 1000 ).toLong()
		DiscordRelay.LOGGER.debug( "Connection attempt $reconnectCount, waiting $duration milliseconds before reconnecting..." )
		delay( duration )

		val resumeBaseUrl = resumeBaseUrl
		val baseUrl = if ( shouldResume && resumeBaseUrl != null ) resumeBaseUrl else API.getGateway().url
		DiscordRelay.LOGGER.debug( "Trying reconnect to '$baseUrl' (Resume: $shouldResume)..." )
		this.shouldResume = shouldResume
		try {
			open(baseUrl)
		} catch (ex: Exception) {
			DiscordRelay.LOGGER.error( "Failed to reconnect! ($ex)" )
			cleanupAfterError()
			reconnectCount++
			DiscordRelay.LOGGER.debug( "Incremented reconnection count to $reconnectCount." )
			coroutineScope.launch {
				tryReconnect(shouldResume = false, force = true)
			}
			return
		}
	}

	private fun cleanupAfterError() {
		DiscordRelay.LOGGER.debug( "Resetting state due to WebSocket error..." )

		this.webSocket = null

		this.sessionIdentifier = null
		this.resumeBaseUrl = null
		this.shouldResume = false

		this.heartbeatAcknowledgementConfirmation?.cancel()
		this.heartbeatAcknowledgementConfirmation = null

		this.heartbeatJob?.cancel()
		this.heartbeatJob = null

		this.myIdentifier = null
	}

	private inner class Listener: WebSocket.Listener {
		private val messageBuilder = StringBuilder()

		override fun onOpen( webSocket: WebSocket ) {
			DiscordRelay.LOGGER.debug( "WebSocket connection opened." )
			alreadyReconnecting = false
			webSocket.request( 1 )
		}

		override fun onClose( webSocket: WebSocket, code: Int, reason: String? ): CompletionStage<*> {
			DiscordRelay.LOGGER.debug( "WebSocket connection closed with code $code & reason '$reason'." )

			DiscordRelay.LOGGER.debug( "Cancelling background heartbeating job..." )
			heartbeatJob?.cancel()

			// DiscordRelay.LOGGER.debug( "Cancelling gateway coroutines..." )
			// coroutineScope.cancel()

			// Unknown to Not Authenticated, or Already Authenticated to Session Timed Out - https://discord.com/developers/docs/topics/opcodes-and-status-codes#gateway-gateway-close-event-codes
			val canResumeFromCloseCode = code in 4000 .. 4003 || code in 4005 .. 4009

			if ( !serverClosed ) {
				DiscordRelay.LOGGER.debug( "Reconnecting due to closure..." )
				coroutineScope.launch {
					tryReconnect( canResumeFromCloseCode )
				}
			}

			return CompletableFuture.completedFuture( null )
		}

		override fun onText( webSocket: WebSocket, data: CharSequence?, isLastMessage: Boolean ): CompletionStage<*>? {
			DiscordRelay.LOGGER.debug( "Received text chunk of ${ data?.length } character(s)." )

			messageBuilder.append( data )

			if ( isLastMessage ) {
				processMessage( webSocket, messageBuilder.toString() )
				messageBuilder.clear()
			}

			webSocket.request( 1 )

			return CompletableFuture.completedFuture( null )
		}

		override fun onError( webSocket: WebSocket, error: Throwable? ) {
			DiscordRelay.LOGGER.error( "WebSocket error: '$error'" )

			// We're in a disconnected state but none of the closing code has run, so reset everything manually
			cleanupAfterError()

			DiscordRelay.LOGGER.debug( "Reconnecting due to WebSocket error..." )
			coroutineScope.launch {
				tryReconnect( false )
			}
		}
	}
}
