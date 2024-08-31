package com.viral32111.discordrelay.events

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.advancement.Advancement
import net.minecraft.server.network.ServerPlayerEntity

fun interface PlayerCompleteAdvancementCallback {
    companion object {
        val EVENT: Event<PlayerCompleteAdvancementCallback> = EventFactory.createArrayBacked( PlayerCompleteAdvancementCallback::class.java ) { listeners ->
            PlayerCompleteAdvancementCallback { player, advancement, shouldAnnounceToChat ->
                for ( listener in listeners ) {
                    listener.interact( player, advancement, shouldAnnounceToChat )
                }
            }
        }
    }

    fun interact( player: ServerPlayerEntity, advancement: Advancement, shouldAnnounceToChat: Boolean )
}
