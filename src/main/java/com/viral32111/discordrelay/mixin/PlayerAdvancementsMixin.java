package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.events.PlayerCompleteAdvancementCallback;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( PlayerAdvancements.class )
public class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @SuppressWarnings( "SameReturnValue" )
    @Shadow public AdvancementProgress getOrStartProgress(AdvancementHolder advancement ) { return null; }

    @Inject( method = "award", at = @At( "RETURN" ) )
    private void grantCriterion(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> info ) {
        if ( !this.getOrStartProgress( advancement ).isDone() ) return;
        var display = advancement.value().display();
        boolean shouldAnnounceToChat =
                display.isPresent()
                && display.map(DisplayInfo::shouldAnnounceChat).orElse(false)
                && player.level().getGameRules().get( GameRules.SHOW_ADVANCEMENT_MESSAGES);
        PlayerCompleteAdvancementCallback.Companion.getEVENT().invoker().interact(player, advancement.value(), shouldAnnounceToChat );
    }

}
