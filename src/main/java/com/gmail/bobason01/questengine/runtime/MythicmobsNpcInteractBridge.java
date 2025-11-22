package com.gmail.bobason01.questengine.runtime;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Locale;
import java.util.Optional;

public final class MythicmobsNpcInteractBridge implements Listener {

    private final Engine engine;

    public MythicmobsNpcInteractBridge(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity clicked = e.getRightClicked();
        if (p == null || clicked == null) return;

        MythicBukkit mythic = MythicBukkit.inst();
        if (mythic == null) return;

        Optional<ActiveMob> opt = mythic.getMobManager().getActiveMob(clicked.getUniqueId());
        if (opt.isEmpty()) return;

        ActiveMob mob = opt.get();
        String mobid = mob.getType().getInternalName();
        if (mobid == null || mobid.isEmpty()) return;

        String key = "MYTHICMOBS:" + mobid.toUpperCase(Locale.ROOT);
        engine.handleNpcInteract(p, key);
    }
}
