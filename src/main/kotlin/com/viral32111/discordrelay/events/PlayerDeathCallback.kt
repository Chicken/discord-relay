package com.viral32111.discordrelay.events

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.server.level.ServerPlayer

fun interface PlayerDeathCallback {
    companion object {
        val EVENT: Event<PlayerDeathCallback> = EventFactory.createArrayBacked( PlayerDeathCallback::class.java ) { listeners ->
            PlayerDeathCallback { player, damageSource ->
                for ( listener in listeners ) {
                    listener.interact( player, damageSource )
                }
            }
        }
    }

    fun interact(player: ServerPlayer, damageSource: DamageSource)
}
