package com.gmail.bobason01.questengine.gui.editor;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EditorFieldRegistry {

    private static final Map<EditorTab, List<EditorField>> FIELDS_BY_TAB = new EnumMap<>(EditorTab.class);

    static {
        List<EditorField> main = new ArrayList<>();
        main.add(new EditorField("id", EditorField.Kind.STRING, Material.NAME_TAG, "Quest Id"));
        main.add(new EditorField("name", EditorField.Kind.STRING, Material.PAPER, "Quest Name"));
        main.add(new EditorField("event", EditorField.Kind.STRING, Material.COMPARATOR, "Event"));
        main.add(new EditorField("type", EditorField.Kind.STRING, Material.REPEATER, "Type"));
        main.add(new EditorField("amount", EditorField.Kind.INT, Material.SLIME_BALL, "Amount"));
        main.add(new EditorField("repeat", EditorField.Kind.INT, Material.SLIME_BLOCK, "Repeat"));
        main.add(new EditorField("points", EditorField.Kind.INT, Material.NETHER_STAR, "Points"));
        main.add(new EditorField("isPublic", EditorField.Kind.BOOLEAN, Material.LIME_DYE, "Public"));
        main.add(new EditorField("party", EditorField.Kind.BOOLEAN, Material.PLAYER_HEAD, "Party"));
        main.add(new EditorField("nextQuestOnComplete", EditorField.Kind.STRING, Material.ARROW, "Next Quest"));
        FIELDS_BY_TAB.put(EditorTab.MAIN, Collections.unmodifiableList(main));

        List<EditorField> display = new ArrayList<>();
        display.add(new EditorField("displayTitle", EditorField.Kind.STRING, Material.WRITABLE_BOOK, "Title"));
        display.add(new EditorField("displayDescription", EditorField.Kind.LIST, Material.WRITTEN_BOOK, "Description"));
        display.add(new EditorField("displayProgress", EditorField.Kind.STRING, Material.MAP, "Progress Format"));
        display.add(new EditorField("displayIcon", EditorField.Kind.STRING, Material.ITEM_FRAME, "Icon Material"));
        display.add(new EditorField("displayHint", EditorField.Kind.STRING, Material.OAK_SIGN, "Hint"));
        display.add(new EditorField("displayCustomModelData", EditorField.Kind.INT, Material.GLOW_ITEM_FRAME, "CustomModelData"));
        FIELDS_BY_TAB.put(EditorTab.DISPLAY, Collections.unmodifiableList(display));

        List<EditorField> condition = new ArrayList<>();
        condition.add(new EditorField("targets", EditorField.Kind.LIST, Material.BONE, "Targets"));
        condition.add(new EditorField("condStart", EditorField.Kind.LIST, Material.REDSTONE_TORCH, "Start Conditions"));
        condition.add(new EditorField("condSuccess", EditorField.Kind.LIST, Material.EMERALD, "Success Conditions"));
        condition.add(new EditorField("condFail", EditorField.Kind.LIST, Material.BARRIER, "Fail Conditions"));
        FIELDS_BY_TAB.put(EditorTab.CONDITION, Collections.unmodifiableList(condition));

        List<EditorField> event = new ArrayList<>();
        FIELDS_BY_TAB.put(EditorTab.EVENT, Collections.unmodifiableList(event));

        List<EditorField> reset = new ArrayList<>();
        reset.add(new EditorField("resetPolicy", EditorField.Kind.STRING, Material.CLOCK, "Reset Policy"));
        reset.add(new EditorField("resetTime", EditorField.Kind.STRING, Material.CLOCK, "Reset Time"));
        FIELDS_BY_TAB.put(EditorTab.RESET, Collections.unmodifiableList(reset));

        List<EditorField> advanced = new ArrayList<>();
        FIELDS_BY_TAB.put(EditorTab.ADVANCED, Collections.unmodifiableList(advanced));
    }

    private EditorFieldRegistry() {
    }

    public static List<EditorField> getFields(EditorTab tab) {
        List<EditorField> list = FIELDS_BY_TAB.get(tab);
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    public static EditorField findField(EditorTab tab, String key) {
        List<EditorField> list = getFields(tab);
        for (EditorField field : list) {
            if (field.key().equalsIgnoreCase(key)) {
                return field;
            }
        }
        return null;
    }
}
