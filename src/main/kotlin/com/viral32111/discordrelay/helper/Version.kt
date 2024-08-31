package com.viral32111.discordrelay.helper

import com.viral32111.discordrelay.DiscordRelay
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.MinecraftVersion

object Version {

	private fun byModIdentifier( modIdentifier: String = DiscordRelay.MOD_ID, fabricLoader: FabricLoader = FabricLoader.getInstance() ) =
		fabricLoader.getModContainer( modIdentifier ).orElseThrow {
			throw IllegalStateException( "Mod container for ID '${ modIdentifier }' not found" )
		}.metadata.version.friendlyString

	fun java(): String = System.getProperty( "java.version" )

	fun minecraft(): String = MinecraftVersion.CURRENT.id

	fun discordRelay(): String = byModIdentifier( "discordrelay" )
}
