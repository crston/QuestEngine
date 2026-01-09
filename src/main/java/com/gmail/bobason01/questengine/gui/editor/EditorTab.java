package com.gmail.bobason01.questengine.gui.editor;

public enum EditorTab {

    META("Meta"),            // ID, Name
    DISPLAY("Display"),      // Title, Icon, Lore...
    TARGETS("Targets"),      // Target list, Amount, Points...
    CONDITIONS("Conditions"),// Start/Success/Fail conditions
    ACTIONS("Actions"),      // Rewards, Commands...
    EVENT("Event"),          // Event type, Start mode
    CUSTOM_EVENT("Custom"),  // Custom event class/captures
    OPTIONS("Options"),      // Reset, Public, Party
    CHAIN("Chain");          // Next quest, Requirements

    private final String displayName;

    EditorTab(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    // 편의를 위한 키 반환 (소문자)
    public String key() {
        return name().toLowerCase();
    }
}