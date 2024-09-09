package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.*
import com.viral32111.discordrelay.discord.data.Gateway
import kotlinx.coroutines.time.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.lang.RuntimeException
import java.time.Duration
import kotlin.jvm.optionals.getOrElse
import kotlin.math.roundToLong

object API {
	private lateinit var apiBaseUrl: String

	private val defaultHttpRequestHeaders: MutableMap<String, String> = mutableMapOf(
		"Accept" to "application/json; */*"
	)

	fun initialize( configuration: Configuration ) {
		apiBaseUrl = "${ configuration.discord.api.baseUrl }/v${ configuration.discord.api.version }"
		DiscordRelay.LOGGER.debug( "Discord API Base URL: '$apiBaseUrl'" )

		defaultHttpRequestHeaders[ "Authorization" ] = "Bot ${ configuration.discord.application.token }"
	}

	private suspend fun request(method: String, endpoint: String, payload: JsonElement? = null, retryDepth: Int = 0 ): JsonElement {
		if ( retryDepth > 0 ) DiscordRelay.LOGGER.debug( "Attempt #$retryDepth of retrying $method '$endpoint'..." )

		val httpRequestHeaders = defaultHttpRequestHeaders.toMutableMap() // Creates a copy
		if ( payload != null ) httpRequestHeaders[ "Content-Type" ] = "application/json; charset=utf-8"

		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint", httpRequestHeaders, body = if ( payload != null ) JSON.encodeToString( payload ) else null )

		val httpResponseStatusCode = httpResponse.statusCode()
		val httpResponseHeaders = httpResponse.headers()

		val rateLimitRequestLimit = httpResponseHeaders.firstValue( "X-RateLimit-Limit" ).getOrElse { null }?.toLongOrNull()
		val rateLimitRemainingRequests = httpResponseHeaders.firstValue( "X-RateLimit-Remaining" ).getOrElse { null }?.toLongOrNull()
		val rateLimitResetTimestamp = httpResponseHeaders.firstValue( "X-RateLimit-Reset" ).getOrElse { null }?.toDoubleOrNull()
		val rateLimitResetAfterSeconds = httpResponseHeaders.firstValue( "X-RateLimit-Reset-After" ).getOrElse { null }?.toDoubleOrNull()
		val rateLimitBucketIdentifier = httpResponseHeaders.firstValue( "X-RateLimit-Bucket" ).getOrElse { null }
		DiscordRelay.LOGGER.debug( "$rateLimitRemainingRequests of $rateLimitRequestLimit request(s) remaining for $method '$endpoint' ($rateLimitBucketIdentifier), resets after $rateLimitResetAfterSeconds second(s) or at $rateLimitResetTimestamp." )

		if ( httpResponseStatusCode == 429 ) {
			val rateLimitIsGlobal = httpResponseHeaders.firstValue( "X-RateLimit-Global" ).getOrElse { null } == "1"
			val rateLimitScopeName = httpResponseHeaders.firstValue( "X-RateLimit-Scope" ).getOrElse { null }
			val rateLimit = JSON.decodeFromString<RateLimit>( httpResponse.body() )

			DiscordRelay.LOGGER.warn( "Hit ${ if ( rateLimitIsGlobal || rateLimit.isGlobal ) "global" else "route" } rate limit for $method '$endpoint' (Bucket: '$rateLimitBucketIdentifier', Scope: '$rateLimitScopeName') with $rateLimitRemainingRequests of $rateLimitRequestLimit remaining request(s)! Retrying in ${ rateLimit.retryAfter } second(s)..." )

			if ( retryDepth > 3 ) throw RuntimeException( "Attempted retry Discord API request $method '$endpoint' too many times" )

			// Wait & retry
			delay( Duration.ofSeconds( rateLimit.retryAfter.roundToLong() ) )
			return request( method, endpoint, payload, retryDepth + 1 )
		}

		if ( httpResponseStatusCode < 200 || httpResponseStatusCode >= 300 ) throw HTTP.HttpException( httpResponseStatusCode, httpResponse.request().method(), httpResponse.request().uri() )

		return if ( httpResponse.statusCode() == 204 ) JSON.encodeToJsonElement( "" ) else JSON.decodeFromString( httpResponse.body() )
	}

	// https://discord.com/developers/docs/topics/gateway#get-gateway-bot
	suspend fun getGateway(): Gateway = JSON.decodeFromJsonElement( request(
		method = HTTP.Method.Get,
		endpoint = "gateway/bot"
	) )

	// https://discord.com/developers/docs/resources/webhook#execute-webhook
	private suspend fun sendWebhookText( identifier: String, token: String, shouldWait: Boolean, threadId: String?, builderBlock: WebhookMessageBuilder.() -> Unit ): JsonElement = request(
		method = HTTP.Method.Post,
		endpoint = "webhooks/$identifier/$token?wait=$shouldWait${ if ( threadId != null ) "&thread_id=$threadId" else "" }",
		payload = JSON.encodeToJsonElement( WebhookMessageBuilder().apply( builderBlock ).apply { preventMentions() }.build() ) as JsonObject
	)
	suspend fun sendWebhookText( identifier: String, token: String, threadId: String?, builderBlock: WebhookMessageBuilder.() -> Unit ): Message =
		JSON.decodeFromJsonElement( sendWebhookText( identifier, token, true, threadId, builderBlock ) )

	private suspend fun sendWebhookEmbed( identifier: String, token: String, shouldWait: Boolean, threadId: String?, embed: Embed ): JsonElement = request(
		method = HTTP.Method.Post,
		endpoint = "webhooks/$identifier/$token?wait=$shouldWait${ if ( threadId != null ) "&thread_id=$threadId" else "" }",
		payload = JSON.encodeToJsonElement( createWebhookMessage {
			preventMentions()
			embeds = listOf( embed )
		} ) as JsonObject
	)

	suspend fun sendWebhookEmbed( identifier: String, token: String, threadId: String?, builderBlock: EmbedBuilder.() -> Unit ): Message =
		JSON.decodeFromJsonElement( sendWebhookEmbed( identifier, token, true, threadId, EmbedBuilder().apply( builderBlock ).build() ) )
	suspend fun sendWebhookEmbedWithoutWaiting( identifier: String, token: String, threadId: String?, builderBlock: EmbedBuilder.() -> Unit )
		{ sendWebhookEmbed( identifier, token, false, threadId, EmbedBuilder().apply( builderBlock ).build() ) }

	// https://discord.com/developers/docs/interactions/application-commands#bulk-overwrite-global-application-commands
	suspend fun registerSlashCommands( identifier: String, payloadString: String): JsonElement {
		val payload = Json.parseToJsonElement(payloadString.trimIndent()) as JsonArray

		for (jsonElement in payload) {
			DiscordRelay.LOGGER.info("Registered slash command '{}'", (jsonElement as JsonObject)["name"]?.jsonPrimitive?.content)
		}

		return request(
			method = HTTP.Method.Put,
			endpoint = "applications/$identifier/commands",
			payload = payload
		)
	}

	// https://discord.com/developers/docs/interactions/receiving-and-responding#responding-to-an-interaction
	suspend fun respondToInteraction( identifier: String, token: String, payloadString: String): JsonElement {
		val payload = Json.parseToJsonElement(payloadString.trimIndent()) as JsonObject

		return request(
			method = HTTP.Method.Post,
			endpoint = "interactions/$identifier/$token/callback",
			payload = payload
		)
	}
}
