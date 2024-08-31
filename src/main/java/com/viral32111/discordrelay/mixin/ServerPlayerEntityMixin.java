package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.events.PlayerDeathCallback;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin( ServerPlayerEntity.class )
public class ServerPlayerEntityMixin {

    @Inject( method = "onDeath", at = @At( "TAIL" ) )
    private void onDeath( DamageSource damageSource, CallbackInfo info ) {
        ServerPlayerEntity player = ( ServerPlayerEntity ) ( Object ) this;
        PlayerDeathCallback.Companion.getEVENT().invoker().interact( player, damageSource );
    }

}
