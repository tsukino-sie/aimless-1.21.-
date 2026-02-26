package com.github.noonmaru.aimless.plugin

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

object PlayerList : Runnable {

    fun registerInterceptor(plugin: JavaPlugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
            object : PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.PLAYER_INFO) {
                override fun onPacketSending(event: PacketEvent) {
                    if (event.player.hasPermission("aimless.bypass.tablist")) return

                    val packet = event.packet
                    val actions = packet.playerInfoActions.read(0)

                    val newActions = EnumSet.copyOf(actions)
                    newActions.add(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME)
                    packet.playerInfoActions.write(0, newActions)

                    val dataList = packet.playerInfoDataLists.read(1)
                    val newList = dataList.map { data ->
                        val uuid = data.profileId
                        val originalName = data.profile?.name ?: "Unknown"
                        val maskedName = originalName.removeLang()

                        val fakeProfile = WrappedGameProfile(uuid, maskedName)

                        PlayerInfoData(
                            uuid,
                            data.latency,
                            data.isListed,
                            data.gameMode,
                            fakeProfile,
                            WrappedChatComponent.fromJson("{\"text\":\"$maskedName\"}")
                        )
                    }
                    packet.playerInfoDataLists.write(1, newList)
                }
            }
        )
    }


    override fun run() {
        val addList = ArrayList<PlayerInfoData>()

        for (offlinePlayer in Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.isOnline) continue

            val uuid = offlinePlayer.uniqueId
            val maskedName = (offlinePlayer.name ?: "Unknown").removeLang()
            val fakeProfile = WrappedGameProfile(uuid, maskedName)

            addList += PlayerInfoData(
                uuid,
                0, // 핑 0
                true,
                EnumWrappers.NativeGameMode.SURVIVAL,
                fakeProfile,
                WrappedChatComponent.fromJson("{\"text\":\"$maskedName\"}")
            )
        }

        if (addList.isEmpty()) return

        val addPacket = PacketContainer(PacketType.Play.Server.PLAYER_INFO)
        addPacket.playerInfoActions.write(0, EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
        ))
        addPacket.playerInfoDataLists.write(1, addList)

        val pm = ProtocolLibrary.getProtocolManager()
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("aimless.bypass.tablist")) continue
            try {
                pm.sendServerPacket(player, addPacket)
            } catch (e: Exception) {}
        }
    }
}