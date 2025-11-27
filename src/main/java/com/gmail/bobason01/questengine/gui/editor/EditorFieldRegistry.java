package com.gmail.bobason01.questengine.gui.editor;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EditorFieldRegistry {

    private static final Map<EditorTab, List<EditorField>> FIELDS_BY_TAB = new EnumMap<>(EditorTab.class);

    static {
        FIELDS_BY_TAB.put(EditorTab.MAIN, List.of(
                new EditorField("id", EditorField.Kind.STRING, Material.NAME_TAG, "Quest Id"),
                new EditorField("name", EditorField.Kind.STRING, Material.PAPER, "Quest Name"),
                new EditorField("event", EditorField.Kind.STRING, Material.COMPARATOR, "Event"),
                new EditorField("type", EditorField.Kind.STRING, Material.REPEATER, "Type"),
                new EditorField("amount", EditorField.Kind.INT, Material.SLIME_BALL, "Amount"),
                new EditorField("repeat", EditorField.Kind.INT, Material.SLIME_BLOCK, "Repeat"),
                new EditorField("points", EditorField.Kind.INT, Material.NETHER_STAR, "Points"),
                new EditorField("isPublic", EditorField.Kind.BOOLEAN, Material.LIME_DYE, "Public"),
                new EditorField("party", EditorField.Kind.BOOLEAN, Material.PLAYER_HEAD, "Party"),
                new EditorField("nextQuestOnComplete", EditorField.Kind.STRING, Material.ARROW, "Next Quest")
        ));

        FIELDS_BY_TAB.put(EditorTab.DISPLAY, List.of(
                new EditorField("displayTitle", EditorField.Kind.STRING, Material.WRITABLE_BOOK, "Title"),
                new EditorField("displayDescription", EditorField.Kind.LIST, Material.WRITTEN_BOOK, "Description"),
                new EditorField("displayProgress", EditorField.Kind.STRING, Material.MAP, "Progress Format"),
                new EditorField("displayIcon", EditorField.Kind.STRING, Material.ITEM_FRAME, "Icon Material"),
                new EditorField("displayHint", EditorField.Kind.STRING, Material.OAK_SIGN, "Hint"),
                new EditorField("displayCustomModelData", EditorField.Kind.INT, Material.GLOW_ITEM_FRAME, "CustomModelData")
        ));

        FIELDS_BY_TAB.put(EditorTab.CONDITION, List.of(
                new EditorField("targets", EditorField.Kind.LIST, Material.BONE, "Targets"),
                new EditorField("condStart", EditorField.Kind.LIST, Material.REDSTONE_TORCH, "Start Conditions"),
                new EditorField("condSuccess", EditorField.Kind.LIST, Material.EMERALD, "Success Conditions"),
                new EditorField("condFail", EditorField.Kind.LIST, Material.BARRIER, "Fail Conditions")
        ));

        FIELDS_BY_TAB.put(EditorTab.EVENT, List.of()); // Empty

        FIELDS_BY_TAB.put(EditorTab.RESET, List.of(
                new EditorField("resetPolicy", EditorField.Kind.STRING, Material.CLOCK, "Reset Policy"),
                new EditorField("resetTime", EditorField.Kind.STRING, Material.CLOCK, "Reset Time")
        ));

        FIELDS_BY_TAB.put(EditorTab.ADVANCED, List.of()); // Empty
    }

    private EditorFieldRegistry() {}

    public static List<EditorField> getFields(EditorTab tab) {
        return FIELDS_BY_TAB.getOrDefault(tab, List.of());
    }

    public static EditorField findField(EditorTab tab, String key) {
        for (EditorField field : getFields(tab)) {
            if (field.key().equalsIgnoreCase(key)) return field;
        }
        return null;
    }
}