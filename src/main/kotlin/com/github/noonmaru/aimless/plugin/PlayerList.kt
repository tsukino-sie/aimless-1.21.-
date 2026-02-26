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

    private fun getFakeUuid(realUuid: UUID): UUID {
        return UUID.nameUUIDFromBytes(("fake_$realUuid").toByteArray())
    }

    fun registerInterceptor(plugin: JavaPlugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
            object : PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_INFO) {
                override fun onPacketSending(event: PacketEvent) {
                    if (event.player.hasPermission("aimless.bypass.tablist")) return

                    val packet = event.packet.deepClone()
                    event.packet = packet

                    val actions = packet.playerInfoActions.read(0)
                    val newActions = EnumSet.copyOf(actions)
                    newActions.add(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME)
                    packet.playerInfoActions.write(0, newActions)

                    val dataList = packet.playerInfoDataLists.read(1)

                    val newList = dataList.map { data ->
                        val isRealOnlinePlayer = Bukkit.getPlayer(data.profileId) != null

                        val isSelf = data.profileId == event.player.uniqueId

                        PlayerInfoData(
                            data.profileId,
                            data.latency,
                            if (isSelf) data.isListed else if (isRealOnlinePlayer) false else data.isListed,
                            data.gameMode,
                            data.profile,
                            data.displayName
                        )
                    }
                    packet.playerInfoDataLists.write(1, newList)
                }
            }
        )
    }

    override fun run() {
        val baseList = ArrayList<PlayerInfoData>()

        for (offlinePlayer in Bukkit.getOfflinePlayers()) {
            val realUuid = offlinePlayer.uniqueId
            val maskedName = (offlinePlayer.name ?: "Unknown").removeLang()

            val fakeUuid = getFakeUuid(realUuid)
            val fakeProfile = WrappedGameProfile(fakeUuid, maskedName)

            baseList += PlayerInfoData(
                fakeUuid,
                0,
                true,
                EnumWrappers.NativeGameMode.SURVIVAL,
                fakeProfile,
                WrappedChatComponent.fromJson("{\"text\":\"$maskedName\"}")
            )
        }

        if (baseList.isEmpty()) return

        val pm = ProtocolLibrary.getProtocolManager()

        for (player in Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("aimless.bypass.tablist")) continue

            //[핵심 2] 방금 만든 가짜 프로필 리스트에서 "나 자신의 가짜 프로필"만 쏙 뺍니다!
            val myFakeUuid = getFakeUuid(player.uniqueId)
            val personalizedList = baseList.filter { it.profileId != myFakeUuid }

            val addPacket = PacketContainer(PacketType.Play.Server.PLAYER_INFO)
            addPacket.playerInfoActions.write(0, EnumSet.of(
                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
                EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
            ))
            addPacket.playerInfoDataLists.write(1, personalizedList)

            try {
                pm.sendServerPacket(player, addPacket)
            } catch (e: Exception) {}
        }
    }
}