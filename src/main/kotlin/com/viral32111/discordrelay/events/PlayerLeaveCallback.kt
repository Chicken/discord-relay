package com.viral32111.discordrelay.events

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.network.ServerPlayerEntity

fun interface PlayerLeaveCallback {
    companion object {
        val EVENT: Event<PlayerLeaveCallback> = EventFactory.createArrayBacked( PlayerLeaveCallback::class.java ) { listeners ->
            PlayerLeaveCallback { player ->
                for ( listener in listeners ) {
                    listener.interact( player )
                }
            }
        }
    }

    fun interact( player: ServerPlayerEntity )
}
