package com.tcpgroupchat.server;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcpgroupchat.common.protocol.ChatMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Real TCP clients, not mocks: verify routing and lifecycle independently of Swing. */
class ChatServerTest {
  @TempDir Path data;
  ChatServer server;
  Thread accept;
  final List<Peer> peers = new ArrayList<>();
<<<<<<< HEAD
=======
  final Map<String, Peer> fixtureCreators = new HashMap<>();
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
  final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void start() throws Exception {
    server = new ChatServer(data);
    launch();
  }

  void launch() throws Exception {
    accept =
        new Thread(
            () -> {
              try {
                server.start(0);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            },
            "test-server");
    accept.start();
    await(() -> server.isRunning());
  }

  @AfterEach
  void stop() throws Exception {
    for (Peer p : peers) p.close();
    server.stop();
    accept.join(3000);
    assertFalse(accept.isAlive());
  }

  void await(java.util.function.BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
    assertTrue(condition.getAsBoolean(), "Condition did not become true");
  }

  Peer client(String name) throws Exception {
    Peer peer = new Peer(name);
    peers.add(peer);
    peer.auth("REGISTER", "test-password");
    assertEquals("REGISTER_OK", peer.next("REGISTER_OK").getType());
    peer.auth("LOGIN", "test-password");
    peer.next("LOGIN_OK");
    return peer;
  }

  Peer[] five() throws Exception {
    return new Peer[] {
      client("client1"), client("client2"), client("client3"), client("client4"), client("client5")
    };
  }

  @Test
  void fiveClientsGroupAndLeaveMatchAssignment() throws Exception {
    Peer[] p = five();
    for (int i = 0; i < 3; i++) p[i].group("JOIN_GROUP", "study", "JOIN_OK");
    p[0].message("GROUP_MESSAGE", null, "study", "Nhóm tiếng Việt");
    p[0].next("ACK");
    for (int i = 0; i < 3; i++)
      assertEquals("Nhóm tiếng Việt", p[i].next("GROUP_MESSAGE").getContent());
    for (int i = 3; i < 5; i++) p[i].assertNoChat();
    p[0].group("LEAVE_GROUP", "study", "LEAVE_OK");
    p[0].message("GROUP_MESSAGE", null, "study", "Gửi sau Leave");
    assertTrue(p[0].next("ACK").getContent().contains("2 client"));
    for (int i = 1; i < 3; i++)
      assertEquals("Gửi sau Leave", p[i].next("GROUP_MESSAGE").getContent());
    p[0].assertNoChat();
    p[3].assertNoChat();
    p[4].assertNoChat();
    assertEquals(2, server.getStats().group());
    assertEquals(2, server.getStats().members());
  }

  @Test
  void unicastOnlyRecipientAndBroadcastAllFive() throws Exception {
    Peer[] p = five();
    p[0].message("UNICAST", "client4", null, "private");
    p[0].next("ACK");
    ChatMessage delivered = p[3].next("UNICAST");
    assertEquals("client1", delivered.getSender());
    assertEquals("client4", delivered.getRecipient());
    for (int i : new int[] {0, 1, 2, 4}) p[i].assertNoChat();
    p[4].message("BROADCAST", null, null, "all");
    p[4].next("ACK");
    for (Peer peer : p) assertEquals("all", peer.next("BROADCAST").getContent());
    assertEquals(1, server.getStats().unicast());
    assertEquals(1, server.getStats().broadcast());
  }

  @Test
  void independentRoomsAndIdempotentJoin() throws Exception {
    Peer a = client("alice"), b = client("bob"), c = client("carol");
    a.group("JOIN_GROUP", "study", "JOIN_OK");
    b.group("JOIN_GROUP", "study", "JOIN_OK");
    a.group("JOIN_GROUP", "study", "JOIN_OK");
    c.group("JOIN_GROUP", "other", "JOIN_OK");
    assertEquals(3, server.getStats().members());
    b.message("GROUP_MESSAGE", null, "study", "room-one");
    b.next("ACK");
    a.next("GROUP_MESSAGE");
    b.next("GROUP_MESSAGE");
    c.assertNoChat();
    c.message("GROUP_MESSAGE", null, "other", "room-two");
    c.next("ACK");
    c.next("GROUP_MESSAGE");
    a.assertNoChat();
    b.assertNoChat();
  }

  @Test
  void authenticationDuplicateAndForgedSender() throws Exception {
    Peer a = client("alice"), b = client("bob"), duplicate = new Peer("ALICE");
    peers.add(duplicate);
    duplicate.auth("LOGIN", "wrong");
    assertEquals("ERROR", duplicate.next("ERROR").getType());
    duplicate.auth("LOGIN", "test-password");
    assertTrue(duplicate.next("ERROR").getContent().contains("client khác"));
    ChatMessage forged = new ChatMessage("UNICAST", "mallory", "bob", null, "identity");
    a.send(forged);
    assertEquals("alice", b.next("UNICAST").getSender());
    a.next("ACK");
    assertEquals(2, server.getOnlineUsers().size());
    Path account = data.resolve("accounts/alice.properties");
    assertFalse(Files.readString(account).contains("test-password"));
    String log = Files.readString(data.resolve("logs").resolve(java.time.LocalDate.now() + ".log"));
    assertFalse(log.contains("test-password"));
  }

  @Test
  void unauthenticatedMalformedAndInvalidMessages() throws Exception {
    Peer peer = new Peer("nobody");
    peers.add(peer);
    peer.message("BROADCAST", null, null, "unauthenticated");
    assertTrue(peer.next("ERROR").getContent().contains("đăng nhập"));
    peer.raw("{bad json}");
    peer.next("ERROR");
    peer.raw("null");
    peer.next("ERROR");
    Peer a = client("alice");
    a.group("JOIN_GROUP", "../../invalid", "ERROR");
    a.message("UNICAST", "offline", null, "x");
    a.next("ERROR");
    a.message("GROUP_MESSAGE", null, "empty", "x");
    a.next("ERROR");
    a.message("BROADCAST", null, null, " ");
    a.next("ERROR");
    a.message("BROADCAST", null, null, "x".repeat(4001));
    a.next("ERROR");
    assertEquals(0, server.getStats().broadcast());
  }

  @Test
  void disconnectCleansMembershipAndAllowsRelogin() throws Exception {
    Peer a = client("alice"), b = client("bob");
    a.group("JOIN_GROUP", "study", "JOIN_OK");
    b.group("JOIN_GROUP", "study", "JOIN_OK");
    String oldId =
        server.getSessions().stream()
            .filter(s -> s.username().equals("alice"))
            .findFirst()
            .orElseThrow()
            .id();
    a.close();
    await(() -> server.getOnlineUsers().size() == 1);
    assertEquals(1, server.getStats().members());
    Peer again = new Peer("alice");
    peers.add(again);
    again.auth("LOGIN", "test-password");
    assertNotEquals(oldId, again.next("LOGIN_OK").getSessionId());
    assertTrue(
        server.getSessions().stream()
            .anyMatch(s -> s.id().equals(oldId) && !s.online() && !s.logoutTime().isBlank()));
    assertEquals(1, server.getStats().members());
  }

  @Test
  void stopClosesAllSocketsAndRestartKeepsAccounts() throws Exception {
    Peer a = client("alice"), b = client("bob");
    a.group("JOIN_GROUP", "study", "JOIN_OK");
    server.stop();
    accept.join(3000);
    assertFalse(server.isRunning());
    assertTrue(server.getOnlineUsers().isEmpty());
    assertEquals(0, server.getStats().members());
    a.assertEof();
    b.assertEof();
    server = new ChatServer(data); // simulate opening a new server process with persisted accounts
    launch();
    Peer again = new Peer("alice");
    peers.add(again);
    again.auth("LOGIN", "test-password");
    again.next("LOGIN_OK");
    assertEquals(List.of("alice"), server.getOnlineUsers());
    assertEquals(2, server.getStats().accounts());
  }

  @Test
  void fragmentedAndCoalescedFramesAndOversizeDisconnect() throws Exception {
    Peer a = client("alice"), b = client("bob");
    String one =
        mapper.writeValueAsString(new ChatMessage("UNICAST", "fake", "bob", null, "fragmented"));
    a.writer.write(one.substring(0, 10));
    a.writer.flush();
    a.writer.write(one.substring(10) + "\n");
    a.writer.flush();
    assertEquals("fragmented", b.next("UNICAST").getContent());
    a.next("ACK");
    a.raw(
        mapper.writeValueAsString(ChatMessage.of("BROADCAST", "fake", "first"))
            + "\n"
            + mapper.writeValueAsString(ChatMessage.of("BROADCAST", "fake", "second")));
    assertEquals("first", b.next("BROADCAST").getContent());
    assertEquals("second", b.next("BROADCAST").getContent());
    a.next("ACK");
    a.next("ACK");
    a.raw("x".repeat(33000));
    a.assertEof();
    await(() -> server.getOnlineUsers().size() == 1);
  }

  @Test
  void kickSessionDoesNotAffectOtherClients() throws Exception {
    Peer a = client("alice"), b = client("bob");
    a.group("JOIN_GROUP", "study", "JOIN_OK");
    server.disconnectSession(
        server.getSessions().stream()
            .filter(s -> s.username().equals("alice"))
            .findFirst()
            .orElseThrow()
            .id());
    a.assertEof();
    assertEquals(List.of("bob"), server.getOnlineUsers());
    b.message("BROADCAST", null, null, "still-online");
    b.next("ACK");
    b.next("BROADCAST");
  }

  @Test
  void concurrentBroadcastWritersDoNotInterleaveJson() throws Exception {
    Peer[] p = five();
    ExecutorService pool = Executors.newFixedThreadPool(5);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Peer peer : p)
        futures.add(
            pool.submit(
                () -> {
                  try {
                    for (int i = 0; i < 20; i++)
                      peer.message("BROADCAST", null, null, peer.name + "-" + i);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
      for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
      for (Peer peer : p) {
        Set<String> contents = new HashSet<>();
        for (int i = 0; i < 100; i++) contents.add(peer.next("BROADCAST").getContent());
        assertEquals(100, contents.size());
      }
      assertEquals(100, server.getStats().broadcast());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void createGroupWithSelectedPeopleAndAddMembers() throws Exception {
    Peer[] p = five();
    ChatMessage create = new ChatMessage("CREATE_GROUP", "forged", null, "project", null);
    create.setUsers(List.of("client2", "client3", "client2"));
    p[0].send(create);
    p[0].next("CREATE_GROUP_OK");
    p[1].next("GROUP_ADDED");
    p[2].next("GROUP_ADDED");
    assertEquals(3, server.getGroups().stream().filter(g -> g.name().equals("project")).findFirst().orElseThrow().count());
    p[0].message("GROUP_MESSAGE", null, "project", "selected-members");
    p[0].next("ACK");
    for (int i = 0; i < 3; i++) assertEquals("selected-members", p[i].next("GROUP_MESSAGE").getContent());
    p[3].assertNoChat(); p[4].assertNoChat();
    ChatMessage add = new ChatMessage("ADD_GROUP_MEMBERS", "client4", null, "project", null);
    add.setUsers(List.of("client5"));
    p[3].send(add); p[3].next("ERROR");
    p[0].send(add); p[0].next("ADD_GROUP_MEMBERS_OK"); p[4].next("GROUP_ADDED");
    p[1].group("LEAVE_GROUP", "project", "LEAVE_OK");
    assertEquals(3, server.getGroups().stream().filter(g -> g.name().equals("project")).findFirst().orElseThrow().count());
    p[0].send(create); p[0].next("ERROR");
    create.setGroup("invalid-selection"); create.setUsers(List.of("client2", "offline"));
    p[0].send(create); p[0].next("ERROR");
    assertTrue(server.getGroups().stream().noneMatch(g -> g.name().equals("invalid-selection")));
  }

<<<<<<< HEAD
=======
  @Test
  void privateGroupsHideMembershipAndRejectUninvitedJoin() throws Exception {
    Peer owner = client("owner"), member = client("member"), stranger = client("stranger");
    ChatMessage create = new ChatMessage("CREATE_GROUP", "owner", null, "private", null);
    create.setUsers(List.of("member"));owner.send(create);owner.next("CREATE_GROUP_OK");member.next("GROUP_ADDED");
    stranger.message("PING", null, null, null);stranger.next("PONG");
    assertTrue(stranger.inbox.stream().noneMatch(m -> "GROUP_STATE".equals(m.getType()) || "GROUP_ADDED".equals(m.getType())));
    stranger.group("GET_GROUP", "private", "ERROR");
    stranger.group("JOIN_GROUP", "private", "ERROR");
    stranger.message("GROUP_MESSAGE", null, "private", "unauthorized");stranger.next("ERROR");
    ChatMessage add = new ChatMessage("ADD_GROUP_MEMBERS", "member", null, "private", null);
    add.setUsers(List.of("stranger"));member.send(add);member.next("ERROR");
    member.group("LEAVE_GROUP", "private", "LEAVE_OK");member.next("GROUP_REMOVED");
    member.group("GET_GROUP", "private", "ERROR");member.group("JOIN_GROUP", "private", "ERROR");
    owner.send(add);owner.next("ADD_GROUP_MEMBERS_OK");stranger.next("GROUP_ADDED");
    assertEquals(2, server.getGroups().get(0).count());
  }

>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
  class Peer implements AutoCloseable {
    final String name;
    final Socket socket;
    final BufferedReader reader;
    final BufferedWriter writer;
    final Deque<ChatMessage> inbox = new ArrayDeque<>();

    Peer(String name) throws Exception {
      this.name = name;
      socket = new Socket("127.0.0.1", server.getPort());
      socket.setSoTimeout(3000);
      reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      writer =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    void auth(String type, String password) throws Exception {
      ChatMessage m = ChatMessage.of(type, name, null);
      m.setPassword(password);
      send(m);
    }

    void group(String type, String group, String expected) throws Exception {
<<<<<<< HEAD
=======
      // Existing routing tests provision membership through the private-group creator first.
      if ("JOIN_GROUP".equals(type) && "JOIN_OK".equals(expected)) {
        Peer creator = fixtureCreators.get(group);
        if (creator == null) {
          ChatMessage create = new ChatMessage("CREATE_GROUP", name, null, group, null);
          create.setUsers(List.of(name));
          send(create); next("CREATE_GROUP_OK"); fixtureCreators.put(group, this);
        } else if (!server.getGroups().stream().anyMatch(g -> g.name().equals(group) && Arrays.asList(g.members().split(", ")).contains(name))) {
          ChatMessage add = new ChatMessage("ADD_GROUP_MEMBERS", creator.name, null, group, null);
          add.setUsers(List.of(name));creator.send(add);creator.next("ADD_GROUP_MEMBERS_OK");
        }
      }
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
      send(new ChatMessage(type, name, null, group, null));
      next(expected);
    }

    void message(String type, String recipient, String group, String content) throws Exception {
      send(new ChatMessage(type, name, recipient, group, content));
    }

    void send(ChatMessage message) throws Exception {
      message.setRequestId(UUID.randomUUID().toString());
      raw(mapper.writeValueAsString(message));
    }

    void raw(String line) throws Exception {
      writer.write(line);
      writer.newLine();
      writer.flush();
    }

    ChatMessage next(String type) throws Exception {
      for (var iterator = inbox.iterator(); iterator.hasNext(); ) {
        ChatMessage m = iterator.next();
        if (type.equals(m.getType())) {
          iterator.remove();
          return m;
        }
      }
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (System.nanoTime() < deadline) {
        String line = reader.readLine();
        assertNotNull(line, "Unexpected EOF awaiting " + type);
        ChatMessage m = mapper.readValue(line, ChatMessage.class);
        if (type.equals(m.getType())) return m;
        inbox.add(m);
      }
      throw new AssertionError("No " + type);
    }

    void assertNoChat() throws Exception {
      for (ChatMessage m : inbox)
        assertFalse(
            Set.of("UNICAST", "GROUP_MESSAGE", "BROADCAST").contains(m.getType()),
            "Unexpected " + m.getType());
      inbox.clear();
      socket.setSoTimeout(150);
      try {
        while (true) {
          String line = reader.readLine();
          if (line == null) return;
          ChatMessage m = mapper.readValue(line, ChatMessage.class);
          assertFalse(
              Set.of("UNICAST", "GROUP_MESSAGE", "BROADCAST").contains(m.getType()),
              "Unexpected " + m.getType());
        }
      } catch (SocketTimeoutException expected) {
      } finally {
        socket.setSoTimeout(3000);
      }
    }

    void assertEof() throws Exception {
      assertTimeoutPreemptively(
          Duration.ofSeconds(3),
          () -> {
            while (reader.readLine() != null) {}
          });
    }

    public void close() throws IOException {
      socket.close();
    }
  }
}
