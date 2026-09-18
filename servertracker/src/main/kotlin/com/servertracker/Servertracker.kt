package com.servertracker

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Servertracker : ModInitializer {
	const val MOD_ID: String = "servertracker"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)
	private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
	private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
	private val logLock = Any()
	private val logDirectory: Path =
		FabricLoader.getInstance().configDir.resolve(MOD_ID).resolve("chat")

	override fun onInitialize() {
		ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
			writeEntry("CHAT", sender.scoreboardName, message.signedContent())
		}

		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			writeEntry("JOIN", handler.player.scoreboardName)
		}

		ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
			writeEntry("LEAVE", handler.player.scoreboardName)
		}

		LOGGER.info("ServerTracker is recording player chat, joins, leaves, and commands in {}", logDirectory)
	}

	@JvmStatic
	fun logCommand(player: ServerPlayer, command: String) {
		val normalized = command.removePrefix("/")
		writeEntry("COMMAND", player.scoreboardName, "/$normalized")
	}

	private fun writeEntry(type: String, playerName: String, detail: String? = null) {
		val date = LocalDate.now()
		val time = LocalTime.now().format(TIME_FORMAT)
		val safeName = singleLine(playerName)
		val safeDetail = detail?.let(::singleLine)
		val line = buildString {
			append('[').append(time).append("] [").append(type).append("] ").append(safeName)
			if (safeDetail != null) {
				append(": ").append(safeDetail)
			}
			append(System.lineSeparator())
		}
		val file = logDirectory.resolve("chatlog-${date.format(DATE_FORMAT)}.log")

		synchronized(logLock) {
			try {
				Files.createDirectories(logDirectory)
				Files.writeString(
					file,
					line,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND,
				)
			} catch (exception: Exception) {
				LOGGER.error("Could not write ServerTracker entry to {}", file, exception)
			}
		}
	}

	private fun singleLine(value: String): String =
		value.replace('\r', ' ').replace('\n', ' ')
}
