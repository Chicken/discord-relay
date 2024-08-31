package com.viral32111.discordrelay.events

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.network.ServerPlayerEntity

fun interface PlayerJoinCallback {
    companion object {
        val EVENT: Event<PlayerJoinCallback> = EventFactory.createArrayBacked( PlayerJoinCallback::class.java ) { listeners ->
            PlayerJoinCallback { player ->
                for ( listener in listeners ) {
                    listener.interact( player )
                }
            }
        }
    }

    fun interact( player: ServerPlayerEntity )
}
