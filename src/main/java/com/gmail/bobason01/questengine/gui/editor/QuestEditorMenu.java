package com.gmail.bobason01.questengine.gui.editor;

import com.gmail.bobason01.questengine.QuestEnginePlugin;
import com.gmail.bobason01.questengine.gui.ChatInput;
import com.gmail.bobason01.questengine.gui.GuiHolder;
import com.gmail.bobason01.questengine.quest.QuestDef;
import com.gmail.bobason01.questengine.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestEditorMenu
 * 메인 퀘스트 편집 GUI
 * DISPLAY / EVENT / CUSTOM_EVENT / TARGETS / META / ACTIONS / CONDITIONS / OPTIONS / CHAIN
 */
public final class QuestEditorMenu implements Listener {

    private static final int SIZE = 54;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;

    private static final String HOLDER_MAIN = "Q_EDITOR_MAIN";
    private static final String HOLDER_LIST = "Q_EDITOR_LIST";
    private static final String HOLDER_EVENT = "Q_EDITOR_EVENT";
    private static final String HOLDER_CAPTURES = "Q_EDITOR_CAPTURES";

    private final QuestEnginePlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public enum EditorTab {
        DISPLAY("display"),
        EVENT("event"),
        CUSTOM_EVENT("custom_event"),
        TARGETS("targets"),
        META("meta"),
        ACTIONS("actions"),
        CONDITIONS("conditions"),
        OPTIONS("options"),
        CHAIN("chain");

        private final String key;

        EditorTab(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public enum ActionGroup {
        ACCEPT("accept"),
        START("start"),
        SUCCESS("success"),
        FAIL("fail"),
        CANCEL("cancel"),
        STOP("stop"),
        RESTART("restart"),
        REPEAT("repeat");

        public final String key;

        ActionGroup(String key) {
            this.key = key;
        }
    }

    private static final class Session {
        final QuestEditorDraft draft;
        EditorTab tab;
        int eventPage;

        Session(QuestEditorDraft draft, EditorTab tab) {
            this.draft = draft;
            this.tab = tab;
            this.eventPage = 0;
        }
    }

    private static final List<String> BUILTIN_EVENTS = new ArrayList<>();

    static {
        BUILTIN_EVENTS.addAll(Arrays.asList(
                "BLOCK_BREAK",
                "BLOCK_BURN",
                "BLOCK_EXPLODE",
                "BLOCK_FERTILIZING",
                "BLOCK_PLACE",
                "BREEDING",
                "BREWING",
                "DEAL_DAMAGE",
                "ENTITY_INTERACT",
                "FISHING",
                "INVENTORY_OPEN",
                "ITEM_BREAK",
                "ITEM_CONSUME",
                "ITEM_CRAFT",
                "ITEM_DAMAGE",
                "ITEM_DROP",
                "ITEM_ENCHANT",
                "ITEM_MENDING",
                "ITEM_MOVE",
                "ITEM_PICKUP",
                "ITEM_REPAIR",
                "MOBKILLING",
                "MYTHICMOBS_ENTITY_KILL",
                "MYTHICMOBS_ENTITY_SPAWN",
                "PLAYER_ARMOR",
                "PLAYER_BED_ENTER",
                "PLAYER_CHAT",
                "PLAYER_COMMAND",
                "PLAYER_EXP_GAIN",
                "PLAYER_LEAVE",
                "PLAYER_LEVELUP",
                "PLAYER_PRE_JOIN",
                "PLAYER_RESPAWN",
                "PLAYER_SWAP_HAND",
                "PLAYER_TELEPORT",
                "PLAYER_WALK",
                "SMITHING",
                "TAMING",
                "WORLD_CHUNK_LOAD"
        ));
        BUILTIN_EVENTS.sort(String::compareToIgnoreCase);
    }

    public QuestEditorMenu(QuestEnginePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openNew(Player player) {
        QuestEditorDraft draft = new QuestEditorDraft();
        draft.id = "new_quest";
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

    private String m(String path) {
        return plugin.msg().get(path);
    }

    private String m(String path, String def) {
        return plugin.msg().get(path, def);
    }

    private List<String> ml(String path) {
        return plugin.msg().list(path);
    }

    private void openMainDelayed(Player player, Session session) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Inventory inv = createMainInventory(session);
            player.openInventory(inv);
        }, 2L);
    }

    private void openListDelayed(Player player, String key) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Inventory inv = createListInventory(player, key);
            if (inv != null) {
                player.openInventory(inv);
            }
        }, 2L);
    }

    private void openEventSelectDelayed(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Session session = sessions.get(player.getUniqueId());
            if (session == null) return;
            Inventory inv = createEventInventory(session);
            player.openInventory(inv);
        }, 2L);
    }

    private void openCapturesDelayed(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Session session = sessions.get(player.getUniqueId());
            if (session == null) return;
            Inventory inv = createCapturesInventory(session);
            player.openInventory(inv);
        }, 2L);
    }

    private Inventory createMainInventory(Session session) {
        String tabName = m("gui.editor.tab." + session.tab.key());
        String title = m("gui.editor.title.main").replace("%tab%", tabName);

        GuiHolder holder = new GuiHolder(HOLDER_MAIN);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, null);
        }

        renderTabs(inv, session.tab);
        renderFields(inv, session.draft, session.tab);
        renderControls(inv);

        return inv;
    }

    private Inventory createListInventory(Player player, String key) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return null;

        ensureActionGroups(session.draft);
        List<String> list = getListReference(session.draft, key);
        if (list == null) {
            player.sendMessage(m("gui.editor.error.list_unknown", "&cUnsupported list key: ") + key);
            return null;
        }

        String title = m("gui.editor.title.list").replace("%key%", key);

        GuiHolder holder = new GuiHolder(HOLDER_LIST + ":" + key);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, null);
        }

        int idx = 0;
        for (String value : list) {
            if (idx >= (CONTENT_END - CONTENT_START + 1)) break;
            int slot = CONTENT_START + idx;

            String name = value == null || value.isEmpty() ? m("gui.editor.common.empty") : value;
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Index " + idx);
            lore.add("");
            lore.add(m("gui.editor.common.text.left"));
            lore.add(m("gui.editor.common.text.right"));

            ItemStack item = new ItemBuilder(Material.PAPER)
                    .setName(name)
                    .setLore(lore)
                    .hideAllFlags()
                    .build();

            inv.setItem(slot, item);
            idx++;
        }

        int addSlot = CONTENT_START + idx;
        if (addSlot <= CONTENT_END) {
            String addName = m("gui.editor.list.add.name", "&aAdd");
            String addLore1 = m("gui.editor.list.add.lore1", "&7Click to add entry");
            ItemStack add = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .setName(addName)
                    .setLore(Collections.singletonList(addLore1))
                    .hideAllFlags()
                    .build();
            inv.setItem(addSlot, add);
        }

        ItemStack back = new ItemBuilder(Material.ARROW)
                .setName(m("gui.editor.captures.back.name"))
                .setLore(Collections.singletonList(m("gui.editor.captures.back.lore")))
                .hideAllFlags()
                .build();
        inv.setItem(53, back);

        return inv;
    }

    private Inventory createEventInventory(Session session) {
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int total = BUILTIN_EVENTS.size();
        int maxPage = Math.max(1, (total + pageSize - 1) / pageSize);
        if (session.eventPage < 0) session.eventPage = 0;
        if (session.eventPage >= maxPage) session.eventPage = maxPage - 1;

        int page = session.eventPage + 1;

        String title = m("gui.editor.title.event")
                .replace("%page%", String.valueOf(page))
                .replace("%max%", String.valueOf(maxPage));

        GuiHolder holder = new GuiHolder(HOLDER_EVENT);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, null);
        }

        int startIndex = session.eventPage * pageSize;
        int idx = 0;
        for (int i = startIndex; i < total && idx < pageSize; i++, idx++) {
            String ev = BUILTIN_EVENTS.get(i);
            String name = m("gui.editor.event.entry.name").replace("%event%", ev);
            String lore1 = m("gui.editor.event.entry.lore1");
            String lore2 = m("gui.editor.event.entry.lore2");

            List<String> lore = new ArrayList<>();
            lore.add(lore1);
            lore.add(lore2);

            ItemStack item = new ItemBuilder(Material.PAPER)
                    .setName(name)
                    .setLore(lore)
                    .hideAllFlags()
                    .build();

            inv.setItem(CONTENT_START + idx, item);
        }

        List<String> helpLore = new ArrayList<>();
        helpLore.add(m("gui.editor.event.help.help1"));
        helpLore.add(m("gui.editor.event.help.help2"));
        helpLore.add(m("gui.editor.event.help.help3"));

        ItemStack help = new ItemBuilder(Material.WRITABLE_BOOK)
                .setName(m("gui.editor.event.help.title"))
                .setLore(helpLore)
                .hideAllFlags()
                .build();
        inv.setItem(40, help);

        if (session.eventPage > 0) {
            String namePrev = m("gui.editor.event.prev.name");
            String lorePrev = m("gui.editor.event.prev.lore").replace("%page%", String.valueOf(page - 1));
            ItemStack prev = new ItemBuilder(Material.ARROW)
                    .setName(namePrev)
                    .setLore(Collections.singletonList(lorePrev))
                    .hideAllFlags()
                    .build();
            inv.setItem(45, prev);
        }

        ItemStack back = new ItemBuilder(Material.BARRIER)
                .setName(m("gui.editor.event.back.name"))
                .setLore(Collections.singletonList(m("gui.editor.event.back.lore")))
                .hideAllFlags()
                .build();
        inv.setItem(49, back);

        if (session.eventPage < maxPage - 1) {
            String nameNext = m("gui.editor.event.next.name");
            String loreNext = m("gui.editor.event.next.lore").replace("%page%", String.valueOf(page + 1));
            ItemStack next = new ItemBuilder(Material.ARROW)
                    .setName(nameNext)
                    .setLore(Collections.singletonList(loreNext))
                    .hideAllFlags()
                    .build();
            inv.setItem(53, next);
        }

        return inv;
    }

    private Inventory createCapturesInventory(Session session) {
        String title = m("gui.editor.title.captures");

        GuiHolder holder = new GuiHolder(HOLDER_CAPTURES);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, null);
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(session.draft.customCaptures.entrySet());
        int idx = 0;
        for (Map.Entry<String, String> entry : entries) {
            if (idx >= (CONTENT_END - CONTENT_START + 1)) break;

            String key = entry.getKey();
            String chain = entry.getValue();

            String name = m("gui.editor.captures.entry.name").replace("%key%", key);
            String chainLine = m("gui.editor.captures.entry.chain").replace("%chain%", chain);
            String left = m("gui.editor.captures.entry.left");
            String right = m("gui.editor.captures.entry.right");

            List<String> lore = new ArrayList<>();
            lore.add(chainLine);
            lore.add("");
            lore.add(left);
            lore.add(right);

            ItemStack item = new ItemBuilder(Material.PAPER)
                    .setName(name)
                    .setLore(lore)
                    .hideAllFlags()
                    .build();

            inv.setItem(CONTENT_START + idx, item);
            idx++;
        }

        int addSlot = CONTENT_START + idx;
        if (addSlot <= CONTENT_END) {
            List<String> lore = new ArrayList<>();
            lore.add(m("gui.editor.captures.add.lore1"));
            lore.add(m("gui.editor.captures.add.lore2"));

            ItemStack add = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .setName(m("gui.editor.captures.add.name"))
                    .setLore(lore)
                    .hideAllFlags()
                    .build();
            inv.setItem(addSlot, add);
        }

        ItemStack back = new ItemBuilder(Material.ARROW)
                .setName(m("gui.editor.captures.back.name"))
                .setLore(Collections.singletonList(m("gui.editor.captures.back.lore")))
                .hideAllFlags()
                .build();
        inv.setItem(53, back);

        return inv;
    }

    private void renderTabs(Inventory inv, EditorTab current) {
        EditorTab[] values = EditorTab.values();
        for (int i = 0; i < values.length; i++) {
            EditorTab tab = values[i];
            boolean selected = (tab == current);

            String base = m("gui.editor.tab." + tab.key());
            String nameTemplate = selected ? m("gui.editor.tab.selected") : m("gui.editor.tab.normal");
            String name = nameTemplate.replace("%name%", base);

            String loreLine = selected ? m("gui.editor.tab.lore.current") : m("gui.editor.tab.lore.switch");

            Material mat = selected ? Material.BLUE_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE;

            ItemStack item = new ItemBuilder(mat)
                    .setName(name)
                    .setLore(Collections.singletonList(loreLine))
                    .hideAllFlags()
                    .build();

            inv.setItem(i, item);
        }
    }

    private void renderFields(Inventory inv, QuestEditorDraft d, EditorTab tab) {
        ensureActionGroups(d);
        switch (tab) {
            case DISPLAY -> renderDisplayTab(inv, d);
            case EVENT -> renderEventTab(inv, d);
            case CUSTOM_EVENT -> renderCustomEventTab(inv, d);
            case TARGETS -> renderTargetsTab(inv, d);
            case META -> renderMetaTab(inv, d);
            case ACTIONS -> renderActionsTab(inv, d);
            case CONDITIONS -> renderConditionsTab(inv, d);
            case OPTIONS -> renderOptionsTab(inv, d);
            case CHAIN -> renderChainTab(inv, d);
        }
    }

    private void renderDisplayTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, textItem("gui.editor.display.title.label", d.displayTitle));
        inv.setItem(12, listItem("gui.editor.display.description.label", d.displayDescription));
        inv.setItem(14, iconItem(d));
        inv.setItem(16, textItem("gui.editor.display.progress.label", d.displayProgress));

        inv.setItem(19, numberItem("gui.editor.display.cmd.label", d.displayCustomModelData));
        inv.setItem(21, textItem("gui.editor.display.hint.label", d.displayHint));
        inv.setItem(23, textItem("gui.editor.display.reward.label", d.displayReward));
        inv.setItem(25, textItem("gui.editor.display.category.label", d.displayCategory));
        inv.setItem(28, textItem("gui.editor.display.difficulty.label", d.displayDifficulty));

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.display.icon.help.help1"));
        lore.add(m("gui.editor.display.icon.help.help2"));
        lore.add(m("gui.editor.display.icon.help.help3"));
        lore.add(m("gui.editor.display.icon.help.help4"));

        ItemStack info = new ItemBuilder(Material.ITEM_FRAME)
                .setName(m("gui.editor.display.icon.help.title"))
                .setLore(lore)
                .hideAllFlags()
                .build();
        inv.setItem(40, info);
    }

    private void renderEventTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, textItem("gui.editor.event.event.label", d.event));
        inv.setItem(12, textItem("gui.editor.event.startmode.label", d.startMode.name()));
        inv.setItem(14, textItem("gui.editor.event.type.label", d.type));

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.event.help.help1"));
        lore.add(m("gui.editor.event.help.help2"));
        lore.add(m("gui.editor.event.help.help3"));

        ItemStack help = new ItemBuilder(Material.WRITABLE_BOOK)
                .setName(m("gui.editor.event.help.title"))
                .setLore(lore)
                .hideAllFlags()
                .build();
        inv.setItem(40, help);
    }

    private void renderCustomEventTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, textItem("gui.editor.custom.eventclass.label", d.customEventClass));
        inv.setItem(12, textItem("gui.editor.custom.playergetter.label", d.customPlayerGetter));

        int count = d.customCaptures.size();
        String name = m("gui.editor.custom.captures.label");
        String entries = m("gui.editor.custom.captures.entries").replace("%count%", String.valueOf(count));
        String left = m("gui.editor.custom.captures.left");
        String right = m("gui.editor.custom.captures.right");

        List<String> lore = new ArrayList<>();
        lore.add(entries);
        lore.add("");
        lore.add(left);
        lore.add(right);

        ItemStack captures = new ItemBuilder(Material.BOOK)
                .setName(name)
                .setLore(lore)
                .hideAllFlags()
                .build();
        inv.setItem(14, captures);
    }

    private void renderTargetsTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, listItem("gui.editor.targets.targets.label", d.targets));
        inv.setItem(12, numberItem("gui.editor.targets.amount.label", d.amount));
        inv.setItem(14, numberItem("gui.editor.targets.repeat.label", d.repeat));
        inv.setItem(16, numberItem("gui.editor.targets.points.label", d.points));
    }

    private void renderMetaTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, textItem("gui.editor.meta.id.label", d.id));
        inv.setItem(12, textItem("gui.editor.meta.name.label", d.name));
    }

    private void renderActionsTab(Inventory inv, QuestEditorDraft d) {
        ensureActionGroups(d);

        int[] slots = {
                10, 12, 14,
                19, 21, 23,
                28, 30
        };
        ActionGroup[] groups = ActionGroup.values();

        for (int i = 0; i < groups.length && i < slots.length; i++) {
            ActionGroup g = groups[i];
            List<String> lines = d.actions.getOrDefault(g.key, Collections.emptyList());

            String groupName = m("gui.editor.actions.group." + g.key);
            String linesText = m("gui.editor.actions.lines").replace("%count%", String.valueOf(lines.size()));
            String left = m("gui.editor.actions.left");
            String right = m("gui.editor.actions.right");

            List<String> lore = new ArrayList<>();
            lore.add(linesText);
            lore.add("");
            lore.add(left);
            lore.add(right);

            ItemStack item = new ItemBuilder(Material.BOOK)
                    .setName(groupName)
                    .setLore(lore)
                    .hideAllFlags()
                    .build();
            inv.setItem(slots[i], item);
        }

        List<String> infoLore = new ArrayList<>();
        infoLore.add(m("gui.editor.actions.help.help1"));
        infoLore.add(m("gui.editor.actions.help.help2"));
        infoLore.add(m("gui.editor.actions.help.help3"));
        infoLore.add(m("gui.editor.actions.help.help4"));

        ItemStack info = new ItemBuilder(Material.MAP)
                .setName(m("gui.editor.actions.help.title"))
                .setLore(infoLore)
                .hideAllFlags()
                .build();
        inv.setItem(40, info);
    }

    private void renderConditionsTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, listItem("gui.editor.conditions.start.label", d.condStart));
        inv.setItem(12, listItem("gui.editor.conditions.success.label", d.condSuccess));
        inv.setItem(14, listItem("gui.editor.conditions.fail.label", d.condFail));
        // === HELP ITEM 추가 ===
        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.conditions.help.help1"));
        lore.add(m("gui.editor.conditions.help.help2"));
        lore.add(m("gui.editor.conditions.help.help3"));

        ItemStack help = new ItemBuilder(Material.WRITABLE_BOOK)
                .setName(m("gui.editor.conditions.help.title"))
                .setLore(lore)
                .hideAllFlags()
                .build();

        inv.setItem(40, help);
    }

    private void renderOptionsTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, textItem("gui.editor.options.resetpolicy.label", d.resetPolicy));
        inv.setItem(12, textItem("gui.editor.options.resettime.label", d.resetTime));
        inv.setItem(28, booleanItem("gui.editor.options.public.label", d.isPublic));
        inv.setItem(30, booleanItem("gui.editor.options.party.label", d.party));
        // === HELP ITEM 추가 ===
        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.options.help.help1"));
        lore.add(m("gui.editor.options.help.help2"));
        lore.add(m("gui.editor.options.help.help3"));
        lore.add(m("gui.editor.options.help.help4"));
        lore.add(m("gui.editor.options.help.help5"));
        lore.add(m("gui.editor.options.help.help6"));
        lore.add(m("gui.editor.options.help.help7"));
        lore.add(m("gui.editor.options.help.help8"));

        ItemStack help = new ItemBuilder(Material.MAP)
                .setName(m("gui.editor.options.help.title"))
                .setLore(lore)
                .hideAllFlags()
                .build();

        inv.setItem(40, help);
    }

    private void renderChainTab(Inventory inv, QuestEditorDraft d) {
        inv.setItem(10, textItem("gui.editor.chain.next.label", d.nextQuestOnComplete));
    }

    private void renderControls(Inventory inv) {
        List<String> saveLore = new ArrayList<>();
        saveLore.add(m("gui.editor.control.save.lore1"));
        saveLore.add(m("gui.editor.control.save.lore2"));

        ItemStack save = new ItemBuilder(Material.EMERALD_BLOCK)
                .setName(m("gui.editor.control.save.name"))
                .setLore(saveLore)
                .hideAllFlags()
                .build();
        inv.setItem(45, save);

        ItemStack close = new ItemBuilder(Material.BARRIER)
                .setName(m("gui.editor.control.close.name"))
                .setLore(Collections.singletonList(m("gui.editor.control.close.lore")))
                .hideAllFlags()
                .build();
        inv.setItem(49, close);
    }

    private ItemStack textItem(String labelPath, String value) {
        String label = m(labelPath);
        String val = (value == null || value.isEmpty()) ? m("gui.editor.common.empty") : value;

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.common.text.value").replace("%value%", val));
        lore.add("");
        lore.add(m("gui.editor.common.text.left"));
        lore.add(m("gui.editor.common.text.right"));

        return new ItemBuilder(Material.PAPER)
                .setName(m("gui.editor.common.text.name").replace("%label%", label))
                .setLore(lore)
                .hideAllFlags()
                .build();
    }

    private ItemStack numberItem(String labelPath, int value) {
        String label = m(labelPath);
        String val = String.valueOf(value);

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.common.number.value").replace("%value%", val));
        lore.add("");
        lore.add(m("gui.editor.common.number.edit"));

        return new ItemBuilder(Material.REPEATER)
                .setName(m("gui.editor.common.number.name").replace("%label%", label))
                .setLore(lore)
                .hideAllFlags()
                .build();
    }

    private ItemStack booleanItem(String labelPath, boolean value) {
        String label = m(labelPath);
        String val = String.valueOf(value);

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.common.boolean.value").replace("%value%", val));
        lore.add("");
        lore.add(m("gui.editor.common.boolean.edit"));

        Material mat = value ? Material.LIME_DYE : Material.GRAY_DYE;

        return new ItemBuilder(mat)
                .setName(m("gui.editor.common.boolean.name").replace("%label%", label))
                .setLore(lore)
                .hideAllFlags()
                .build();
    }

    private ItemStack listItem(String labelPath, List<String> list) {
        String label = m(labelPath);
        int count = list == null ? 0 : list.size();

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.editor.common.list.entries").replace("%count%", String.valueOf(count)));
        lore.add("");
        lore.add(m("gui.editor.common.list.edit"));

        return new ItemBuilder(Material.BOOK)
                .setName(m("gui.editor.common.list.name").replace("%label%", label))
                .setLore(lore)
                .hideAllFlags()
                .build();
    }

    private ItemStack iconItem(QuestEditorDraft d) {
        Material mat;
        try {
            mat = Material.valueOf(d.displayIcon.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            mat = Material.BOOK;
        }

        ItemBuilder builder = new ItemBuilder(mat);

        String name = m("gui.editor.display.icon.name");
        List<String> lore = new ArrayList<>();

        lore.add(m("gui.editor.display.icon.value").replace("%value%", mat.name()));
        builder.setModelData(d.displayCustomModelData);

        lore.add("");
        lore.add(m("gui.editor.display.icon.left"));
        lore.add(m("gui.editor.display.icon.right"));
        lore.add(m("gui.editor.display.icon.inv"));

        return builder
                .setName(name)
                .setLore(lore)
                .hideAllFlags()
                .build();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player player)) return;

        if (!(e.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) return;
        String id = holder.id();
        if (id == null) return;

        e.setCancelled(true);

        if (id.startsWith(HOLDER_MAIN)) {
            handleMainClick(player, e);
        } else if (id.startsWith(HOLDER_LIST)) {
            handleListClick(player, e, id, e.getView().getTitle());
        } else if (id.equals(HOLDER_EVENT)) {
            handleEventSelectClick(player, e);
        } else if (id.equals(HOLDER_CAPTURES)) {
            handleCapturesClick(player, e);
        } else if (id.startsWith("QEDITOR_QLIST_")) {
            handleQuestListClick(player, e, id);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder holder) {
            String id = holder.id();
            if (id != null && id.startsWith("Q_EDITOR_")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) return;
        String id = holder.id();
        if (id == null || !id.startsWith("Q_EDITOR_")) return;
    }

    private void handleMainClick(Player player, InventoryClickEvent e) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int rawSlot = e.getRawSlot();
        ClickType click = e.getClick();

        EditorTab[] tabs = EditorTab.values();
        if (rawSlot >= 0 && rawSlot < tabs.length) {
            EditorTab target = tabs[rawSlot];
            if (target != session.tab) {
                session.tab = target;
                try {
                    player.closeInventory();
                } catch (Throwable ignored) {
                }
                openMainDelayed(player, session);
            }
            return;
        }

        if (session.tab == EditorTab.DISPLAY && rawSlot >= SIZE) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                session.draft.displayIcon = clicked.getType().name();
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.hasCustomModelData()) {
                    session.draft.displayCustomModelData = meta.getCustomModelData();
                } else {
                    session.draft.displayCustomModelData = -1;
                }
                openMainDelayed(player, session);
            }
            return;
        }

        if (rawSlot == 45) {
            saveDraft(player, session.draft);
            return;
        }
        if (rawSlot == 49) {
            player.closeInventory();
            return;
        }

        if (rawSlot < 0 || rawSlot >= SIZE) return;

        handleFieldClick(player, session, e);
    }

    private void handleFieldClick(Player player, Session session, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        ClickType click = e.getClick();
        QuestEditorDraft d = session.draft;
        EditorTab tab = session.tab;

        switch (tab) {
            case DISPLAY -> handleDisplayClick(player, d, slot, click);
            case EVENT -> handleEventClick(player, d, slot, click, session);
            case CUSTOM_EVENT -> handleCustomEventClick(player, d, slot, click);
            case TARGETS -> handleTargetsClick(player, d, slot, click);
            case META -> handleMetaClick(player, d, slot, click);
            case ACTIONS -> handleActionsClick(player, d, slot, click);
            case CONDITIONS -> handleConditionsClick(player, d, slot, click);
            case OPTIONS -> handleOptionsClick(player, d, slot, click);
            case CHAIN -> handleChainClick(player, d, slot, click);
        }
    }

    private void handleDisplayClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        if (slot == 10) {
            if (right) {
                d.displayTitle = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_title"), (player, msg) -> {
                    d.displayTitle = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 12) {
            if (left) {
                openListDelayed(p, "display.description");
            }
        } else if (slot == 14) {
            if (right) {
                d.displayIcon = "BOOK";
                d.displayCustomModelData = -1;
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_icon"), (player, msg) -> {
                    d.displayIcon = msg.toUpperCase(Locale.ROOT);
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 16) {
            if (right) {
                d.displayProgress = "&7%value%/%target%";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_progress"), (player, msg) -> {
                    d.displayProgress = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 19) {
            if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_cmd"), (player, msg) -> {
                    try {
                        d.displayCustomModelData = Integer.parseInt(msg.trim());
                    } catch (NumberFormatException ignored) {
                        d.displayCustomModelData = -1;
                    }
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 21) {
            if (right) {
                d.displayHint = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_hint"), (player, msg) -> {
                    d.displayHint = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 23) {
            if (right) {
                d.displayReward = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_reward"), (player, msg) -> {
                    d.displayReward = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 25) {
            if (right) {
                d.displayCategory = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_category"), (player, msg) -> {
                    d.displayCategory = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 28) {
            if (right) {
                d.displayDifficulty = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.display_difficulty"), (player, msg) -> {
                    d.displayDifficulty = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        }
    }

    private void handleEventClick(Player p, QuestEditorDraft d, int slot, ClickType click, Session session) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();
        boolean shift = click.isShiftClick();

        if (slot == 10) {
            if (right) {
                d.event = "CUSTOM";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (shift && left) {
                openEventSelectDelayed(p);
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.event_name"), (player, msg) -> {
                    d.event = msg.toUpperCase(Locale.ROOT);
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 12) {
            if (left) {
                QuestDef.StartMode[] modes = QuestDef.StartMode.values();
                int idx = 0;
                for (int i = 0; i < modes.length; i++) {
                    if (modes[i] == d.startMode) {
                        idx = i;
                        break;
                    }
                }
                idx = (idx + 1) % modes.length;
                d.startMode = modes[idx];
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            }
        } else if (slot == 14) {
            if (right) {
                d.type = "vanilla";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.event_type"), (player, msg) -> {
                    d.type = msg.toLowerCase(Locale.ROOT);
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        }
    }

    private void handleCustomEventClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        if (slot == 10) {
            if (right) {
                d.customEventClass = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.custom_event_class"), (player, msg) -> {
                    d.customEventClass = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 12) {
            if (right) {
                d.customPlayerGetter = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.custom_player_getter"), (player, msg) -> {
                    d.customPlayerGetter = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 14) {
            if (right) {
                d.customCaptures.clear();
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                openCapturesDelayed(p);
            }
        }
    }

    private void handleTargetsClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();

        if (slot == 10) {
            if (left) {
                openListDelayed(p, "targets");
            }
        } else if (slot == 12) {
            if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.targets_amount"), (player, msg) -> {
                    try {
                        d.amount = Math.max(1, Integer.parseInt(msg.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 14) {
            if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.targets_repeat"), (player, msg) -> {
                    try {
                        d.repeat = Integer.parseInt(msg.trim());
                    } catch (NumberFormatException ignored) {
                    }
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 16) {
            if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.targets_points"), (player, msg) -> {
                    try {
                        d.points = Math.max(0, Integer.parseInt(msg.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        }
    }

    private void handleMetaClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        if (slot == 10) {
            if (right) {
                d.id = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.meta_id"), (player, msg) -> {
                    d.id = msg.toLowerCase(Locale.ROOT);
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 12) {
            if (right) {
                d.name = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.meta_name"), (player, msg) -> {
                    d.name = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        }
    }

    private void handleActionsClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        int[] slots = {
                10, 12, 14,
                19, 21, 23,
                28, 30
        };
        ActionGroup[] groups = ActionGroup.values();

        for (int i = 0; i < groups.length && i < slots.length; i++) {
            if (slot == slots[i]) {
                ActionGroup g = groups[i];
                List<String> list = d.actions.getOrDefault(g.key, new ArrayList<>());

                if (right) {
                    list.clear();
                    d.actions.put(g.key, list);
                    openMainDelayed(p, sessions.get(p.getUniqueId()));
                } else if (left) {
                    openListDelayed(p, "actions." + g.key);
                }
                return;
            }
        }
    }

    private void handleConditionsClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();

        if (slot == 10) {
            if (left) openListDelayed(p, "conditions.start");
        } else if (slot == 12) {
            if (left) openListDelayed(p, "conditions.success");
        } else if (slot == 14) {
            if (left) openListDelayed(p, "conditions.fail");
        }
    }

    private void handleOptionsClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        if (slot == 10) {
            if (right) {
                d.resetPolicy = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.options_resetpolicy"), (player, msg) -> {
                    d.resetPolicy = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 12) {
            if (right) {
                d.resetTime = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.options_resettime"), (player, msg) -> {
                    d.resetTime = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        } else if (slot == 28) {
            if (left) {
                d.isPublic = !d.isPublic;
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            }
        } else if (slot == 30) {
            if (left) {
                d.party = !d.party;
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            }
        }
    }

    private void handleChainClick(Player p, QuestEditorDraft d, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        if (slot == 10) {
            if (right) {
                d.nextQuestOnComplete = "";
                openMainDelayed(p, sessions.get(p.getUniqueId()));
            } else if (left) {
                p.closeInventory();
                ChatInput.await(p, m("gui.editor.prompt.chain_next"), (player, msg) -> {
                    d.nextQuestOnComplete = msg;
                    openMainDelayed(player, sessions.get(player.getUniqueId()));
                });
            }
        }
    }

    private void handleListClick(Player player, InventoryClickEvent e, String holderId, String title) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        String key;
        if (holderId.contains(":")) {
            key = holderId.substring(holderId.indexOf(':') + 1);
        } else {
            String rawTitle = ChatColor.stripColor(title);
            String prefix = m("gui.editor.title.list.prefix");
            int idx = rawTitle.indexOf(prefix);
            if (idx >= 0) {
                key = rawTitle.substring(idx + prefix.length()).trim();
            } else {
                key = rawTitle;
            }
        }

        List<String> list = getListReference(session.draft, key);
        if (list == null) return;

        int slot = e.getRawSlot();
        ClickType click = e.getClick();

        if (slot == 53) {
            try {
                player.closeInventory();
            } catch (Throwable ignored) {
            }
            openMainDelayed(player, session);
            return;
        }

        if (slot < CONTENT_START || slot > CONTENT_END) return;

        int index = slot - CONTENT_START;

        if (index < list.size()) {
            if (click.isRightClick()) {
                list.remove(index);
                openListDelayed(player, key);
            } else if (click.isLeftClick()) {
                String old = list.get(index);
                player.closeInventory();
                String prompt = m("gui.editor.prompt.list_edit").replace("%old%", old);
                ChatInput.await(player, prompt, (p, msg) -> {
                    list.set(index, msg);
                    openListDelayed(p, key);
                });
            }
        } else if (index == list.size()) {
            if (click.isLeftClick()) {
                player.closeInventory();
                ChatInput.await(player, m("gui.editor.prompt.list_add"), (p, msg) -> {
                    list.add(msg);
                    openListDelayed(p, key);
                });
            }
        }
    }

    private void handleEventSelectClick(Player player, InventoryClickEvent e) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int slot = e.getRawSlot();

        if (slot == 45) {
            if (session.eventPage > 0) {
                session.eventPage--;
                Inventory inv = createEventInventory(session);
                player.openInventory(inv);
            }
            return;
        }

        if (slot == 53) {
            int pageSize = CONTENT_END - CONTENT_START + 1;
            int total = BUILTIN_EVENTS.size();
            int maxPage = Math.max(1, (total + pageSize - 1) / pageSize);
            if (session.eventPage < maxPage - 1) {
                session.eventPage++;
                Inventory inv = createEventInventory(session);
                player.openInventory(inv);
            }
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            openMainDelayed(player, session);
            return;
        }

        if (slot < CONTENT_START || slot > CONTENT_END) return;

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name == null || name.isEmpty()) return;

        session.draft.event = name;
        player.closeInventory();
        openMainDelayed(player, session);
    }

    private void handleCapturesClick(Player player, InventoryClickEvent e) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int slot = e.getRawSlot();
        ClickType click = e.getClick();

        if (slot == 53) {
            player.closeInventory();
            openMainDelayed(player, session);
            return;
        }

        if (slot < CONTENT_START || slot > CONTENT_END) return;

        int index = slot - CONTENT_START;
        List<Map.Entry<String, String>> entries = new ArrayList<>(session.draft.customCaptures.entrySet());

        if (index < entries.size()) {
            Map.Entry<String, String> entry = entries.get(index);
            String key = entry.getKey();
            String chain = entry.getValue();

            if (click.isRightClick()) {
                session.draft.customCaptures.remove(key);
                openCapturesDelayed(player);
            } else if (click.isLeftClick()) {
                String line = key + ";" + chain;
                String prompt = m("gui.editor.prompt.captures_edit").replace("%line%", line);
                player.closeInventory();
                ChatInput.await(player, prompt, (p, msg) -> {
                    if (!applyCaptureLine(session.draft, msg.trim(), true)) {
                        p.sendMessage(m("gui.editor.error.captures_format"));
                    }
                    openCapturesDelayed(p);
                });
            }
        } else if (index == entries.size()) {
            if (click.isLeftClick()) {
                String example = m("gui.editor.custom.captures.format");
                String prompt = m("gui.editor.prompt.captures_add").replace("%example%", example);
                player.closeInventory();
                ChatInput.await(player, prompt, (p, msg) -> {
                    if (!applyCaptureLine(session.draft, msg.trim(), false)) {
                        p.sendMessage(m("gui.editor.error.captures_format"));
                    }
                    openCapturesDelayed(p);
                });
            }
        }
    }

    private boolean applyCaptureLine(QuestEditorDraft d, String line, boolean replaceIfExists) {
        if (line == null || line.isEmpty()) return false;

        int semi = line.indexOf(';');
        if (semi <= 0 || semi == line.length() - 1) return false;

        String key = line.substring(0, semi).trim();
        String chain = line.substring(semi + 1).trim();
        if (key.isEmpty() || chain.isEmpty()) return false;

        int len = key.length();
        if (len > 2 && key.charAt(0) == '%' && key.charAt(len - 1) == '%') {
            key = key.substring(1, len - 1);
        }

        if (key.isEmpty()) return false;

        if (!replaceIfExists && d.customCaptures.containsKey(key)) {
            return false;
        }
        d.customCaptures.put(key, chain);
        return true;
    }

    private List<String> getListReference(QuestEditorDraft d, String key) {
        return switch (key) {
            case "display.description" -> d.displayDescription;
            case "targets" -> d.targets;
            case "conditions.start" -> d.condStart;
            case "conditions.success" -> d.condSuccess;
            case "conditions.fail" -> d.condFail;
            default -> {
                if (key.startsWith("actions.")) {
                    String group = key.substring("actions.".length());
                    ensureActionGroups(d);
                    yield d.actions.computeIfAbsent(group, k -> new ArrayList<>());
                }
                yield null;
            }
        };
    }

    private void ensureActionGroups(QuestEditorDraft d) {
        for (ActionGroup g : ActionGroup.values()) {
            d.actions.computeIfAbsent(g.key, k -> new ArrayList<>());
        }
    }

    private void saveDraft(Player player, QuestEditorDraft draft) {
        if (draft.id == null || draft.id.trim().isEmpty()) {
            player.sendMessage(m("gui.editor.error.id_empty"));
            return;
        }

        try {
            QuestDef def = draft.buildQuestDef();
            org.bukkit.configuration.file.YamlConfiguration yml = QuestDef.toYaml(def);

            File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("quests.folder", "quests"));
            if (!folder.exists() && !folder.mkdirs()) {
                player.sendMessage(m("gui.editor.error.folder_create"));
                return;
            }
            File file = new File(folder, def.id + ".yml");
            yml.save(file);

            plugin.quests().reload();
            plugin.quests().rebuildEventMap();

            String msg = m("gui.editor.save.ok").replace("%id%", def.id);
            player.sendMessage(msg);
        } catch (Exception ex) {
            String msg = m("gui.editor.save.fail").replace("%msg%", ex.getMessage() == null ? "null" : ex.getMessage());
            player.sendMessage(msg);
            ex.printStackTrace();
        }
    }
    public void openNewWithId(Player player, String id) {
        QuestEditorDraft draft = new QuestEditorDraft();
        draft.id = id.toLowerCase(Locale.ROOT);
        draft.name = "&f" + id;
        draft.displayTitle = "&f" + id;

        ensureActionGroups(draft);

        Session session = new Session(draft, EditorTab.DISPLAY);
        sessions.put(player.getUniqueId(), session);

        openMainDelayed(player, session);
    }
    // ============================
    // /questeditor list
    // ============================
    public void openListSelection(Player player, int page) {

        List<QuestDef> all = new ArrayList<>(plugin.quests().all());
        all.sort(Comparator.comparing(q -> q.id));

        int maxPage = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        page = Math.max(1, Math.min(page, maxPage));

        String title = "§9Quest List - Page " + page;
        GuiHolder holder = new GuiHolder("QEDITOR_QLIST_" + page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // --- 여기가 핵심 수정 ---
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .build();
        plugin.gui().fill(inv, filler);
        // ---------------------------

        int start = (page - 1) * 45;
        int end = Math.min(start + 45, all.size());

        int slot = 0;
        for (int i = start; i < end; i++) {
            QuestDef def = all.get(i);

            ItemStack item = new ItemBuilder(Material.PAPER)
                    .setName("§f" + def.id)
                    .setLore(Collections.singletonList("§7Click to edit"))
                    .build();

            inv.setItem(slot++, item);
        }

        if (page > 1) {
            inv.setItem(45, new ItemBuilder(Material.ARROW)
                    .setName("§ePrevious Page")
                    .setLore(Collections.singletonList("§7Page " + (page - 1)))
                    .build());
        }

        if (page < maxPage) {
            inv.setItem(53, new ItemBuilder(Material.ARROW)
                    .setName("§eNext Page")
                    .setLore(Collections.singletonList("§7Page " + (page + 1)))
                    .build());
        }

        player.openInventory(inv);
    }

    // ============================
    // Quest List Selection Click
    // ============================
    private void handleQuestListClick(Player p, InventoryClickEvent e, String id) {

        // HOLDER ID 예: QEDITOR_QLIST_1
        if (!id.startsWith("QEDITOR_QLIST_")) return;

        e.setCancelled(true);

        String pageStr = id.substring("QEDITOR_QLIST_".length());
        int page = 1;
        try { page = Integer.parseInt(pageStr); } catch (Exception ignored) {}

        int slot = e.getRawSlot();

        // Prev
        if (slot == 45) {
            openListSelection(p, page - 1);
            return;
        }

        // Next
        if (slot == 53) {
            openListSelection(p, page + 1);
            return;
        }

        // Quest 선택
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() != Material.PAPER) return;

        String questId = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        QuestDef def = plugin.quests().get(questId);

        if (def == null) {
            p.sendMessage("§cQuest not found: " + questId);
            return;
        }
        openEdit(p, def);
    }
}
