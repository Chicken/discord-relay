package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.Gateway
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

@Suppress( "UNUSED" )
class DiscordRelay: DedicatedServerModInitializer {
	private val coroutineScope = CoroutineScope( Dispatchers.IO )
	private var configuration = Configuration()

	companion object {
		const val MOD_ID = "discordrelay"
		val LOGGER: Logger = LoggerFactory.getLogger( "com.viral32111.$MOD_ID" )

		const val CONFIGURATION_FILE_NAME = "$MOD_ID.json"
	}

	override fun onInitializeServer() {
		LOGGER.info( "Discord Relay v${ Version.discordRelay() } initialized on the server." )

		configuration = loadConfigurationFile()

		HTTP.initialize( configuration )
		API.initialize( configuration )

		if (
			configuration.discord.application.token.isNotBlank()
			&& configuration.discord.relay.webhook.token.isNotBlank()
			&& configuration.discord.relay.webhook.identifier.isNotBlank()
			) {
			registerCallbackListeners( coroutineScope, configuration )

			ServerLifecycleEvents.SERVER_STARTED.register { server ->
				val gateway = Gateway( configuration, server.playerManager )

				coroutineScope.launch {
					val gatewayUrl = API.getGateway().url
					LOGGER.debug( "Discord Gateway URL: '$gatewayUrl'" )
					gateway.open( gatewayUrl )
				}

				ServerLifecycleEvents.SERVER_STOPPING.register {
					coroutineScope.launch {
						LOGGER.debug( "Closing Discord Gateway connection..." )
						gateway.close( WebSocketCloseCode.Normal, "Server stopping.", true )
					}
				}
			}
		}

		ServerLifecycleEvents.SERVER_STOPPED.register {
			coroutineScope.cancel()
		}
	}

	private fun loadConfigurationFile(): Configuration {
		val serverConfigurationDirectory = FabricLoader.getInstance().configDir
		val configurationFile = serverConfigurationDirectory.resolve( CONFIGURATION_FILE_NAME )

		if ( serverConfigurationDirectory.notExists() ) {
			serverConfigurationDirectory.createDirectory()
			LOGGER.debug("Created directory '{}' for configuration files.", serverConfigurationDirectory)
		}

		if ( configurationFile.notExists() ) {
			val configAsJSON = PrettyJSON.encodeToString( Configuration() )

			configurationFile.writeText( configAsJSON, options = arrayOf(
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
			) )

			LOGGER.debug("Created configuration file '{}'.", configurationFile)
		}

		val configAsJSON = configurationFile.readText()
		val config = PrettyJSON.decodeFromString<Configuration>( configAsJSON )
		LOGGER.debug("Loaded configuration from file '{}'", configurationFile)

		return config
	}
}
