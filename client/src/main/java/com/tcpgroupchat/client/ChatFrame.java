package com.tcpgroupchat.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.FlatClientProperties;
import com.tcpgroupchat.common.protocol.ChatMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class ChatFrame extends JFrame {
    private static final String GROUP = "study";
    private final String username;
    private final Socket socket;
    private final ObjectMapper mapper = new ObjectMapper();
    private BufferedReader reader;
    private BufferedWriter writer;
    private final JTextArea conversation = new JTextArea();
    private final JTextField input = new JTextField();
    private final JTextField recipient = new JTextField("username");
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(usersModel);
    private final JLabel status = new JLabel("Đang kết nối…");
    private boolean joinedGroup;

    public ChatFrame(String username, Socket socket) {
        this.username = username;
        this.socket = socket;
        setTitle("TCPGroupChat — " + username);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(850, 560));
        setSize(1000, 650);
        setLocationRelativeTo(null);
        buildUi();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        });
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("TCPGroupChat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        header.add(title, BorderLayout.WEST);
        status.setForeground(new Color(30, 130, 80));
        header.add(status, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        conversation.setEditable(false);
        conversation.setLineWrap(true);
        conversation.setWrapStyleWord(true);
        conversation.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        conversation.setBorder(new EmptyBorder(12, 12, 12, 12));
        conversation.putClientProperty(FlatClientProperties.STYLE, "arc: 12");
        root.add(new JScrollPane(conversation), BorderLayout.CENTER);

        JPanel sidebar = new JPanel(new BorderLayout(6, 8));
        sidebar.setPreferredSize(new Dimension(190, 0));
        JLabel usersTitle = new JLabel("Người dùng online");
        usersTitle.setFont(usersTitle.getFont().deriveFont(Font.BOLD, 14f));
        sidebar.add(usersTitle, BorderLayout.NORTH);
        sidebar.add(new JScrollPane(usersList), BorderLayout.CENTER);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && usersList.getSelectedValue() != null) {
                recipient.setText(usersList.getSelectedValue());
            }
        });
        root.add(sidebar, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton join = new JButton("Join Group");
        JButton leave = new JButton("Leave Group");
        JButton unicast = new JButton("Unicast");
        JButton group = new JButton("Group");
        JButton broadcast = new JButton("Broadcast");
        join.addActionListener(e -> { send(ChatMessage.of("JOIN_GROUP", username, null)); joinedGroup = true; });
        leave.addActionListener(e -> { send(ChatMessage.of("LEAVE_GROUP", username, null)); joinedGroup = false; });
        unicast.addActionListener(e -> sendText("UNICAST"));
        group.addActionListener(e -> sendText("GROUP_MESSAGE"));
        broadcast.addActionListener(e -> sendText("BROADCAST"));
        modes.add(join); modes.add(leave);
        modes.add(new JLabel("Gửi riêng tới:")); recipient.setColumns(10); modes.add(recipient);
        modes.add(unicast); modes.add(group); modes.add(broadcast);
        bottom.add(modes, BorderLayout.NORTH);

        JPanel compose = new JPanel(new BorderLayout(8, 0));
        input.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Nhập tin nhắn…");
        input.addActionListener(e -> sendText("BROADCAST"));
        JButton send = new JButton("Gửi");
        send.addActionListener(e -> sendText("BROADCAST"));
        compose.add(input, BorderLayout.CENTER);
        compose.add(send, BorderLayout.EAST);
        bottom.add(compose, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
    }

    public void connect() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            send(ChatMessage.of("LOGIN", username, null));
            Thread receiver = new Thread(this::readLoop, "chat-receiver-" + username);
            receiver.setDaemon(true);
            receiver.start();
        } catch (IOException ex) {
            append("Không thể khởi tạo kết nối: " + ex.getMessage());
        }
    }

    private void sendText(String type) {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        ChatMessage message = ChatMessage.of(type, username, text);
        if ("UNICAST".equals(type)) message.setRecipient(recipient.getText().trim());
        if ("GROUP_MESSAGE".equals(type)) message.setGroup(GROUP);
        send(message);
        input.setText("");
    }

    private void send(ChatMessage message) {
        try {
            synchronized (this) {
                if (writer == null) return;
                writer.write(mapper.writeValueAsString(message));
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ex) {
            append("Lỗi gửi tin: " + ex.getMessage());
        }
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                ChatMessage message = mapper.readValue(line, ChatMessage.class);
                SwingUtilities.invokeLater(() -> handleMessage(message));
            }
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> status.setText("Đã mất kết nối"));
        }
    }

    private void handleMessage(ChatMessage message) {
        switch (message.getType()) {
            case "USER_LIST" -> {
                usersModel.clear();
                if (message.getContent() != null && !message.getContent().isBlank()) {
                    Arrays.stream(message.getContent().split(","))
                            .filter(name -> !name.equals(username)).forEach(usersModel::addElement);
                }
            }
            case "LOGIN_OK" -> { status.setText("Online: " + username); append(message.getContent()); }
            case "SYSTEM", "ERROR" -> append("[" + message.getType() + "] " + message.getContent());
            case "UNICAST" -> append("[Riêng từ " + message.getSender() + "] " + message.getContent());
            case "GROUP_MESSAGE" -> append("[Nhóm #" + message.getGroup() + "] " + message.getSender() + ": " + message.getContent());
            case "BROADCAST" -> append("[Broadcast] " + message.getSender() + ": " + message.getContent());
            default -> append(message.getContent() == null ? message.getType() : message.getContent());
        }
    }

    private void append(String text) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        conversation.append("[" + time + "] " + text + System.lineSeparator());
        conversation.setCaretPosition(conversation.getDocument().getLength());
    }
}
