package com.gmail.bobason01.questengine.gui.editor;

import org.bukkit.Material;

public record EditorField(String key, Kind kind, Material icon, String label) {
    public enum Kind {
        STRING, INT, BOOLEAN, LIST
    }
}