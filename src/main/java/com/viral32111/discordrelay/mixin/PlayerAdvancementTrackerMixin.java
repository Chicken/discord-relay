package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.events.PlayerCompleteAdvancementCallback;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( PlayerAdvancementTracker.class )
public class PlayerAdvancementTrackerMixin {

    @Shadow
    private ServerPlayerEntity owner;

    @SuppressWarnings( "SameReturnValue" )
    @Shadow public AdvancementProgress getProgress( AdvancementEntry advancement ) { return null; }

    @Inject( method = "grantCriterion", at = @At( "RETURN" ) )
    private void grantCriterion( AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> info ) {
        if ( !this.getProgress( advancement ).isDone() ) return;
        var display = advancement.value().display();
        boolean shouldAnnounceToChat =
                display.isPresent()
                && display.map(AdvancementDisplay::shouldAnnounceToChat).orElse(false)
                && owner.getWorld().getGameRules().getBoolean( GameRules.ANNOUNCE_ADVANCEMENTS );
        PlayerCompleteAdvancementCallback.Companion.getEVENT().invoker().interact( owner, advancement.value(), shouldAnnounceToChat );
    }

}
