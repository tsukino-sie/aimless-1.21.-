package com.github.noonmaru.aimless.plugin

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.event.server.TabCompleteEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.NumberConversions
import java.util.*
import kotlin.random.Random.Default.nextInt

class EventListener : Listener {
    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        if (event.result == PlayerLoginEvent.Result.KICK_FULL)
            event.allow()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // null 대입 대신 함수 호출
        event.joinMessage(null)

        PlayerList.run()

        val player = event.player
        val plugin = JavaPlugin.getPlugin(AimlessPlugin::class.java)

        if (!event.player.hasPermission("aimless.bypass.tablist")){
            player.sendPlayerListHeader(
                Component.text("탭이 비활성화 되었어요")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
            )
        }

        // VoxelMap (legacy) 차단
        player.sendMessage("§3 §6 §3 §6 §3 §6 §e")

        // JourneyMap 차단 패킷 전송 (기능 비활성화 요청)
        // 레이더, 텔레포트, 동굴 지도 등 비활성화 요청
        val jmPayload = "{\"radar\":{\"enabled\":false},\"teleport\":{\"enabled\":false},\"cave\":{\"enabled\":false}}"
        player.sendPluginMessage(plugin, "journeymap:common_network", jmPayload.toByteArray(Charsets.UTF_8))

        // Xaero's Minimap 차단 패킷 전송 (레벨 ID 설정)
        player.sendPluginMessage(plugin, "xaero:minimap", byteArrayOf(0))

        if (!player.hasPlayedBefore()) {
            player.teleport(getSpawnLocation(player.name))
        }

        player.compassTarget = Bukkit.getWorlds().first().spawnLocation
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // null 대입 대신 함수 호출
        event.quitMessage(null)

    }

    @EventHandler(ignoreCancelled = true)
    fun onTabComplete(event: TabCompleteEvent) {
        if (event.sender.hasPermission("aimless.bypass.tabcomplete")) return
        event.isCancelled = true
    }

    @EventHandler
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        if (event.player.hasPermission("aimless.bypass.command")) return
        event.isCancelled = true

        val message = event.message.removePrefix("/")
        Emote.emoteBy(message)?.invoke(event.player.location)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // String -> Component
        event.deathMessage(Component.text("사람이 죽었다.").color(NamedTextColor.RED))
    }

    @EventHandler(ignoreCancelled = true)
    fun onAsyncPlayerChat(event: AsyncPlayerChatEvent) {
        if (event.player.hasPermission("aimless.bypass.use-chat")) return
        event.isCancelled = true

        val message = event.message
        val emote = Emote.emoteBy(message)

        if (emote != null) {
            emote.invoke(event.player.location)

            // TextComponent(Bungee) -> Component(Adventure)
            val component = Component.text("[$message]")
                .color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/$message"))

            event.player.sendMessage(component)
        }
    }

    @EventHandler
    fun onPaperServerListPing(event: PaperServerListPingEvent) {
        val c = Calendar.getInstance()

        event.numPlayers = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
        event.maxPlayers = c.get(Calendar.HOUR) * 10000 + c.get(Calendar.MINUTE) * 100 + c.get(Calendar.SECOND)

        // MOTD (RGB)
        val randomColor = TextColor.color(nextInt(0xFFFFFF))
        event.motd(
            Component.text("AIMLESS SERVER 2026")
                .color(randomColor)
                .decorate(TextDecoration.BOLD)
        )

        // getPlayerSample() -> 1.21
        event.listedPlayers.clear()
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerSign(event: SignChangeEvent) {
        val block = event.block
        val x = block.x
        val z = block.z

        if (!(x in -16..15 && z in -16..15)) {
            // 표지판의 Component 내용을 String으로 변환 후 처리
            for (i in 0 until 4) {
                val lineComponent = event.line(i) ?: continue
                val plainText = PlainTextComponentSerializer.plainText().serialize(lineComponent)

                event.line(i, Component.text(plainText.removeLang()))
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerBookEdit(event: PlayerEditBookEvent) {
        val loc = event.player.location
        val x = loc.blockX
        val z = loc.blockZ

        if (!(x in -16..15 && z in -16..15)) {
            val meta = event.newBookMeta

            // 책 페이지 처리 (Component 변환)
            val newPages = meta.pages().map { pageComponent ->
                val plainText = PlainTextComponentSerializer.plainText().serialize(pageComponent)
                Component.text(plainText.removeLang())
            }
            meta.pages(newPages)

            meta.title()?.let { titleComponent ->
                val plainTitle = PlainTextComponentSerializer.plainText().serialize(titleComponent)
                meta.title(Component.text(plainTitle.removeLang()))
            }

            event.newBookMeta = meta
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (event.isBedSpawn || event.isAnchorSpawn) return

        event.respawnLocation = getSpawnLocation(event.player.name)
    }

    private fun getSpawnLocation(name: String): Location {
        val seed = name.hashCode()
        // Kotlin의 Random과 Java의 Random 혼동 방지를 위해 명시
        val random = Random(seed.toLong() xor 0x19940423)
        val world = Bukkit.getWorlds().first()
        val border = world.worldBorder
        val size = border.size / 2.0

        val x = random.nextDouble() * size - size / 2.0
        val z = random.nextDouble() * size - size / 2.0
        val block = world.getHighestBlockAt(NumberConversions.floor(x), NumberConversions.floor(z))

        return block.location.add(0.5, 1.0, 0.5)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerKick(event: PlayerKickEvent) {
        event.isCancelled = true
    }
}

fun String.removeLang(): String {
    return this.replace("([a-zA-Z])|([ㄱ-힣])".toRegex(), "?")
}
fun String.removeText(): String {
    return this.replace("([a-zA-Z])|([ㄱ-힣])|([0-9])|_".toRegex(), "?")
}