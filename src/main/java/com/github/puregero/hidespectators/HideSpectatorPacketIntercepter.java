package com.github.puregero.hidespectators;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

public class HideSpectatorPacketIntercepter extends ChannelDuplexHandler {

    private final HideSpectators plugin;
    private final Player player;
    private final Set<Integer> shownPlayers = new HashSet<>();
    private final Set<Integer> hiddenPlayers = new HashSet<>();

    public HideSpectatorPacketIntercepter(HideSpectators plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    private boolean filterPacket(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ClientboundAddPlayerPacket addPlayerPacket) {
            shownPlayers.add(addPlayerPacket.getEntityId());
            Player shownPlayer = plugin.getServer().getPlayer(addPlayerPacket.getPlayerId());
            if (shownPlayer != null && shownPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                // Don't show spectators
                hiddenPlayers.add(addPlayerPacket.getEntityId());
                return false;
            }
        }

        if (msg instanceof ClientboundPlayerInfoUpdatePacket playerInfoUpdatePacket) {
            boolean updatingGamemode = playerInfoUpdatePacket.actions().stream().anyMatch(action -> action == ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
            if (updatingGamemode) {
                playerInfoUpdatePacket.entries().forEach(entry -> {
                    Player shownPlayer = plugin.getServer().getPlayer(entry.profileId());
                    if (shownPlayer != null && entry.gameMode() == GameType.SPECTATOR && shownPlayers.contains(shownPlayer.getEntityId())) {
                        // Hide new spectator
                        hiddenPlayers.add(shownPlayer.getEntityId());
                        ctx.write(new ClientboundRemoveEntitiesPacket(shownPlayer.getEntityId()));
                    }
                    if (shownPlayer != null && entry.gameMode() != GameType.SPECTATOR && hiddenPlayers.contains(shownPlayer.getEntityId())) {
                        // Show new non-spectator
                        hiddenPlayers.remove(shownPlayer.getEntityId());
                        Optional.ofNullable(((CraftPlayer) shownPlayer).getHandle().tracker)
                                .ifPresent(tracker -> tracker.serverEntity.sendPairingData(((CraftPlayer) player).getHandle(), ctx::write));
                    }
                });
            }
        }

        if (msg instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
            removeEntitiesPacket.getEntityIds().forEach(entityId -> {
                shownPlayers.remove(entityId);
                hiddenPlayers.remove(entityId);
            });
        }

        return true;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ClientboundBundlePacket bundlePacket) {
            Set<Object> packetsToRemove = new HashSet<>();
            bundlePacket.subPackets().forEach(subPacket -> {
                if (!filterPacket(ctx, subPacket)) {
                    packetsToRemove.add(subPacket);
                }
            });

            if (!packetsToRemove.isEmpty()) {
                msg = new ClientboundBundlePacket(StreamSupport.stream(bundlePacket.subPackets().spliterator(), false).filter(subPacket -> !packetsToRemove.contains(subPacket)).toList());
            }
        } else if (!filterPacket(ctx, msg)) {
            return;
        }

        super.write(ctx, msg, promise);
    }
}
