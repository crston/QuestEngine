package com.gmail.bobason01.questengine.gui.editor;

import org.bukkit.Material;

public final class EditorField {

    public enum Kind {
        STRING,
        INT,
        BOOLEAN,
        LIST
    }

    private final String key;
    private final Kind kind;
    private final Material icon;
    private final String label;

    public EditorField(String key, Kind kind, Material icon, String label) {
        this.key = key;
        this.kind = kind;
        this.icon = icon;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    public Material icon() {
        return icon;
    }

    public String label() {
        return label;
    }
}
