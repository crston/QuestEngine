package com.gmail.bobason01.questengine.runtime;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class CitizensNpcInteractBridge implements Listener {

    private final Engine engine;

    public CitizensNpcInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity clicked = e.getRightClicked();
        if (p == null || clicked == null) return;

        var npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
        if (npc == null) return;

        String key = "CITIZENS:" + npc.getId();
        engine.handleNpcInteract(p, key);
    }
}
