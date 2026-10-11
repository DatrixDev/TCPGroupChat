package com.tcpgroupchat.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcpgroupchat.common.protocol.ChatMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {
    private static final int DEFAULT_PORT = 2006;
    private static final String DEFAULT_GROUP = "study";
    private static final int MAX_LOGS = 500;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> recentLogs = new CopyOnWriteArrayList<>();
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public static void main(String[] args) {
        com.formdev.flatlaf.FlatLightLaf.setup();
        javax.swing.SwingUtilities.invokeLater(() -> {
            ChatServer server = new ChatServer();
            ServerFrame frame = new ServerFrame(server);
            frame.setVisible(true);
        });
    }

    public void start(int port) throws IOException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Cổng phải nằm trong khoảng 1–65535.");
        }
        if (running) {
            log("SERVER", "Server đã đang chạy.");
            return;
        }

        groups.putIfAbsent(DEFAULT_GROUP, ConcurrentHashMap.newKeySet());
        try (ServerSocket listener = new ServerSocket(port)) {
            serverSocket = listener;
            running = true;
            log("SERVER", "TCPGroupChat đang lắng nghe tại cổng " + port);
            while (running && !listener.isClosed()) {
                try {
                    Socket socket = listener.accept();
                    log("CONNECT", "Kết nối mới từ " + socket.getRemoteSocketAddress());
                    Thread thread = new Thread(() -> handleConnection(socket), "client-" + socket.getPort());
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException ex) {
                    if (running) {
                        log("ERROR", "Không thể nhận kết nối: " + ex.getMessage());
                    }
                }
            }
        } finally {
            running = false;
            serverSocket = null;
            log("SERVER", "TCPGroupChat đã dừng.");
        }
    }

    public void stop() {
        running = false;
        ServerSocket current = serverSocket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ex) {
                log("ERROR", "Lỗi khi đóng server socket: " + ex.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        ServerSocket current = serverSocket;
        return current == null ? DEFAULT_PORT : current.getLocalPort();
    }

    public List<String> getOnlineUsers() {
        return clients.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> getRecentLogs() {
        return List.copyOf(recentLogs);
    }

    private void log(String event, String message) {
        String line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "  [" + event + "]  " + message;
        recentLogs.add(line);
        while (recentLogs.size() > MAX_LOGS) {
            recentLogs.remove(0);
        }
    }

    private void handleConnection(Socket socket) {
        ClientSession session = null;
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            session = new ClientSession(socket, writer);
            String line;
            while ((line = reader.readLine()) != null) {
                ChatMessage message;
                try {
                    message = mapper.readValue(line, ChatMessage.class);
                } catch (IOException ex) {
                    session.send(ChatMessage.of("ERROR", "SERVER", "Thông điệp JSON không hợp lệ."));
                    continue;
                }

                if (message == null || message.getType() == null) {
                    session.send(ChatMessage.of("ERROR", "SERVER", "Thông điệp thiếu loại."));
                    continue;
                }

                if ("LOGIN".equals(message.getType())) {
                    if (session.username != null) {
                        session.send(ChatMessage.of("ERROR", "SERVER", "Kết nối này đã đăng nhập."));
                        continue;
                    }

                    String username = cleanUsername(message.getSender());
                    if (username == null) {
                        session.send(ChatMessage.of("ERROR", "SERVER",
                                "Tên người dùng phải có 2–20 ký tự: chữ không dấu, số, _ hoặc -."));
                        continue;
                    }

                    ClientSession existing = clients.putIfAbsent(username, session);
                    if (existing != null) {
                        session.send(ChatMessage.of("ERROR", "SERVER", "Tên người dùng đang được sử dụng."));
                        break;
                    }

                    session.username = username;
                    session.send(ChatMessage.of("LOGIN_OK", "SERVER", "Đăng nhập thành công: " + username));
                    broadcastSystem(username + " đã kết nối.");
                    sendUserList();
                    log("LOGIN", username + " kết nối từ " + socket.getRemoteSocketAddress());
                    continue;
                }

                if (session.username == null) {
                    session.send(ChatMessage.of("ERROR", "SERVER", "Hãy đăng nhập trước."));
                    continue;
                }

                route(session, message);
            }
        } catch (IOException ex) {
            log("DISCONNECT", "Kết nối kết thúc: " + ex.getMessage());
        } finally {
            if (session != null && session.username != null) {
                String username = session.username;
                clients.remove(username, session);
                groups.values().forEach(members -> members.remove(username));
                broadcastSystem(username + " đã ngắt kết nối.");
                sendUserList();
                log("LOGOUT", username + " đã ngắt kết nối.");
            }
        }
    }

    private void route(ClientSession sender, ChatMessage message) {
        String content = message.getContent() == null ? "" : message.getContent().trim();

        switch (message.getType()) {
            case "JOIN_GROUP" -> {
                String group = normalizeGroup(message.getGroup());
                Set<String> members = groups.computeIfAbsent(group, ignored -> ConcurrentHashMap.newKeySet());
                boolean added = members.add(sender.username);
                sender.send(new ChatMessage("SYSTEM", "SERVER", null, group,
                        added ? "Bạn đã tham gia nhóm #" + group : "Bạn đã ở trong nhóm #" + group));
                if (added) {
                    sendToGroup(group,
                            ChatMessage.of("SYSTEM", "SERVER", sender.username + " đã tham gia nhóm #" + group),
                            sender.username);
                    log("GROUP", sender.username + " tham gia nhóm #" + group);
                }
            }
            case "LEAVE_GROUP" -> {
                String group = normalizeGroup(message.getGroup());
                Set<String> members = groups.get(group);
                if (members != null && members.remove(sender.username)) {
                    sender.send(new ChatMessage("SYSTEM", "SERVER", null, group,
                            "Bạn đã rời nhóm #" + group));
                    sendToGroup(group,
                            ChatMessage.of("SYSTEM", "SERVER", sender.username + " đã rời nhóm #" + group),
                            null);
                    log("GROUP", sender.username + " rời nhóm #" + group);
                } else {
                    sender.send(ChatMessage.of("ERROR", "SERVER", "Bạn chưa tham gia nhóm #" + group));
                }
            }
            case "GROUP_MESSAGE" -> {
                String group = normalizeGroup(message.getGroup());
                Set<String> members = groups.get(group);
                if (members == null || !members.contains(sender.username)) {
                    sender.send(ChatMessage.of("ERROR", "SERVER",
                            "Bạn cần Join nhóm trước khi gửi tin nhắn nhóm."));
                } else if (!content.isBlank()) {
                    sendToGroup(group,
                            new ChatMessage("GROUP_MESSAGE", sender.username, null, group, content), null);
                    log("GROUP MSG", sender.username + " → #" + group + ": " + content);
                }
            }
            case "UNICAST" -> {
                String recipient = message.getRecipient();
                if (recipient == null || recipient.isBlank()) {
                    sender.send(ChatMessage.of("ERROR", "SERVER", "Hãy nhập tên người nhận."));
                    break;
                }
                ClientSession target = clients.get(recipient.trim());
                if (target == null) {
                    sender.send(ChatMessage.of("ERROR", "SERVER", "Người nhận không online."));
                } else if (!content.isBlank()) {
                    ChatMessage outgoing = new ChatMessage("UNICAST", sender.username, recipient.trim(), null, content);
                    target.send(outgoing);
                    if (target != sender) {
                        sender.send(outgoing);
                    }
                    log("UNICAST", sender.username + " → " + recipient.trim() + ": " + content);
                }
            }
            case "BROADCAST" -> {
                if (!content.isBlank()) {
                    ChatMessage outgoing = ChatMessage.of("BROADCAST", sender.username, content);
                    clients.values().forEach(target -> target.send(outgoing));
                    log("BROADCAST", sender.username + ": " + content);
                }
            }
            case "GET_USERS" -> sendUserList();
            default -> sender.send(ChatMessage.of("ERROR", "SERVER", "Loại thông điệp không được hỗ trợ."));
        }
    }

    private void sendToGroup(String group, ChatMessage message, String excludedUser) {
        Set<String> members = groups.get(group);
        if (members == null) return;
        for (String username : members) {
            if (excludedUser == null || !excludedUser.equals(username)) {
                ClientSession client = clients.get(username);
                if (client != null) client.send(message);
            }
        }
    }

    private void broadcastSystem(String content) {
        ChatMessage message = ChatMessage.of("SYSTEM", "SERVER", content);
        clients.values().forEach(client -> client.send(message));
    }

    private void sendUserList() {
        String content = String.join(",", clients.keySet().stream().sorted().toList());
        ChatMessage message = ChatMessage.of("USER_LIST", "SERVER", content);
        clients.values().forEach(client -> client.send(message));
    }

    private static String cleanUsername(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.matches("[A-Za-z0-9_-]{2,20}") ? value : null;
    }

    private static String normalizeGroup(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_GROUP;
        String value = raw.trim().toLowerCase();
        return value.matches("[a-z0-9_-]{1,30}") ? value : DEFAULT_GROUP;
    }

    private final class ClientSession {
        private final Socket socket;
        private final BufferedWriter writer;
        private final Object lock = new Object();
        private volatile String username;

        private ClientSession(Socket socket, BufferedWriter writer) {
            this.socket = socket;
            this.writer = writer;
        }

        private void send(ChatMessage message) {
            try {
                synchronized (lock) {
                    writer.write(mapper.writeValueAsString(message));
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException ex) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Socket is already unavailable.
                }
            }
        }
    }
}