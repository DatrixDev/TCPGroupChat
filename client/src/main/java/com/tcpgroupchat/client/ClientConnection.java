package com.tcpgroupchat.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcpgroupchat.common.protocol.ChatMessage;
import com.tcpgroupchat.common.protocol.JsonLines;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * All socket work runs outside Swing. Login reads exactly one frame, leaving later events queued.
 */
public final class ClientConnection implements AutoCloseable {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Socket socket;
  private final BufferedReader reader;
  private final BufferedWriter writer;
  private final ExecutorService sender =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "client-send");
            t.setDaemon(true);
            return t;
          });
  private final AtomicBoolean closed = new AtomicBoolean();
  private Consumer<String> disconnected;
  public final String endpoint;
  public final String localIp;
  public final String username;
  public final String sessionId;
  public final String loginTime;

  private ClientConnection(
      Socket socket,
      BufferedReader reader,
      BufferedWriter writer,
      String endpoint,
      String username,
      ChatMessage login) {
    this.socket = socket;
    this.reader = reader;
    this.writer = writer;
    this.endpoint = endpoint;
    String address = socket.getLocalAddress().getHostAddress();
    if (socket.getLocalAddress().isLoopbackAddress()) {
      try { address = InetAddress.getLocalHost().getHostAddress(); }
      catch (UnknownHostException ignored) { /* Keep the socket address as fallback. */ }
    }
    localIp = address;
    this.username = username;
    sessionId = login.getSessionId();
    loginTime = login.getTimestamp();
  }

  public static ClientConnection open(
      String host, int port, String user, String password, boolean register) throws IOException {
    Socket socket = new Socket();
    try {
      socket.connect(new InetSocketAddress(host, port), 4000);
      socket.setSoTimeout(10_000);
      socket.setTcpNoDelay(true);
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      BufferedWriter writer =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      ObjectMapper mapper = new ObjectMapper();
      ChatMessage request = ChatMessage.of(register ? "REGISTER" : "LOGIN", user, null);
      request.setPassword(password);
      writer.write(mapper.writeValueAsString(request));
      writer.newLine();
      writer.flush();
      String line = JsonLines.read(reader);
      if (line == null) throw new IOException("Server đóng kết nối trước khi phản hồi.");
      ChatMessage response = mapper.readValue(line, ChatMessage.class);
      String expected = register ? "REGISTER_OK" : "LOGIN_OK";
      if (response == null || !expected.equals(response.getType()))
        throw new IOException(response == null ? "Phản hồi không hợp lệ." : response.getContent());
      socket.setSoTimeout(0);
      return new ClientConnection(
          socket,
          reader,
          writer,
          host + ":" + port,
          user.trim().toLowerCase(java.util.Locale.ROOT),
          response);
    } catch (IOException | RuntimeException e) {
      socket.close();
      throw e;
    }
  }

  public void listen(Consumer<ChatMessage> received, Consumer<String> disconnected) {
    this.disconnected = disconnected;
    Thread receiver =
        new Thread(
            () -> {
              String reason = "Server đã đóng kết nối.";
              try {
                String line;
                while (!closed.get() && (line = JsonLines.read(reader)) != null) {
                  ChatMessage message = mapper.readValue(line, ChatMessage.class);
                  received.accept(message);
                }
              } catch (IOException e) {
                reason = "Mất kết nối: " + e.getMessage();
              } finally {
                boolean notify = !closed.get();
                close();
                if (notify) disconnected.accept(reason);
              }
            },
            "client-receive");
    receiver.setDaemon(true);
    receiver.start();
  }

  public void send(ChatMessage message) {
    if (closed.get()) return;
    try {
      sender.execute(
          () -> {
            try {
              writer.write(mapper.writeValueAsString(message));
              writer.newLine();
              writer.flush();
            } catch (IOException e) {
              boolean notify = !closed.get();
              close();
              if (notify && disconnected != null) disconnected.accept("Lỗi gửi: " + e.getMessage());
            }
          });
    } catch (RejectedExecutionException ignored) {
      /* connection already closed */
    }
  }

  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    sender.shutdownNow();
  }
}
