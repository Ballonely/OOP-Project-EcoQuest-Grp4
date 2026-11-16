package com.ecoquest.ui;

import com.ecoquest.core.InputHandler;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Renders and handles navigation for the equipment shop. */
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
        String detail;
        final String description;
        final int price;
        final ItemType type;
        boolean purchased;

        ShopItem(String name, String stat, String detail, String description,
                int price, ItemType type, boolean purchased) {
            this.name = name;
            this.stat = stat;
            this.detail = detail;
            this.description = description;
            this.price = price;
            this.type = type;
            this.purchased = purchased;
        }
    }

    private static final int DESIGN_WIDTH = 702;
    private static final int DESIGN_HEIGHT = 390;
    private static final Rectangle[] CARD_BOUNDS = {
        new Rectangle(20, 68, 211, 108),
        new Rectangle(246, 68, 210, 108),
        new Rectangle(471, 68, 211, 108),
        new Rectangle(20, 188, 211, 108),
        new Rectangle(246, 188, 210, 108),
        new Rectangle(471, 188, 211, 108)
    };
    private static final Rectangle DESCRIPTION_BOUNDS = new Rectangle(20, 309, 662, 40);
    private static final Rectangle CLOSE_BOUNDS = new Rectangle(548, 357, 134, 27);

    private static final Color BG = new Color(28, 45, 62);
    private static final Color CARD = new Color(47, 65, 82);
    private static final Color CARD_DARK = new Color(17, 29, 40);
    private static final Color BORDER = new Color(7, 17, 26);
    private static final Color BLUE = new Color(73, 163, 255);
    private static final Color WHITE = new Color(237, 241, 247);
    private static final Color MUTED = new Color(167, 181, 194);
    private static final Color GOLD = new Color(255, 202, 61);
    private static final Color GREEN = new Color(48, 166, 84);
    private static final Color RED = new Color(255, 91, 107);

    private final ShopListener listener;
    private final List<ShopItem> items = new ArrayList<>();
    private int money;
    private int selectedIndex;
    private int hoveredIndex = -1;
    private int potionCount;
    private ItemType equippedBag;
    private boolean shoesEquipped;
    private boolean glovesEquipped;
    private String message = "";
    private Color messageColor = RED;

    public ShopManager(ShopListener listener) {
        this.listener = listener;
        items.add(new ShopItem("Small Trash Bag", "+1 storage slot", "Capacity +1",
                "Adds one storage slot to your default five-slot trash bag.",
                100, ItemType.SMALL_BAG, false));
        items.add(new ShopItem("Medium Trash Bag", "+1 storage slot", "Capacity +1",
                "Adds one storage slot to your default five-slot trash bag.",
                229, ItemType.MEDIUM_BAG, false));
        items.add(new ShopItem("Large Trash Bag", "+2 storage slots", "Capacity +2",
                "Adds two storage slots to your default five-slot trash bag.",
                409, ItemType.LARGE_BAG, false));
        items.add(new ShopItem("Running Shoes", "Run 25% faster", "Speed +25%",
                "Lightweight running shoes help you move around the neighborhood faster.",
                189, ItemType.SHOES, false));
        items.add(new ShopItem("Heavy-Duty Gloves", "Improved scanning", "Scan range 13 -> 21",
                "Improves your scanning range so you can spot litter from farther away.",
                150, ItemType.GLOVES, false));
        items.add(new ShopItem("Healing Potion", "Heals 1 heart", "You have 0/5",
                "Use H during a level to restore one heart. You can carry up to five.",
                50, ItemType.POTION, false));
    }

    public void handleInput(InputHandler input, int width, int height) {
        if (input.isKeyJustPressed(KeyEvent.VK_ESCAPE)
                || input.isKeyJustPressed(KeyEvent.VK_BACK_SPACE)) {
            listener.onBackToMenu();
            return;
        }

        if (input.isKeyJustPressed(KeyEvent.VK_LEFT)) {
            moveSelection(-1, 0);
        } else if (input.isKeyJustPressed(KeyEvent.VK_RIGHT)) {
            moveSelection(1, 0);
        } else if (input.isKeyJustPressed(KeyEvent.VK_UP)) {
            moveSelection(0, -1);
        } else if (input.isKeyJustPressed(KeyEvent.VK_DOWN)) {
            moveSelection(0, 1);
        } else if (input.isKeyJustPressed(KeyEvent.VK_ENTER)
                || input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            activateSelected();
        }

        Point mouse = toDesignPoint(input.getMouseX(), input.getMouseY(), width, height);
        hoveredIndex = itemAt(mouse.x, mouse.y);
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
        if (item.purchased) {
            equip(item.type);
            message = item.name + " equipped.";
            messageColor = GREEN;
            return;
        }
        if (money < item.price) {
            message = "Not enough money! Need P" + (item.price - money) + " more.";
            messageColor = RED;
            return;
        }

        int newBalance = money - item.price;
        if (!listener.onMoneyChanged(newBalance)) {
            message = "Purchase cancelled because your balance could not be saved.";
            messageColor = RED;
            return;
        }
        money = newBalance;
        item.purchased = true;
        equip(item.type);
        message = item.name + " purchased and equipped.";
        messageColor = GREEN;
    }

    private void buyPotion(ShopItem item) {
        if (potionCount >= 5) {
            message = "Your potion pouch is full.";
            messageColor = RED;
        } else if (money < item.price) {
            message = "Not enough money! Need P" + (item.price - money) + " more.";
            messageColor = RED;
        } else {
            int newBalance = money - item.price;
            if (!listener.onMoneyChanged(newBalance)) {
                message = "Purchase cancelled because your balance could not be saved.";
                messageColor = RED;
                return;
            }
            money = newBalance;
            potionCount++;
            item.detail = "You have " + potionCount + "/5";
            message = "Healing Potion purchased.";
            messageColor = GREEN;
        }
    }

    private void equip(ItemType type) {
        switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> equippedBag = type;
            case SHOES -> shoesEquipped = true;
            case GLOVES -> glovesEquipped = true;
            case POTION -> { }
        }
    }

    public int getMoney() { return money; }
    public void setMoney(int amount) { money = Math.max(0, amount); }
    public int getCapacityBonus() {
        if (equippedBag == null) return 0;
        return switch (equippedBag) {
            case SMALL_BAG, MEDIUM_BAG -> 1;
            case LARGE_BAG -> 2;
            default -> 0;
        };
    }
    public boolean hasRunningShoes() { return shoesEquipped; }
    public boolean hasGloves() { return glovesEquipped; }
    public boolean consumePotion() {
        if (potionCount <= 0) return false;
        potionCount--;
        for (ShopItem item : items) {
            if (item.type == ItemType.POTION) item.detail = "You have " + potionCount + "/5";
        }
        return true;
    }
    public int getPotionCount() { return potionCount; }

    public void render(Graphics2D g, int width, int height) {
        double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
        int drawWidth = (int) Math.round(DESIGN_WIDTH * scale);
        int drawHeight = (int) Math.round(DESIGN_HEIGHT * scale);
        Graphics2D shop = (Graphics2D) g.create();
        shop.translate((width - drawWidth) / 2, (height - drawHeight) / 2);
        shop.scale(scale, scale);
        drawScreen(shop);
        shop.dispose();
    }

    private void drawScreen(Graphics2D g) {
        g.setColor(BG);
        g.fillRect(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
        g.setColor(BLUE);
        g.setStroke(new BasicStroke(4));
        g.drawRect(2, 2, DESIGN_WIDTH - 4, DESIGN_HEIGHT - 4);

        drawHeader(g);
        for (int i = 0; i < items.size(); i++) {
            drawCard(g, CARD_BOUNDS[i], items.get(i), i == selectedIndex, i == hoveredIndex);
        }
        drawDescription(g);
        drawFooter(g);
    }

    private void drawHeader(Graphics2D g) {
        g.setColor(new Color(24, 39, 54));
        g.fillRect(4, 4, DESIGN_WIDTH - 8, 52);
        g.setColor(BLUE);
        g.fillRect(4, 54, DESIGN_WIDTH - 8, 2);

        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
        g.setColor(GOLD);
        g.drawString("SHOP", 21, 41);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        g.setColor(MUTED);
        g.drawString("EQUIPMENT", 154, 38);

        g.setColor(BORDER);
        g.fillRect(541, 11, 140, 33);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2));
        g.drawRect(543, 13, 136, 29);
        drawCoin(g, 555, 28, 8);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
        g.setColor(WHITE);
        String balance = "P" + money;
        g.drawString(balance, 670 - g.getFontMetrics().stringWidth(balance), 35);
    }

    private void drawCard(Graphics2D g, Rectangle bounds, ShopItem item,
            boolean selected, boolean hovered) {
        g.setColor(BORDER);
        g.fillRect(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);
        Color border = selected
                ? (isEquipped(item.type) ? GREEN : WHITE)
                : hovered ? BLUE : new Color(119, 142, 163);
        g.setColor(border);
        g.setStroke(new BasicStroke(selected || hovered ? 3 : 2));
        g.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 1, bounds.height + 1);
        g.setColor(CARD);
        g.fillRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4);

        int iconX = bounds.x + 10;
        int iconY = bounds.y + 10;
        g.setColor(CARD_DARK);
        g.fillRect(iconX, iconY, 50, 51);
        drawItemIcon(g, item.type, iconX + 25, iconY + 27);

        int textX = bounds.x + 68;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(WHITE);
        g.drawString(item.name, textX, bounds.y + 22);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(MUTED);
        g.drawString(item.stat, textX, bounds.y + 39);
        g.setColor(GOLD);
        g.drawString(item.detail, textX, bounds.y + 54);

        Rectangle button = new Rectangle(bounds.x + 9, bounds.y + 73, bounds.width - 18, 24);
        drawCardButton(g, button, item);
    }

    private void drawCardButton(Graphics2D g, Rectangle button, ShopItem item) {
        boolean equipped = isEquipped(item.type);
        boolean owned = item.purchased;
        if (equipped) {
            g.setColor(GREEN);
        } else if (owned || money >= item.price) {
            g.setColor(new Color(232, 173, 47));
        } else if (item.type == ItemType.POTION && potionCount >= 5) {
            g.setColor(new Color(62, 72, 83));
        } else {
            g.setColor(new Color(62, 72, 83));
        }
        g.fillRect(button.x, button.y, button.width, button.height);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(2));
        g.drawRect(button.x, button.y, button.width, button.height);

        String label;
        Color textColor;
        if (equipped) {
            label = "EQUIPPED";
            textColor = WHITE;
        } else if (owned) {
            label = "EQUIP";
            textColor = new Color(38, 34, 24);
        } else if (item.type == ItemType.POTION && potionCount >= 5) {
            label = "FULL";
            textColor = MUTED;
        } else if (money < item.price) {
            label = "NEED P" + (item.price - money) + " MORE";
            textColor = RED;
        } else {
            label = "BUY    P" + item.price;
            textColor = new Color(38, 34, 24);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g.setColor(textColor);
        centerText(g, label, button.x + button.width / 2, button.y + 16);
    }

    private boolean isEquipped(ItemType type) {
        return switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> equippedBag == type;
            case SHOES -> shoesEquipped;
            case GLOVES -> glovesEquipped;
            case POTION -> false;
        };
    }

    private void drawItemIcon(Graphics2D g, ItemType type, int centerX, int centerY) {
        switch (type) {
            case SMALL_BAG, MEDIUM_BAG, LARGE_BAG -> {
                Color bag = type == ItemType.SMALL_BAG ? new Color(97, 174, 218)
                        : type == ItemType.MEDIUM_BAG ? new Color(68, 203, 95)
                        : new Color(81, 87, 100);
                g.setColor(new Color(20, 32, 44));
                g.fillRect(centerX - 14, centerY - 15, 28, 34);
                g.setColor(bag);
                g.fillRect(centerX - 11, centerY - 11, 22, 28);
                g.setColor(new Color(34, 45, 58));
                g.fillRect(centerX - 3, centerY - 9, 6, 24);
                g.setColor(type == ItemType.LARGE_BAG ? new Color(67, 74, 86) : new Color(164, 222, 243));
                g.fillRect(centerX - 8, centerY - 5, 6, 13);
            }
            case SHOES -> {
                g.setColor(new Color(154, 43, 61));
                g.fillRect(centerX - 17, centerY - 7, 28, 17);
                g.fillRect(centerX - 8, centerY + 5, 25, 8);
                g.setColor(new Color(240, 233, 215));
                g.fillRect(centerX - 13, centerY - 2, 21, 5);
                g.setColor(new Color(228, 70, 77));
                g.fillRect(centerX - 14, centerY - 13, 19, 8);
            }
            case GLOVES -> {
                g.setColor(new Color(244, 159, 46));
                g.fillRect(centerX - 12, centerY - 12, 24, 31);
                g.fillRect(centerX - 8, centerY - 19, 17, 10);
                g.setColor(new Color(251, 192, 75));
                g.fillRect(centerX - 7, centerY - 9, 16, 23);
            }
            case POTION -> {
                g.setColor(new Color(194, 205, 206));
                g.fillRect(centerX - 7, centerY - 18, 14, 8);
                g.setColor(new Color(217, 225, 219));
                g.fillRect(centerX - 13, centerY - 10, 26, 31);
                g.setColor(new Color(228, 70, 88));
                g.fillRect(centerX - 10, centerY + 2, 20, 13);
                g.setColor(WHITE);
                g.fillRect(centerX - 2, centerY + 3, 4, 11);
                g.fillRect(centerX - 6, centerY + 6, 12, 4);
            }
        }
    }

    private void drawDescription(Graphics2D g) {
        g.setColor(CARD_DARK);
        g.fillRect(DESCRIPTION_BOUNDS.x, DESCRIPTION_BOUNDS.y,
                DESCRIPTION_BOUNDS.width, DESCRIPTION_BOUNDS.height);
        g.setColor(new Color(102, 127, 147));
        g.setStroke(new BasicStroke(2));
        g.drawRect(DESCRIPTION_BOUNDS.x, DESCRIPTION_BOUNDS.y,
                DESCRIPTION_BOUNDS.width, DESCRIPTION_BOUNDS.height);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(new Color(200, 208, 216));
        g.drawString(items.get(selectedIndex).description, DESCRIPTION_BOUNDS.x + 13, 334);
    }

    private void drawFooter(Graphics2D g) {
        if (!message.isEmpty()) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            g.setColor(messageColor);
            g.drawString(message, 21, 377);
        }

        g.setColor(new Color(128, 46, 46));
        g.fillRect(CLOSE_BOUNDS.x, CLOSE_BOUNDS.y, CLOSE_BOUNDS.width, CLOSE_BOUNDS.height);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(3));
        g.drawRect(CLOSE_BOUNDS.x, CLOSE_BOUNDS.y, CLOSE_BOUNDS.width, CLOSE_BOUNDS.height);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(WHITE);
        centerText(g, "CLOSE  [ESC]", CLOSE_BOUNDS.x + CLOSE_BOUNDS.width / 2,
                CLOSE_BOUNDS.y + 18);
    }

    private void drawCoin(Graphics2D g, int centerX, int centerY, int radius) {
        g.setColor(new Color(175, 111, 22));
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g.setColor(GOLD);
        g.fillOval(centerX - radius + 2, centerY - radius + 2,
                radius * 2 - 4, radius * 2 - 4);
        g.setColor(new Color(255, 230, 120));
        g.fillRect(centerX - 2, centerY - 5, 3, 9);
    }

    private void centerText(Graphics2D g, String text, int centerX, int baselineY) {
        g.drawString(text, centerX - g.getFontMetrics().stringWidth(text) / 2, baselineY);
    }

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
