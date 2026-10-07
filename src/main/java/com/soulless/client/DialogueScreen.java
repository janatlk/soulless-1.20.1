package com.soulless.client;

import com.soulless.network.DialogueChoicePacket;
import com.soulless.network.DialogueShowPacket;
import com.soulless.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialogue window: a dark panel at the bottom of the screen framed in the character's accent colour, the
 * speaker's name on a plate above it and the character itself standing on the panel, following the mouse. The text
 * types itself out (a click or Space shows it all), pages are clicked through, then the options appear: mouse,
 * arrows + Enter, or the number keys 1-9. Esc ends the conversation. The game is not paused.
 */
public class DialogueScreen extends Screen {
    private static final int TEXT = 0xFFEDE6D8, DIM = 0xFFA39B90, PANEL = 0xEC121014;
    private static final int PAD = 10, LINE = 11, ROW = 15, MAX_WIDTH = 380;
    private static final float CHARS_PER_TICK = 1.6F;

    private int entityId;
    private Component name = Component.empty();
    private int accent;
    private List<Component> pages = List.of();
    private List<Component> options = List.of();
    private int page;
    private List<String> lines = List.of();
    private int pageLength;
    private float typed;
    private int selected;
    private int ticks;
    private boolean waiting;
    private boolean closedByServer;
    /** The option row under the mouse, or -1. */
    private int hovered = -1;

    private DialogueScreen(DialogueShowPacket p) {
        super(p.name());
        load(p);
    }

    /** A packet from the server: open the window, show the next node in it, or close it. */
    public static void show(DialogueShowPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.entityId() < 0) {
            if (mc.screen instanceof DialogueScreen s) {
                s.closedByServer = true;
                s.onClose();
            }
            return;
        }
        if (mc.screen instanceof DialogueScreen s) s.load(p);
        else mc.setScreen(new DialogueScreen(p));
    }

    private void load(DialogueShowPacket p) {
        entityId = p.entityId();
        name = p.name();
        accent = p.accent();
        pages = p.pages().isEmpty() ? List.of(Component.empty()) : p.pages();
        options = p.options();
        selected = 0;
        waiting = false;
        setPage(0);
    }

    private void setPage(int i) {
        page = i;
        typed = 0;
        if (font != null) wrap();
    }

    @Override
    protected void init() {
        wrap();
    }

    private int panelWidth() {
        return Math.min(MAX_WIDTH, width - 24);
    }

    private void wrap() {
        List<String> out = new ArrayList<>();
        for (FormattedText t : font.getSplitter().splitLines(pages.get(page), panelWidth() - PAD * 2, Style.EMPTY)) {
            out.add(t.getString());
        }
        lines = out;
        pageLength = lines.stream().mapToInt(String::length).sum();
    }

    private boolean typing() {
        return typed < pageLength;
    }

    private boolean lastPage() {
        return page >= pages.size() - 1;
    }

    private boolean optionsShown() {
        return lastPage() && !typing() && !options.isEmpty();
    }

    @Override
    public void tick() {
        ticks++;
        if (typing()) typed = Math.min(pageLength, typed + CHARS_PER_TICK);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing

    private static int alpha(int color, int a) {
        return (color & 0xFFFFFF) | (a << 24);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float appear = Mth.clamp((ticks + partialTick) / 6F, 0, 1);
        appear = 1 - (1 - appear) * (1 - appear);
        g.fillGradient(0, height / 2, width, height, 0x00000000, alpha(0, (int) (0xA0 * appear)));

        int pw = panelWidth();
        int textLines = Math.max(lines.size(), 1);
        int rows = lastPage() ? options.size() : 0;
        int ph = PAD + textLines * LINE + (rows > 0 ? 8 + rows * ROW : 10) + PAD - 4;
        int px = (width - pw) / 2;
        int py = height - ph - 12 + (int) ((1 - appear) * 20);

        // the character standing on the panel, watching the mouse
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e instanceof LivingEntity living) {
            int ex = px + pw - 46, ey = py;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, ex, ey, 50, ex - mouseX, ey - 80 - mouseY, living);
        }

        // the panel and its frame
        g.fill(px, py, px + pw, py + ph, PANEL);
        int frame = alpha(accent, 0x90);
        g.hLine(px, px + pw - 1, py + ph - 1, frame);
        g.vLine(px, py, py + ph - 1, frame);
        g.vLine(px + pw - 1, py, py + ph - 1, frame);
        g.fill(px, py, px + pw, py + 2, accent);
        for (int[] c : new int[][]{{px - 2, py - 2}, {px + pw - 2, py - 2}, {px - 2, py + ph - 2}, {px + pw - 2, py + ph - 2}}) {
            g.fill(c[0], c[1], c[0] + 4, c[1] + 4, accent);
        }

        // the speaker's name plate
        int nw = font.width(name) + 16;
        int nx = px + 12, ny = py - 15;
        g.fill(nx, ny, nx + nw, py, PANEL);
        g.fill(nx, ny, nx + nw, ny + 1, accent);
        g.vLine(nx, ny, py, frame);
        g.vLine(nx + nw - 1, ny, py, frame);
        g.drawString(font, name, nx + 8, ny + 4, brighten(accent), false);

        // the text, typing itself out
        int left = (int) typed;
        int ty = py + PAD;
        for (String line : lines) {
            if (left <= 0) break;
            String shown = line.length() <= left ? line : cut(line, left);
            left -= line.length();
            g.drawString(font, shown, px + PAD, ty, TEXT, false);
            ty += LINE;
        }

        int base = py + PAD + textLines * LINE + 8;
        hovered = -1;
        if (optionsShown()) {
            for (int i = 0; i < options.size(); i++) {
                int ry = base + i * ROW;
                boolean hover = mouseX >= px + PAD - 4 && mouseX < px + pw - PAD + 4 && mouseY >= ry - 3 && mouseY < ry + ROW - 3;
                if (hover) hovered = i;
                if (hover && !waiting) selected = i;
                boolean sel = i == selected;
                if (sel) {
                    g.fill(px + PAD - 4, ry - 3, px + pw - PAD + 4, ry + ROW - 4, alpha(accent, 0x38));
                    g.fill(px + PAD - 4, ry - 3, px + PAD - 2, ry + ROW - 4, accent);
                }
                String num = (i + 1) + ".";
                g.drawString(font, num, px + PAD + 2, ry, sel ? brighten(accent) : alpha(accent, 0xB0), false);
                g.drawString(font, options.get(i), px + PAD + 6 + font.width("9."), ry, sel ? 0xFFFFFFFF : DIM, false);
            }
        } else if (!typing() && (ticks / 8) % 2 == 0) {
            // more to read (or the end): a blinking marker
            g.drawString(font, "▼", px + pw - PAD - font.width("▼"), py + ph - PAD - 4, accent, false);
        }
    }

    /** The first {@code n} characters, never splitting a § formatting code. */
    private static String cut(String s, int n) {
        if (n > 0 && s.charAt(n - 1) == '§') n--;
        return s.substring(0, n);
    }

    private static int brighten(int c) {
        int r = (c >> 16) & 0xFF, gr = (c >> 8) & 0xFF, b = c & 0xFF;
        r = r + (255 - r) / 3;
        gr = gr + (255 - gr) / 3;
        b = b + (255 - b) / 3;
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // ---- input

    /** Click / Space / Enter: finish the typing, turn the page, choose the selected option or close. */
    private void advance() {
        if (waiting) return;
        if (typing()) {
            typed = pageLength;
        } else if (!lastPage()) {
            setPage(page + 1);
            click(1.4F);
        } else if (options.isEmpty()) {
            onClose();
        } else {
            choose(selected);
        }
    }

    private void choose(int i) {
        if (waiting || i < 0 || i >= options.size()) return;
        waiting = true;
        click(1.0F);
        ModNetwork.sendToServer(new DialogueChoicePacket(i));
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // once the options are up, a click chooses only the one under the mouse
            if (optionsShown()) choose(hovered);
            else advance();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            advance();
            return true;
        }
        if (optionsShown() && !waiting) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
                selected = (selected + options.size() - 1) % options.size();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
                selected = (selected + 1) % options.size();
                return true;
            }
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
                choose(key - GLFW.GLFW_KEY_1);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (!closedByServer) ModNetwork.sendToServer(new DialogueChoicePacket(-1));
    }
}
