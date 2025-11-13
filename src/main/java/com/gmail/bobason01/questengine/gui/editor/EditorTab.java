package com.gmail.bobason01.questengine.gui.editor;

public enum EditorTab {

    MAIN("Main"),
    DISPLAY("Display"),
    CONDITION("Condition"),
    EVENT("Event"),
    RESET("Reset"),
    ADVANCED("Advanced");

    private final String displayName;

    EditorTab(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
