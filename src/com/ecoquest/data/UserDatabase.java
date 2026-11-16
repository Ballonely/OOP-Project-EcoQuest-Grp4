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

    private final Path databaseFile;
    private final SecureRandom random = new SecureRandom();

    public UserDatabase() {
        this(Paths.get(System.getProperty("user.home"), ".ecoquest", "accounts.db"));
    }

    public UserDatabase(Path databaseFile) {
        this.databaseFile = databaseFile.toAbsolutePath();
    }

    public synchronized Account register(String username, char[] password)
            throws IOException, GeneralSecurityException {
        validateUsername(username);
        validateNewPassword(password);

        Map<String, Account> accounts = readAccounts();
        String key = username.toLowerCase(Locale.ROOT);
        if (accounts.containsKey(key)) {
            throw new IllegalArgumentException("That username is already registered.");
        }

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] passwordHash = hashPassword(password, salt);
        Account account = new Account(username, salt, passwordHash, 0, 0);
        accounts.put(key, account);
        writeAccounts(accounts);
        return account;
    }

    public synchronized Account login(String username, char[] password)
            throws IOException, GeneralSecurityException {
        validateUsername(username);
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter your password.");
        }

        Account account = readAccounts().get(username.toLowerCase(Locale.ROOT));
        if (account == null || !MessageDigest.isEqual(
                account.passwordHash, hashPassword(password, account.salt))) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        return account;
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
                    if (fields.length != 5) throw new IllegalArgumentException();
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
                    String key = username.toLowerCase(Locale.ROOT);
                    if (accounts.putIfAbsent(key,
                            new Account(username, salt, passwordHash, score, money)) != null) {
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

        private Account(String username, byte[] salt, byte[] passwordHash, int score, int money) {
            this.username = username;
            this.salt = salt.clone();
            this.passwordHash = passwordHash.clone();
            this.score = score;
            this.money = money;
        }

        private Account withProgress(int score, int money) {
            return new Account(username, salt, passwordHash, score, money);
        }

        public String getUsername() { return username; }
        public int getScore() { return score; }
        public int getMoney() { return money; }
    }
}
