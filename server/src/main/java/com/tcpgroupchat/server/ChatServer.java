package com.tcpgroupchat.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcpgroupchat.common.protocol.ChatMessage;
import com.tcpgroupchat.common.protocol.JsonLines;
import com.tcpgroupchat.server.service.AccountStore;
import com.tcpgroupchat.server.net.UdpServerTransport;
import com.tcpgroupchat.common.net.LocalAddresses;
import com.tcpgroupchat.common.net.UdpPackets;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plain TCP sockets. One reader and one bounded outgoing queue per connection. */
public final class ChatServer {
  public record LogEntry(String time, String event, String information) {}

  public record SessionInfo(
      String id,
      String username,
      String address,
      String loginTime,
      String logoutTime,
      boolean online,
      String groups) {}

  public record GroupInfo(String name, int count, String members, String ip, int port) {}

  public record Stats(
      int accounts, int online, int members, long unicast, long group, long broadcast) {}

  private final Object stateLock = new Object();
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, ClientSession> clients = new TreeMap<>();
  private final Map<String, Set<String>> groups = new TreeMap<>();
  private final Map<String, String> groupIps = new TreeMap<>();
  private int nextGroupIp = 1;
  private UdpServerTransport udp;
  private String networkIp = LocalAddresses.preferredIp();
  private final int configuredUdpPort = Integer.getInteger("chat.udp.port", 2008);
  private final int groupPort = Integer.getInteger("chat.group.port", 2007);
  private final Map<String, String> groupCreators = new TreeMap<>();
  private final Set<ClientSession> connections = new HashSet<>();
  private final Deque<LogEntry> logs = new ArrayDeque<>();
  private final Deque<ClientSession> history = new ArrayDeque<>();
  private final AccountStore accounts;
  private final Path dataRoot;
  private volatile ServerSocket serverSocket;
  private volatile boolean running;
  private long unicastCount, groupCount, broadcastCount;

  public ChatServer() {
    this(Path.of(System.getProperty("chat.data", "server_data")));
  }

  public ChatServer(Path dataRoot) {
    this.dataRoot = dataRoot.toAbsolutePath();
    accounts = new AccountStore(this.dataRoot.resolve("accounts"));

  }

  public static void main(String[] args) {
    com.formdev.flatlaf.FlatLightLaf.setup();
    com.tcpgroupchat.common.ui.UiStyles.install();
    javax.swing.SwingUtilities.invokeLater(
        () -> {
          try {
            new ServerFrame(new ChatServer()).setVisible(true);
          } catch (RuntimeException e) {
            javax.swing.JOptionPane.showMessageDialog(
                null, e.getMessage(), "Không thể mở Server", javax.swing.JOptionPane.ERROR_MESSAGE);
          }
        });
  }

  /** Binds before entering the accept loop. Run this method outside the Swing EDT. */
  public void start(int port) throws IOException {
    if (port < 0 || port > 65535)
      throw new IllegalArgumentException("Cổng cần nằm trong khoảng 1–65535.");
    final ServerSocket listener;
    synchronized (stateLock) {
      if (serverSocket != null) throw new IllegalStateException("Server đang chạy hoặc đang dừng.");
      listener = new ServerSocket();
      try {
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(port));
        udp = new UdpServerTransport(networkIp, configuredUdpPort, groupPort, this::handleUdp);
      } catch (IOException | RuntimeException e) {
        listener.close();
        if (udp != null) { udp.close(); udp = null; }
        throw e;
      }
      serverSocket = listener;
      running = true;
      log("SERVER_START", "Lắng nghe TCP tại 0.0.0.0:" + listener.getLocalPort());
    }
    try {
      while (!listener.isClosed()) {
        Socket socket = listener.accept();
        synchronized (stateLock) {
          if (!running || serverSocket != listener) {
            socket.close();
            continue;
          }
          if (connections.size() >= 100) {
            socket.close();
            log("REJECT", "Đạt giới hạn 100 kết nối.");
            continue;
          }
          ClientSession session = new ClientSession(socket);
          connections.add(session);
          log("CONNECT", session.address());
          session.start();
        }
      }
    } catch (IOException e) {
      if (!listener.isClosed()) throw e;
    } finally {
      synchronized (stateLock) {
        if (serverSocket == listener) shutdown();
      }
    }
  }

  public void stop() {
    synchronized (stateLock) {
      shutdown();
    }
  }

  private void shutdown() {
    if (serverSocket == null) return;
    running = false;
    try {
      serverSocket.close();
    } catch (IOException ignored) {
    }
    for (ClientSession session : List.copyOf(connections)) disconnect(session, "Server dừng");
    if (udp != null) { udp.close(); udp = null; }
    groupIps.clear();
    nextGroupIp = 1;
    groups.clear();
    groupCreators.clear();
    serverSocket = null;
    log("SERVER_STOP", "Đã đóng toàn bộ kết nối và xóa thành viên nhóm.");
  }

  public boolean isRunning() {
    return running;
  }

  public int getPort() {
    ServerSocket socket = serverSocket;
    return socket == null ? 2006 : socket.getLocalPort();
  }

  public List<String> getOnlineUsers() {
    synchronized (stateLock) {
      return List.copyOf(clients.keySet());
    }
  }

  public List<LogEntry> getLogs() {
    synchronized (stateLock) {
      return List.copyOf(logs);
    }
  }

  public List<String> getRecentLogs() {
    return getLogs().stream()
        .map(l -> l.time() + " [" + l.event() + "] " + l.information())
        .toList();
  }

  public List<SessionInfo> getSessions() {
    synchronized (stateLock) {
      return history.stream()
          .map(
              s ->
                  new SessionInfo(
                      s.id,
                      s.username,
                      s.address(),
                      s.loginTime,
                      s.logoutTime,
                      clients.get(s.username) == s,
                      String.join(", ", joined(s.username))))
          .toList();
    }
  }

  public List<GroupInfo> getGroups() {
    synchronized (stateLock) {
      return groups.entrySet().stream()
          .map(e -> new GroupInfo(e.getKey(), e.getValue().size(), String.join(", ", e.getValue()), groupIps.get(e.getKey()), groupPort))
          .toList();
    }
  }

  public void disconnectSession(String id) {
    synchronized (stateLock) {
      connections.stream()
          .filter(s -> s.id.equals(id))
          .findFirst()
          .ifPresent(s -> disconnect(s, "Quản trị viên ngắt phiên"));
    }
  }

  public Stats getStats() {
    synchronized (stateLock) {
      return new Stats(
          accounts.users().size(),
          clients.size(),
          groups.values().stream().mapToInt(Set::size).sum(),
          unicastCount,
          groupCount,
          broadcastCount);
    }
  }

  private void log(String event, String information) {
    synchronized (stateLock) {
      LogEntry entry =
          new LogEntry(
              LocalDateTime.now().toString(),
              event,
              information.replace('\n', ' ').replace('\r', ' '));
      logs.addLast(entry);
      if (logs.size() > 500) logs.removeFirst();
      try {
        Files.createDirectories(dataRoot.resolve("logs"));
        Files.writeString(
            dataRoot.resolve("logs").resolve(java.time.LocalDate.now() + ".log"),
            entry.time() + "\t" + event + "\t" + entry.information() + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      } catch (IOException e) {
        System.err.println("Không thể ghi log: " + e.getMessage());
      }
    }
  }

  public void configureUdp(String ip) {
    synchronized (stateLock) {
      if (running) throw new IllegalStateException("Dừng server trước khi đổi card mạng.");
      networkIp = ip;
    }
  }

  public String getBroadcastIp() {
    try { return UdpPackets.broadcastIp(networkIp); }
    catch (IOException e) { return "255.255.255.255"; }
  }
  public int getUdpPort() { UdpServerTransport transport = udp; return transport == null ? configuredUdpPort : transport.port(); }
  public int getGroupPort() { return groupPort; }

  private void handleUdp(DatagramPacket packet, ChatMessage request) {
    synchronized (stateLock) {
      ClientSession session = connections.stream()
          .filter(s -> s.username != null && s.id.equals(request.getSessionId()) && s.udpPort == packet.getPort())
          .findFirst().orElse(null);
      if (session == null) return;
      InetAddress peer = session.socket.getInetAddress();
      boolean sameComputer = peer.isLoopbackAddress() && packet.getAddress().getHostAddress().equals(networkIp);
      if (!peer.equals(packet.getAddress()) && !sameComputer) return;
      session.udpAddress = packet.getAddress();
      if ("UDP_HELLO".equals(request.getType())) {
        try { udp.unicast(ChatMessage.of("UDP_READY", "SERVER", "UDP sẵn sàng"), packet.getAddress(), packet.getPort()); }
        catch (IOException e) { log("ERROR", "UDP handshake: " + e.getMessage()); }
        return;
      }
      handle(session, request);
    }
  }

  private void handle(ClientSession session, ChatMessage message) {
    synchronized (stateLock) {
      if (!running || session.closed.get()) return;
      try {
        if (message == null || message.getType() == null)
          throw new IllegalArgumentException("Thông điệp thiếu loại.");
        if ("REGISTER".equals(message.getType())) {
          if (session.username != null)
            throw new IllegalArgumentException("Hãy đăng xuất trước khi đăng ký.");
          accounts.register(
              message.getSender(),
              message.getPassword(),
              session.socket.getInetAddress().getHostAddress());
          reply(session, message, "REGISTER_OK", "Đăng ký thành công. Bạn có thể đăng nhập.");
          log("REGISTER", AccountStore.username(message.getSender()) + " từ " + session.address());
          return;
        }
        if ("LOGIN".equals(message.getType())) {
          if (session.username != null)
            throw new IllegalArgumentException("Kết nối này đã đăng nhập.");
          String user = AccountStore.username(message.getSender());
          if (!accounts.authenticate(user, message.getPassword()))
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng.");
          if (clients.containsKey(user))
            throw new IllegalArgumentException("Tài khoản đang đăng nhập ở client khác.");
          if (message.getUdpPort() < 0 || message.getUdpPort() > 65535)
            throw new IllegalArgumentException("Cổng UDP client không hợp lệ.");
          if (message.getUdpPort() > 0) {
            if (udp == null) udp = new UdpServerTransport(networkIp, configuredUdpPort, groupPort, this::handleUdp);
            session.udpPort = message.getUdpPort();
            session.udpAddress = session.socket.getInetAddress();
          }
          session.username = user;
          session.loginTime = LocalDateTime.now().toString();
          session.socket.setSoTimeout(0);
          clients.put(user, session);
          history.addFirst(session);
          // Keep all online sessions; trim the oldest offline record when necessary.
          while (history.size() > 1000) {
            ClientSession old =
                history.stream()
                    .filter(s -> clients.get(s.username) != s)
                    .reduce((a, b) -> b)
                    .orElse(null);
            if (old == null) break;
            history.remove(old);
          }
          reply(session, message, "LOGIN_OK", "Đăng nhập thành công: " + user);
          log("LOGIN", user + " | phiên " + session.id + " | " + session.address());
          broadcastSystem(user + " đã đăng nhập.");
          publishUsers();
          for (String group : joined(user)) sendGroupState(session, group, null);
          return;
        }
        if (session.username == null) throw new IllegalArgumentException("Hãy đăng nhập trước.");
        route(session, message);
      } catch (IllegalArgumentException e) {
        reply(session, message, "ERROR", e.getMessage());
        log(
            "REJECT",
            (session.username == null ? session.address() : session.username)
                + " | "
                + e.getMessage());
      } catch (IOException e) {
        reply(session, message, "ERROR", "Server không thể đọc/ghi dữ liệu hoặc gửi UDP.");
        log("ERROR", e.getMessage());
      }
    }
  }

  private void route(ClientSession sender, ChatMessage request) throws IOException {
    switch (request.getType()) {
      case "CREATE_GROUP", "ADD_GROUP_MEMBERS" -> {
        String group = groupName(request.getGroup());
        boolean create = "CREATE_GROUP".equals(request.getType());
        Set<String> current = groups.get(group);
        if (create && current != null) throw new IllegalArgumentException("Tên nhóm đã tồn tại. Hãy chọn tên khác.");
        if (!create && (current == null || !current.contains(sender.username) || !sender.username.equals(groupCreators.get(group))))
          throw new IllegalArgumentException("Chỉ người tạo nhóm đang ở trong nhóm mới được thêm người.");
        if (create && groups.size() >= 100) throw new IllegalArgumentException("Đạt giới hạn 100 phòng.");
        if (request.getUsers() == null || request.getUsers().isEmpty() || request.getUsers().size() > 100)
          throw new IllegalArgumentException("Hãy chọn từ 1 đến 100 người đang online.");
        Set<String> selected = new TreeSet<>();
        for (String name : request.getUsers()) {
          String user = AccountStore.username(name);
          if (!clients.containsKey(user)) throw new IllegalArgumentException(user + " không còn online. Hãy chọn lại.");
          selected.add(user);
        }
        // Validate every recipient before changing membership, under the routing lock.
        if (create) { groupIps.put(group, "239.255.1." + nextGroupIp++); current = new TreeSet<>(); groups.put(group, current); groupCreators.put(group, sender.username); selected.add(sender.username); }
        for (String user : selected) {
          if (current.add(user)) {
            ChatMessage notice = new ChatMessage("GROUP_ADDED", "SERVER", user, group,
                sender.username + " đã thêm bạn vào #" + group);
            clients.get(user).send(notice);
            log("JOIN", user + " → #" + group + " | được thêm bởi " + sender.username);
          }
        }
        reply(sender, request, create ? "CREATE_GROUP_OK" : "ADD_GROUP_MEMBERS_OK", "Đã cập nhật thành viên #" + group);
        groupSystem(group, sender.username + (create ? " đã tạo nhóm." : " đã thêm thành viên vào nhóm."));
        publishGroup(group);
      }
      case "JOIN_GROUP", "LEAVE_GROUP", "GET_GROUP" -> {
        String group = groupName(request.getGroup());
        Set<String> members = groups.get(group);
        if ("GET_GROUP".equals(request.getType())) {
          if (members == null || !members.contains(sender.username)) throw new IllegalArgumentException("Bạn không phải thành viên nhóm.");
          sendGroupState(sender, group, request);
          return;
        }
        if ("JOIN_GROUP".equals(request.getType())) {
          if (members == null || !members.contains(sender.username))
            throw new IllegalArgumentException("Nhóm riêng tư: cần được người tạo nhóm thêm vào.");
          reply(sender, request, "JOIN_OK", "Bạn đã ở trong #" + group);
        } else {
          if (members == null || !members.remove(sender.username)) throw new IllegalArgumentException("Bạn chưa tham gia nhóm.");
          reply(sender, request, "LEAVE_OK", "Đã Leave #" + group);
          sender.send(new ChatMessage("GROUP_REMOVED", "SERVER", sender.username, group, "Bạn đã rời nhóm."));
          groupSystem(group, sender.username + " đã Leave nhóm.");
          log("LEAVE", sender.username + " ← #" + group);
        }
        publishGroup(group);
      }
      case "UNICAST", "GROUP_MESSAGE", "BROADCAST" -> {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isBlank() || content.length() > 4000)
          throw new IllegalArgumentException("Tin nhắn cần từ 1 đến 4000 ký tự.");
        ChatMessage outgoing = ChatMessage.of(request.getType(), sender.username, content);
        outgoing.setTimestamp(LocalDateTime.now().toString());
        outgoing.setRequestId(request.getRequestId());
        List<ClientSession> targets;
        String destination;
        switch (request.getType()) {
          case "UNICAST" -> {
            String recipient = AccountStore.username(request.getRecipient());
            ClientSession target = clients.get(recipient);
            if (target == null) throw new IllegalArgumentException("Người nhận không online.");
            outgoing.setRecipient(recipient);
            targets = List.of(target);
            destination = recipient + (target.udpPort > 0 ? " [UDP " + target.udpAddress.getHostAddress() + ":" + target.udpPort + "]" : " [TCP]");
            unicastCount++;
          }
          case "GROUP_MESSAGE" -> {
            String group = groupName(request.getGroup());
            if (!groups.getOrDefault(group, Set.of()).contains(sender.username))
              throw new IllegalArgumentException("Bạn không còn là thành viên nhóm này.");
            Set<String> members = groups.getOrDefault(group, Set.of());
            targets = members.stream().map(clients::get).filter(Objects::nonNull).toList();
            if (targets.isEmpty())
              throw new IllegalArgumentException("Nhóm chưa có thành viên online.");
            outgoing.setGroup(group);
            destination = "#" + group + " [Multicast " + groupIps.get(group) + ":" + groupPort + "]";
            groupCount++;
          }
          default -> {
            targets = List.copyOf(clients.values());
            destination = "ALL [Broadcast " + getBroadcastIp() + ":" + groupPort + "]";
            broadcastCount++;
          }
        }
        // Application clients use UDP. Legacy TCP peers remain supported for protocol tests.
        List<ClientSession> udpTargets = targets.stream().filter(s -> s.udpPort > 0).toList();
        if (!udpTargets.isEmpty()) {
          switch (request.getType()) {
            case "UNICAST" -> udp.unicast(outgoing, udpTargets.get(0).udpAddress, udpTargets.get(0).udpPort);
            case "GROUP_MESSAGE" -> udp.multicast(outgoing, groupIps.get(outgoing.getGroup()));
            case "BROADCAST" -> udp.broadcast(outgoing);
          }
        }
        targets.stream().filter(s -> s.udpPort == 0).forEach(s -> s.send(outgoing));
        reply(
            sender,
            request,
            "ACK",
            "Server đã gửi tới "
                + targets.size()
                + " client: "
                + String.join(", ", targets.stream().map(s -> s.username).toList()));
        log(
            request.getType(),
            sender.username
                + " → "
                + destination
                + " | nhận: "
                + String.join(", ", targets.stream().map(s -> s.username).toList())
                + " | "
                + content);
      }
      case "GET_USERS" -> {
        ChatMessage response = ChatMessage.of("USER_LIST", "SERVER", null);
        response.setUsers(List.copyOf(clients.keySet()));
        response.setRequestId(request.getRequestId());
        sender.send(response);
      }
      case "LOGOUT" -> disconnect(sender, "Đăng xuất");
      case "PING" -> reply(sender, request, "PONG", "Server đang hoạt động.");
      default -> throw new IllegalArgumentException("Loại thông điệp không được hỗ trợ.");
    }
  }

  private void reply(ClientSession target, ChatMessage request, String type, String content) {
    ChatMessage response = ChatMessage.of(type, "SERVER", content);
    response.setRequestId(request == null ? null : request.getRequestId());
    response.setGroup(request == null ? null : request.getGroup());
    response.setSessionId(target.id);
    if ("LOGIN_OK".equals(type)) {
      response.setNetworkIp(networkIp);
      response.setUdpPort(getUdpPort());
      response.setGroupPort(groupPort);
      response.setBroadcastIp(getBroadcastIp());
    }
    response.setTimestamp(LocalDateTime.now().toString());
    target.send(response);
  }

  private List<String> joined(String username) {
    return groups.entrySet().stream()
        .filter(e -> e.getValue().contains(username))
        .map(Map.Entry::getKey)
        .toList();
  }

  private void sendGroupState(ClientSession target, String group, ChatMessage request) {
    ChatMessage state = new ChatMessage("GROUP_STATE", "SERVER", null, group, null);
    state.setMembers(List.copyOf(groups.getOrDefault(group, Set.of())));
    state.setJoinedGroups(joined(target.username));
    state.setGroupCreator(groupCreators.get(group));
    state.setGroupIp(groupIps.get(group));
    state.setGroupPort(groupPort);
    state.setRequestId(request == null ? null : request.getRequestId());
    target.send(state);
  }

  private void publishGroup(String group) {
    for (String user : List.copyOf(groups.getOrDefault(group, Set.of()))) {
      ClientSession session = clients.get(user);
      if (session != null) sendGroupState(session, group, null);
    }
  }

  private void groupSystem(String group, String content) {
    ChatMessage message = new ChatMessage("SYSTEM", "SERVER", null, group, content);
    groups.getOrDefault(group, Set.of()).stream()
        .map(clients::get)
        .filter(Objects::nonNull)
        .forEach(s -> s.send(message));
  }

  private void broadcastSystem(String content) {
    clients.values().forEach(s -> s.send(ChatMessage.of("SYSTEM", "SERVER", content)));
  }

  private void publishUsers() {
    ChatMessage message = ChatMessage.of("USER_LIST", "SERVER", null);
    message.setUsers(List.copyOf(clients.keySet()));
    clients.values().forEach(s -> s.send(message));
  }

  private void disconnect(ClientSession session, String reason) {
    synchronized (stateLock) {
      if (!session.closed.compareAndSet(false, true)) return;
      try {
        session.socket.close();
      } catch (IOException ignored) {
      }
      if (session.writerThread != null) session.writerThread.interrupt();
      session.outgoing.clear();
      connections.remove(session);
      if (session.username != null && clients.remove(session.username, session)) {
        session.logoutTime = LocalDateTime.now().toString();
        List<String> changed = joined(session.username);
        groups.values().forEach(s -> s.remove(session.username));
        log("LOGOUT", session.username + " | phiên " + session.id + " | " + reason);
        if (running) {
          broadcastSystem(session.username + " đã ngắt kết nối.");
          publishUsers();
          changed.forEach(this::publishGroup);
        }
      }
    }
  }

  private static String groupName(String raw) {
    String group = raw == null || raw.isBlank() ? "study" : raw.trim().toLowerCase(Locale.ROOT);
    if (!group.matches("[a-z0-9_-]{1,30}"))
      throw new IllegalArgumentException("Tên nhóm cần 1–30 ký tự: chữ không dấu, số, _ hoặc -.");
    return group;
  }

  private final class ClientSession {
    final Socket socket;
    final String id = UUID.randomUUID().toString();
    final ArrayBlockingQueue<String> outgoing = new ArrayBlockingQueue<>(256);
    final AtomicBoolean closed = new AtomicBoolean();
    int udpPort;
    InetAddress udpAddress;
    Thread writerThread;
    String username, loginTime = "", logoutTime = "";

    ClientSession(Socket socket) throws IOException {
      this.socket = socket;
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(60_000);
    }

    String address() {
      return socket.getRemoteSocketAddress().toString();
    }

    void start() {
      writerThread =
          new Thread(
              () -> {
                try (BufferedWriter writer =
                    new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                  while (!closed.get()) {
                    writer.write(outgoing.take());
                    writer.newLine();
                    writer.flush();
                  }
                } catch (IOException | InterruptedException e) {
                  /* cleanup below also wakes the reader */
                } finally {
                  disconnect(this, "Kết nối gửi đã đóng");
                }
              },
              "writer-" + id);
      writerThread.setDaemon(true);
      writerThread.start();
      Thread readerThread =
          new Thread(
              () -> {
                try (BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while (!closed.get() && (line = JsonLines.read(reader)) != null) {
                    ChatMessage message;
                    try {
                      message = mapper.readValue(line, ChatMessage.class);
                    } catch (IOException e) {
                      reply(this, null, "ERROR", "Thông điệp JSON không hợp lệ.");
                      continue;
                    }
                    handle(this, message);
                  }
                } catch (IOException e) {
                  if (!closed.get()) log("DISCONNECT", address() + " | " + e.getMessage());
                } finally {
                  disconnect(this, "Client đóng kết nối");
                }
              },
              "reader-" + id);
      readerThread.setDaemon(true);
      readerThread.start();
    }

    void send(ChatMessage message) {
      if (closed.get()) return;
      try {
        if (!outgoing.offer(mapper.writeValueAsString(message))) {
          // Schedule cleanup rather than mutating recipient collections mid-broadcast.
          socket.close();
          writerThread.interrupt();
        }
      } catch (IOException e) {
        try {
          socket.close();
        } catch (IOException ignored) {
        }
        writerThread.interrupt();
      }
    }
  }
}
