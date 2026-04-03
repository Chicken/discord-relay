package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.events.PlayerDeathCallback;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin( ServerPlayer.class )
public class ServerPlayerMixin {

    @Inject( method = "die", at = @At( "TAIL" ) )
    private void onDeath(DamageSource damageSource, CallbackInfo info ) {
        ServerPlayer player = (ServerPlayer) ( Object ) this;
        PlayerDeathCallback.Companion.getEVENT().invoker().interact( player, damageSource );
    }

}
