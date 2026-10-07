package com.soulless.client;

import com.soulless.item.FamilyScrollItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Reads the Scroll of the Family History in the vanilla book view (lang keys book.soulless.family_scroll.N). */
public final class FamilyScrollScreen {
    public static void open() {
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
                return FamilyScrollItem.PAGES;
            }

            @Override
            public FormattedText getPageRaw(int index) {
                return Component.translatable("book.soulless.family_scroll." + (index + 1));
            }
        }));
    }

    private FamilyScrollScreen() {}
}
