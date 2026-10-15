/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.commands

import gg.essential.api.commands.ArgumentParser
import gg.essential.api.commands.Command
import gg.essential.api.commands.CommandRegistry
import gg.essential.commands.engine.CommandParser
import gg.essential.commands.impl.*
import gg.essential.config.EssentialConfig
import gg.essential.event.network.chat.SendCommandEvent
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.sps.WorldsManager
import gg.essential.universal.ChatColor
import gg.essential.util.MinecraftUtils
import gg.essential.util.Multithreading
import me.kbrewster.eventbus.Subscribe
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object EssentialCommandRegistry : CommandRegistry {
    private val refHolder = ReferenceHolderImpl()

    internal val commands = ConcurrentHashMap<String, Pair<Command, Boolean>>()
    private val friends = CommandMcFriends()
    private val message = CommandMessage()
    private val commandSession = CommandSession
    private val invite = CommandInvite

    private val spsHostCommands = listOf(
        CommandOp,
        CommandDeOp,
        CommandKick,
    )

    init {
        //Run Async as not to freeze the client
        Multithreading.runAsync {
            // Default Essential commands
            registerCommand(CommandConfig())
            checkMiniCommands()
        }
    }

    fun checkMiniCommands() {
        if (!EssentialConfig.essentialFull) {
            unregisterCommand(friends)
            unregisterCommand(message)
            unregisterCommand(commandSession)
            unregisterCommand(invite)
        } else {
            registerCommand(friends)
            registerCommand(message)
            registerCommand(commandSession)
            registerCommand(invite)
        }
    }

    fun registerSPSHostCommandsState(worldsManager: WorldsManager) {
        val shouldBeRegistered = memo {
            worldsManager.integratedServerWorld()?.localWorldOpen() == true
        }
        effect(refHolder) {
            if (shouldBeRegistered()) {
                registerSPSHostCommands()
            } else {
                unregisterSPSHostCommands()
            }
        }
    }

    private fun registerSPSHostCommands() {
        for (spsHostCommand in spsHostCommands) {
            registerCommand(spsHostCommand)
        }
    }

    private fun unregisterSPSHostCommands() {
        for (spsHostCommand in spsHostCommands) {
            unregisterCommand(spsHostCommand)
        }
    }

    override fun unregisterCommand(command: Command) {
        commands.remove(command.name.lowercase(Locale.ENGLISH))
        command.commandAliases?.forEach {
            commands.remove(it.alias)
        }
    }

    override fun registerCommand(command: Command) {
        commands[command.name.lowercase(Locale.ENGLISH)] = command to command.hideFromAutocomplete
        command.commandAliases?.forEach { commands[it.alias] = command to it.hideFromAutocomplete } ?: return
    }

    override fun <T> registerParser(type: Class<T>, parser: ArgumentParser<T>) {
        CommandParser.registerArgumentParser(type, parser)
    }

    @Subscribe
    fun onSendCommand(event: SendCommandEvent) {
        val args = event.commandLine.trim().split(" ")
        if (args.isEmpty())
            return

        val command = commands[args[0].lowercase(Locale.ENGLISH)]?.first ?: return

        event.isCancelled = true

        if (args.size >= 2) {
            val subCommand = args[1].lowercase(Locale.ENGLISH)
            val subCommandHandler = command.subCommands[subCommand]

            if (subCommandHandler != null) {
                CommandParser.parseCommandAndCallHandler(args.drop(2), subCommandHandler, command)
                return
            }

            if (subCommand == "help" && command.autoHelpSubcommand) {
                printCommandHelpMessage(command)
                return
            }
        }

        val defaultHandler = command.defaultHandler
        if (defaultHandler == null || (args.size > 1 && defaultHandler.params.isEmpty())) {
            val subCommands = command.subCommands.keys.sorted().let {
                if (command.autoHelpSubcommand) listOf("help") + it else it
            }.joinToString(separator = "|")
            MinecraftUtils.sendMessage("", "${ChatColor.RED}Usage: /${command.name} <$subCommands>")
            return
        }

        CommandParser.parseCommandAndCallHandler(args.drop(1), defaultHandler, command)
    }

    fun getCompletionOptions(commandString: String): Array<String> {
        val availableCompletions = mutableListOf<String>()

        if (commandString[0] != '/')
            return availableCompletions.toTypedArray()

        val args = commandString.drop(1).split(" ")
        if (args.isEmpty())
            return availableCompletions.toTypedArray()

        if (args.size == 1) {
            availableCompletions.addAll(commands.keys.sorted().filter {
                it.startsWith(args[0]) && !(commands[it]?.second ?: true)
            }.map { "/$it" })
            return availableCompletions.toTypedArray()
        }

        val command = commands[args[0].lowercase(Locale.ENGLISH)]?.first ?: return availableCompletions.toTypedArray()

        command.defaultHandler?.let {
            availableCompletions.addAll(CommandParser.getCompletionOptions(args.drop(1), it))
        }

        if (args.size >= 2) {
            val subCommand = args[1].lowercase(Locale.ENGLISH)

            if (args.size == 2 && command.subCommands.isNotEmpty()) {
                availableCompletions.addAll(command.subCommands.keys.filter { it.startsWith(subCommand) })

                availableCompletions.sort()
            }

            val subCommandHandler = command.subCommands[subCommand]

            if (subCommandHandler != null) {
                availableCompletions.addAll(
                    CommandParser.getCompletionOptions(
                        args.drop(2),
                        subCommandHandler
                    )
                )
            }
        }

        if (command.autoHelpSubcommand && "help".startsWith(args[1].lowercase()))
            availableCompletions.add("help")

        return availableCompletions.toTypedArray()
    }

    private fun printCommandHelpMessage(command: Command) {
        MinecraftUtils.sendMessage("", "${ChatColor.AQUA}Usage for /${command.name}:")
        command.uniqueSubCommands.forEach { (handler, ann) ->
            val usage = CommandParser.getHandlerUsage(command, handler)
            val description = ann.description

            if (description != "")
                MinecraftUtils.sendMessage(
                    "",
                    "${ChatColor.RED}$usage ${ChatColor.GRAY}- ${ChatColor.ITALIC}$description"
                )
            else
                MinecraftUtils.sendMessage("", "${ChatColor.RED}$usage")
        }
    }
}
