package com.ecoquest.ui;

import com.ecoquest.data.CleanerRole;
import com.ecoquest.data.UserDatabase;
import com.ecoquest.data.UserDatabase.Account;
import com.ecoquest.ui.SoundFx.Sound;
import com.ecoquest.util.Constants;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/** 16-bit pixel-art login and registration screen drawn over the animated menu background. */
public final class AccountScreen extends JPanel {
    // Palette (matches the Eco Quest pixel login mock-up).
    private static final Color SLATE_950 = new Color(2, 6, 23);
    private static final Color SLATE_900 = new Color(15, 23, 42);
    private static final Color SLATE_800 = new Color(30, 41, 59);
    private static final Color SLATE_700 = new Color(51, 65, 85);
    private static final Color SLATE_600 = new Color(71, 85, 105);
    private static final Color SLATE_500 = new Color(100, 116, 139);
    private static final Color SLATE_400 = new Color(148, 163, 184);
    private static final Color SLATE_300 = new Color(203, 213, 225);
    private static final Color WHITE = new Color(248, 250, 252);
    private static final Color GREEN = new Color(34, 197, 94);
    private static final Color GREEN_LIGHT = new Color(74, 222, 128);
    private static final Color GREEN_PALE = new Color(134, 239, 172);
    private static final Color GREEN_DARK = new Color(21, 128, 61);
    private static final Color GREEN_INK = new Color(5, 46, 22);
    private static final Color EMERALD_300 = new Color(110, 231, 183);
    private static final Color EMERALD_400 = new Color(52, 211, 153);
    private static final Color EMERALD_500 = new Color(16, 185, 129);
    private static final Color EMERALD_600 = new Color(5, 150, 105);
    private static final Color EMERALD_900 = new Color(6, 78, 59);
    private static final Color YELLOW = new Color(234, 179, 8);
    private static final Color YELLOW_300 = new Color(253, 224, 71);
    private static final Color YELLOW_400 = new Color(250, 204, 21);
    private static final Color YELLOW_PALE = new Color(254, 240, 138);
    private static final Color YELLOW_INK = new Color(66, 32, 6);
    private static final Color AMBER_400 = new Color(251, 191, 36);
    private static final Color AMBER_600 = new Color(217, 119, 6);
    private static final Color RED_400 = new Color(248, 113, 113);
    private static final Color RED_500 = new Color(239, 68, 68);
    private static final Color RED_600 = new Color(220, 38, 38);
    private static final Color CYAN_400 = new Color(34, 211, 238);
    private static final Color SKY_300 = new Color(125, 211, 252);
    private static final Color SKY_400 = new Color(56, 189, 248);
    private static final Color SKY_600 = new Color(2, 132, 199);

    private static final Font INPUT_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 18);
    private static final int CARD_WIDTH = 600;
    private static final int MODAL_WIDTH = 470;
    private static final int CARD_RING = 4;
    private static final int CARD_PAD = 24;
    private static final String REMEMBER_KEY = "rememberedCleanerId";
    private static final int TOAST_MS = 3000;

    // Pixel icons ('#' = filled pixel).
    private static final String[] ICON_ID = {
            "#######", "#.....#", "#.##..#", "#.##.##", "#.....#", "#...###", "#######"};
    private static final String[] ICON_KEY = {
            "..###..", ".#...#.", ".#...#.", "..###..", "...#...", "...##..", "...#...", "...##.."};
    private static final String[] ICON_USER = {
            "..###..", "..###..", "..###..", "...#...", ".#####.", "#######", "#######"};
    private static final String[] ICON_MAIL = {
            "#######", "##...##", "#.#.#.#", "#..#..#", "#.....#", "#######"};
    private static final String[] ICON_LOCK = {
            "..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"};
    private static final String[] ICON_CHECK = {
            "......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", "......."};
    private static final String[] ICON_EYE = {
            "...###...", ".##...##.", "#...#...#", "#..###..#", "#...#...#", ".##...##.", "...###..."};
    private static final String[] ICON_EYE_SLASH = {
            "...###.#.", ".##...#..", "#...##..#", "#..##...#", "#..#....#", ".##...##.", "#..###..."};
    private static final String[] ICON_TICK = {
            ".....#", "....##", "#..##.", "####..", ".##..."};
    private static final String[] ICON_SPARKLE = {
            "...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."};
    private static final String[] ICON_BOLT = {
            "...##", "..##.", ".##..", "#####", "..##.", ".##..", "##..."};
    private static final String[] ICON_SPEAKER_ON = {
            "...#....", "..##..#.", "####...#", "####.#.#", "####...#", "..##..#.", "...#...."};
    private static final String[] ICON_SPEAKER_OFF = {
            "...#....", "..##....", "####.#.#", "####..#.", "####.#.#", "..##....", "...#...."};
    private static final String[] ICON_PAD = {
            ".#####.", "#.....#", "#.#..##", "###...#", "#.#.#.#", "#.....#", ".#####."};
    private static final String[] ICON_PLUS = {
            "..#..", "..#..", "#####", "..#..", "..#.."};

    private static final Map<Color, PixelFont.Style> STYLES = new HashMap<>();

    private final UserDatabase database;
    private final Consumer<Account> onAuthenticated;

    private final JTextField loginIdField = new JTextField();
    private final JPasswordField loginPasswordField = new JPasswordField();
    private final JTextField regNameField = new JTextField();
    private final JTextField regEmailField = new JTextField();
    private final JPasswordField regPasswordField = new JPasswordField();
    private final JPasswordField regConfirmField = new JPasswordField();
    private final JTextField forgotIdField = new JTextField();
    private final JTextField forgotEmailField = new JTextField();
    private final JPasswordField forgotPasswordField = new JPasswordField();
    private final JPasswordField forgotConfirmField = new JPasswordField();

    private final PixelButton loginTab = new PixelButton("PLAYER LOGIN", null);
    private final PixelButton registerTab = new PixelButton("NEW CLEANER", null);
    private final PixelButton loginSubmit = new PixelButton("START SHIFT ▶", ICON_PAD);
    private final PixelButton registerSubmit = new PixelButton("JOIN ECO SQUAD ▶", ICON_PLUS);
    private final PixelButton guestButton = new PixelButton("GUEST PATROL", ICON_BOLT);
    private final PixelButton forgotSubmit = new PixelButton("RESET PASSCODE", null);
    private final RememberToggle rememberToggle = new RememberToggle("REMEMBER CLEANER ID");
    private final StrengthMeter strengthMeter = new StrengthMeter();
    private final RoleCard[] roleCards = new RoleCard[CleanerRole.values().length];
    private final Branding branding = new Branding();
    private final JPanel loginView = transparentPanel(new GridBagLayout());
    private final JPanel registerView = transparentPanel(new GridBagLayout());
    private final JPanel mainLayer = transparentPanel(new BorderLayout());
    private final ModalLayer modalLayer = new ModalLayer();
    private final List<Toast> toasts = new ArrayList<>();
    private CleanerRole selectedRole = CleanerRole.WASTE_COLLECTOR;
    private boolean registering;
    private boolean launching;
    private final int cleanliness;

    private final AnimatedBackground background = new AnimatedBackground();
    private final Timer animationTimer = new Timer(33, e -> repaint());

    public AccountScreen(UserDatabase database, Consumer<Account> onAuthenticated) {
        this.database = database;
        this.onAuthenticated = onAuthenticated;
        this.cleanliness = loadCleanliness(database);
        setLayout(null);
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));

        JPanel column = transparentPanel(null);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        JPanel card = buildCard();
        WasteBadges badges = new WasteBadges();
        branding.setAlignmentX(CENTER_ALIGNMENT);
        card.setAlignmentX(CENTER_ALIGNMENT);
        badges.setAlignmentX(CENTER_ALIGNMENT);
        column.add(branding);
        column.add(Box.createVerticalStrut(12));
        column.add(card);
        column.add(Box.createVerticalStrut(10));
        column.add(badges);

        JPanel center = transparentPanel(new GridBagLayout());
        center.add(column);
        mainLayer.add(buildTopBar(), BorderLayout.NORTH);
        mainLayer.add(center, BorderLayout.CENTER);

        buildForgotModal();
        modalLayer.setVisible(false);
        add(modalLayer);   // index 0 = painted on top
        add(mainLayer);

        loadRememberedId();
        setMode(false);
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void doLayout() {
        for (Component child : getComponents()) child.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false; // layers overlap
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(2, 6, 23, 230));
                g.fillRect(0, 0, getWidth(), getHeight() - 4);
                g.setColor(SLATE_800);
                g.fillRect(0, getHeight() - 4, getWidth(), 4);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        CleanlinessHud hud = new CleanlinessHud(cleanliness);
        JPanel left = transparentPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(hud);

        SfxButton sfx = new SfxButton();
        sfx.addActionListener(e -> toggleSfx(sfx));
        guestButton.setTheme(YELLOW, YELLOW_INK, YELLOW_PALE, YELLOW_INK, null);
        guestButton.setTextPadding(9);
        guestButton.setToolTipText("Play without an account. Progress is not saved.");
        guestButton.addActionListener(e -> startGuestPatrol());
        JPanel right = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(sfx);
        right.add(guestButton);

        JPanel row = transparentPanel(new BorderLayout());
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        bar.add(row, BorderLayout.CENTER);
        // Vertically centre the right-hand buttons against the taller health box.
        right.setBorder(BorderFactory.createEmptyBorder(
                Math.max(0, (hud.getPreferredSize().height - guestButton.getPreferredSize().height) / 2), 0, 0, 0));
        return bar;
    }

    private JPanel buildCard() {
        JPanel card = new CardPanel(new GridBagLayout(), CARD_WIDTH);
        card.setBorder(BorderFactory.createEmptyBorder(CARD_RING + CARD_PAD - 4, CARD_RING + CARD_PAD,
                CARD_RING + 6 + CARD_PAD - 4, CARD_RING + CARD_PAD));

        loginTab.addActionListener(e -> {
            if (registering) SoundFx.play(Sound.TAB);
            setMode(false);
        });
        registerTab.addActionListener(e -> {
            if (!registering) SoundFx.play(Sound.TAB);
            setMode(true);
        });
        JPanel tabs = transparentPanel(new GridLayout(1, 2, 4, 0));
        tabs.add(loginTab);
        tabs.add(registerTab);

        buildLoginView();
        buildRegisterView();

        int row = 0;
        addRow(card, tabs, row++, 12);
        addRow(card, loginView, row++, 0);
        addRow(card, registerView, row, 0);
        return card;
    }

    private void buildLoginView() {
        LinkButton forgot = new LinkButton("FORGOT CODE?", YELLOW_400);
        forgot.addActionListener(e -> openForgotModal());
        JPanel passcodeRow = transparentPanel(new BorderLayout());
        passcodeRow.add(new PixelLabel("PASSCODE", EMERALD_400, ICON_KEY), BorderLayout.WEST);
        passcodeRow.add(forgot, BorderLayout.EAST);

        int row = 0;
        addRow(loginView, new PixelLabel("CLEANER ID / EMAIL", EMERALD_400, ICON_ID), row++, 6);
        addRow(loginView, new InputBox(loginIdField, "e.g. EcoHero99", null, false), row++, 14);
        addRow(loginView, passcodeRow, row++, 6);
        addRow(loginView, new InputBox(loginPasswordField, "••••••••",
                new EyeToggle(loginPasswordField), false), row++, 12);
        addRow(loginView, rememberToggle, row++, 14);
        loginSubmit.setTheme(GREEN, GREEN_INK, GREEN_PALE, GREEN_INK, null);
        loginSubmit.setTextPadding(14);
        loginSubmit.addActionListener(this::submit);
        addRow(loginView, loginSubmit, row, 0);
    }

    private void buildRegisterView() {
        DocumentListener strengthListener = documentListener(this::updatePasswordStrength);
        regPasswordField.getDocument().addDocumentListener(strengthListener);
        regConfirmField.getDocument().addDocumentListener(strengthListener);

        JPanel passwordGrid = transparentPanel(new GridLayout(1, 2, 12, 0));
        passwordGrid.add(labelledBox("PASSCODE", ICON_LOCK,
                new InputBox(regPasswordField, "Min 8 chars", new EyeToggle(regPasswordField), true)));
        passwordGrid.add(labelledBox("CONFIRM", ICON_CHECK,
                new InputBox(regConfirmField, "Re-enter code", null, true)));

        JPanel roleHeader = transparentPanel(new BorderLayout());
        roleHeader.add(new PixelLabel("SELECT STARTER ROLE & GEAR", YELLOW_300, null), BorderLayout.WEST);
        roleHeader.add(new PixelLabel("+₱" + UserDatabase.STARTER_BONUS + " BONUS", EMERALD_400, null),
                BorderLayout.EAST);
        JPanel roles = transparentPanel(new GridLayout(1, roleCards.length, 8, 0));
        CleanerRole[] values = CleanerRole.values();
        for (int i = 0; i < values.length; i++) {
            roleCards[i] = new RoleCard(values[i]);
            roles.add(roleCards[i]);
        }

        int row = 0;
        addRow(registerView, labelledBox("CLEANER CODENAME", ICON_USER,
                new InputBox(regNameField, "Choose hero name", null, true)), row++, 10);
        addRow(registerView, labelledBox("HQ EMAIL", ICON_MAIL,
                new InputBox(regEmailField, "cleaner@city.org", null, true)), row++, 10);
        addRow(registerView, passwordGrid, row++, 10);
        addRow(registerView, strengthMeter, row++, 12);
        addRow(registerView, roleHeader, row++, 6);
        addRow(registerView, roles, row++, 12);
        registerSubmit.setTheme(YELLOW, YELLOW_INK, YELLOW_PALE, YELLOW_INK, null);
        registerSubmit.setTextPadding(12);
        registerSubmit.addActionListener(this::submit);
        addRow(registerView, registerSubmit, row, 0);
    }

    private void buildForgotModal() {
        JPanel box = new CardPanel(new GridBagLayout(), MODAL_WIDTH);
        box.setBorder(BorderFactory.createEmptyBorder(CARD_RING + 18, CARD_RING + 22,
                CARD_RING + 6 + 20, CARD_RING + 22));

        LinkButton close = new LinkButton("X", SLATE_400);
        close.addActionListener(e -> closeForgotModal());
        JPanel header = transparentPanel(new BorderLayout());
        header.add(new PixelLabel("HQ PASSCODE RECOVERY", YELLOW_300, null), BorderLayout.WEST);
        header.add(close, BorderLayout.EAST);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, SLATE_700),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)));

        JPanel passwords = transparentPanel(new GridLayout(1, 2, 12, 0));
        passwords.add(labelledBox("NEW CODE", ICON_LOCK,
                new InputBox(forgotPasswordField, "Min 8 chars", null, true)));
        passwords.add(labelledBox("CONFIRM", ICON_CHECK,
                new InputBox(forgotConfirmField, "Re-enter code", null, true)));

        forgotSubmit.setTheme(YELLOW, YELLOW_INK, YELLOW_PALE, YELLOW_INK, null);
        forgotSubmit.setTextPadding(10);
        forgotSubmit.addActionListener(e -> submitPasswordReset());

        int row = 0;
        addRow(box, header, row++, 12);
        addRow(box, new PixelParagraph("Enter your cleaner ID and the email you registered with, "
                + "then choose a new passcode.", SLATE_300, MODAL_WIDTH - 2 * (CARD_RING + 22)), row++, 12);
        addRow(box, labelledBox("CLEANER ID", ICON_ID,
                new InputBox(forgotIdField, "e.g. EcoHero99", null, true)), row++, 10);
        addRow(box, labelledBox("HQ EMAIL", ICON_MAIL,
                new InputBox(forgotEmailField, "cleaner@city.org", null, true)), row++, 10);
        addRow(box, passwords, row++, 14);
        addRow(box, forgotSubmit, row, 0);

        modalLayer.add(box);
        modalLayer.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        modalLayer.getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (modalLayer.isVisible()) closeForgotModal();
            }
        });
    }

    private JPanel labelledBox(String label, String[] icon, InputBox box) {
        JPanel group = transparentPanel(new BorderLayout(0, 6));
        group.add(new PixelLabel(label, EMERALD_400, icon), BorderLayout.NORTH);
        group.add(box, BorderLayout.CENTER);
        return group;
    }

    private static void addRow(JPanel panel, JComponent content, int row, int bottom) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, bottom, 0);
        panel.add(content, c);
    }

    private static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = layout == null ? new JPanel() : new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static DocumentListener documentListener(Runnable onChange) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onChange.run(); }
            @Override public void removeUpdate(DocumentEvent e) { onChange.run(); }
            @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
        };
    }

    // ------------------------------------------------------------------ behaviour

    private void setMode(boolean register) {
        registering = register;
        loginView.setVisible(!register);
        registerView.setVisible(register);
        branding.setCompact(register);
        if (register) {
            registerTab.setTheme(YELLOW, YELLOW_INK, YELLOW_PALE, YELLOW_INK, YELLOW_INK);
            loginTab.setFlat();
        } else {
            loginTab.setTheme(GREEN, GREEN_INK, GREEN_PALE, GREEN_INK, YELLOW_300);
            registerTab.setFlat();
        }
        revalidate();
        repaint();
        updateDefaultButton();
        focusFirstField();
    }

    private void updateDefaultButton() {
        if (getRootPane() == null) return;
        getRootPane().setDefaultButton(modalLayer.isVisible() ? forgotSubmit
                : registering ? registerSubmit : loginSubmit);
    }

    private void focusFirstField() {
        SwingUtilities.invokeLater(() -> {
            if (modalLayer.isVisible()) {
                (forgotIdField.getText().isEmpty() ? forgotIdField : forgotEmailField).requestFocusInWindow();
            } else if (registering) {
                regNameField.requestFocusInWindow();
            } else if (loginIdField.getText().isEmpty()) {
                loginIdField.requestFocusInWindow();
            } else {
                loginPasswordField.requestFocusInWindow();
            }
        });
    }

    private void selectRole(CleanerRole role) {
        if (selectedRole != role) SoundFx.play(Sound.BLIP);
        selectedRole = role;
        for (RoleCard card : roleCards) card.repaint();
    }

    private void toggleSfx(SfxButton button) {
        SoundFx.setEnabled(!SoundFx.isEnabled());
        button.repaint();
        if (SoundFx.isEnabled()) {
            SoundFx.play(Sound.BLIP);
            showToast("SOUND & MUSIC ENABLED", ToastType.SUCCESS);
        } else {
            showToast("SOUND & MUSIC MUTED", ToastType.INFO);
        }
    }

    private void openForgotModal() {
        SoundFx.play(Sound.BLIP);
        String id = loginIdField.getText().trim();
        if (id.indexOf('@') >= 0) {
            forgotEmailField.setText(id);
        } else {
            forgotIdField.setText(id);
        }
        modalLayer.setVisible(true);
        updateDefaultButton();
        focusFirstField();
    }

    private void closeForgotModal() {
        SoundFx.play(Sound.BLIP);
        modalLayer.setVisible(false);
        forgotPasswordField.setText("");
        forgotConfirmField.setText("");
        updateDefaultButton();
        focusFirstField();
    }

    private void submitPasswordReset() {
        char[] password = forgotPasswordField.getPassword();
        char[] confirmation = forgotConfirmField.getPassword();
        try {
            if (!Arrays.equals(password, confirmation)) {
                fail("PASSCODES DO NOT MATCH!");
                forgotConfirmField.requestFocusInWindow();
                return;
            }
            Account account = database.resetPassword(forgotIdField.getText().trim(),
                    forgotEmailField.getText().trim(), password);
            SoundFx.play(Sound.COIN);
            modalLayer.setVisible(false);
            loginIdField.setText(account.getUsername());
            if (registering) setMode(false);
            updateDefaultButton();
            focusFirstField();
            showToast("PASSCODE RESET! LOG IN WITH YOUR NEW CODE", ToastType.SUCCESS);
        } catch (IllegalArgumentException | IOException | GeneralSecurityException e) {
            fail(e.getMessage());
            forgotPasswordField.requestFocusInWindow();
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            forgotPasswordField.setText("");
            forgotConfirmField.setText("");
        }
    }

    private void startGuestPatrol() {
        if (launching) return;
        launching = true;
        SoundFx.play(Sound.FANFARE);
        showToast("GUEST PATROL! PROGRESS WILL NOT BE SAVED", ToastType.INFO);
        launchAfter(900, Account.guest());
    }

    private void updatePasswordStrength() {
        char[] password = regPasswordField.getPassword();
        char[] confirmation = regConfirmField.getPassword();
        try {
            if (password.length == 0) {
                strengthMeter.set("NO CODE", SLATE_500, 0, SLATE_800);
                return;
            }
            if (confirmation.length > 0 && !Arrays.equals(password, confirmation)) {
                strengthMeter.set("MISMATCH!", RED_400, 1, RED_500);
                return;
            }
            boolean upperOrDigit = false;
            boolean symbol = false;
            for (char ch : password) {
                if (Character.isUpperCase(ch) || Character.isDigit(ch)) upperOrDigit = true;
                if (!Character.isLetterOrDigit(ch)) symbol = true;
            }
            int score = 0;
            if (password.length >= 8) score++;
            if (upperOrDigit) score++;
            if (symbol) score++;
            if (password.length >= 12) score++;

            switch (score) {
                case 0: strengthMeter.set("TOO SHORT", RED_400, 0, SLATE_800); break;
                case 1: strengthMeter.set("WEAK CODE", RED_400, 1, RED_500); break;
                case 2: strengthMeter.set("FAIR SECURITY", YELLOW_400, 2, YELLOW_400); break;
                case 3: strengthMeter.set("STRONG CLEANER", EMERALD_400, 3, EMERALD_400); break;
                default: strengthMeter.set("MAX ECO CODE!", CYAN_400, 4, CYAN_400); break;
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    private void submit(ActionEvent event) {
        if (launching) return;
        JPasswordField passwordField = registering ? regPasswordField : loginPasswordField;
        char[] password = passwordField.getPassword();
        char[] confirmation = regConfirmField.getPassword();
        try {
            if (registering) {
                if (!Arrays.equals(password, confirmation)) {
                    fail("PASSCODES DO NOT MATCH!");
                    regConfirmField.requestFocusInWindow();
                    return;
                }
                Account account = database.register(regNameField.getText().trim(),
                        regEmailField.getText().trim(), password, selectedRole);
                launching = true;
                SoundFx.play(Sound.FANFARE);
                showToast("WELCOME BONUS +₱" + UserDatabase.STARTER_BONUS + " ADDED!", ToastType.SUCCESS);
                launchAfter(700, account);
            } else {
                String id = loginIdField.getText().trim();
                Account account = database.login(id, password);
                saveRememberedId(rememberToggle.isChecked() ? id : null);
                launching = true;
                SoundFx.play(Sound.FANFARE);
                onAuthenticated.accept(account);
            }
        } catch (IllegalArgumentException | IOException | GeneralSecurityException e) {
            fail(e.getMessage());
            passwordField.requestFocusInWindow();
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            loginPasswordField.setText("");
            regPasswordField.setText("");
            regConfirmField.setText("");
        }
    }

    private void launchAfter(int delayMs, Account account) {
        Timer timer = new Timer(delayMs, e -> onAuthenticated.accept(account));
        timer.setRepeats(false);
        timer.start();
    }

    private void fail(String message) {
        SoundFx.play(Sound.ERROR);
        showToast(message, ToastType.ERROR);
    }

    private static int loadCleanliness(UserDatabase database) {
        try {
            return database.getCleanliness();
        } catch (IOException e) {
            System.err.println("Could not read cleanliness: " + e.getMessage());
            return UserDatabase.CLEANLINESS_BASE;
        }
    }

    private void loadRememberedId() {
        try {
            String id = Preferences.userNodeForPackage(AccountScreen.class).get(REMEMBER_KEY, "");
            loginIdField.setText(id);
            rememberToggle.setChecked(true);
        } catch (RuntimeException e) {
            System.err.println("Could not read remembered cleaner ID: " + e.getMessage());
        }
    }

    private static void saveRememberedId(String id) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(AccountScreen.class);
            if (id == null) prefs.remove(REMEMBER_KEY);
            else prefs.put(REMEMBER_KEY, id);
        } catch (RuntimeException e) {
            System.err.println("Could not save remembered cleaner ID: " + e.getMessage());
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        animationTimer.start();
        SwingUtilities.invokeLater(this::updateDefaultButton);
        focusFirstField();
    }

    @Override
    public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (background.isLoaded()) {
            background.render(g2, getWidth(), getHeight());
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(35, 67, 68), 0, getHeight(),
                    new Color(9, 20, 27)));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        g2.setColor(new Color(3, 10, 18, 175));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(0, 0, 0, 28));
        for (int y = 0; y < getHeight(); y += 4) g2.drawLine(0, y, getWidth(), y);
        g2.dispose();
    }

    // ------------------------------------------------------------------ toasts

    private enum ToastType { SUCCESS, ERROR, INFO }

    private static final class Toast {
        final List<String> lines;
        final ToastType type;
        final long expiresAt;

        Toast(String message, ToastType type) {
            this.lines = wrap(message == null ? "" : message.toUpperCase(), 2, 400);
            this.type = type;
            this.expiresAt = System.currentTimeMillis() + TOAST_MS;
        }
    }

    private void showToast(String message, ToastType type) {
        toasts.add(new Toast(message, type));
        while (toasts.size() > 5) toasts.remove(0);
        repaint();
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        long now = System.currentTimeMillis();
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); ) {
            if (it.next().expiresAt <= now) it.remove();
        }
        if (toasts.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        int y = getHeight() - 16;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast toast = toasts.get(i);
            int textW = 0;
            for (String line : toast.lines) textW = Math.max(textW, PixelFont.width(line, 2));
            int w = textW + 32;
            int h = 22 + toast.lines.size() * 20 - 6;
            int x = getWidth() - 16 - w;
            y -= h;
            Color accent = toast.type == ToastType.SUCCESS ? EMERALD_400
                    : toast.type == ToastType.ERROR ? RED_500 : SKY_400;
            Color ink = toast.type == ToastType.SUCCESS ? EMERALD_300
                    : toast.type == ToastType.ERROR ? RED_400 : SKY_300;
            ring(g2, x, y, w, h, 3, Color.BLACK);
            g2.setColor(SLATE_900);
            g2.fillRect(x, y, w, h);
            g2.setColor(new Color(255, 255, 255, 64));
            g2.fillRect(x, y, w, 3);
            g2.fillRect(x, y, 3, h);
            g2.setColor(new Color(0, 0, 0, 102));
            g2.fillRect(x, y + h - 3, w, 3);
            g2.fillRect(x + w - 3, y, 3, h);
            g2.setColor(accent);
            g2.fillRect(x + 6, y + 6, 4, h - 12);
            for (int l = 0; l < toast.lines.size(); l++) {
                text(g2, toast.lines.get(l), x + 18, y + 11 + l * 20, 2, ink);
            }
            y -= 12;
        }
        g2.dispose();
    }

    // ------------------------------------------------------------------ drawing helpers

    private static PixelFont.Style style(Color color) {
        return STYLES.computeIfAbsent(color, c -> new PixelFont.Style(c, c, null, null, 0, null));
    }

    private static void text(Graphics2D g, String s, int x, int y, int scale, Color color) {
        PixelFont.draw(g, s, x, y, scale, style(color));
    }

    private static void pixels(Graphics2D g, String[] rows, int x, int y, int scale, Color color) {
        g.setColor(color);
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == '#') g.fillRect(x + c * scale, y + r * scale, scale, scale);
            }
        }
    }

    /** Draws {x, y, w, h, rgb} rectangles on a small grid (SVG-style pixel sprites). */
    private static void sprite(Graphics2D g, int[][] rects, int x, int y, int scale) {
        for (int[] r : rects) {
            g.setColor(new Color(r[4]));
            g.fillRect(x + r[0] * scale, y + r[1] * scale, r[2] * scale, r[3] * scale);
        }
    }

    /** Pixel "border" ring around a rect with notched corners, like stacked CSS box-shadows. */
    private static void ring(Graphics2D g, int x, int y, int w, int h, int t, Color color) {
        g.setColor(color);
        g.fillRect(x - t, y, t, h);
        g.fillRect(x + w, y, t, h);
        g.fillRect(x, y - t, w, t);
        g.fillRect(x, y + h, w, t);
    }

    private static Color lighten(Color c, double k) {
        return new Color((int) (c.getRed() + (255 - c.getRed()) * k),
                (int) (c.getGreen() + (255 - c.getGreen()) * k),
                (int) (c.getBlue() + (255 - c.getBlue()) * k));
    }

    private static List<String> wrap(String message, int scale, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : message.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (PixelFont.width(candidate, scale) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ------------------------------------------------------------------ components

    /** Translucent card with green bevel, dark ring and hard drop shadow. */
    private static final class CardPanel extends JPanel {
        private final int width;

        CardPanel(LayoutManager layout, int width) {
            super(layout);
            this.width = width;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, super.getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int t = CARD_RING;
            int x = t, y = t, w = getWidth() - 2 * t, h = getHeight() - 2 * t - 6;
            g2.setColor(Color.BLACK);
            g2.fillRect(x, y + h + t, w, 6);
            ring(g2, x, y, w, h, t, SLATE_900);
            g2.setColor(new Color(15, 23, 42, 225));
            g2.fillRect(x, y, w, h);
            g2.setColor(GREEN_LIGHT);
            g2.fillRect(x, y, w, t);
            g2.fillRect(x, y, t, h);
            g2.setColor(EMERALD_900);
            g2.fillRect(x, y + h - t, w, t);
            g2.fillRect(x + w - t, y, t, h);
            g2.dispose();
        }
    }

    /** Full-screen dim layer that hosts the passcode recovery dialog and blocks clicks beneath. */
    private static final class ModalLayer extends JPanel {
        ModalLayer() {
            super(new GridBagLayout());
            setOpaque(false);
            setFocusCycleRoot(true);
            addMouseListener(new MouseAdapter() { });
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(2, 6, 23, 205));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /** Chunky arcade button: coloured face, dark ring, highlight strip and hard shadow. */
    private static final class PixelButton extends JButton {
        private static final int RING = 4;
        private static final int DROP = 2;
        private final String label;
        private final String[] icon;
        private Color fill = SLATE_800;
        private Color ringColor = SLATE_700;
        private Color shine = SLATE_700;
        private Color ink = SLATE_400;
        private Color arrow;
        private boolean flat = true;
        private int padY = 10;

        PixelButton(String label, String[] icon) {
            this.label = label;
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(label.replace("▶", "").trim());
        }

        void setTheme(Color fill, Color ringColor, Color shine, Color ink, Color arrow) {
            this.fill = fill;
            this.ringColor = ringColor;
            this.shine = shine;
            this.ink = ink;
            this.arrow = arrow;
            this.flat = false;
            repaint();
        }

        void setFlat() {
            flat = true;
            arrow = null;
            repaint();
        }

        void setTextPadding(int padY) {
            this.padY = padY;
        }

        private int iconWidth() {
            return icon == null ? 0 : icon[0].length() * 2 + 10;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(PixelFont.width("▶ " + label, 2) + iconWidth() + 32 + 2 * RING,
                    PixelFont.GLYPH_H * 2 + 2 * padY + 2 * RING + DROP);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            boolean hover = getModel().isRollover();
            int x = RING, y = RING + (pressed ? DROP : 0);
            int w = getWidth() - 2 * RING, h = getHeight() - 2 * RING - DROP;
            Color textColor;
            if (flat) {
                g2.setColor(SLATE_700);
                g2.fillRect(x - 2, y - 2, w + 4, h + 4);
                g2.setColor(SLATE_800);
                g2.fillRect(x, y, w, h);
                textColor = hover ? WHITE : SLATE_400;
            } else {
                if (!pressed) {
                    g2.setColor(Color.BLACK);
                    g2.fillRect(x, y + h + RING, w, DROP);
                }
                ring(g2, x, y, w, h, RING, ringColor);
                g2.setColor(hover && !pressed ? lighten(fill, 0.12) : fill);
                g2.fillRect(x, y, w, h);
                g2.setColor(pressed ? ringColor : shine);
                g2.fillRect(x, y, w, pressed ? 2 : 4);
                textColor = ink;
            }
            if (isFocusOwner()) {
                g2.setColor(flat ? SLATE_400 : ink);
                for (int i = x + 4; i < x + w - 4; i += 4) {
                    g2.fillRect(i, y + h - 5, 2, 1);
                }
            }
            String prefix = arrow != null ? "▶ " : "";
            int tw = iconWidth() + PixelFont.width(prefix + label, 2);
            int tx = x + (w - tw) / 2;
            int ty = y + (h - PixelFont.GLYPH_H * 2) / 2 + 1;
            if (icon != null) {
                pixels(g2, icon, tx, y + (h - icon.length * 2) / 2 + 1, 2, textColor);
                tx += iconWidth();
            }
            if (arrow != null) {
                text(g2, "▶", tx, ty, 2, arrow);
                tx += PixelFont.width(prefix, 2) + 2;
            }
            text(g2, label, tx, ty, 2, textColor);
            g2.dispose();
        }
    }

    /** Text-only pixel link ("FORGOT CODE?", modal close "X"); underlines on hover/focus. */
    private static final class LinkButton extends JButton {
        private final String label;
        private final Color color;

        LinkButton(String label, Color color) {
            this.label = label;
            this.color = color;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("X".equals(label) ? "Close" : label);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(PixelFont.width(label, 2) + 4, PixelFont.GLYPH_H * 2 + 6);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            boolean active = getModel().isRollover() || isFocusOwner();
            Color c = active && color == SLATE_400 ? WHITE : color;
            int ty = (getHeight() - PixelFont.GLYPH_H * 2 - 4) / 2;
            text(g2, label, 2, ty, 2, c);
            if (active && color != SLATE_400) {
                g2.setColor(c);
                g2.fillRect(2, ty + PixelFont.GLYPH_H * 2 + 2, PixelFont.width(label, 2), 2);
            }
            g2.dispose();
        }
    }

    /** Pixel-font caption with an optional yellow icon. */
    private static final class PixelLabel extends JComponent {
        private final String label;
        private final Color color;
        private final String[] icon;

        PixelLabel(String label, Color color, String[] icon) {
            this.label = label;
            this.color = color;
            this.icon = icon;
        }

        @Override
        public Dimension getPreferredSize() {
            int iconW = icon == null ? 0 : icon[0].length() * 2 + 8;
            int iconH = icon == null ? 0 : icon.length * 2;
            return new Dimension(iconW + PixelFont.width(label, 2), Math.max(iconH, PixelFont.GLYPH_H * 2));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int h = getPreferredSize().height;
            int x = 0;
            if (icon != null) {
                pixels(g2, icon, 0, (h - icon.length * 2) / 2, 2, YELLOW_400);
                x = icon[0].length() * 2 + 8;
            }
            text(g2, label, x, (h - PixelFont.GLYPH_H * 2) / 2, 2, color);
            g2.dispose();
        }
    }

    /** Word-wrapped block of pixel text. */
    private static final class PixelParagraph extends JComponent {
        private final List<String> lines;
        private final Color color;

        PixelParagraph(String text, Color color, int width) {
            this.lines = wrap(text.toUpperCase(), 2, width);
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, lines.size() * 20 - 6);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            for (int i = 0; i < lines.size(); i++) text(g2, lines.get(i), 0, i * 20, 2, color);
            g2.dispose();
        }
    }

    /** Recessed pixel input: slate ring that turns green on focus, inner shadow, placeholder. */
    private static final class InputBox extends JPanel {
        private static final int RING = 3;
        private final JTextField field;
        private final String placeholder;

        InputBox(JTextField field, String placeholder, JComponent trailing, boolean compact) {
            super(new BorderLayout(8, 0));
            this.field = field;
            this.placeholder = placeholder;
            int padY = compact ? 5 : 9;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(RING + padY, RING + 12, RING + padY, RING + 10));
            field.setOpaque(false);
            field.setBorder(BorderFactory.createEmptyBorder());
            field.setFont(INPUT_FONT);
            field.setForeground(WHITE);
            field.setCaretColor(GREEN_LIGHT);
            field.setSelectionColor(GREEN_DARK);
            field.setSelectedTextColor(WHITE);
            field.setPreferredSize(new Dimension(120, 26));
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            add(field, BorderLayout.CENTER);
            if (trailing != null) add(trailing, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int x = RING, y = RING, w = getWidth() - 2 * RING, h = getHeight() - 2 * RING;
            ring(g2, x, y, w, h, RING, field.isFocusOwner() ? GREEN : SLATE_700);
            g2.setColor(SLATE_900);
            g2.fillRect(x, y, w, h);
            g2.setColor(SLATE_950);
            g2.fillRect(x, y, w, RING);
            g2.fillRect(x, y, RING, h);
            if (field.getDocument().getLength() == 0 && placeholder != null) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(INPUT_FONT);
                g2.setColor(SLATE_600);
                FontMetrics fm = g2.getFontMetrics();
                int ty = field.getY() + (field.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, field.getX() + 2, ty);
            }
            g2.dispose();
        }
    }

    /** Pixel eye button that shows or hides a password field. */
    private static final class EyeToggle extends JComponent {
        private final JPasswordField field;
        private final char echo;
        private boolean hover;

        EyeToggle(JPasswordField field) {
            this.field = field;
            this.echo = field.getEchoChar();
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Show / hide passcode");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { toggle(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        private void toggle() {
            SoundFx.play(Sound.BLIP);
            field.setEchoChar(field.getEchoChar() == 0 ? echo : (char) 0);
            field.requestFocusInWindow();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(18, 14);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            String[] icon = field.getEchoChar() == 0 ? ICON_EYE_SLASH : ICON_EYE;
            pixels(g2, icon, 0, (getHeight() - 14) / 2, 2, hover ? WHITE : SLATE_400);
            g2.dispose();
        }
    }

    /** Square pixel checkbox with a caption; toggles on click or space. */
    private static final class RememberToggle extends JComponent {
        private final String label;
        private boolean checked;

        RememberToggle(String label) {
            this.label = label;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                    setChecked(!checked);
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "toggle");
            getActionMap().put("toggle", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { setChecked(!checked); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean checked) {
            this.checked = checked;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(16 + 10 + PixelFont.width(label, 2), 18);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int y = (getHeight() - 16) / 2;
            g2.setColor(checked ? EMERALD_500 : SLATE_600);
            g2.fillRect(0, y, 16, 16);
            g2.setColor(checked ? EMERALD_500 : SLATE_900);
            g2.fillRect(2, y + 2, 12, 12);
            if (checked) pixels(g2, ICON_TICK, 2, y + 3, 2, WHITE);
            text(g2, label, 26, (getHeight() - PixelFont.GLYPH_H * 2) / 2, 2,
                    isFocusOwner() ? WHITE : SLATE_300);
            g2.dispose();
        }
    }

    /** Selectable starter-role card with a 16x16 pixel character sprite. */
    private final class RoleCard extends JComponent {
        private final CleanerRole role;
        private boolean hover;

        RoleCard(CleanerRole role) {
            this.role = role;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(role.getDisplayName() + ": " + role.getGear() + ", " + role.getPerk());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                    selectRole(role);
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
            getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "select");
            getActionMap().put("select", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { selectRole(role); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 8 + 32 + 6 + 7 + 6 + 7 + 6 + 7 + 8);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            boolean selected = selectedRole == role;
            int w = getWidth(), h = getHeight();
            g2.setColor(selected ? EMERALD_400 : hover || isFocusOwner() ? SLATE_500 : SLATE_700);
            g2.fillRect(0, 0, w, h);
            g2.setColor(selected ? new Color(2, 44, 34, 235) : new Color(15, 23, 42, 235));
            g2.fillRect(2, 2, w - 4, h - 4);

            int y = 8;
            BufferedImage portrait = rolePortrait(role);
            if (portrait != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(portrait, (w - 32) / 2, y, 32, 32, null);
            } else {
                sprite(g2, spriteFor(role), (w - 32) / 2, y, 2);
            }
            y += 32 + 6;
            centered(g2, role.getDisplayName().toUpperCase(), y, selected ? WHITE : SLATE_300);
            y += 7 + 6;
            centered(g2, role.getGear().toUpperCase(), y, selected ? EMERALD_300 : SLATE_400);
            y += 7 + 6;
            Color perk = role == CleanerRole.WASTE_COLLECTOR ? YELLOW_400
                    : role == CleanerRole.LITTER_PICKER ? EMERALD_400 : CYAN_400;
            centered(g2, role.getPerk().toUpperCase(), y, perk);
            g2.dispose();
        }

        private void centered(Graphics2D g2, String s, int y, Color color) {
            text(g2, s, (getWidth() - PixelFont.width(s, 1)) / 2, y, 1, color);
        }
    }

    private static final Map<CleanerRole, BufferedImage> PORTRAITS = new EnumMap<>(CleanerRole.class);

    /** Idle front frame (top-left 16x16) of the role's in-game spritesheet, or null if missing. */
    private static BufferedImage rolePortrait(CleanerRole role) {
        if (PORTRAITS.containsKey(role)) return PORTRAITS.get(role);
        BufferedImage frame = null;
        String dir = System.getProperty("user.dir");
        for (File file : new File[] {new File(dir, "assets/" + role.getSpriteSheet()),
                new File(dir, "bin/assets/" + role.getSpriteSheet())}) {
            if (!file.isFile()) continue;
            try {
                BufferedImage sheet = ImageIO.read(file);
                if (sheet != null && sheet.getWidth() >= 16 && sheet.getHeight() >= 16) {
                    frame = sheet.getSubimage(0, 0, 16, 16);
                    break;
                }
            } catch (IOException e) {
                System.err.println("Could not read " + file + ": " + e.getMessage());
            }
        }
        PORTRAITS.put(role, frame);
        return frame;
    }

    /** Fallback card art used only if a role spritesheet is missing. */
    private static int[][] spriteFor(CleanerRole role) {
        switch (role) {
            case WASTE_COLLECTOR:
                return new int[][] {
                        {3, 1, 10, 2, 0x854d0e}, {2, 3, 12, 1, 0xa16207}, {5, 4, 6, 4, 0xfed7aa},
                        {6, 5, 1, 1, 0x000000}, {9, 5, 1, 1, 0x000000}, {4, 8, 8, 5, 0x166534},
                        {7, 8, 2, 5, 0xca8a04}, {5, 13, 2, 3, 0x3f6212}, {9, 13, 2, 3, 0x3f6212}};
            case VOLUNTEER:
                return new int[][] {
                        {4, 1, 8, 3, 0xef4444}, {5, 4, 6, 4, 0xfde047}, {6, 5, 1, 1, 0x000000},
                        {9, 5, 1, 1, 0x000000}, {4, 8, 8, 5, 0x0284c7}, {2, 9, 2, 3, 0x38bdf8},
                        {5, 13, 2, 3, 0x0f172a}, {9, 13, 2, 3, 0x0f172a}};
            case LITTER_PICKER:
            default:
                return new int[][] {
                        {4, 1, 8, 2, 0x22c55e}, {3, 3, 10, 1, 0x15803d}, {5, 4, 6, 4, 0xfde047},
                        {6, 5, 1, 1, 0x000000}, {9, 5, 1, 1, 0x000000}, {4, 8, 8, 5, 0x38bdf8},
                        {6, 8, 4, 5, 0xeab308}, {5, 13, 2, 3, 0x1e293b}, {9, 13, 2, 3, 0x1e293b},
                        {12, 6, 1, 9, 0x92400e}, {11, 14, 3, 2, 0xfde047}};
        }
    }

    /** Four-segment "health bar" password strength meter. */
    private static final class StrengthMeter extends JComponent {
        private String status = "NO CODE";
        private Color statusColor = SLATE_500;
        private int filled;
        private Color segmentColor = SLATE_800;

        void set(String status, Color statusColor, int filled, Color segmentColor) {
            this.status = status;
            this.statusColor = statusColor;
            this.filled = filled;
            this.segmentColor = segmentColor;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 2 + 7 + 14 + 6 + 12 + 7 + 2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            g2.setColor(SLATE_800);
            g2.fillRect(0, 0, w, h);
            g2.setColor(SLATE_950);
            g2.fillRect(2, 2, w - 4, h - 4);
            text(g2, "SECURITY RATING:", 10, 9, 2, SLATE_400);
            text(g2, status, w - 10 - PixelFont.width(status, 2), 9, 2, statusColor);

            int bx = 10, by = 29, bw = w - 20, bh = 12;
            g2.setColor(SLATE_800);
            g2.fillRect(bx, by, bw, bh);
            g2.setColor(SLATE_900);
            g2.fillRect(bx + 1, by + 1, bw - 2, bh - 2);
            int gap = 4;
            int segW = (bw - 6 - gap * 3) / 4;
            for (int i = 0; i < 4; i++) {
                g2.setColor(i < filled ? segmentColor : SLATE_800);
                g2.fillRect(bx + 3 + i * (segW + gap), by + 3, segW, bh - 6);
            }
            g2.dispose();
        }
    }

    /** Top-bar cleanliness tracker: twinkling sparkle, percentage, status tag and gradient bar. */
    private static final class CleanlinessHud extends JComponent {
        private static final int BAR_W = 170;
        private final int health;

        CleanlinessHud(int health) {
            this.health = Math.max(0, Math.min(100, health));
            setToolTipText("Community cleanliness: starts at " + UserDatabase.CLEANLINESS_BASE
                    + "% and rises as every cleaner scores points.");
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(2 + 12 + 14 + 8 + PixelFont.width("CLEANLINESS:", 2) + 14 + BAR_W + 12 + 2,
                    2 + 7 + 14 + 5 + 14 + 7 + 2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            g2.setColor(SLATE_700);
            g2.fillRect(0, 0, w, h);
            g2.setColor(SLATE_900);
            g2.fillRect(2, 2, w - 4, h - 4);

            boolean blinkOn = (System.currentTimeMillis() / 500) % 2 == 0;
            pixels(g2, ICON_SPARKLE, 14, (h - 14) / 2, 2, blinkOn ? EMERALD_400 : new Color(6, 95, 70));
            int x = 14 + 14 + 8;
            text(g2, "CLEANLINESS:", x, (h - 14) / 2, 2, EMERALD_400);
            x += PixelFont.width("CLEANLINESS:", 2) + 14;

            String pct = health + "%";
            String tag;
            Color tagColor;
            if (health < 40) { tag = "[CRITICAL]"; tagColor = RED_400; }
            else if (health < 75) { tag = "[MODERATE]"; tagColor = YELLOW_400; }
            else { tag = "[OPTIMAL]"; tagColor = EMERALD_400; }
            int top = 2 + 7;
            text(g2, pct, x, top, 2, YELLOW_300);
            text(g2, tag, x + BAR_W - PixelFont.width(tag, 1), top + 4, 1, tagColor);

            int by = top + 14 + 5;
            g2.setColor(SLATE_700);
            g2.fillRect(x, by, BAR_W, 14);
            g2.setColor(SLATE_950);
            g2.fillRect(x + 2, by + 2, BAR_W - 4, 10);
            int fillW = (int) Math.round((BAR_W - 8) * health / 100.0);
            if (fillW > 0) {
                g2.setPaint(new LinearGradientPaint(x + 4, 0, x + 4 + Math.max(2, fillW), 0,
                        new float[] {0f, 0.5f, 1f}, new Color[] {RED_500, AMBER_400, EMERALD_400}));
                g2.fillRect(x + 4, by + 4, fillW, 6);
            }
            g2.dispose();
        }
    }

    /** Speaker toggle button for menu sound effects. */
    private static final class SfxButton extends JButton {
        SfxButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("Toggle sound effects");
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(2 + 12 + 16 + 8 + PixelFont.width("SFX: OFF", 2) + 12 + 2, 38);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            boolean on = SoundFx.isEnabled();
            g2.setColor(isFocusOwner() ? SLATE_400 : SLATE_700);
            g2.fillRect(0, 0, w, h);
            g2.setColor(getModel().isRollover() ? SLATE_800 : SLATE_900);
            g2.fillRect(2, 2, w - 4, h - 4);
            pixels(g2, on ? ICON_SPEAKER_ON : ICON_SPEAKER_OFF, 14, (h - 14) / 2, 2,
                    on ? YELLOW_400 : SLATE_500);
            text(g2, on ? "SFX: ON" : "SFX: OFF", 14 + 16 + 8, (h - 14) / 2, 2, EMERALD_400);
            g2.dispose();
        }
    }

    /** Floating pixel globe emblem, ECO QUEST title and subtitle. Compact mode hides the emblem. */
    private static final class Branding extends JComponent {
        private static final String TITLE = "ECO QUEST";
        private static final String SUBTITLE =
                "★ RECYCLE • CLEAN THE CITY • DEFEAT POLLUTION ★";
        private static final int TITLE_SCALE = 5;
        // x, y, w, h, rgb in a 16x16 grid.
        private static final int[][] GLOBE = {
                {4, 1, 8, 1, 0x0284c7}, {2, 2, 12, 2, 0x38bdf8}, {1, 4, 14, 8, 0x0284c7},
                {2, 12, 12, 2, 0x38bdf8}, {4, 14, 8, 1, 0x0284c7}, {4, 3, 4, 3, 0x22c55e},
                {9, 5, 5, 4, 0x15803d}, {3, 8, 6, 4, 0x22c55e}, {10, 2, 2, 2, 0x4ade80},
                {6, 6, 4, 4, 0xfde047}, {7, 5, 2, 1, 0xeab308}, {7, 10, 2, 1, 0xeab308}};
        private static final int EMBLEM = 48 + 16 + 8;
        private boolean compact;

        void setCompact(boolean compact) {
            if (this.compact == compact) return;
            this.compact = compact;
            revalidate();
            repaint();
        }

        private int emblemSpace() {
            return compact ? 0 : 6 + EMBLEM + 12;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(CARD_WIDTH, emblemSpace() + PixelFont.GLYPH_H * TITLE_SCALE + 4
                    + 10 + PixelFont.GLYPH_H * 2 + 2);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int cx = getWidth() / 2;

            if (!compact) {
                // steps(4) float: 0 -> -6px -> 0 over 2.5s.
                double t = (System.currentTimeMillis() % 2500) / 2500.0;
                double step = Math.floor(((t % 0.5) / 0.5) * 4) / 4;
                int bob = (int) Math.round(t < 0.5 ? -6 * step : -6 + 6 * step);
                int ex = cx - EMBLEM / 2, ey = 6 + bob;
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRect(ex + 4, ey + 8, EMBLEM, EMBLEM);
                g2.setColor(EMERALD_500);
                g2.fillRect(ex, ey, EMBLEM, EMBLEM);
                g2.setColor(new Color(15, 23, 42, 235));
                g2.fillRect(ex + 4, ey + 4, EMBLEM - 8, EMBLEM - 8);
                sprite(g2, GLOBE, ex + 12, ey + 12, 3);
            }

            int ty = emblemSpace();
            int tw = PixelFont.width(TITLE, TITLE_SCALE);
            text(g2, TITLE, cx - tw / 2 + 4, ty + 4, TITLE_SCALE, Color.BLACK);
            text(g2, TITLE, cx - tw / 2, ty, TITLE_SCALE, EMERALD_400);

            int sy = ty + PixelFont.GLYPH_H * TITLE_SCALE + 4 + 10;
            int sw = PixelFont.width(SUBTITLE, 2);
            text(g2, SUBTITLE, cx - sw / 2 + 2, sy + 2, 2, Color.BLACK);
            text(g2, SUBTITLE, cx - sw / 2, sy, 2, YELLOW_300);
            g2.dispose();
        }
    }

    /** Footer row of the four waste-category badges. */
    private static final class WasteBadges extends JComponent {
        private static final String[] NAMES = {"BIODEGRADABLE", "RECYCLABLE", "NON-RECYCLABLE", "HAZARDOUS"};
        private static final Color[] BORDERS = {EMERALD_600, SKY_600, AMBER_600, RED_600};
        private static final Color[] TEXT = {EMERALD_400, SKY_400, YELLOW_400, RED_400};
        // x, y, w, h, rgb in an 8x8 grid.
        private static final int[][][] ICONS = {
                {{3, 1, 2, 6, 0x4ade80}, {2, 2, 4, 3, 0x22c55e}},
                {{2, 1, 4, 6, 0x38bdf8}, {3, 2, 2, 4, 0x0284c7}},
                {{1, 2, 6, 5, 0xeab308}},
                {{2, 1, 4, 6, 0xef4444}, {3, 3, 2, 2, 0xfde047}}};
        private static final int BADGE_H = 26;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(CARD_WIDTH, 2 + 10 + BADGE_H);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        private static int badgeWidth(int i) {
            return 8 + 16 + 6 + PixelFont.width(NAMES[i], 1) + 8;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(SLATE_800);
            g2.fillRect(0, 0, getWidth(), 2);

            int total = 0;
            for (int i = 0; i < NAMES.length; i++) total += badgeWidth(i);
            int space = (getWidth() - total) / NAMES.length;
            int x = space / 2;
            int y = 12;
            for (int i = 0; i < NAMES.length; i++) {
                int bw = badgeWidth(i);
                g2.setColor(BORDERS[i]);
                g2.fillRect(x, y, bw, BADGE_H);
                g2.setColor(SLATE_900);
                g2.fillRect(x + 1, y + 1, bw - 2, BADGE_H - 2);
                sprite(g2, ICONS[i], x + 8, y + 5, 2);
                text(g2, NAMES[i], x + 8 + 16 + 6, y + (BADGE_H - PixelFont.GLYPH_H) / 2, 1, TEXT[i]);
                x += bw + space;
            }
            g2.dispose();
        }
    }
}
