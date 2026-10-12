package com.lanchat.client;

import com.lanchat.client.ui.BrandPanel;
import com.lanchat.client.ui.RoundedButton;
import com.lanchat.client.ui.RoundedPasswordField;
import com.lanchat.client.ui.RoundedTextField;
import com.lanchat.client.ui.Theme;
import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.TextMessage;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/** Login / register window. On a successful login it opens the ChatFrame and hands over the connection. */
public class LoginFrame extends JFrame {

    private final RoundedTextField hostField = new RoundedTextField(14);
    private final RoundedTextField portField = new RoundedTextField(5);
    private final RoundedTextField userField = new RoundedTextField(20);
    private final RoundedPasswordField passField = new RoundedPasswordField(20);
    private final RoundedButton loginButton = RoundedButton.primary("Sign in");
    private final RoundedButton registerButton = RoundedButton.ghost("Create account");
    private final JLabel statusLabel = new JLabel(" ");

    private ChatConnection connection;   // kept between a Register click and the following Login click

    public LoginFrame() {
        super("LAN Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(new BrandPanel(), BorderLayout.WEST);
        add(buildForm(), BorderLayout.CENTER);

        loginButton.addActionListener(e -> submit(MessageType.LOGIN));
        registerButton.addActionListener(e -> submit(MessageType.REGISTER));
        getRootPane().setDefaultButton(loginButton);   // Enter key = Sign in

        setSize(860, 540);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private JPanel buildForm() {
        hostField.setText("localhost");
        portField.setText("5000");
        userField.setPlaceholder("Your username");
        passField.setPlaceholder("Your password");

        JLabel title = new JLabel("Welcome back");
        title.setFont(Theme.font(Font.BOLD, 28f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Sign in to join the conversation");
        subtitle.setFont(Theme.font(Font.PLAIN, 14f));
        subtitle.setForeground(Theme.TEXT_MUTED);

        JPanel serverRow = new JPanel(new BorderLayout(10, 0));
        serverRow.setOpaque(false);
        serverRow.add(hostField, BorderLayout.CENTER);
        serverRow.add(portField, BorderLayout.EAST);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        buttons.add(loginButton);
        buttons.add(registerButton);

        statusLabel.setFont(Theme.font(Font.PLAIN, 13f));
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        statusLabel.setPreferredSize(new Dimension(300, 40));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int row = 0;
        addRow(form, Box.createHorizontalStrut(340), row++, 0);
        addRow(form, title, row++, 0);
        addRow(form, subtitle, row++, 4);
        addRow(form, fieldLabel("USERNAME"), row++, 26);
        addRow(form, userField, row++, 6);
        addRow(form, fieldLabel("PASSWORD"), row++, 14);
        addRow(form, passField, row++, 6);
        addRow(form, fieldLabel("SERVER ADDRESS AND PORT"), row++, 14);
        addRow(form, serverRow, row++, 6);
        addRow(form, buttons, row++, 24);
        addRow(form, statusLabel, row, 12);

        JPanel outer = new JPanel(new GridBagLayout());   // centers the form
        outer.setBackground(Theme.WHITE);
        outer.add(form);
        return outer;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 11f));
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    private static void addRow(JPanel panel, Component c, int row, int topGap) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(topGap, 0, 0, 0);
        panel.add(c, gc);
    }

    private void submit(MessageType type) {
        final String host = hostField.getText().trim();
        final String user = userField.getText().trim();
        final String pass = new String(passField.getPassword());

        Integer parsedPort = parsePort();
        if (parsedPort == null) {
            setStatus("The port must be a number.", true);
            return;
        }
        final int port = parsedPort;
        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            setStatus("Please enter the server, username and password.", true);
            return;
        }

        setBusy(true);
        // Network work runs off the Swing thread so the window never freezes.
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                if (connection == null) {
                    connection = new ChatConnection(host, port);
                }
                connection.send(new TextMessage(type, user, null, pass));
                return connection.readOne();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    handleReply(type, user, get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    resetConnection();
                    setStatus("Cannot reach the server: " + rootMessage(e), true);
                }
            }
        }.execute();
    }

    private void handleReply(MessageType type, String user, Message reply) {
        String text = reply instanceof TextMessage ? ((TextMessage) reply).getContent() : reply.display();
        if (reply.getType() == MessageType.OK && type == MessageType.LOGIN) {
            ChatConnection ready = connection;
            connection = null;                       // the chat window owns it now
            new ChatFrame(ready, user).setVisible(true);
            dispose();
        } else if (reply.getType() == MessageType.OK) {
            setStatus(text, false);
        } else {
            setStatus(text, true);
        }
    }

    private Integer parsePort() {
        try {
            return Integer.valueOf(portField.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void resetConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
        if (busy) {
            setStatus("Connecting...", false);
        }
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setForeground(error ? Theme.ERROR_FG : new Color(27, 122, 62));
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        statusLabel.setText("<html><body style='width:300px'>" + safe + "</body></html>");
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
