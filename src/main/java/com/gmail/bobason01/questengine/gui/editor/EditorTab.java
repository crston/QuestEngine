package com.gmail.bobason01.questengine.gui.editor;

public enum EditorTab {

    META("Meta"),
    DISPLAY("Display"),
    TARGETS("Targets"),
    CONDITIONS("Conditions"),
    ACTIONS("Actions"),
    EVENT("Event"),
    CUSTOM_EVENT("Custom"),
    OPTIONS("Options"),
    CHAIN("Chain");

    private final String displayName;

    EditorTab(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase();
    }
}