package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;
import com.ecoquest.data.ShopInventory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders and handles navigation for the equipment shop.
 *
 * <p>The screen shares the parchment look and the {@link PixelFont} lettering of the level select:
 * a striped market awning, a 3 x 2 grid of item cards with pixel-art icons, a detail panel for the
 * focused item and a footer with the controls. Purchases, equipped gear and potions live in a
 * {@link ShopInventory} so they survive a restart and apply to every level.
 */
public class ShopManager {

    public interface ShopListener {
        void onBackToMenu();
        boolean onMoneyChanged(int money);
    }

    private enum ItemType {
        SMALL_BAG, MEDIUM_BAG, LARGE_BAG, SHOES, GLOVES, POTION
    }

    private static final class ShopItem {
        final String name;
        final String stat;
        final String detail;
        final String description;
        final int price;
        final ItemType type;

        ShopItem(String name, String stat, String detail, String description, int price, ItemType type) {
            this.name = name;
            this.stat = stat;
            this.detail = detail;
            this.description = description;
            this.price = price;
            this.type = type;
        }
    }

    // ------------------------------------------------------------------ layout (logical pixels)
    private static final int DESIGN_WIDTH = 1250;
    private static final int DESIGN_HEIGHT = 780;
    private static final int GRID_X = 29;
    private static final int CARD_W = 384;
    private static final int CARD_H = 212;
    private static final int CARD_GAP = 20;
    private static final int[] ROW_Y = {176, 408};
    private static final int GRID_RIGHT = GRID_X + CARD_W * 3 + CARD_GAP * 2;
    private static final Rectangle[] CARD_BOUNDS = new Rectangle[6];
    static {
        for (int i = 0; i < CARD_BOUNDS.length; i++) {
            CARD_BOUNDS[i] = new Rectangle(GRID_X + (i % 3) * (CARD_W + CARD_GAP), ROW_Y[i / 3], CARD_W, CARD_H);
        }
    }
    private static final Rectangle DESCRIPTION_BOUNDS = new Rectangle(GRID_X, 636, GRID_RIGHT - GRID_X, 68);
    private static final Rectangle CLOSE_BOUNDS = new Rectangle(GRID_RIGHT - 200, 718, 200, 38);
    private static final double MESSAGE_SECONDS = 4;

    // ------------------------------------------------------------------ palette (matches level select)
    private static final Color PARCHMENT = new Color(0xEFDCB4);
    private static final Color PARCHMENT_DARK = new Color(0xE2C892);
    private static final Color PARCHMENT_LIGHT = new Color(0xFBF1DA);
    private static final Color BORDER = new Color(0xC9A46A);
    private static final Color BORDER_DARK = new Color(0x9C7A46);
    private static final Color INK = new Color(0x48321B);
    private static final Color INK_SOFT = new Color(0x8A6A44);
    private static final Color FRAME_COLOR = new Color(0x1F1A17);
    private static final Color WOOD = new Color(0x5A3A22);
    private static final Color WOOD_LIGHT = new Color(0x7A5232);
    private static final Color GOLD = new Color(0xFFC931);
    private static final Color GOLD_LIGHT = new Color(0xFFE27A);
    private static final Color GREEN = new Color(0x3FA34D);
    private static final Color BLUE = new Color(0x4A8FD0);
    private static final Color LOCKED = new Color(0xCDB084);
    private static final Color AWNING_RED = new Color(0xD9534F);
    private static final Color AWNING_CREAM = new Color(0xF6E7C6);
    private static final Color CHIP_FILL = new Color(0xDFF3D2);
    private static final Color CHIP_BORDER = new Color(0x2E7D32);

    // ------------------------------------------------------------------ text styles (one font, three sizes)
    private static final PixelFont.Style TITLE_ECO = new PixelFont.Style(
            new Color(178, 244, 120), new Color(36, 148, 82), new Color(220, 255, 170),
            new Color(22, 84, 52), 2, new Color(10, 28, 18), true);
    private static final PixelFont.Style TITLE_SHOP = new PixelFont.Style(
            new Color(255, 238, 118), new Color(244, 146, 36), new Color(255, 252, 200),
            new Color(140, 72, 22), 2, new Color(40, 20, 8), true);
    private static final PixelFont.Style INK_STYLE = flat(INK);
    private static final PixelFont.Style INK_SOFT_STYLE = flat(INK_SOFT);
    private static final PixelFont.Style CHIP_STYLE = flat(new Color(0x1F5E2A));
    private static final PixelFont.Style RED_STYLE = flat(new Color(0xA8322B));
    private static final PixelFont.Style GREEN_STYLE = flat(new Color(0x2E7D32));
    private static final PixelFont.Style LIGHT_STYLE = new PixelFont.Style(
            Color.WHITE, new Color(0xF4FBEA), null, new Color(0x1F1A17), 1, null);

    // ------------------------------------------------------------------ pixel icons
    private static final PixelFont.Icon SMALL_BAG_ICON = bagIcon(0x6EC6F0, 0xBDE8FA, 0x3F8FBF);
    private static final PixelFont.Icon MEDIUM_BAG_ICON = bagIcon(0x4CC66A, 0xA6EBA8, 0x2E8B47);
    private static final PixelFont.Icon LARGE_BAG_ICON = bagIcon(0x5A6274, 0x8D97AB, 0x3A404D);

    private static final PixelFont.Icon SHOES_ICON = new PixelFont.Icon(pal(
            'o', 0x2A1414, 'R', 0xD94848, 'h', 0xF08080, 'w', 0xFFFFFF,
            'y', 0xFFD34D, 'D', 0x9E2B2B, 'S', 0xF2EAD8),
            "................",
            "................",
            "................",
            ".....oooo.......",
            "....oRRRRo......",
            "....oRhRRRo.....",
            "....oRwRwRRo....",
            "...ooRRwRwRRo...",
            "..oRRRRRRRRRRoo.",
            ".oRRRyyyRRRRRRRo",
            ".oRRRRRyyyRRRRRo",
            ".oDDDDDDDDDDDDDo",
            ".oSSSSSSSSSSSSSo",
            "..ooooooooooooo.",
            "................",
            "................");

    private static final PixelFont.Icon GLOVES_ICON = new PixelFont.Icon(pal(
            'o', 0x3A2410, 'G', 0xF2A33A, 'h', 0xFFD27F, 'd', 0xC77A1E,
            'C', 0x5C7FA3, 'c', 0x8FB0D1),
            "...oo.oo.oo.oo..",
            "..oGGoGGoGGoGGo.",
            "..oGhoGhoGhoGho.",
            "..oGGoGGoGGoGGo.",
            "..oGGoGGoGGoGGo.",
            "..oGGGGGGGGGGGo.",
            "oo.oGGGGGGGGGGo.",
            "oGGoGGGGGGGGGGo.",
            "oGGGGGGGGGGGGdo.",
            ".oGGGGGGGGGGGdo.",
            "..oGGGGGGGGGGdo.",
            "...oGGGGGGGGdo..",
            "...oCCCCCCCCCo..",
            "...oCcCcCcCcCo..",
            "...oCCCCCCCCCo..",
            "....ooooooooo...");

    private static final PixelFont.Icon POTION_ICON = new PixelFont.Icon(pal(
            'o', 0x23303A, 'K', 0xA8743F, 'W', 0xDCEFF2, 'h', 0xFFFFFF,
            'R', 0xE5484D, 'w', 0xFFFFFF),
            "......oooo......",
            "......oKKo......",
            "......oKKo......",
            ".....oooooo.....",
            "......oWWo......",
            "......oWWo......",
            ".....oWWWWo.....",
            "....oWWWWWWo....",
            "...oWhRRRRRWo...",
            "..oWhRRRRRRRWo..",
            "..oWRRRwRRRRWo..",
            "..oWRRwwwRRRWo..",
            "..oWRRRwRRRRWo..",
            "...oWRRRRRRWo...",
            "....oWWWWWWo....",
            ".....oooooo.....");

    private static final PixelFont.Icon COIN_ICON = new PixelFont.Icon(pal(
            'o', 0x6E4210, 'Y', 0xFFC931, 'y', 0xFFE27A, 'D', 0xC98A12),
            "..oooo..",
            ".oYYYYo.",
            "oYyYYYDo",
            "oYyYYYDo",
            "oYyYYYDo",
            "oYYYYYDo",
            ".oDDDDo.",
            "..oooo..");

    /** Mini potion bottle for the pouch counter and the potion card's pips (7 x 9). */
    private static final String[] BOTTLE_ROWS = {
        "..ooo..", "..oKo..", "..oWo..", ".oWWWo.", "oWhRRWo", "oWRRRWo", "oWRRRWo", ".oWWWo.", "..ooo.."};
    private static final PixelFont.Icon BOTTLE_FULL = new PixelFont.Icon(pal(
            'o', 0x23303A, 'K', 0xA8743F, 'W', 0xDCEFF2, 'h', 0xFFFFFF, 'R', 0xE5484D), BOTTLE_ROWS);
    private static final PixelFont.Icon BOTTLE_EMPTY = new PixelFont.Icon(pal(
            'o', 0x9C7A46, 'K', 0xC9A46A, 'W', 0xEFDCB4, 'h', 0xEFDCB4, 'R', 0xEFDCB4), BOTTLE_ROWS);

    private static final PixelFont.Icon CHECK_ICON = new PixelFont.Icon(pal('w', 0xFFFFFF),
            "......w",
            ".....ww",
            "w...ww.",
            "ww.ww..",
            ".www...",
            "..w....");

    private final ShopListener listener;
    private final ShopInventory inventory;
    private final List<ShopItem> items = new ArrayList<>();
    private int money;
    private int selectedIndex;
    private int hoveredIndex = -1;
    private boolean closeHovered;
    private String message = "";
    private boolean messageGood;
    private long messageAt;

    public ShopManager(ShopListener listener) {
        this(listener, ShopInventory.inMemory());
    }

    public ShopManager(ShopListener listener, ShopInventory inventory) {
        this.listener = listener;
        this.inventory = inventory == null ? ShopInventory.inMemory() : inventory;
        items.add(new ShopItem("Small Trash Bag", "+1 storage slot", "Capacity +1",
                "Adds one storage slot to your default five-slot trash bag.",
                100, ItemType.SMALL_BAG));
        items.add(new ShopItem("Medium Trash Bag", "+2 storage slots", "Capacity +2",
                "Adds two storage slots to your default five-slot trash bag.",
                229, ItemType.MEDIUM_BAG));
        items.add(new ShopItem("Large Trash Bag", "+3 storage slots", "Capacity +3",
                "Adds three storage slots to your default five-slot trash bag.",
                409, ItemType.LARGE_BAG));
        items.add(new ShopItem("Running Shoes", "Run 25% faster", "Speed +25%",
                "Lightweight running shoes help you move around the neighborhood faster.",
                189, ItemType.SHOES));
        items.add(new ShopItem("Heavy-Duty Gloves", "Improved scanning", "Scan range 13 -> 21",
                "Improves your scanning range so you can spot litter from farther away.",
                150, ItemType.GLOVES));
        items.add(new ShopItem("Healing Potion", "Heals 1 heart", "",
                "Use H during a level to restore one heart. You can carry up to five.",
                50, ItemType.POTION));
    }

    // ------------------------------------------------------------------ input

    public void handleInput(InputHandler input, int width, int height) {
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)
                || input.isKeyJustPressed(KeyEvent.VK_BACK_SPACE)) {
            listener.onBackToMenu();
            return;
        }

        if (input.isKeyJustPressed(KeyEvent.VK_LEFT) || input.isKeyJustPressed(KeyEvent.VK_A)) {
            moveSelection(-1, 0);
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT) || input.isKeyJustPressed(KeyEvent.VK_D)) {
            moveSelection(1, 0);
        } else if (input.isKeyJustPressed(KeyEvent.VK_UP) || input.isKeyJustPressed(KeyEvent.VK_W)) {
            moveSelection(0, -1);
        } else if (input.isKeyJustPressed(KeyEvent.VK_DOWN) || input.isKeyJustPressed(KeyEvent.VK_S)) {
            moveSelection(0, 1);
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER)
                || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            activateSelected();
        }

        Point mouse = toDesignPoint(input.getMouseX(), input.getMouseY(), width, height);
        hoveredIndex = itemAt(mouse.x, mouse.y);
        closeHovered = CLOSE_BOUNDS.contains(mouse);
        Point click = input.consumeMouseClick();
        if (click == null) return;

        Point designClick = toDesignPoint(click.x, click.y, width, height);
        if (CLOSE_BOUNDS.contains(designClick)) {
            listener.onBackToMenu();
            return;
        }

        int clickedIndex = itemAt(designClick.x, designClick.y);
        if (clickedIndex >= 0) {
            selectedIndex = clickedIndex;
            activateSelected();
        }
    }

    private void moveSelection(int dx, int dy) {
        int column = selectedIndex % 3;
        int row = selectedIndex / 3;
        column = (column + dx + 3) % 3;
        row = (row + dy + 2) % 2;
        selectedIndex = row * 3 + column;
    }

    private void activateSelected() {
        ShopItem item = items.get(selectedIndex);
        if (item.type == ItemType.POTION) {
            buyPotion(item);
            return;
        }
        if (isOwned(item.type)) {
            if (isEquipped(item.type)) {
                unequip(item.type);
                say(item.name + " unequipped.", true);
            } else {
                equip(item.type);
                say(item.name + " equipped.", true);
            }
            return;
        }
        if (money < item.price) {
            say("Not enough money! Need ₱" + (item.price - money) + " more.", false);
            return;
        }

        int newBalance = money - item.price;
        if (!listener.onMoneyChanged(newBalance)) {
            say("Purchase cancelled because your balance could not be saved.", false);
            return;
        }
        money = newBalance;
        inventory.addOwned(item.type.name());
        equip(item.type);
        say(item.name + " purchased and equipped.", true);
    }

    private void buyPotion(ShopItem item) {
        int potions = inventory.getPotions();
        if (potions >= ShopInventory.MAX_POTIONS) {
            say("Your potion pouch is full.", false);
        } else if (money < item.price) {
            say("Not enough money! Need ₱" + (item.price - money) + " more.", false);
        } else {
            int newBalance = money - item.price;
            if (!listener.onMoneyChanged(newBalance)) {
                say("Purchase cancelled because your balance could not be saved.", false);
                return;
            }
            money = newBalance;
            inventory.setPotions(potions + 1);
            say("Healing Potion purchased.", true);
        }
    }

    private void say(String text, boolean good) {
        message = text;
        messageGood = good;
        messageAt = System.currentTimeMillis();
    }

    private void equip(ItemType type) {
        switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> inventory.setEquippedBag(type.name());
            case SHOES -> inventory.setShoesEquipped(true);
            case GLOVES -> inventory.setGlovesEquipped(true);
            case POTION -> { }
        }
    }

    private void unequip(ItemType type) {
        switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> inventory.setEquippedBag("");
            case SHOES -> inventory.setShoesEquipped(false);
            case GLOVES -> inventory.setGlovesEquipped(false);
            case POTION -> { }
        }
    }

    private boolean isOwned(ItemType type) {
        return inventory.owns(type.name());
    }

    private boolean isEquipped(ItemType type) {
        return switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> inventory.getEquippedBag().equals(type.name());
            case SHOES -> inventory.isShoesEquipped();
            case GLOVES -> inventory.isGlovesEquipped();
            case POTION -> false;
        };
    }

    // ------------------------------------------------------------------ gameplay hooks

    public int getMoney() { return money; }
    public void setMoney(int amount) { money = Math.max(0, amount); }

    /** Extra bag slots from the equipped trash bag (0 when none is equipped). */
    public int getCapacityBonus() {
        String bag = inventory.getEquippedBag();
        if (ItemType.LARGE_BAG.name().equals(bag)) return 3;
        if (ItemType.MEDIUM_BAG.name().equals(bag)) return 2;
        if (ItemType.SMALL_BAG.name().equals(bag)) return 1;
        return 0;
    }

    public boolean hasRunningShoes() { return isOwned(ItemType.SHOES) && inventory.isShoesEquipped(); }
    public boolean hasGloves() { return isOwned(ItemType.GLOVES) && inventory.isGlovesEquipped(); }

    /** Name of the equipped bag, or null when the default bag is used. */
    public String getEquippedBagName() {
        for (ShopItem item : items) {
            if (item.type.name().equals(inventory.getEquippedBag())) return item.name;
        }
        return null;
    }

    public boolean consumePotion() {
        int potions = inventory.getPotions();
        if (potions <= 0) return false;
        inventory.setPotions(potions - 1);
        return true;
    }

    public int getPotionCount() { return inventory.getPotions(); }
    public int getMaxPotions() { return ShopInventory.MAX_POTIONS; }

    // ------------------------------------------------------------------ render

    public void render(Graphics2D g, int width, int height) {
        double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
        int drawWidth = (int) Math.round(DESIGN_WIDTH * scale);
        int drawHeight = (int) Math.round(DESIGN_HEIGHT * scale);
        g.setColor(PARCHMENT);                  // a wide window: parchment fills the sides too
        g.fillRect(0, 0, width, height);
        Graphics2D shop = (Graphics2D) g.create();
        shop.translate((width - drawWidth) / 2, (height - drawHeight) / 2);
        shop.scale(scale, scale);
        shop.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        shop.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawScreen(shop);
        shop.dispose();
    }

    private void drawScreen(Graphics2D g) {
        double t = System.currentTimeMillis() / 1000.0;
        drawParchment(g);
        drawAwning(g);
        drawHeader(g);
        for (int i = 0; i < items.size(); i++) if (i != selectedIndex) drawCard(g, i, t);
        drawCard(g, selectedIndex, t);   // on top, so its lift and glow overlap neighbours
        drawDescription(g);
        drawFooter(g);
    }

    private void drawParchment(Graphics2D g) {
        int w = DESIGN_WIDTH, h = DESIGN_HEIGHT;
        g.setColor(PARCHMENT);
        g.fillRect(0, 0, w, h);
        g.setColor(PARCHMENT_DARK);
        for (int i = 0; i < 160; i++) {
            int x = Math.floorMod(i * 7919 + 13, w), y = Math.floorMod(i * 104729 + 71, h);
            g.fillRect(x, y, 2 + i % 3, 2);
        }
        g.setColor(BORDER);
        g.fillRect(0, 0, w, 8);
        g.fillRect(0, h - 8, w, 8);
        g.fillRect(0, 0, 8, h);
        g.fillRect(w - 8, 0, 8, h);
        g.setColor(BORDER_DARK);
        g.drawRect(12, 12, w - 25, h - 25);
    }

    /** Wooden beam with a red-and-cream scalloped market awning hanging from it. */
    private void drawAwning(Graphics2D g) {
        int left = 8, right = DESIGN_WIDTH - 8, stripe = 50;
        Shape oldClip = g.getClip();
        g.clipRect(left, 0, right - left, DESIGN_HEIGHT);
        g.setColor(new Color(72, 50, 27, 50));   // shadow under the scallops
        for (int x = left; x < right; x += stripe) g.fillArc(x, 40, stripe, 30, 180, 180);
        for (int x = left, i = 0; x < right; x += stripe, i++) {
            g.setColor(i % 2 == 0 ? AWNING_RED : AWNING_CREAM);
            g.fillRect(x, 22, stripe, 30);
            g.fillArc(x, 36, stripe, 30, 180, 180);
        }
        g.setColor(new Color(0, 0, 0, 35));   // fold shade just under the beam
        g.fillRect(left, 22, right - left, 5);
        g.setClip(oldClip);
        g.setColor(WOOD);
        g.fillRect(left, 8, right - left, 14);
        g.setColor(WOOD_LIGHT);
        g.fillRect(left, 8, right - left, 3);
        g.setColor(FRAME_COLOR);
        g.fillRect(left, 22, right - left, 2);
    }

    private void drawHeader(Graphics2D g) {
        int scale = 5, titleY = 78;
        int ecoW = PixelFont.width("ECO", scale, true), shopW = PixelFont.width("SHOP", scale, true);
        int gap = 6 * scale;
        int x = (DESIGN_WIDTH - (ecoW + gap + shopW)) / 2;
        PixelFont.draw(g, "ECO", x, titleY, scale, TITLE_ECO);
        PixelFont.draw(g, "SHOP", x + ecoW + gap, titleY, scale, TITLE_SHOP);

        String subtitle = "GEAR UP FOR A CLEANER CITY";
        PixelFont.draw(g, subtitle, (DESIGN_WIDTH - PixelFont.width(subtitle, 2)) / 2, 138, 2, INK_STYLE);

        // potion pouch, top left
        String pouch = getPotionCount() + " / " + ShopInventory.MAX_POTIONS;
        int pouchW = 14 + 21 + 10 + PixelFont.width(pouch, 3) + 16;
        plate(g, GRID_X, 84, pouchW, 44);
        PixelFont.drawIcon(g, BOTTLE_FULL, GRID_X + 14, 84 + 8, 3);
        PixelFont.draw(g, pouch, GRID_X + 14 + 21 + 10, 84 + 12, 3, INK_STYLE);

        // wallet, top right
        String balance = "₱" + money;
        int walletW = 14 + 24 + 10 + PixelFont.width(balance, 3) + 16;
        int wx = GRID_RIGHT - walletW;
        plate(g, wx, 84, walletW, 44);
        PixelFont.drawIcon(g, COIN_ICON, wx + 14, 84 + 10, 3);
        PixelFont.draw(g, balance, wx + 14 + 24 + 10, 84 + 12, 3, INK_STYLE);
    }

    private void drawCard(Graphics2D g, int index, double t) {
        ShopItem item = items.get(index);
        boolean selected = index == selectedIndex;
        boolean hovered = index == hoveredIndex && !selected;
        int lift = selected ? -6 : hovered ? -2 : 0;
        Rectangle b = new Rectangle(CARD_BOUNDS[index]);
        b.translate(0, lift);

        // drop shadow (grows when lifted), frame, body
        g.setColor(new Color(72, 50, 27, selected ? 70 : 45));
        g.fillRect(b.x + 6, b.y + 8 - lift, b.width, b.height);
        g.setColor(FRAME_COLOR);
        g.fillRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6);
        g.setColor(PARCHMENT_LIGHT);
        g.fillRect(b.x, b.y, b.width, b.height);

        // name band with a category colour strip
        g.setColor(PARCHMENT);
        g.fillRect(b.x, b.y, b.width, 44);
        g.setColor(BORDER);
        g.fillRect(b.x, b.y + 44, b.width, 2);
        g.setColor(accent(item.type));
        g.fillRect(b.x, b.y, 6, 44);
        PixelFont.draw(g, item.name, b.x + 18, b.y + 12, 3, INK_STYLE);

        // icon tile
        Rectangle tile = new Rectangle(b.x + 16, b.y + 58, 96, 96);
        g.setColor(FRAME_COLOR);
        g.fillRect(tile.x - 2, tile.y - 2, tile.width + 4, tile.height + 4);
        g.setColor(tileColor(item.type));
        g.fillRect(tile.x, tile.y, tile.width, tile.height);
        g.setColor(new Color(0, 0, 0, 24));
        g.fillRect(tile.x, tile.y + tile.height - 20, tile.width, 20);
        PixelFont.Icon icon = icon(item.type);
        int s = iconScale(item.type);
        int bob = selected ? (int) Math.round(Math.sin(t * 4) * 2) : 0;
        int ix = tile.x + (tile.width - icon.w() * s) / 2;
        int iy = tile.y + tile.height - icon.h() * s - 2 + bob;
        PixelFont.drawIcon(g, icon, ix, iy, s);
        if (isEquipped(item.type)) drawCheckBadge(g, tile.x + tile.width - 16, tile.y - 8);
        if (item.type == ItemType.POTION && getPotionCount() > 0) {
            drawCountBadge(g, "X" + getPotionCount(), tile.x + tile.width, tile.y - 8);
        }

        // stat, detail chip, slot / potion pips
        int tx = tile.x + tile.width + 16;
        PixelFont.draw(g, item.stat, tx, b.y + 60, 2, INK_STYLE);
        String detail = detailOf(item);
        int chipW = PixelFont.width(detail, 2) + 12;
        g.setColor(CHIP_BORDER);
        g.fillRect(tx, b.y + 82, chipW, 24);
        g.setColor(CHIP_FILL);
        g.fillRect(tx + 2, b.y + 84, chipW - 4, 20);
        PixelFont.draw(g, detail, tx + 6, b.y + 87, 2, CHIP_STYLE);
        if (item.type == ItemType.POTION) {
            for (int p = 0; p < ShopInventory.MAX_POTIONS; p++) {
                PixelFont.drawIcon(g, p < getPotionCount() ? BOTTLE_FULL : BOTTLE_EMPTY, tx + p * 26, b.y + 116, 3);
            }
        } else {
            PixelFont.draw(g, slotLabel(item.type), tx, b.y + 124, 2, INK_SOFT_STYLE);
        }

        drawCardButton(g, new Rectangle(b.x + 16, b.y + 166, b.width - 32, 32), item);

        // focus outline
        if (selected) {
            int pulse = (int) (155 + 100 * (0.5 + 0.5 * Math.sin(t * 5)));
            g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), pulse));
            g.setStroke(new BasicStroke(4));
            g.drawRect(b.x - 7, b.y - 7, b.width + 13, b.height + 13);
            g.setStroke(new BasicStroke(1));
            drawArrow(g, b.x + b.width / 2, b.y - 11 + (int) Math.round(Math.sin(t * 6) * 3));
        } else if (hovered) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3));
            g.drawRect(b.x - 6, b.y - 6, b.width + 11, b.height + 11);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawCardButton(Graphics2D g, Rectangle button, ShopItem item) {
        boolean potionFull = item.type == ItemType.POTION && getPotionCount() >= ShopInventory.MAX_POTIONS;
        String label;
        Color fill;
        PixelFont.Style style;
        if (isEquipped(item.type)) {
            label = "EQUIPPED";
            fill = GREEN;
            style = LIGHT_STYLE;
        } else if (isOwned(item.type)) {
            label = "EQUIP";
            fill = BLUE;
            style = LIGHT_STYLE;
        } else if (potionFull) {
            label = "POUCH FULL";
            fill = LOCKED;
            style = INK_SOFT_STYLE;
        } else if (money < item.price) {
            label = "NEED ₱" + (item.price - money) + " MORE";
            fill = LOCKED;
            style = RED_STYLE;
        } else {
            label = "BUY  ₱" + item.price;
            fill = GOLD;
            style = INK_STYLE;
        }
        drawButton(g, button, label, fill, style);
    }

    private void drawDescription(Graphics2D g) {
        Rectangle d = DESCRIPTION_BOUNDS;
        plate(g, d.x, d.y, d.width, d.height);
        ShopItem focus = items.get(hoveredIndex >= 0 ? hoveredIndex : selectedIndex);
        PixelFont.draw(g, "▶ " + focus.name, d.x + 16, d.y + 14, 2, INK_STYLE);
        PixelFont.draw(g, focus.description, d.x + 16, d.y + 40, 2, INK_SOFT_STYLE);

        // right side: the last purchase message while fresh, otherwise what Enter/click will do
        boolean fresh = !message.isEmpty()
                && System.currentTimeMillis() - messageAt < MESSAGE_SECONDS * 1000;
        String right = fresh ? message : actionHint(focus, hoveredIndex >= 0 ? "CLICK" : "ENTER");
        PixelFont.Style style = fresh ? (messageGood ? GREEN_STYLE : RED_STYLE) : INK_SOFT_STYLE;
        PixelFont.draw(g, right, d.x + d.width - 16 - PixelFont.width(right, 2), d.y + 14, 2, style);
    }

    private String actionHint(ShopItem item, String verb) {
        if (item.type == ItemType.POTION && getPotionCount() >= ShopInventory.MAX_POTIONS) return "POUCH FULL";
        if (isEquipped(item.type)) return verb + ": UNEQUIP";
        if (isOwned(item.type)) return verb + ": EQUIP";
        if (money < item.price) return "NEED ₱" + (item.price - money) + " MORE";
        return verb + ": BUY FOR ₱" + item.price;
    }

    private void drawFooter(Graphics2D g) {
        String hint = "ARROWS: SELECT  •  ENTER: BUY / EQUIP  •  ESC: BACK";
        PixelFont.draw(g, hint, GRID_X, CLOSE_BOUNDS.y + (CLOSE_BOUNDS.height - 14) / 2, 2, INK_STYLE);
        drawButton(g, CLOSE_BOUNDS, "CLOSE  [ESC]", closeHovered ? WOOD_LIGHT : WOOD, LIGHT_STYLE);
    }

    // ------------------------------------------------------------------ widgets

    /** Parchment plate with a dark rim, used for the wallet, pouch and detail panel. */
    private static void plate(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(BORDER_DARK);
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        g.setColor(PARCHMENT_LIGHT);
        g.fillRect(x, y, w, h);
    }

    /** Chunky pixel button: dark rim, light top edge, dark bottom edge, centred label. */
    private static void drawButton(Graphics2D g, Rectangle r, String label, Color fill, PixelFont.Style style) {
        g.setColor(FRAME_COLOR);
        g.fillRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(mix(fill, Color.WHITE, 0.35));
        g.fillRect(r.x, r.y, r.width, 3);
        g.setColor(mix(fill, Color.BLACK, 0.25));
        g.fillRect(r.x, r.y + r.height - 4, r.width, 4);
        int lw = PixelFont.width(label, 2);
        PixelFont.draw(g, label, r.x + (r.width - lw) / 2, r.y + (r.height - 14) / 2 - 1, 2, style);
    }

    private static void drawCheckBadge(Graphics2D g, int x, int y) {
        g.setColor(FRAME_COLOR);
        g.fillRect(x - 2, y - 2, 26, 26);
        g.setColor(GREEN);
        g.fillRect(x, y, 22, 22);
        g.setColor(new Color(0x6BD08A));
        g.fillRect(x, y, 22, 2);
        PixelFont.drawIcon(g, CHECK_ICON, x + 4, y + 5, 2);
    }

    /** Small dark bubble with white text, right edge at {@code right}. */
    private static void drawCountBadge(Graphics2D g, String text, int right, int y) {
        int w = PixelFont.width(text, 2) + 10;
        int x = right - w + 8;
        g.setColor(FRAME_COLOR);
        g.fillRect(x - 2, y - 2, w + 4, 26);
        g.setColor(new Color(0xC2185B));
        g.fillRect(x, y, w, 22);
        PixelFont.draw(g, text, x + 5, y + 4, 2, LIGHT_STYLE);
    }

    /** Small gold arrow pointing down at the selected card. */
    private static void drawArrow(Graphics2D g, int cx, int tipY) {
        int[][] rows = {{-7, 7}, {-6, 6}, {-5, 5}, {-4, 4}, {-3, 3}, {-2, 2}, {-1, 1}};
        for (int r = 0; r < rows.length; r++) {
            int y = tipY - rows.length + r;
            g.setColor(FRAME_COLOR);
            g.fillRect(cx + rows[r][0] - 1, y - 1, rows[r][1] - rows[r][0] + 3, 3);
        }
        for (int r = 0; r < rows.length; r++) {
            int y = tipY - rows.length + r;
            g.setColor(r < 2 ? GOLD_LIGHT : GOLD);
            g.fillRect(cx + rows[r][0], y, rows[r][1] - rows[r][0] + 1, 1);
        }
    }

    // ------------------------------------------------------------------ item visuals

    private String detailOf(ShopItem item) {
        return item.type == ItemType.POTION
                ? "You have " + getPotionCount() + "/" + ShopInventory.MAX_POTIONS
                : item.detail;
    }

    private static PixelFont.Icon icon(ItemType type) {
        return switch (type) {
            case SMALL_BAG -> SMALL_BAG_ICON;
            case MEDIUM_BAG -> MEDIUM_BAG_ICON;
            case LARGE_BAG -> LARGE_BAG_ICON;
            case SHOES -> SHOES_ICON;
            case GLOVES -> GLOVES_ICON;
            case POTION -> POTION_ICON;
        };
    }

    /** Bags grow with their size; everything else fills the tile. */
    private static int iconScale(ItemType type) {
        return switch (type) {
            case SMALL_BAG -> 4;
            case LARGE_BAG -> 6;
            default -> 5;
        };
    }

    private static Color accent(ItemType type) {
        return switch (type) {
            case SMALL_BAG -> new Color(0x3F8FBF);
            case MEDIUM_BAG -> new Color(0x2E8B47);
            case LARGE_BAG -> new Color(0x5A6274);
            case SHOES -> new Color(0xD94848);
            case GLOVES -> new Color(0xE08A1E);
            case POTION -> new Color(0xC2185B);
        };
    }

    private static Color tileColor(ItemType type) {
        return switch (type) {
            case SMALL_BAG -> new Color(0xD6EEF8);
            case MEDIUM_BAG -> new Color(0xD8F2D4);
            case LARGE_BAG -> new Color(0xDCDDE3);
            case SHOES -> new Color(0xF8DADA);
            case GLOVES -> new Color(0xFBE6C4);
            case POTION -> new Color(0xF6D8E4);
        };
    }

    private static String slotLabel(ItemType type) {
        return switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> "SLOT: BAG";
            case SHOES -> "SLOT: FEET";
            case GLOVES -> "SLOT: HANDS";
            case POTION -> "CONSUMABLE";
        };
    }

    private static PixelFont.Icon bagIcon(int base, int highlight, int shade) {
        return new PixelFont.Icon(pal('o', 0x23303A, 'B', base, 'h', highlight, 'D', shade, 'L', 0xF4D35E),
                "................",
                "......oo.oo.....",
                ".....oLLoLLo....",
                "......oLLLo.....",
                ".......oLo......",
                "......oBBBo.....",
                ".....oBBhBBo....",
                "....oBBhBBBBo...",
                "...oBBhBBBBBBo..",
                "...oBhBBBBBBBo..",
                "...oBhBBBBBDBo..",
                "...oBBBBBBBDBo..",
                "...oBBBBBBDDBo..",
                "....oDBBBBDDo...",
                ".....oooooooo...",
                "................");
    }

    private static Map<Character, Color> pal(Object... kv) {
        Map<Character, Color> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], new Color((Integer) kv[i + 1]));
        return m;
    }

    private static PixelFont.Style flat(Color c) {
        return new PixelFont.Style(c, c, null, null, 0, null);
    }

    private static Color mix(Color a, Color b, double k) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    // ------------------------------------------------------------------ hit testing

    private int itemAt(int x, int y) {
        for (int i = 0; i < CARD_BOUNDS.length; i++) {
            if (CARD_BOUNDS[i].contains(x, y)) return i;
        }
        return -1;
    }

    private Point toDesignPoint(int x, int y, int width, int height) {
        double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
        int drawWidth = (int) Math.round(DESIGN_WIDTH * scale);
        int drawHeight = (int) Math.round(DESIGN_HEIGHT * scale);
        int offsetX = (width - drawWidth) / 2;
        int offsetY = (height - drawHeight) / 2;
        return new Point((int) ((x - offsetX) / scale), (int) ((y - offsetY) / scale));
    }
}
