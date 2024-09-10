package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.net.http.WebSocket
import java.nio.channels.UnresolvedAddressException
import java.time.Duration

object HTTP {
	private lateinit var httpClient: HttpClient

	private val defaultHttpRequestHeaders: MutableMap<String, String> = mutableMapOf(
		"Accept" to "*/*"
	)

	// For matching up request & response logs
	private var requestCounter = 0

	fun initialize( configuration: Configuration ) {
		httpClient = HttpClient.newBuilder()
			.connectTimeout( Duration.ofSeconds( configuration.http.timeoutSeconds ) )
			.build()

		defaultHttpRequestHeaders[ "User-Agent" ] = arrayOf(
			configuration.http.userAgentPrefix,
			"Discord Relay/${ Version.discordRelay() }",
			"Minecraft/${ Version.minecraft() }",
			"Java/${ Version.java() }"
		).filter { it.isNotBlank() }.joinToString( " " )
		DiscordRelay.LOGGER.debug( "HTTP User Agent: '${ defaultHttpRequestHeaders[ "User-Agent" ] }'" )

		if ( configuration.http.fromAddress.isNotBlank() ) {
			defaultHttpRequestHeaders[ "From" ] = configuration.http.fromAddress
			DiscordRelay.LOGGER.debug( "HTTP From Address: '${ defaultHttpRequestHeaders[ "From" ] }'" )
		}
	}

	suspend fun request( method: String, url: String, headers: Map<String, String>? = null, body: String? = null, parameters: Map<String, String>? = null ): HttpResponse<String> {
		if ( !::httpClient.isInitialized ) throw IllegalStateException( "HTTP client not initialized" )
		if ( body != null && headers?.containsKey( "Content-Type" ) == false ) throw IllegalArgumentException( "HTTP content type header required for body" )

		val queryString = if ( !parameters.isNullOrEmpty() ) "?" + parameters.map { "${ it.key }=${ it.value }" }.joinToString( "&" ) else ""
		val uri = URI.create( url + queryString )
		val bodyPublisher = if ( !body.isNullOrBlank() ) HttpRequest.BodyPublishers.ofString( body )
			else HttpRequest.BodyPublishers.noBody()

		val httpRequestBuilder = HttpRequest.newBuilder()
			.timeout( httpClient.connectTimeout().get() )
			.method( method, bodyPublisher )
			.uri( uri )

		defaultHttpRequestHeaders.forEach( httpRequestBuilder::header )
		headers?.forEach( httpRequestBuilder::header )

		val requestCounter = requestCounter++

		val httpRequest = httpRequestBuilder.build()
		DiscordRelay.LOGGER.debug( "HTTP Request #$requestCounter: ${ httpRequest.method() } '${ httpRequest.uri() }' '${ body.orEmpty() }' (${ httpRequest.bodyPublisher().get().contentLength() } bytes)" )

		try {
			val httpResponse = httpClient.sendAsync( httpRequest, HttpResponse.BodyHandlers.ofString() ).await()
			DiscordRelay.LOGGER.debug( "HTTP Response #$requestCounter: ${ httpResponse.statusCode() } '${ httpResponse.body() }' (${ httpResponse.body().length } bytes)" )

			return httpResponse
		} catch ( exception: HttpTimeoutException ) {
			DiscordRelay.LOGGER.error( "Timed out sending HTTP request! ($exception)" )
		} catch ( exception: ConnectException ) {
			DiscordRelay.LOGGER.error( "Failed to connect to HTTP server! ($exception)" )
		} catch ( exception: UnresolvedAddressException ) {
			DiscordRelay.LOGGER.error( "Unable to resolve HTTP server! ($exception)" )
		}

		DiscordRelay.LOGGER.debug( "Retrying HTTP request after 30 seconds..." )
		delay( 30000 )
		return request( method, url, headers, body, parameters )
	}

	suspend fun startWebSocketConnection( url: URI, timeoutSeconds: Long, listener: WebSocket.Listener ): WebSocket {
		if ( !::httpClient.isInitialized ) throw IllegalStateException( "HTTP client not initialized" )

		return httpClient.newWebSocketBuilder()
			.connectTimeout( Duration.ofSeconds( timeoutSeconds ) )
			.buildAsync( url, listener )
			.await()
	}

	object Method {
		const val Get = "GET"
		const val Post = "POST"
		const val Put = "PUT"
	}

	class HttpException(
		private val responseStatusCode: Int,
		private val requestMethod: String,
		private val requestUri: URI
	) : Exception() {
		override val message: String get() = "$requestMethod '$requestUri' -> $responseStatusCode"
	}
}
