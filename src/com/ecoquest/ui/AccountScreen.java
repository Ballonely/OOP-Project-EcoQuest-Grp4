package com.ecoquest.ui;

import com.ecoquest.data.UserDatabase;
import com.ecoquest.data.UserDatabase.Account;
import com.ecoquest.util.Constants;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.function.Consumer;

/** Retro terminal-style login and registration screen. */
public final class AccountScreen extends JPanel {
    private static final Color TERMINAL = new Color(5, 14, 24, 238);
    private static final Color INPUT = new Color(3, 10, 20);
    private static final Color MUTED = new Color(137, 163, 171);
    private static final Font LABEL_FONT = new Font("Monospaced", Font.BOLD, 13);
    private static final Font INPUT_FONT = new Font("Monospaced", Font.PLAIN, 15);
    private static final Font DETAIL_FONT = new Font("Monospaced", Font.PLAIN, 11);

    private final UserDatabase database;
    private final Consumer<Account> onAuthenticated;
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmPasswordField = new JPasswordField();
    private final JPanel confirmGroup = new JPanel(new BorderLayout(0, 7));
    private final JLabel messageLabel = new JLabel(" ");
    private final JLabel passwordStrengthLabel = new JLabel("[EMPTY]");
    private final JLabel securityStateLabel = new JLabel("[WAITING]");
    private final JPanel[] strengthSegments = {new JPanel(), new JPanel(), new JPanel()};
    private final JButton signInTab = new JButton("[1] SIGN IN");
    private final JButton registerTab = new JButton("[2] NEW ENLISTMENT");
    private final JButton submitButton = new JButton();
    private final JPanel form = new JPanel();
    private boolean registering;
    private BufferedImage backgroundImage;

    public AccountScreen(UserDatabase database, Consumer<Account> onAuthenticated) {
        this.database = database;
        this.onAuthenticated = onAuthenticated;
        setLayout(new GridBagLayout());
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        loadBackground();
        add(buildTerminal());
        setMode(false);
    }

    private JPanel buildTerminal() {
        JPanel terminal = new JPanel(new BorderLayout(0, 14)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(TERMINAL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(53, 218, 172, 12));
                for (int y = 3; y < getHeight(); y += 4) {
                    g2.drawLine(2, y, getWidth() - 3, y);
                }
                g2.setColor(new Color(10, 18, 27, 245));
                g2.drawRect(7, 7, getWidth() - 15, getHeight() - 15);
                g2.setColor(Constants.COLOR_TEAL);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        terminal.setOpaque(false);
        terminal.setBorder(BorderFactory.createEmptyBorder(28, 34, 24, 34));
        terminal.setPreferredSize(new Dimension(720, 680));
        terminal.setMinimumSize(new Dimension(560, 600));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel gateway = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        gateway.setOpaque(false);
        addStatusLight(gateway, new Color(255, 78, 80));
        addStatusLight(gateway, Constants.COLOR_GOLD);
        addStatusLight(gateway, Constants.COLOR_TEAL);
        JLabel gatewayTitle = new JLabel("CADET GATEWAY");
        gatewayTitle.setFont(new Font("Monospaced", Font.BOLD, 16));
        gatewayTitle.setForeground(Constants.COLOR_TEAL);
        gateway.add(gatewayTitle);

        form.setOpaque(false);
        form.setLayout(new GridBagLayout());
        form.setMaximumSize(new Dimension(610, Integer.MAX_VALUE));
        form.setAlignmentX(CENTER_ALIGNMENT);
        signInTab.addActionListener(event -> setMode(false));
        registerTab.addActionListener(event -> setMode(true));
        styleTab(signInTab);
        styleTab(registerTab);
        JPanel tabs = new JPanel(new GridLayout(1, 2, 12, 0));
        tabs.setOpaque(false);
        tabs.add(signInTab);
        tabs.add(registerTab);

        JLabel authStatus = new JLabel("LOCAL AUTH");
        authStatus.setFont(DETAIL_FONT);
        authStatus.setForeground(MUTED);
        heading.add(gateway, BorderLayout.WEST);
        heading.add(authStatus, BorderLayout.EAST);
        heading.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(28, 68, 70)),
                BorderFactory.createEmptyBorder(0, 0, 12, 0)));

        usernameField.setFont(INPUT_FONT);
        passwordField.setFont(INPUT_FONT);
        confirmPasswordField.setFont(INPUT_FONT);
        styleField(usernameField);
        styleField(passwordField);
        styleField(confirmPasswordField);
        passwordStrengthLabel.setFont(new Font("Monospaced", Font.BOLD, 10));
        passwordStrengthLabel.setForeground(Constants.COLOR_GOLD);
        passwordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) { updatePasswordStrength(); }
            @Override
            public void removeUpdate(DocumentEvent event) { updatePasswordStrength(); }
            @Override
            public void changedUpdate(DocumentEvent event) { updatePasswordStrength(); }
        });

        confirmGroup.setOpaque(false);
        confirmGroup.add(fieldGroup("CONFIRM SECURITY KEY", "RE-ENTER PASSWORD",
                "▣", confirmPasswordField), BorderLayout.CENTER);

        int row = 0;
        addFormRow(tabs, row++, 0, 19);
        addFormRow(fieldGroup("RANGER CODENAME / ID", "MAX 24 CHARS",
                "\u26a1", usernameField), row++, 0, 17);
        addFormRow(fieldGroup("SECURITY KEY (PASSWORD)", passwordStrengthLabel,
                "\u25a3", passwordField), row++, 0, 7);
        addFormRow(buildSecurityStatus(), row++, 0, 16);
        addFormRow(confirmGroup, row++, 0, 15);

        messageLabel.setFont(DETAIL_FONT);
        messageLabel.setForeground(Constants.COLOR_GOLD);
        addFormRow(buildUtilityRow(), row++, 0, 12);
        addFormRow(messageLabel, row++, 0, 8);

        submitButton.setFont(new Font("Monospaced", Font.BOLD, 20));
        submitButton.setForeground(new Color(3, 25, 26));
        submitButton.setBackground(new Color(45, 212, 165));
        submitButton.setOpaque(true);
        submitButton.setFocusPainted(false);
        submitButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(157, 255, 218), 3),
                BorderFactory.createEmptyBorder(15, 10, 15, 10)));
        submitButton.addActionListener(this::submit);
        addFormRow(submitButton, row, 0, 0);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(form);

        JLabel footer = new JLabel("PROGRESS SAVED LOCALLY  \u2022  v1.0.4");
        footer.setFont(DETAIL_FONT);
        footer.setForeground(MUTED);

        terminal.add(heading, BorderLayout.NORTH);
        terminal.add(center, BorderLayout.CENTER);
        terminal.add(footer, BorderLayout.SOUTH);
        return terminal;
    }

    private void addStatusLight(JPanel row, Color color) {
        JLabel light = new JLabel("\u25a0");
        light.setFont(new Font("Monospaced", Font.BOLD, 13));
        light.setForeground(color);
        row.add(light);
    }

    private JPanel buildSecurityStatus() {
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        status.setOpaque(false);
        JLabel caption = new JLabel("SECURITY STATUS:");
        caption.setFont(new Font("Monospaced", Font.PLAIN, 9));
        caption.setForeground(MUTED);
        status.add(caption);

        for (JPanel segment : strengthSegments) {
            segment.setOpaque(true);
            segment.setBackground(new Color(35, 57, 61));
            segment.setPreferredSize(new Dimension(20, 8));
            status.add(segment);
        }

        securityStateLabel.setFont(new Font("Monospaced", Font.BOLD, 9));
        securityStateLabel.setForeground(Constants.COLOR_TEAL);
        status.add(securityStateLabel);
        return status;
    }

    private JPanel buildUtilityRow() {
        JPanel utilities = new JPanel(new BorderLayout());
        utilities.setOpaque(false);
        JLabel saveStatus = new JLabel("\u2713  SAVE GAME STATE");
        saveStatus.setFont(new Font("Monospaced", Font.BOLD, 12));
        saveStatus.setForeground(Constants.COLOR_TEXT_LIGHT);

        JLabel sos = new JLabel("<html><u>[?] TRANSMIT SOS</u></html>");
        sos.setFont(new Font("Monospaced", Font.BOLD, 12));
        sos.setForeground(Constants.COLOR_GOLD);
        utilities.add(saveStatus, BorderLayout.WEST);
        utilities.add(sos, BorderLayout.EAST);
        return utilities;
    }

    private void updatePasswordStrength() {
        char[] password = passwordField.getPassword();
        int strength = 0;
        if (password.length >= 8) strength++;
        if (password.length >= 12) strength++;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char character : password) {
            if (Character.isLetter(character)) hasLetter = true;
            if (Character.isDigit(character)) hasDigit = true;
        }
        if (hasLetter && hasDigit) strength++;

        String label = password.length == 0 ? "[EMPTY]"
                : strength <= 1 ? "[LOW]"
                : strength == 2 ? "[MEDIUM]" : "[HIGH]";
        passwordStrengthLabel.setText(label);
        securityStateLabel.setText(password.length == 0 ? "[WAITING]"
                : strength <= 1 ? "[LOW]"
                : strength == 2 ? "[SECURE]" : "[FORTIFIED]");
        Color active = strength <= 1 ? Constants.COLOR_GOLD
                : strength == 2 ? Constants.COLOR_TEAL : new Color(89, 234, 133);
        for (int index = 0; index < strengthSegments.length; index++) {
            strengthSegments[index].setBackground(index < strength
                    ? active : new Color(35, 57, 61));
        }
        Arrays.fill(password, '\0');
    }

    private JPanel fieldGroup(String label, String detail, String icon, JTextField field) {
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(new Font("Monospaced", Font.BOLD, 10));
        detailLabel.setForeground(MUTED);
        return fieldGroup(label, detailLabel, icon, field);
    }

    private JPanel fieldGroup(String label, JLabel detail, String icon, JTextField field) {
        JPanel group = new JPanel(new BorderLayout(0, 7));
        group.setOpaque(false);
        JPanel captionRow = new JPanel(new BorderLayout());
        captionRow.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setFont(LABEL_FONT);
        caption.setForeground(Constants.COLOR_TEAL);
        captionRow.add(caption, BorderLayout.WEST);
        captionRow.add(detail, BorderLayout.EAST);

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(true);
        inputRow.setBackground(INPUT);
        inputRow.setBorder(BorderFactory.createLineBorder(new Color(52, 83, 91)));
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        iconLabel.setForeground(Constants.COLOR_GOLD);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        inputRow.add(iconLabel, BorderLayout.WEST);
        inputRow.add(field, BorderLayout.CENTER);
        group.add(captionRow, BorderLayout.NORTH);
        group.add(inputRow, BorderLayout.CENTER);
        return group;
    }

    private void addFormRow(JComponent content, int row, int top, int bottom) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(top, 0, bottom, 0);
        form.add(content, constraints);
    }

    private void styleTab(JButton button) {
        button.setFont(new Font("Monospaced", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(17, 8, 17, 8));
    }

    private void styleField(JTextField field) {
        field.setForeground(Constants.COLOR_TEXT_LIGHT);
        field.setCaretColor(Constants.COLOR_TEAL);
        field.setBackground(INPUT);
        field.setFont(INPUT_FONT);
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 12));
        field.setPreferredSize(new Dimension(400, 48));
        field.setMinimumSize(new Dimension(240, 48));
    }

    private void setMode(boolean register) {
        registering = register;
        confirmGroup.setVisible(register);
        setTabStyle(signInTab, !register);
        setTabStyle(registerTab, register);
        submitButton.setText(register
                ? "\u25b6  CREATE CADET ACCOUNT"
                : "<html><center>\u25b6  INITIALIZE MISSION<br>(LOGIN)</center></html>");
        messageLabel.setText(" ");
        form.revalidate();
        form.repaint();
        if (getRootPane() != null) getRootPane().setDefaultButton(submitButton);
    }

    private void setTabStyle(JButton button, boolean selected) {
        button.setForeground(selected ? new Color(3, 25, 26) : MUTED);
        button.setBackground(selected ? Constants.COLOR_TEAL : new Color(5, 14, 24));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected
                        ? new Color(145, 255, 213) : new Color(44, 67, 77),
                        selected ? 2 : 1),
                BorderFactory.createEmptyBorder(17, 8, 17, 8)));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            if (getRootPane() != null) getRootPane().setDefaultButton(submitButton);
            usernameField.requestFocusInWindow();
        });
    }

    private void submit(ActionEvent event) {
        char[] password = passwordField.getPassword();
        char[] confirmation = confirmPasswordField.getPassword();
        try {
            String username = usernameField.getText().trim();
            if (registering && !Arrays.equals(password, confirmation)) {
                showMessage("SECURITY KEYS DO NOT MATCH.");
                confirmPasswordField.requestFocusInWindow();
                return;
            }
            Account account = registering
                    ? database.register(username, password)
                    : database.login(username, password);
            onAuthenticated.accept(account);
        } catch (IllegalArgumentException | IOException | GeneralSecurityException e) {
            showMessage(e.getMessage());
            passwordField.requestFocusInWindow();
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            passwordField.setText("");
            confirmPasswordField.setText("");
        }
    }

    private void showMessage(String message) {
        String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        messageLabel.setText("<html><div style='width:380px'>" + escaped + "</div></html>");
    }

    private void loadBackground() {
        try {
            if (getClass().getResource("/assets/menu_bg.jpg") != null) {
                backgroundImage = ImageIO.read(getClass().getResource("/assets/menu_bg.jpg"));
            } else {
                File file = new File("assets/menu_bg.jpg");
                if (file.isFile()) backgroundImage = ImageIO.read(file);
            }
        } catch (IOException e) {
            System.err.println("Could not load account screen background: " + e.getMessage());
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (backgroundImage != null) {
            double scale = Math.max((double) getWidth() / backgroundImage.getWidth(),
                    (double) getHeight() / backgroundImage.getHeight());
            int width = (int) Math.round(backgroundImage.getWidth() * scale);
            int height = (int) Math.round(backgroundImage.getHeight() * scale);
            g2.drawImage(backgroundImage, (getWidth() - width) / 2,
                    (getHeight() - height) / 2, width, height, null);
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
}