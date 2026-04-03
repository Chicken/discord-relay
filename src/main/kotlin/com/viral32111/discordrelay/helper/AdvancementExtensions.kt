package com.viral32111.discordrelay.helper

import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementType

fun AdvancementType?.getText(): String? =
    when ( this ) {
        AdvancementType.TASK -> "has made the advancement"
        AdvancementType.CHALLENGE -> "completed the challenge"
        AdvancementType.GOAL -> "reached the goal"
        else -> null
    }

fun AdvancementType?.getColor(): Int =
    when ( this ) {
        AdvancementType.CHALLENGE -> 0xA700A7 // Challenge Purple
        else -> 0x54FB54 // Advancement Green
    }

fun Advancement.getText(): String? = display.map { it.type.getText() }.orElse(null)

fun Advancement.getColor(): Int? = display.map { it.type.getColor() }.orElse(null)
