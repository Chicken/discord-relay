package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.events.PlayerJoinCallback;
import com.viral32111.discordrelay.events.PlayerLeaveCallback;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin( PlayerManager.class )
public class PlayerManagerMixin {

    @Inject( method = "onPlayerConnect", at = @At( "TAIL" )  )
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci ) {
        PlayerJoinCallback.Companion.getEVENT().invoker().interact( player );
    }

    @Inject( method = "remove", at = @At( "TAIL" ) )
    private void remove( ServerPlayerEntity player, CallbackInfo callbackInfo ) {
        PlayerLeaveCallback.Companion.getEVENT().invoker().interact( player );
    }

}
