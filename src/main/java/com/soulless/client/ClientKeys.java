package com.soulless.client;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.Locale;

/** Key names for UI text, independent of the active keyboard layout (R stays "R" on a Russian layout). */
public final class ClientKeys {
    private static final String KEYBOARD = "key.keyboard.";

    public static String slamKeyName() {
        InputConstants.Key key = ClientModEvents.SLAM_KEY.getKey();
        String name = key.getName();
        if (key.getType() == InputConstants.Type.KEYSYM && name.startsWith(KEYBOARD)) {
            String id = name.substring(KEYBOARD.length());
            if (id.length() == 1) {
                return id.toUpperCase(Locale.ROOT);   // letters and digits: the US-layout label
            }
        }
        return key.getDisplayName().getString();       // F-keys, mouse buttons, etc.
    }

    private ClientKeys() {}
}
