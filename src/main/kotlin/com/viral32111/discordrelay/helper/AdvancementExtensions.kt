package com.viral32111.discordrelay.helper

import net.minecraft.advancement.Advancement
import net.minecraft.advancement.AdvancementFrame

fun AdvancementFrame?.getText(): String? =
    when ( this ) {
        AdvancementFrame.TASK -> "has made the advancement"
        AdvancementFrame.CHALLENGE -> "completed the challenge"
        AdvancementFrame.GOAL -> "reached the goal"
        else -> null
    }

fun AdvancementFrame?.getColor(): Int =
    when ( this ) {
        AdvancementFrame.CHALLENGE -> 0xA700A7 // Challenge Purple
        else -> 0x54FB54 // Advancement Green
    }

fun Advancement.getText(): String? = display.map { it.frame.getText() }.orElse(null)

fun Advancement.getColor(): Int? = display.map { it.frame.getColor() }.orElse(null)
