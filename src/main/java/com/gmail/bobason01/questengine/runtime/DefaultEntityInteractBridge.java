package com.gmail.bobason01.questengine.runtime;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class DefaultEntityInteractBridge implements Listener {

    private final Engine engine;

    public DefaultEntityInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity clicked = e.getRightClicked();
        if (p == null || clicked == null) return;

        String key = "ENTITY:" + clicked.getType().name();
        engine.handleNpcInteract(p, key);
    }
}
