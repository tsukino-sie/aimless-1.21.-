package com.github.noonmaru.aimless.plugin

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

object PlayerList: Runnable {

    private var update = false

    fun update() {
        update = true
    }

    override fun run() {
        if (update) {
            update = false
            updatePlayerList()
        }
    }

    private fun updatePlayerList() {
        val packet = PacketContainer(PacketType.Play.Server.PLAYER_INFO)
        val list = ArrayList<PlayerInfoData>()

        for (offlinePlayer in Bukkit.getOfflinePlayers().asSequence()) {
            val safeName = offlinePlayer.name ?: "Unknown"
            val uuid = offlinePlayer.uniqueId

            // fromOfflinePlayer -> 직접 생성자 사용 (GetId err)
            val profile = WrappedGameProfile(uuid, safeName)

            list += PlayerInfoData(
                profile,
                0,
                EnumWrappers.NativeGameMode.SURVIVAL,
                WrappedChatComponent.fromText(safeName)
            )
        }

        // EnumSet(다중 액션) 사용.
        packet.playerInfoActions.write(0, EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED
        ))

        packet.playerInfoDataLists.write(1, list)

        val pm = ProtocolLibrary.getProtocolManager()

        for (player in Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("aimless.bypass.tablist")) {
                continue
            }
            try {
                pm.sendServerPacket(player, packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}