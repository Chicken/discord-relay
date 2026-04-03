package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.events.PlayerJoinCallback;
import com.viral32111.discordrelay.events.PlayerLeaveCallback;
import net.minecraft.network.Connection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin( PlayerList.class )
public class PlayerListMixin {

    @Inject( method = "placeNewPlayer", at = @At( "TAIL" )  )
    private void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci ) {
        PlayerJoinCallback.Companion.getEVENT().invoker().interact( player );
    }

    @Inject( method = "remove", at = @At( "TAIL" ) )
    private void remove(ServerPlayer player, CallbackInfo callbackInfo ) {
        PlayerLeaveCallback.Companion.getEVENT().invoker().interact( player );
    }

}
