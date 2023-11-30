package com.github.puregero.hidespectators;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public class HideSpectators extends JavaPlugin implements Listener {

    private static final Permission PERMISSION = new Permission("hidespectators", PermissionDefault.FALSE);

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().addPermission(PERMISSION);

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        System.out.println(event);
        if (event.getPlayer().hasPermission(PERMISSION)) {
            System.out.println("Adding packet listener to " + event.getPlayer().getName());
            ((CraftPlayer) event.getPlayer()).getHandle().connection.connection.channel.pipeline().addBefore("packet_handler", "hidespectators", new HideSpectatorPacketIntercepter(this, event.getPlayer()));
        }
    }
}
