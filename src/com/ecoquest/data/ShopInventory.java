package com.ecoquest.data;

import com.ecoquest.data.UserDatabase.Account;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Per-account shop inventory: which items were bought, what is equipped and how many healing
 * potions are carried. Stored next to the account file in ~/.ecoquest/inventory.db so purchases
 * survive a restart; guest inventories stay in memory.
 *
 * <p>Line format (tab separated): key, owned item ids (comma separated), equipped bag id
 * (empty for none), shoes equipped (0/1), gloves equipped (0/1), potion count.
 */
public final class ShopInventory {
    public static final int MAX_POTIONS = 5;

    private final Path file;   // null = never saved (guest)
    private final String key;  // Base64 of the lower-case username
    private final Set<String> owned = new LinkedHashSet<>();
    private String equippedBag = "";
    private boolean shoesEquipped;
    private boolean glovesEquipped;
    private int potions;

    private ShopInventory(Path file, String key) {
        this.file = file;
        this.key = key;
    }

    /** An inventory that is never written to disk. */
    public static ShopInventory inMemory() {
        return new ShopInventory(null, null);
    }

    /** Loads the account's inventory; guests (and unreadable files) start empty. */
    public static ShopInventory load(Account account) {
        return load(account, Paths.get(System.getProperty("user.home"), ".ecoquest", "inventory.db"));
    }

    public static ShopInventory load(Account account, Path file) {
        if (account == null || account.isGuest()) return inMemory();
        String key = Base64.getEncoder().encodeToString(
                account.getUsername().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        ShopInventory inventory = new ShopInventory(file.toAbsolutePath(), key);
        try {
            if (Files.exists(inventory.file)) {
                for (String line : Files.readAllLines(inventory.file, StandardCharsets.UTF_8)) {
                    String[] f = line.split("\t", -1);
                    if (f.length != 6 || !f[0].equals(key)) continue;
                    for (String id : f[1].split(",")) {
                        if (!id.isBlank()) inventory.owned.add(id.trim());
                    }
                    inventory.equippedBag = inventory.owned.contains(f[2]) ? f[2] : "";
                    inventory.shoesEquipped = "1".equals(f[3]);
                    inventory.glovesEquipped = "1".equals(f[4]);
                    inventory.potions = Math.max(0, Math.min(MAX_POTIONS, Integer.parseInt(f[5])));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read shop inventory: " + e.getMessage());
        }
        return inventory;
    }

    public boolean owns(String itemId) { return owned.contains(itemId); }
    public Set<String> getOwned() { return Collections.unmodifiableSet(owned); }
    public String getEquippedBag() { return equippedBag; }
    public boolean isShoesEquipped() { return shoesEquipped; }
    public boolean isGlovesEquipped() { return glovesEquipped; }
    public int getPotions() { return potions; }

    public void addOwned(String itemId) { owned.add(itemId); save(); }
    public void setEquippedBag(String itemId) { equippedBag = itemId == null ? "" : itemId; save(); }
    public void setShoesEquipped(boolean equipped) { shoesEquipped = equipped; save(); }
    public void setGlovesEquipped(boolean equipped) { glovesEquipped = equipped; save(); }
    public void setPotions(int count) { potions = Math.max(0, Math.min(MAX_POTIONS, count)); save(); }

    private void save() {
        if (file == null) return;
        try {
            write();
        } catch (IOException e) {
            System.err.println("Could not save shop inventory: " + e.getMessage());
        }
    }

    private void write() throws IOException {
        List<String> lines = new ArrayList<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.startsWith(key + "\t")) lines.add(line);   // keep other accounts
            }
        }
        lines.add(key + "\t" + String.join(",", owned) + "\t" + equippedBag + "\t"
                + (shoesEquipped ? 1 : 0) + "\t" + (glovesEquipped ? 1 : 0) + "\t" + potions);
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "inventory-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
