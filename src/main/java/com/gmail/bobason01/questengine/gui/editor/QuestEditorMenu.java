package com.gmail.bobason01.questengine.gui.editor;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.gui.ChatInput;
import com.gmail.bobason01.questengine.gui.GuiHolder;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public final class QuestEditorMenu implements Listener {

    private static final int SIZE = 54;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;

    private static final String HOLDER_MAIN = "Q_EDITOR_MAIN";
    private static final String HOLDER_LIST = "Q_EDITOR_LIST";
    private static final String HOLDER_EVENT = "Q_EDITOR_EVENT";
    private static final String HOLDER_CAPTURES = "Q_EDITOR_CAPTURES";
    private static final String HOLDER_TEMPLATE = "Q_TEMPLATE:";

    private final QuestEnginePlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public enum ActionGroup {
        ACCEPT("accept"), START("start"), SUCCESS("success"), FAIL("fail"),
        CANCEL("cancel"), STOP("stop"), RESTART("restart"), REPEAT("repeat");
        public final String key;
        ActionGroup(String key) { this.key = key; }
    }

    private static final class Session {
        final QuestEditorDraft draft;
        EditorTab tab;
        int eventPage;
        Session(QuestEditorDraft draft, EditorTab tab) { this.draft = draft; this.tab = tab; }
    }

    private static final List<String> BUILTIN_EVENTS = List.of(
            "NONE", "BLOCK_BREAK", "BLOCK_BURN", "BLOCK_EXPLODE", "BLOCK_FERTILIZING", "BLOCK_PLACE",
            "BREEDING", "BREWING", "DEAL_DAMAGE", "PLAYER_ATTACK", "ENTITY_INTERACT", "FISHING",
            "GUIMANAGER_OPEN", "HOTKEY_INPUT", "ITEM_BREAK", "ITEM_CONSUME", "ITEM_CRAFT", "ITEM_DAMAGE",
            "ITEM_DROP", "ITEM_ENCHANT", "ITEM_MENDING", "ITEM_MOVE", "ITEM_PICKUP",
            "ITEM_REPAIR", "MOBKILLING", "MYTHICMOBS_ENTITY_KILL", "MYTHICMOBS_ENTITY_SPAWN",
            "PLAYER_ARMOR", "PLAYER_BED_ENTER", "PLAYER_CHAT", "PLAYER_COMMAND",
            "PLAYER_EXP_GAIN", "PLAYER_LEAVE", "PLAYER_LEVELUP", "PLAYER_PRE_JOIN",
            "PLAYER_RESPAWN", "PLAYER_SWAP_HAND", "PLAYER_TELEPORT", "PLAYER_WALK",
            "DISTANCE_FROM", "SMITHING", "TAMING", "WORLD_CHUNK_LOAD", "CRAFTSLOT_CLICK"
    );

    public QuestEditorMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private String m(Player p, String path) { return plugin.msg().get(p, path); }
    private List<String> mL(Player p, String path) { return plugin.msg().list(p, path); }

    public void openNew(Player player) { openNewWithId(player, "new_quest"); }

    public void openNewWithId(Player player, String id) {
        QuestEditorDraft draft = new QuestEditorDraft();
        draft.id = id.toLowerCase(Locale.ROOT);
        draft.name = "New Quest";
        draft.displayTitle = "&fNew Quest";
        ensureActionGroups(draft);
        Session session = new Session(draft, EditorTab.DISPLAY);
        sessions.put(player.getUniqueId(), session);
        openMainDelayed(player, session);
    }

    public void openEdit(Player player, QuestDef quest) {
        QuestEditorDraft draft = QuestEditorDraft.fromQuest(quest);
        ensureActionGroups(draft);
        Session session = new Session(draft, EditorTab.DISPLAY);
        sessions.put(player.getUniqueId(), session);
        openMainDelayed(player, session);
    }

    private void openMainDelayed(Player p, Session s) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.openInventory(createMainInventory(p, s)); }, 1L);
    }
    private void openListDelayed(Player p, String k) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) { Inventory inv = createListInventory(p, k); if (inv != null) p.openInventory(inv); }
        }, 1L);
    }
    private void openEventSelectDelayed(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) { Session s = sessions.get(p.getUniqueId()); if (s != null) p.openInventory(createEventInventory(p, s)); }
        }, 1L);
    }
    private void openCapturesDelayed(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) { Session s = sessions.get(p.getUniqueId()); if (s != null) p.openInventory(createCapturesInventory(p, s)); }
        }, 1L);
    }

    private Inventory createMainInventory(Player p, Session session) {
        String tabName = m(p, "gui.editor.tab." + session.tab.name().toLowerCase());
        String title = m(p, "gui.editor.title.main").replace("%tab%", tabName);
        GuiHolder holder = new GuiHolder(HOLDER_MAIN);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        plugin.gui().fill(inv, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        renderTabs(p, inv, session.tab);
        renderFields(p, inv, session.draft, session.tab);
        renderControls(p, inv);
        return inv;
    }

    private Inventory createListInventory(Player p, String key) {
        Session session = sessions.get(p.getUniqueId());
        if (session == null) return null;
        ensureActionGroups(session.draft);
        List<String> list = getListReference(session.draft, key);
        if (list == null) return null;

        String title = m(p, "gui.editor.title.list").replace("%key%", key);
        GuiHolder holder = new GuiHolder(HOLDER_LIST + ":" + key);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        plugin.gui().fill(inv, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        int idx = 0;
        int max = CONTENT_END - CONTENT_START + 1;
        for (String val : list) {
            if (idx >= max) break;
            inv.setItem(CONTENT_START + idx++, new ItemBuilder(Material.PAPER)
                    .setName(val == null || val.isEmpty() ? m(p, "gui.editor.common.empty") : plugin.msg().color(val))
                    .setLore(Arrays.asList(ChatColor.GRAY + "Idx " + (idx-1), "", m(p, "gui.editor.common.text.left"), m(p, "gui.editor.common.text.right")))
                    .hideAllFlags().build());
        }
        if (CONTENT_START + idx <= CONTENT_END) {
            inv.setItem(CONTENT_START + idx, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .setName(m(p, "gui.editor.list.add.name")).setLore(List.of(m(p, "gui.editor.list.add.lore1"))).hideAllFlags().build());
        }
        inv.setItem(53, new ItemBuilder(Material.ARROW).setName(m(p, "gui.editor.captures.back.name")).build());
        return inv;
    }

    private Inventory createEventInventory(Player p, Session session) {
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int total = BUILTIN_EVENTS.size();
        int maxPage = Math.max(1, (total + pageSize - 1) / pageSize);
        session.eventPage = Math.max(0, Math.min(session.eventPage, maxPage - 1));

        String title = m(p, "gui.editor.title.event_view")
                .replace("%page%", String.valueOf(session.eventPage + 1))
                .replace("%max%", String.valueOf(maxPage));

        GuiHolder holder = new GuiHolder(HOLDER_EVENT);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        plugin.gui().fill(inv, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        int start = session.eventPage * pageSize;
        int idx = 0;
        for (int i = start; i < total && idx < pageSize; i++, idx++) {
            inv.setItem(CONTENT_START + idx, new ItemBuilder(Material.PAPER)
                    .setName(m(p, "gui.editor.event.entry.name").replace("%event%", BUILTIN_EVENTS.get(i)))
                    .setLore(Arrays.asList(m(p, "gui.editor.event.entry.lore1"), m(p, "gui.editor.event.entry.lore2")))
                    .hideAllFlags().build());
        }

        inv.setItem(48, new ItemBuilder(Material.WRITABLE_BOOK)
                .setName(m(p, "gui.editor.event.help.title"))
                .setLore(mL(p, "gui.editor.event.help.lore"))
                .hideAllFlags().build());

        if (session.eventPage > 0) inv.setItem(45, new ItemBuilder(Material.ARROW).setName(m(p, "gui.editor.event.prev.name")).build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER).setName(m(p, "gui.editor.event.back.name")).build());
        if (session.eventPage < maxPage - 1) inv.setItem(53, new ItemBuilder(Material.ARROW).setName(m(p, "gui.editor.event.next.name")).build());

        return inv;
    }

    private Inventory createCapturesInventory(Player p, Session session) {
        String title = m(p, "gui.editor.title.captures_view");
        GuiHolder holder = new GuiHolder(HOLDER_CAPTURES);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        plugin.gui().fill(inv, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        List<Map.Entry<String, String>> entries = new ArrayList<>(session.draft.customCaptures.entrySet());
        int idx = 0;
        int max = CONTENT_END - CONTENT_START + 1;
        for (var e : entries) {
            if (idx >= max) break;
            inv.setItem(CONTENT_START + idx++, new ItemBuilder(Material.PAPER)
                    .setName(m(p, "gui.editor.captures.entry.name").replace("%key%", e.getKey()))
                    .setLore(Arrays.asList(m(p, "gui.editor.captures.entry.chain").replace("%chain%", e.getValue()), "", m(p, "gui.editor.captures.entry.left"), m(p, "gui.editor.captures.entry.right")))
                    .hideAllFlags().build());
        }
        if (CONTENT_START + idx <= CONTENT_END) {
            inv.setItem(CONTENT_START + idx, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .setName(m(p, "gui.editor.captures.add.name")).setLore(mL(p, "gui.editor.captures.add.lore")).hideAllFlags().build());
        }
        inv.setItem(53, new ItemBuilder(Material.ARROW).setName(m(p, "gui.editor.captures.back.name")).build());
        return inv;
    }

    private void renderTabs(Player p, Inventory inv, EditorTab current) {
        EditorTab[] vals = EditorTab.values();
        for (int i = 0; i < vals.length; i++) {
            boolean sel = vals[i] == current;
            inv.setItem(i, new ItemBuilder(sel ? Material.BLUE_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName((sel ? m(p, "gui.editor.tab.selected") : m(p, "gui.editor.tab.normal")).replace("%name%", m(p, "gui.editor.tab." + vals[i].name().toLowerCase())))
                    .setLore(List.of(sel ? m(p, "gui.editor.tab.lore.current") : m(p, "gui.editor.tab.lore.switch")))
                    .hideAllFlags().build());
        }
    }

    private void renderFields(Player p, Inventory inv, QuestEditorDraft d, EditorTab tab) {
        ensureActionGroups(d);
        switch (tab) {
            case DISPLAY -> {
                inv.setItem(10, textItem(p, "gui.editor.display.title.label", d.displayTitle));
                inv.setItem(12, listItem(p, "gui.editor.display.description.label", d.displayDescription));
                inv.setItem(14, iconItem(p, d));
                inv.setItem(16, textItem(p, "gui.editor.display.progress.label", d.displayProgress));
                inv.setItem(19, textItem(p, "gui.editor.display.model.label", d.displayModel));
                inv.setItem(21, textItem(p, "gui.editor.display.hint.label", d.displayHint));
                inv.setItem(23, textItem(p, "gui.editor.display.reward.label", d.displayReward));
                inv.setItem(25, textItem(p, "gui.editor.display.category.label", d.displayCategory));
                inv.setItem(28, textItem(p, "gui.editor.display.difficulty.label", d.displayDifficulty));
                inv.setItem(40, new ItemBuilder(Material.ITEM_FRAME).setName(m(p, "gui.editor.display.icon.help.title")).setLore(mL(p, "gui.editor.display.icon.help.lore")).hideAllFlags().build());
            }
            case EVENT -> {
                inv.setItem(10, textItem(p, "gui.editor.event.event.label", d.event));
                inv.setItem(12, textItem(p, "gui.editor.event.startmode.label", d.startMode.name()));
                inv.setItem(14, textItem(p, "gui.editor.event.type.label", d.type));
                inv.setItem(48, new ItemBuilder(Material.WRITABLE_BOOK).setName(m(p, "gui.editor.event.help.title")).setLore(mL(p, "gui.editor.event.help.lore")).hideAllFlags().build());
            }
            case CUSTOM_EVENT -> {
                inv.setItem(10, textItem(p, "gui.editor.custom.eventclass.label", d.customEventClass));
                inv.setItem(12, textItem(p, "gui.editor.custom.playergetter.label", d.customPlayerGetter));
                inv.setItem(14, new ItemBuilder(Material.BOOK).setName(m(p, "gui.editor.custom.captures.label")).setLore(mL(p, "gui.editor.custom.captures.lore").stream().map(s -> s.replace("%count%", String.valueOf(d.customCaptures.size()))).toList()).hideAllFlags().build());
            }
            case TARGETS -> {
                inv.setItem(10, listItem(p, "gui.editor.targets.targets.label", d.targets));
                inv.setItem(12, numberItem(p, "gui.editor.targets.amount.label", d.amount));
                inv.setItem(14, numberItem(p, "gui.editor.targets.repeat.label", d.repeat));
                inv.setItem(16, numberItem(p, "gui.editor.targets.points.label", d.points));
            }
            case META -> {
                inv.setItem(10, textItem(p, "gui.editor.meta.id.label", d.id));
                inv.setItem(12, textItem(p, "gui.editor.meta.name.label", d.name));
            }
            case ACTIONS -> {
                int[] slots = {10, 12, 14, 19, 21, 23, 28, 30};
                ActionGroup[] grps = ActionGroup.values();
                for (int i = 0; i < grps.length && i < slots.length; i++) {
                    inv.setItem(slots[i], new ItemBuilder(Material.BOOK).setName(m(p, "gui.editor.actions.group." + grps[i].key)).setLore(Arrays.asList(m(p, "gui.editor.actions.lines").replace("%count%", String.valueOf(d.actions.getOrDefault(grps[i].key, List.of()).size())), "", m(p, "gui.editor.common.text.left"), m(p, "gui.editor.common.text.right"))).hideAllFlags().build());
                }
                inv.setItem(40, new ItemBuilder(Material.MAP).setName(m(p, "gui.editor.actions.help.title")).setLore(mL(p, "gui.editor.actions.help.lore")).hideAllFlags().build());
            }
            case CONDITIONS -> {
                inv.setItem(10, listItem(p, "gui.editor.conditions.start.label", d.condStart));
                inv.setItem(12, listItem(p, "gui.editor.conditions.success.label", d.condSuccess));
                inv.setItem(14, listItem(p, "gui.editor.conditions.fail.label", d.condFail));
                inv.setItem(40, new ItemBuilder(Material.WRITABLE_BOOK).setName(m(p, "gui.editor.conditions.help.title")).setLore(mL(p, "gui.editor.conditions.help.lore")).hideAllFlags().build());
            }
            case OPTIONS -> {
                inv.setItem(10, textItem(p, "gui.editor.options.resetpolicy.label", d.resetPolicy));
                inv.setItem(12, textItem(p, "gui.editor.options.resettime.label", d.resetTime));
                inv.setItem(28, booleanItem(p, "gui.editor.options.public.label", d.isPublic));
                inv.setItem(30, booleanItem(p, "gui.editor.options.party.label", d.party));
                inv.setItem(40, new ItemBuilder(Material.MAP).setName(m(p, "gui.editor.options.help.title")).setLore(mL(p, "gui.editor.options.help.lore")).hideAllFlags().build());
            }
            case CHAIN -> {
                inv.setItem(10, textItem(p, "gui.editor.chain.next.label", d.nextQuestOnComplete));
                inv.setItem(12, listItem(p, "gui.editor.chain.required.label", d.requiredQuests));
            }
        }
    }

    private void renderControls(Player p, Inventory inv) {
        inv.setItem(45, new ItemBuilder(Material.EMERALD_BLOCK).setName(m(p, "gui.editor.control.save.name")).setLore(Arrays.asList(m(p, "gui.editor.control.save.lore1"), m(p, "gui.editor.control.save.lore2"))).hideAllFlags().build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER).setName(m(p, "gui.editor.control.close.name")).setLore(List.of(m(p, "gui.editor.control.close.lore"))).hideAllFlags().build());
    }

    private ItemStack textItem(Player p, String k, String v) { return new ItemBuilder(Material.PAPER).setName(m(p, "gui.editor.common.text.name").replace("%label%", m(p, k))).setLore(Arrays.asList(m(p, "gui.editor.common.text.value").replace("%value%", v == null || v.isEmpty() ? m(p, "gui.editor.common.empty") : plugin.msg().color(v)), "", m(p, "gui.editor.common.text.left"), m(p, "gui.editor.common.text.right"))).hideAllFlags().build(); }
    private ItemStack numberItem(Player p, String k, int v) { return new ItemBuilder(Material.REPEATER).setName(m(p, "gui.editor.common.number.name").replace("%label%", m(p, k))).setLore(Arrays.asList(m(p, "gui.editor.common.number.value").replace("%value%", String.valueOf(v)), "", m(p, "gui.editor.common.number.edit"))).hideAllFlags().build(); }
    private ItemStack booleanItem(Player p, String k, boolean v) { return new ItemBuilder(v ? Material.LIME_DYE : Material.GRAY_DYE).setName(m(p, "gui.editor.common.boolean.name").replace("%label%", m(p, k))).setLore(Arrays.asList(m(p, "gui.editor.common.boolean.value").replace("%value%", String.valueOf(v)), "", m(p, "gui.editor.common.boolean.edit"))).hideAllFlags().build(); }
    private ItemStack listItem(Player p, String k, List<String> l) { return new ItemBuilder(Material.BOOK).setName(m(p, "gui.editor.common.list.name").replace("%label%", m(p, k))).setLore(Arrays.asList(m(p, "gui.editor.common.list.entries").replace("%count%", String.valueOf(l == null ? 0 : l.size())), "", m(p, "gui.editor.common.list.edit"))).hideAllFlags().build(); }

    private ItemStack iconItem(Player p, QuestEditorDraft d) {
        Material m = Material.getMaterial(d.displayIcon.toUpperCase(Locale.ROOT));
        if (m == null) m = Material.BOOK;
        return new ItemBuilder(m)
                .setName(m(p, "gui.editor.display.icon.name"))
                .setLore(mL(p, "gui.editor.display.icon.lore"))
                .setModel(d.displayModel)
                .hideAllFlags().build();
    }

    private void ensureActionGroups(QuestEditorDraft d) { for (ActionGroup g : ActionGroup.values()) d.actions.computeIfAbsent(g.key, k -> new ArrayList<>()); }

    private List<String> getListReference(QuestEditorDraft d, String key) {
        return switch (key) {
            case "display.description" -> d.displayDescription;
            case "targets" -> d.targets;
            case "conditions.start" -> d.condStart;
            case "conditions.success" -> d.condSuccess;
            case "conditions.fail" -> d.condFail;
            case "requiredQuests" -> d.requiredQuests;
            default -> key.startsWith("actions.") ? d.actions.computeIfAbsent(key.substring(8), k -> new ArrayList<>()) : null;
        };
    }

    private void promptOrClear(Player p, boolean L, boolean R, String def, java.util.function.Consumer<String> setter, String promptKey) {
        if (R) { setter.accept(def); openMainDelayed(p, sessions.get(p.getUniqueId())); }
        else if (L) {
            p.closeInventory();
            ChatInput.await(p, m(p, promptKey), (pl, s) -> { setter.accept(s); openMainDelayed(pl, sessions.get(pl.getUniqueId())); });
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player) || !(e.getView().getTopInventory().getHolder() instanceof GuiHolder h)) return;
        String id = h.id();
        if (id == null) return;
        e.setCancelled(true);

        if (id.startsWith(HOLDER_MAIN)) handleMainClick(player, e);
        else if (id.startsWith(HOLDER_LIST)) handleListClick(player, e, id);
        else if (id.equals(HOLDER_EVENT)) handleEventSelectClick(player, e);
        else if (id.equals(HOLDER_CAPTURES)) handleCapturesClick(player, e);
        else if (id.startsWith("QEDITOR_QLIST_")) handleQuestListClick(player, e, id);
        else if (id.startsWith(HOLDER_TEMPLATE)) handleTemplateClick(player, e, id);
    }

    private void handleMainClick(Player p, InventoryClickEvent e) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        int slot = e.getRawSlot();
        if (slot < 0) return;
        if (slot < EditorTab.values().length) {
            s.tab = EditorTab.values()[slot];
            openMainDelayed(p, s);
            return;
        }
        if (s.tab == EditorTab.DISPLAY && slot >= SIZE && e.getCurrentItem() != null) {
            s.draft.displayIcon = e.getCurrentItem().getType().name();
            if (e.getCurrentItem().hasItemMeta()) {
                var meta = e.getCurrentItem().getItemMeta();
                if (meta.hasCustomModelData()) {
                    s.draft.displayModel = String.valueOf(meta.getCustomModelData());
                } else {
                    s.draft.displayModel = "-1";
                }
            }
            openMainDelayed(p, s);
            return;
        }
        if (slot == 45) { saveDraft(p, s.draft); return; }
        if (slot == 49) { p.closeInventory(); return; }
        if (slot >= SIZE) return;

        handleFieldClick(p, s, e);
    }

    private void handleFieldClick(Player p, Session s, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        ClickType c = e.getClick();
        QuestEditorDraft d = s.draft;
        boolean L = c.isLeftClick(), R = c.isRightClick();

        switch (s.tab) {
            case DISPLAY -> {
                if (slot == 10) promptOrClear(p, L, R, d.displayTitle, v -> d.displayTitle = v, "gui.editor.prompt.display_title");
                else if (slot == 12 && L) openListDelayed(p, "display.description");
                else if (slot == 14) promptOrClear(p, L, R, "BOOK", v -> { d.displayIcon = v.toUpperCase(Locale.ROOT); d.displayModel = "-1"; }, "gui.editor.prompt.display_icon");
                else if (slot == 16) promptOrClear(p, L, R, "&7%value%/%target%", v -> d.displayProgress = v, "gui.editor.prompt.display_progress");
                else if (slot == 19) promptOrClear(p, L, R, "-1", v -> d.displayModel = v, "gui.editor.prompt.display_cmd");
                else if (slot == 21) promptOrClear(p, L, R, "", v -> d.displayHint = v, "gui.editor.prompt.display_hint");
                else if (slot == 23) promptOrClear(p, L, R, "", v -> d.displayReward = v, "gui.editor.prompt.display_reward");
                else if (slot == 25) promptOrClear(p, L, R, "", v -> d.displayCategory = v, "gui.editor.prompt.display_category");
                else if (slot == 28) promptOrClear(p, L, R, "", v -> d.displayDifficulty = v, "gui.editor.prompt.display_difficulty");
            }
            case EVENT -> {
                if (slot == 10) {
                    if (c == ClickType.SHIFT_LEFT) openEventSelectDelayed(p);
                    else if (R) { d.event = "CUSTOM"; openMainDelayed(p, s); }
                    else if (L) {
                        p.closeInventory();
                        ChatInput.await(p, m(p, "gui.editor.prompt.event_name"), (pl, v) -> {
                            s.draft.event = v.toUpperCase(Locale.ROOT);
                            openMainDelayed(pl, sessions.get(pl.getUniqueId()));
                        });
                    }
                }
                else if (slot == 12 && L) { d.startMode = QuestDef.StartMode.values()[(d.startMode.ordinal()+1)%4]; openMainDelayed(p,s); }
                else if (slot == 14) promptOrClear(p, L, R, "vanilla", v -> d.type = v.toLowerCase(Locale.ROOT), "gui.editor.prompt.event_type");
            }
            case CUSTOM_EVENT -> {
                if (slot == 10) promptOrClear(p, L, R, "", v -> d.customEventClass = v, "gui.editor.prompt.custom_event_class");
                else if (slot == 12) promptOrClear(p, L, R, "getPlayer()", v -> d.customPlayerGetter = v, "gui.editor.prompt.custom_player_getter");
                else if (slot == 14) { if (R) { d.customCaptures.clear(); openMainDelayed(p,s); } else if (L) openCapturesDelayed(p); }
            }
            case TARGETS -> {
                if (slot == 10 && L) openListDelayed(p, "targets");
                else if (slot == 12 && L) { p.closeInventory(); ChatInput.await(p, m(p, "gui.editor.prompt.targets_amount"), (pl, v) -> { try { d.amount = Math.max(1, Integer.parseInt(v.trim())); } catch(Exception ignored){} openMainDelayed(pl, sessions.get(pl.getUniqueId())); }); }
                else if (slot == 14 && L) { p.closeInventory(); ChatInput.await(p, m(p, "gui.editor.prompt.targets_repeat"), (pl, v) -> { try { d.repeat = Integer.parseInt(v.trim()); } catch(Exception ignored){} openMainDelayed(pl, sessions.get(pl.getUniqueId())); }); }
                else if (slot == 16 && L) { p.closeInventory(); ChatInput.await(p, m(p, "gui.editor.prompt.targets_points"), (pl, v) -> { try { d.points = Math.max(0, Integer.parseInt(v.trim())); } catch(Exception ignored){} openMainDelayed(pl, sessions.get(pl.getUniqueId())); }); }
            }
            case META -> {
                if (slot == 10) promptOrClear(p, L, R, d.id, v -> d.id = v.toLowerCase(Locale.ROOT), "gui.editor.prompt.meta_id");
                else if (slot == 12) promptOrClear(p, L, R, d.name, v -> d.name = v, "gui.editor.prompt.meta_name");
            }
            case ACTIONS -> {
                int[] slots = {10,12,14,19,21,23,28,30};
                ActionGroup[] gs = ActionGroup.values();
                for (int i=0; i<gs.length && i<slots.length; i++) {
                    if (slot == slots[i]) { if (R) { d.actions.put(gs[i].key, new ArrayList<>()); openMainDelayed(p, s); } else if (L) openListDelayed(p, "actions." + gs[i].key); }
                }
            }
            case CONDITIONS -> {
                if (L) { if (slot==10) openListDelayed(p,"conditions.start"); else if (slot==12) openListDelayed(p,"conditions.success"); else if (slot==14) openListDelayed(p,"conditions.fail"); }
            }
            case OPTIONS -> {
                if (slot == 10) promptOrClear(p, L, R, "", v -> d.resetPolicy = v, "gui.editor.prompt.options_resetpolicy");
                else if (slot == 12) promptOrClear(p, L, R, "", v -> d.resetTime = v, "gui.editor.prompt.options_resettime");
                else if (slot == 28 && L) { d.isPublic = !d.isPublic; openMainDelayed(p, s); }
                else if (slot == 30 && L) { d.party = !d.party; openMainDelayed(p, s); }
            }
            case CHAIN -> {
                if (slot == 10) promptOrClear(p, L, R, "", v -> d.nextQuestOnComplete = v, "gui.editor.prompt.chain_next");
                else if (slot == 12 && L) openListDelayed(p, "requiredQuests");
            }
        }
    }

    private void handleListClick(Player p, InventoryClickEvent e, String id) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        String key = id.contains(":") ? id.substring(id.indexOf(':') + 1) : "targets";
        List<String> list = getListReference(s.draft, key);
        if (list == null) return;
        int slot = e.getRawSlot();
        if (slot == 53) { openMainDelayed(p, s); return; }
        if (slot < CONTENT_START || slot > CONTENT_END) return;
        int idx = slot - CONTENT_START;
        if (idx < list.size()) {
            if (e.getClick().isRightClick()) { list.remove(idx); openListDelayed(p, key); }
            else if (e.getClick().isLeftClick()) {
                String old = list.get(idx);
                p.closeInventory();
                ChatInput.await(p, m(p, "gui.editor.prompt.list_edit").replace("%old%", old), (pl, v) -> { list.set(idx, v); openListDelayed(pl, key); });
            }
        } else if (idx == list.size()) {
            openTemplateSelector(p, key);
            plugin.gui().sound(p, "click");
        }
    }

    private void handleEventSelectClick(Player p, InventoryClickEvent e) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        int slot = e.getRawSlot();

        if (slot == 45) { if (s.eventPage > 0) { s.eventPage--; openEventSelectDelayed(p); } return; }
        if (slot == 53) { int max = (BUILTIN_EVENTS.size() + 35) / 36; if (s.eventPage < max - 1) { s.eventPage++; openEventSelectDelayed(p); } return; }
        if (slot == 49) { openMainDelayed(p, s); return; }

        ItemStack item = e.getCurrentItem();
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            s.draft.event = name;
            openMainDelayed(p, s);
        }
    }

    private void handleCapturesClick(Player p, InventoryClickEvent e) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        int slot = e.getRawSlot();
        if (slot == 53) { openMainDelayed(p, s); return; }
        if (slot < CONTENT_START || slot > CONTENT_END) return;
        var entries = new ArrayList<>(s.draft.customCaptures.entrySet());
        int idx = slot - CONTENT_START;
        if (idx < entries.size()) {
            String key = entries.get(idx).getKey();
            if (e.getClick().isRightClick()) { s.draft.customCaptures.remove(key); openCapturesDelayed(p); }
            else if (e.getClick().isLeftClick()) {
                p.closeInventory();
                ChatInput.await(p, m(p, "gui.editor.prompt.captures_edit").replace("%line%", key + ";" + entries.get(idx).getValue()), (pl, v) -> { if (applyCaptureLine(s.draft, v, true)) openCapturesDelayed(pl); });
            }
        } else if (idx == entries.size()) {
            p.closeInventory();
            ChatInput.await(p, m(p, "gui.editor.prompt.captures_add"), (pl, v) -> { if (applyCaptureLine(s.draft, v, false)) openCapturesDelayed(pl); });
        }
    }

    private void handleQuestListClick(Player p, InventoryClickEvent e, String id) {
        e.setCancelled(true);
        String pageStr = id.substring("QEDITOR_QLIST_".length());
        int page = 1;
        try { page = Integer.parseInt(pageStr); } catch (Exception ignored) {}
        int slot = e.getRawSlot();
        if (slot == 45) { openListSelection(p, page - 1); return; }
        if (slot == 53) { openListSelection(p, page + 1); return; }
        ItemStack item = e.getCurrentItem();
        if (item != null && item.getType() == Material.PAPER && item.hasItemMeta()) {
            String qId = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            QuestDef def = plugin.quests().get(qId);
            if (def != null) openEdit(p, def);
        }
    }

    private void saveDraft(Player p, QuestEditorDraft d) {
        if (d.id == null || d.id.isBlank()) { p.sendMessage(m(p, "gui.editor.error.id_empty")); return; }
        try {
            QuestDef def = d.buildQuestDef();
            org.bukkit.configuration.file.YamlConfiguration yml = QuestDef.toYaml(def);
            File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("quests.folder", "quests"));
            if (!folder.exists()) folder.mkdirs();
            yml.save(new File(folder, def.id + ".yml"));
            plugin.quests().reload();
            p.sendMessage(m(p, "gui.editor.save.ok").replace("%id%", def.id));
        } catch (Exception ex) { p.sendMessage(m(p, "gui.editor.save.fail").replace("%msg%", ex.getMessage())); ex.printStackTrace(); }
    }

    public void openListSelection(Player player, int page) {
        List<QuestDef> all = new ArrayList<>(plugin.quests().all());
        all.sort(Comparator.comparing(q -> q.id));
        int maxPage = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        page = Math.max(1, Math.min(page, maxPage));
        String title = "Quest List - Page " + page;
        GuiHolder holder = new GuiHolder("QEDITOR_QLIST_" + page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        plugin.gui().fill(inv, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        int start = (page - 1) * 45;
        int end = Math.min(start + 45, all.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            QuestDef def = all.get(i);
            inv.setItem(slot++, new ItemBuilder(Material.PAPER).setName("§f" + def.id).setLore(List.of("Click to edit")).build());
        }
        if (page > 1) inv.setItem(45, new ItemBuilder(Material.ARROW).setName("Previous").build());
        if (page < maxPage) inv.setItem(53, new ItemBuilder(Material.ARROW).setName("Next").build());
        player.openInventory(inv);
    }

    private boolean applyCaptureLine(QuestEditorDraft d, String line, boolean replace) {
        if (line == null || line.isEmpty()) return false;
        String[] parts = line.split(";");
        if (parts.length < 2) return false;

        String k = parts[0].trim();
        String v = parts[1].trim();

        if (k.length() > 2 && k.startsWith("%") && k.endsWith("%")) k = k.substring(1, k.length()-1);
        if (k.isEmpty()) return false;
        if (!replace && d.customCaptures.containsKey(k)) return false;

        d.customCaptures.put(k, v);
        return true;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof GuiHolder h && h.id().startsWith("Q_EDITOR")) e.setCancelled(true); }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {}

    private void openTemplateSelector(Player p, String key) {
        GuiHolder holder = new GuiHolder(HOLDER_TEMPLATE + key);
        String title = m(p, "gui.editor.template.title");
        Inventory inv = Bukkit.createInventory(holder, 36, title);

        plugin.gui().fill(inv, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        if (key.startsWith("actions.")) {
            inv.setItem(10, templateItem(p, m(p, "gui.editor.template.action.msg"), "msg{m=\"&aQuest Complete!\"} @self"));
            inv.setItem(11, templateItem(p, m(p, "gui.editor.template.action.cmd"), "cmd{c=\"give %player% diamond 1\"} @server"));
            inv.setItem(12, templateItem(p, m(p, "gui.editor.template.action.item"), "item{i=DIAMOND;a=1} @self"));
            inv.setItem(13, templateItem(p, m(p, "gui.editor.template.action.title"), "title{t=\"&6Clear!\";s=\"&fGood job\"} @self"));
            inv.setItem(14, templateItem(p, m(p, "gui.editor.template.action.sound"), "sound{s=ENTITY_PLAYER_LEVELUP;v=1;p=1} @self"));
            inv.setItem(15, templateItem(p, m(p, "gui.editor.template.action.particle"), "particle{p=FLAME;a=20} @self"));
            inv.setItem(16, templateItem(p, m(p, "gui.editor.template.action.potion"), "potion{t=SPEED;d=200;l=2} @self"));
            inv.setItem(17, templateItem(p, m(p, "gui.editor.template.action.delay"), "delay 20t"));
        } else if (key.startsWith("conditions.")) {
            inv.setItem(10, templateItem(p, m(p, "gui.editor.template.cond.level"), "%player_level% >= 10"));
            inv.setItem(11, templateItem(p, m(p, "gui.editor.template.cond.world"), "%player_world% == 'world_nether'"));
            inv.setItem(12, templateItem(p, m(p, "gui.editor.template.cond.papi"), "%vault_eco_balance% >= 1000"));
            inv.setItem(13, templateItem(p, "GUI Condition", "%gui_id% == 'shop'"));
            inv.setItem(14, templateItem(p, "Always Pass", "NONE"));
        } else if (key.equals("targets")) {
            inv.setItem(10, templateItem(p, m(p, "gui.editor.template.target.all"), "*"));
            inv.setItem(11, templateItem(p, m(p, "gui.editor.template.target.block"), "STONE"));
            inv.setItem(12, templateItem(p, m(p, "gui.editor.template.target.mob"), "ZOMBIE"));
            inv.setItem(13, templateItem(p, m(p, "gui.editor.template.target.mythic_kill"), "SkeletonKing"));
            inv.setItem(14, templateItem(p, m(p, "gui.editor.template.target.citizens"), "CITIZENS:1"));
            inv.setItem(15, templateItem(p, m(p, "gui.editor.template.target.mythic_interact"), "MYTHICMOBS:NPC_NAME"));
            inv.setItem(16, templateItem(p, m(p, "gui.editor.template.target.vanilla_interact"), "ENTITY:COW"));
            inv.setItem(19, templateItem(p, m(p, "gui.editor.template.target.command"), "spawn"));
            inv.setItem(20, templateItem(p, m(p, "gui.editor.template.target.chat"), "hello"));
            inv.setItem(21, templateItem(p, m(p, "gui.editor.template.target.multiple"), "ZOMBIE|SKELETON"));
            inv.setItem(22, templateItem(p, "Hotkey Input", "SHIFT_F"));
        } else {
            inv.setItem(10, templateItem(p, m(p, "gui.editor.template.default"), "example_item"));
        }

        inv.setItem(26, templateItem(p, m(p, "gui.editor.template.custom_input"), "CUSTOM_INPUT"));
        inv.setItem(35, new ItemBuilder(Material.BARRIER).setName(m(p, "gui.editor.template.cancel")).build());

        p.openInventory(inv);
    }

    private ItemStack templateItem(Player p, String name, String template) {
        return new ItemBuilder(Material.PAPER)
                .setName(name)
                .setLore(Arrays.asList(
                        m(p, "gui.editor.template.lore.add"),
                        "§f" + template,
                        "",
                        m(p, "gui.editor.template.lore.edit")
                )).hideAllFlags().build();
    }

    private void handleTemplateClick(Player p, InventoryClickEvent e, String id) {
        String key = id.substring(HOLDER_TEMPLATE.length());
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        List<String> list = getListReference(s.draft, key);
        if (list == null) return;

        int slot = e.getRawSlot();
        if (slot == 35) {
            openListDelayed(p, key);
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return;

        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) return;

        String template = ChatColor.stripColor(lore.get(1));

        if (template.equals("CUSTOM_INPUT")) {
            p.closeInventory();
            ChatInput.await(p, m(p, "gui.editor.prompt.list_add"), (pl, v) -> {
                list.add(v);
                openListDelayed(pl, key);
            });
        } else {
            list.add(template);
            plugin.gui().sound(p, "success");
            openListDelayed(p, key);
        }
    }
}