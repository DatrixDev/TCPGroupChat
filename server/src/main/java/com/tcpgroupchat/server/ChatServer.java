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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int DEFAULT_PORT = 2006;
    private static final String DEFAULT_GROUP = "study";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("CHAT_PORT", String.valueOf(DEFAULT_PORT)));
        new ChatServer().start(port);
    }

    public void start(int port) throws IOException {
        groups.put(DEFAULT_GROUP, ConcurrentHashMap.newKeySet());
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCPGroupChat server listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                Thread thread = new Thread(() -> handleConnection(socket), "client-" + socket.getPort());
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    private void handleConnection(Socket socket) {
        ClientSession session = null;
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            session = new ClientSession(socket, writer);
            String line;
            while ((line = reader.readLine()) != null) {
                ChatMessage message = mapper.readValue(line, ChatMessage.class);
                if (message == null || message.getType() == null) continue;
                if ("LOGIN".equals(message.getType())) {
                    String username = cleanUsername(message.getSender());
                    if (username == null) {
                        session.send(ChatMessage.of("ERROR", "SERVER", "Tên người dùng không hợp lệ."));
                        continue;
                    }
                    ClientSession existing = clients.putIfAbsent(username, session);
                    if (existing != null && existing != session) {
                        session.send(ChatMessage.of("ERROR", "SERVER", "Tên người dùng đang được sử dụng."));
                        break;
                    }
                    session.username = username;
                    session.send(ChatMessage.of("LOGIN_OK", "SERVER", "Đăng nhập thành công: " + username));
                    broadcastSystem(username + " đã kết nối.");
                    sendUserList();
                    System.out.println(username + " connected from " + socket.getRemoteSocketAddress());
                    continue;
                }
                if (session.username == null) {
                    session.send(ChatMessage.of("ERROR", "SERVER", "Hãy đăng nhập trước."));
                    continue;
                }
                route(session, message);
            }
        } catch (IOException ex) {
            System.out.println("Client connection ended: " + ex.getMessage());
        } finally {
            if (session != null && session.username != null) {
                clients.remove(session.username, session);
                groups.values().forEach(members -> members.remove(session.username));
                broadcastSystem(session.username + " đã ngắt kết nối.");
                sendUserList();
            }
        }
    }

    private void route(ClientSession sender, ChatMessage message) {
        String content = message.getContent() == null ? "" : message.getContent().trim();
        switch (message.getType()) {
            case "JOIN_GROUP" -> {
                String group = normalizeGroup(message.getGroup());
                groups.computeIfAbsent(group, ignored -> ConcurrentHashMap.newKeySet()).add(sender.username);
                sender.send(new ChatMessage("SYSTEM", "SERVER", null, group, "Bạn đã tham gia nhóm #" + group));
                sendToGroup(group, ChatMessage.of("SYSTEM", "SERVER", sender.username + " đã tham gia nhóm #" + group), null);
            }
            case "LEAVE_GROUP" -> {
                String group = normalizeGroup(message.getGroup());
                Set<String> members = groups.get(group);
                if (members != null && members.remove(sender.username)) {
                    sender.send(new ChatMessage("SYSTEM", "SERVER", null, group, "Bạn đã rời nhóm #" + group));
                    sendToGroup(group, ChatMessage.of("SYSTEM", "SERVER", sender.username + " đã rời nhóm #" + group), null);
                } else {
                    sender.send(ChatMessage.of("ERROR", "SERVER", "Bạn chưa tham gia nhóm #" + group));
                }
            }
            case "GROUP_MESSAGE" -> {
                String group = normalizeGroup(message.getGroup());
                Set<String> members = groups.get(group);
                if (members == null || !members.contains(sender.username)) {
                    sender.send(ChatMessage.of("ERROR", "SERVER", "Bạn cần Join nhóm trước khi gửi tin nhắn nhóm."));
                } else if (!content.isBlank()) {
                    sendToGroup(group, new ChatMessage("GROUP_MESSAGE", sender.username, null, group, content), null);
                }
            }
            case "UNICAST" -> {
                String recipient = message.getRecipient();
                ClientSession target = recipient == null ? null : clients.get(recipient);
                if (target == null) sender.send(ChatMessage.of("ERROR", "SERVER", "Người nhận không online."));
                else if (!content.isBlank()) target.send(new ChatMessage("UNICAST", sender.username, recipient, null, content));
            }
            case "BROADCAST" -> {
                if (!content.isBlank()) {
                    ChatMessage outgoing = ChatMessage.of("BROADCAST", sender.username, content);
                    clients.values().forEach(target -> target.send(outgoing));
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
        if (!value.matches("[A-Za-z0-9_-]{2,20}")) return null;
        return value;
    }

    private static String normalizeGroup(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_GROUP;
        String value = raw.trim().toLowerCase();
        return value.matches("[a-z0-9_-]{1,30}") ? value : DEFAULT_GROUP;
    }

    private static final class ClientSession {
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
                    writer.write(new ObjectMapper().writeValueAsString(message));
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException ex) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
