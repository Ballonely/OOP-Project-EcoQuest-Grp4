package com.ecoquest.data;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Local account store for EcoQuest. Passwords are stored only as salted hashes. */
public final class UserDatabase {
    private static final int HASH_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,24}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}");
    /** Coins granted to every newly registered cleaner. */
    public static final int STARTER_BONUS = 50;
    public static final int CLEANLINESS_BASE = 0;
    public static final int POINTS_PER_CLEANLINESS_PERCENT = 200;

    /**
     * Built-in developer login, available on every machine without registering. Only a salted
     * hash of its password is kept here. The account is never written to disk (like a guest),
     * and it turns on the in-game developer tools (F1 shows the key guide).
     * Remove or change it before a public release.
     */
    public static final String DEV_USERNAME = "developer";
    private static final byte[] DEV_SALT = Base64.getDecoder().decode("X+kKo9tZPZ+3INdDhNtx8w==");
    private static final byte[] DEV_HASH = Base64.getDecoder().decode("LbB4jISXTcXuGmTO1MNtoVDODRpq6oKNoyL3M096DhQ=");
    /** Coins the developer account starts with, enough to test every shop item. */
    public static final int DEV_MONEY = 99_999;

    private final Path databaseFile;
    private final SecureRandom random = new SecureRandom();

    public UserDatabase() {
        this(Paths.get(System.getProperty("user.home"), ".ecoquest", "accounts.db"));
    }

    public UserDatabase(Path databaseFile) {
        this.databaseFile = databaseFile.toAbsolutePath();
    }

    public synchronized Account register(String username, String email, char[] password,
            CleanerRole role) throws IOException, GeneralSecurityException {
        validateUsername(username);
        if (DEV_USERNAME.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("That username is reserved.");
        }
        String cleanEmail = email == null ? "" : email.trim();
        validateEmail(cleanEmail);
        validateNewPassword(password);
        if (role == null) role = CleanerRole.LITTER_PICKER;

        Map<String, Account> accounts = readAccounts();
        String key = username.toLowerCase(Locale.ROOT);
        if (accounts.containsKey(key)) {
            throw new IllegalArgumentException("That username is already registered.");
        }
        if (findByEmail(accounts, cleanEmail) != null) {
            throw new IllegalArgumentException("That email is already registered.");
        }

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] passwordHash = hashPassword(password, salt);
        Account account = new Account(username, salt, passwordHash, 0, STARTER_BONUS,
                cleanEmail, role, false);
        accounts.put(key, account);
        writeAccounts(accounts);
        return account;
    }

    /** Logs in with either a cleaner ID (username) or the email registered to it. */
    public synchronized Account login(String usernameOrEmail, char[] password)
            throws IOException, GeneralSecurityException {
        String identifier = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        boolean byEmail = identifier.indexOf('@') >= 0;
        if (!byEmail) validateUsername(identifier);
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter your password.");
        }
        if (DEV_USERNAME.equalsIgnoreCase(identifier)) {
            if (!MessageDigest.isEqual(DEV_HASH, hashPassword(password, DEV_SALT))) {
                throw new IllegalArgumentException("Invalid username or password.");
            }
            return Account.developer();
        }

        Map<String, Account> accounts = readAccounts();
        Account account = byEmail ? findByEmail(accounts, identifier)
                : accounts.get(identifier.toLowerCase(Locale.ROOT));
        if (account == null || !MessageDigest.isEqual(
                account.passwordHash, hashPassword(password, account.salt))) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        return account;
    }

    /**
     * Sets a new password when the cleaner ID and its registered email both match.
     * Accounts created before emails were collected cannot be recovered this way.
     */
    public synchronized Account resetPassword(String username, String email, char[] newPassword)
            throws IOException, GeneralSecurityException {
        validateUsername(username == null ? "" : username.trim());
        String cleanEmail = email == null ? "" : email.trim();
        validateEmail(cleanEmail);
        validateNewPassword(newPassword);

        Map<String, Account> accounts = readAccounts();
        String key = username.trim().toLowerCase(Locale.ROOT);
        Account existing = accounts.get(key);
        if (existing == null || existing.email.isEmpty()
                || !existing.email.equalsIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("No cleaner matches that ID and email.");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Account updated = new Account(existing.username, salt, hashPassword(newPassword, salt),
                existing.score, existing.money, existing.email, existing.role, false);
        accounts.put(key, updated);
        writeAccounts(accounts);
        return updated;
    }

    /**
     * Community cleanliness shown on the title screen: starts at {@value #CLEANLINESS_BASE}%
     * and rises 1% for every {@value #POINTS_PER_CLEANLINESS_PERCENT} points scored by all cleaners.
     */
    public synchronized int getCleanliness() throws IOException {
        long total = 0;
        for (Account account : readAccounts().values()) total += account.score;
        return (int) Math.min(100, CLEANLINESS_BASE + total / POINTS_PER_CLEANLINESS_PERCENT);
    }

    private static Account findByEmail(Map<String, Account> accounts, String email) {
        for (Account account : accounts.values()) {
            if (!account.email.isEmpty() && account.email.equalsIgnoreCase(email)) return account;
        }
        return null;
    }

    public synchronized Account updateProgress(String username, int scoreEarned, int money)
            throws IOException {
        if (scoreEarned < 0 || money < 0) {
            throw new IllegalArgumentException("Score and money must not be negative.");
        }
        Map<String, Account> accounts = readAccounts();
        String key = username.toLowerCase(Locale.ROOT);
        Account existing = requireAccount(accounts, key);
        if (scoreEarned > Integer.MAX_VALUE - existing.score) {
            throw new IOException("Total score exceeds the supported range.");
        }
        Account updated = existing.withProgress(existing.score + scoreEarned, money);
        accounts.put(key, updated);
        writeAccounts(accounts);
        return updated;
    }

    public synchronized Account updateBalance(String username, int money) throws IOException {
        if (money < 0) {
            throw new IllegalArgumentException("Money must not be negative.");
        }
        Map<String, Account> accounts = readAccounts();
        String key = username.toLowerCase(Locale.ROOT);
        Account existing = requireAccount(accounts, key);
        Account updated = existing.withProgress(existing.score, money);
        accounts.put(key, updated);
        writeAccounts(accounts);
        return updated;
    }

    private Account requireAccount(Map<String, Account> accounts, String key) {
        Account account = accounts.get(key);
        if (account == null) {
            throw new IllegalArgumentException("Account no longer exists.");
        }
        return account;
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3-24 characters using letters, numbers, or underscores.");
        }
    }

    private void validateEmail(String email) {
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
    }

    private void validateNewPassword(char[] password) {
        if (password == null || password.length < 8 || password.length > 128) {
            throw new IllegalArgumentException("Password must be between 8 and 128 characters.");
        }
    }

    private byte[] hashPassword(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, HASH_ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private Map<String, Account> readAccounts() throws IOException {
        Map<String, Account> accounts = new LinkedHashMap<>();
        if (!Files.exists(databaseFile)) {
            return accounts;
        }

        try (BufferedReader reader = Files.newBufferedReader(databaseFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = line.split("\t", -1);
                try {
                    // 5 fields = accounts saved before emails/roles existed; 7 = current format.
                    if (fields.length != 5 && fields.length != 7) throw new IllegalArgumentException();
                    String username = new String(Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8);
                    validateUsername(username);
                    byte[] salt = Base64.getDecoder().decode(fields[1]);
                    byte[] passwordHash = Base64.getDecoder().decode(fields[2]);
                    int score = Integer.parseInt(fields[3]);
                    int money = Integer.parseInt(fields[4]);
                    if (salt.length != SALT_BYTES || passwordHash.length != HASH_BITS / 8
                            || score < 0 || money < 0) {
                        throw new IllegalArgumentException();
                    }
                    String email = fields.length == 7
                            ? new String(Base64.getDecoder().decode(fields[5]), StandardCharsets.UTF_8) : "";
                    CleanerRole role = fields.length == 7 ? CleanerRole.parse(fields[6]) : CleanerRole.LITTER_PICKER;
                    String key = username.toLowerCase(Locale.ROOT);
                    if (accounts.putIfAbsent(key, new Account(username, salt, passwordHash,
                            score, money, email, role, false)) != null) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid account database entry at line " + lineNumber + ".", e);
                }
            }
        }
        return accounts;
    }

    private void writeAccounts(Map<String, Account> accounts) throws IOException {
        Path parent = databaseFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, "accounts-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                for (Account account : accounts.values()) {
                    writer.write(Base64.getEncoder().encodeToString(
                            account.username.getBytes(StandardCharsets.UTF_8)));
                    writer.write('\t');
                    writer.write(Base64.getEncoder().encodeToString(account.salt));
                    writer.write('\t');
                    writer.write(Base64.getEncoder().encodeToString(account.passwordHash));
                    writer.write('\t');
                    writer.write(Integer.toString(account.score));
                    writer.write('\t');
                    writer.write(Integer.toString(account.money));
                    writer.write('\t');
                    writer.write(Base64.getEncoder().encodeToString(
                            account.email.getBytes(StandardCharsets.UTF_8)));
                    writer.write('\t');
                    writer.write(account.role.name());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporaryFile, databaseFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, databaseFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public static final class Account {
        private final String username;
        private final byte[] salt;
        private final byte[] passwordHash;
        private final int score;
        private final int money;
        private final String email;
        private final CleanerRole role;
        private final boolean guest;
        private final boolean developer;

        private Account(String username, byte[] salt, byte[] passwordHash, int score, int money,
                String email, CleanerRole role, boolean guest) {
            this(username, salt, passwordHash, score, money, email, role, guest, false);
        }

        private Account(String username, byte[] salt, byte[] passwordHash, int score, int money,
                String email, CleanerRole role, boolean guest, boolean developer) {
            this.developer = developer;
            this.username = username;
            this.salt = salt.clone();
            this.passwordHash = passwordHash.clone();
            this.score = score;
            this.money = money;
            this.email = email == null ? "" : email;
            this.role = role == null ? CleanerRole.LITTER_PICKER : role;
            this.guest = guest;
        }

        /** Temporary account for a guest patrol; nothing about it is ever saved. */
        public static Account guest() {
            return new Account("Guest Cleaner", new byte[0], new byte[0], 0, 0, "",
                    CleanerRole.LITTER_PICKER, true);
        }

        /** Built-in developer account: kept in memory only (like a guest), with dev tools on. */
        public static Account developer() {
            return new Account(DEV_USERNAME, new byte[0], new byte[0], 0, DEV_MONEY, "",
                    CleanerRole.LITTER_PICKER, true, true);
        }

        private Account withProgress(int score, int money) {
            return new Account(username, salt, passwordHash, score, money, email, role, guest, developer);
        }

        /** In-memory progress update for a guest account, which is never written to disk. */
        public Account withGuestProgress(int score, int money) {
            if (!guest) throw new IllegalStateException("Only guest progress is kept in memory.");
            return withProgress(Math.max(0, score), Math.max(0, money));
        }

        public String getUsername() { return username; }
        public int getScore() { return score; }
        public int getMoney() { return money; }
        public String getEmail() { return email; }
        public CleanerRole getRole() { return role; }
        public boolean isGuest() { return guest; }
        /** True for the built-in developer login: enables the in-game developer tools. */
        public boolean isDeveloper() { return developer; }
    }
}
