package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
data class SlashCommand(
    @Required var name: String,
    var description: String? = null,
    // We only need string options
    var options: List<StringOption>? = null,
    @Required val type: Int = 1 // = chat input application command (a slash command)
)

@Serializable
data class StringOption(
    @Required var name: String,
    @Required var description: String,
    var required: Boolean,
    @Required var type: Int = 3 // = string option
)

class SlashCommandBuilder {
    var name: String = "";
    var description: String = "";
    private var options: List<StringOption>? = null;

    fun options(block: OptionsBuilder.() -> Unit) = apply {
        options = OptionsBuilder().apply(block).build()
    }

    fun build() = SlashCommand(name, description, options)
}

class OptionsBuilder {
    private val options = mutableListOf<StringOption>()

    fun stringOption(block: StringOptionBuilder.() -> Unit) {
        options.add(StringOptionBuilder().apply(block).build())
    }

    fun build(): List<StringOption> = options
}

class StringOptionBuilder {
    var name: String = "";
    var description: String = "";
    var required: Boolean = false;

    fun build() = StringOption(name, description, required)
}

class SlashCommandsBuilder {
    private val commands = mutableListOf<SlashCommand>()

    fun command(block: SlashCommandBuilder.() -> Unit) {
        commands.add(SlashCommandBuilder().apply(block).build())
    }

    fun build(): List<SlashCommand> = commands
}
