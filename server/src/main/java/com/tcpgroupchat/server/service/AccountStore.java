package com.tcpgroupchat.server.service;

import com.tcpgroupchat.common.protocol.ChatMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Account persistence with an optional MySQL backend and a file-compatible fallback. */
public final class AccountStore {
  public record StoredGroup(String name, String creator, String multicastIp, Set<String> members) {}
  private static final int ITERATIONS = 120_000;
  private final Path root;
  private final String databaseUrl;
  private final String databaseUser;
  private final String databasePassword;
  private final boolean databaseEnabled;

  public AccountStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try { Files.createDirectories(this.root); }
    catch (IOException e) { throw new IllegalStateException("Không thể tạo thư mục tài khoản", e); }
    databaseUrl = setting("CHAT_DB_URL", "chat.db.url", "");
    databaseUser = setting("CHAT_DB_USER", "chat.db.user", "tcpchat");
    databasePassword = setting("CHAT_DB_PASSWORD", "chat.db.password", "tcpchat");
    databaseEnabled = initializeDatabase();
    if (!databaseEnabled && Boolean.parseBoolean(setting("CHAT_DB_REQUIRED", "chat.db.required", "false")))
      throw new IllegalStateException("Không thể kết nối MySQL. Hãy chạy docker compose up -d trước.");
    if (databaseEnabled) migrateFileAccountsToDatabase();
  }

  private static String setting(String environment, String property, String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) value = System.getenv(environment);
    return value == null || value.isBlank() ? fallback : value;
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
  }

  private boolean initializeDatabase() {
    if (databaseUrl.isBlank()) return false;
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          create table if not exists accounts (
            username varchar(20) primary key, salt text not null, password_hash text not null,
            iterations integer not null, registered_at timestamp(6) not null, register_ip varchar(64) not null
          ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci""");
      statement.executeUpdate("""
          create table if not exists chat_groups (
            name varchar(30) primary key, creator varchar(20) not null,
            multicast_ip varchar(15) not null unique, created_at timestamp(6) not null,
            constraint fk_group_creator foreign key (creator) references accounts(username)
          ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci""");
      statement.executeUpdate("""
          create table if not exists group_members (
            group_name varchar(30) not null, username varchar(20) not null,
            joined_at timestamp(6) not null,
            primary key(group_name, username),
            constraint fk_member_group foreign key (group_name) references chat_groups(name) on delete cascade,
            constraint fk_member_user foreign key (username) references accounts(username) on delete cascade
          ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci""");
      statement.executeUpdate("""
          create table if not exists chat_messages (
            id bigint auto_increment primary key, request_id varchar(64), message_type varchar(32) not null,
            sender varchar(20) not null, recipient varchar(20), group_name varchar(30),
            content text, sender_ip varchar(64), attachment_name text, attachment_type varchar(128),
            attachment_size bigint not null default 0, sent_at timestamp(6) not null,
            index idx_chat_sent_at(sent_at), index idx_chat_sender(sender)
          ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci""");
      System.out.println("MySQL đã kết nối: " + databaseUrl);
      return true;
    } catch (SQLException e) {
      System.err.println("MySQL không khả dụng, tiếp tục dùng file: " + e.getMessage());
      return false;
    }
  }

  public boolean isDatabaseEnabled() { return databaseEnabled; }

  /** Copies legacy file accounts to MySQL without removing the recovery copies. */
  private void migrateFileAccountsToDatabase() {
    int migrated = 0;
    try (var paths = Files.list(root)) {
      for (Path file : paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".properties")).toList()) {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { props.load(reader); }
        String user = username(props.getProperty("username",
            file.getFileName().toString().replaceFirst("\\.properties$", "")));
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
            "insert ignore into accounts(username,salt,password_hash,iterations,registered_at,register_ip) values (?,?,?,?,?,?)")) {
          statement.setString(1, user);
          statement.setString(2, Objects.requireNonNull(props.getProperty("salt")));
          statement.setString(3, Objects.requireNonNull(props.getProperty("hash")));
          statement.setInt(4, Integer.parseInt(props.getProperty("iterations", String.valueOf(ITERATIONS))));
          statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(
              props.getProperty("registeredAt", LocalDateTime.now().toString()))));
          statement.setString(6, props.getProperty("registerIp", "unknown"));
          migrated += statement.executeUpdate();
        } catch (SQLException | RuntimeException invalid) {
          System.err.println("Không thể di chuyển tài khoản " + user + " sang MySQL: " + invalid.getMessage());
        }
      }
    } catch (IOException e) {
      System.err.println("Không thể đọc tài khoản cũ để di chuyển: " + e.getMessage());
    }
    if (migrated > 0) System.out.println("Đã di chuyển " + migrated + " tài khoản cũ sang MySQL.");
  }

  public static String username(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[a-z0-9_-]{2,20}"))
      throw new IllegalArgumentException("Tên đăng nhập cần 2–20 ký tự: chữ không dấu, số, _ hoặc -.");
    return value;
  }

  private static void validatePassword(String password) {
    if (password == null || password.length() < 4 || password.length() > 128)
      throw new IllegalArgumentException("Mật khẩu cần từ 4 đến 128 ký tự.");
  }

  public synchronized void register(String raw, String password, String ip) throws IOException {
    String user = username(raw);
    validatePassword(password);
    if (users().contains(user)) throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    String saltText = Base64.getEncoder().encodeToString(salt);
    String hashText = Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS));
    String registeredAt = LocalDateTime.now().toString();
    if (databaseEnabled) {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
          "insert into accounts(username,salt,password_hash,iterations,registered_at,register_ip) values (?,?,?,?,?,?)")) {
        statement.setString(1, user); statement.setString(2, saltText); statement.setString(3, hashText);
        statement.setInt(4, ITERATIONS); statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(registeredAt)));
        statement.setString(6, ip); statement.executeUpdate();
      } catch (SQLException e) { throw new IOException("Không thể ghi tài khoản vào MySQL", e); }
      return;
    }
    Properties props = new Properties();
    props.setProperty("username", user); props.setProperty("salt", saltText);
    props.setProperty("hash", hashText); props.setProperty("iterations", String.valueOf(ITERATIONS));
    props.setProperty("registeredAt", registeredAt); props.setProperty("registerIp", ip);
    Path temporary = Files.createTempFile(root, "account-", ".tmp");
    try {
      try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        props.store(writer, "TCPGroupChat account");
      }
      Files.move(temporary, root.resolve(user + ".properties"));
    } finally { Files.deleteIfExists(temporary); }
  }

  public boolean authenticate(String raw, String password) throws IOException {
    String user = username(raw);
    validatePassword(password);
    if (databaseEnabled) {
      try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
          "select salt,password_hash,iterations from accounts where username=?")) {
        statement.setString(1, user);
        try (ResultSet row = statement.executeQuery()) {
          if (row.next()) {
            byte[] actual = hash(password, Base64.getDecoder().decode(row.getString(1)), row.getInt(3));
            return MessageDigest.isEqual(Base64.getDecoder().decode(row.getString(2)), actual);
          }
          return false;
        }
      } catch (SQLException e) { throw new IOException("Không thể đọc tài khoản từ MySQL", e); }
    }
    Path file = root.resolve(user + ".properties");
    if (!Files.isRegularFile(file)) return false;
    Properties props = new Properties();
    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { props.load(reader); }
    byte[] expected = Base64.getDecoder().decode(props.getProperty("hash"));
    byte[] actual = hash(password, Base64.getDecoder().decode(props.getProperty("salt")),
        Integer.parseInt(props.getProperty("iterations")));
    return MessageDigest.isEqual(expected, actual);
  }

  public List<String> users() {
    Set<String> result = new TreeSet<>();
    if (databaseEnabled) {
      try (Connection connection = connection(); Statement statement = connection.createStatement();
           ResultSet rows = statement.executeQuery("select username from accounts order by username")) {
        while (rows.next()) result.add(rows.getString(1));
      } catch (SQLException e) { throw new IllegalStateException("Không thể thống kê MySQL", e); }
      return List.copyOf(result);
    }
    try (var paths = Files.list(root)) {
      paths.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".properties"))
          .map(n -> n.substring(0, n.length() - 11)).forEach(result::add);
    } catch (IOException e) { throw new IllegalStateException("Không thể đọc tài khoản", e); }
    return List.copyOf(result);
  }

  public void recordMessage(ChatMessage message) {
    if (!databaseEnabled) return;
    try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
        "insert into chat_messages(request_id,message_type,sender,recipient,group_name,content,sender_ip,attachment_name,attachment_type,attachment_size,sent_at) values (?,?,?,?,?,?,?,?,?,?,?)")) {
      statement.setString(1, message.getRequestId()); statement.setString(2, message.getType());
      statement.setString(3, message.getSender()); statement.setString(4, message.getRecipient());
      statement.setString(5, message.getGroup()); statement.setString(6, message.getContent());
      statement.setString(7, message.getSenderIp()); statement.setString(8, message.getAttachmentName());
      statement.setString(9, message.getAttachmentType()); statement.setLong(10, message.getAttachmentSize());
      statement.setTimestamp(11, Timestamp.valueOf(message.getTimestamp())); statement.executeUpdate();
    } catch (SQLException | RuntimeException e) {
      System.err.println("Không thể lưu lịch sử tin nhắn: " + e.getMessage());
    }
  }

  public List<StoredGroup> loadGroups() {
    if (!databaseEnabled) return List.of();
    Map<String, StoredGroup> result = new LinkedHashMap<>();
    String sql = """
        select g.name, g.creator, g.multicast_ip, m.username
        from chat_groups g left join group_members m on m.group_name=g.name
        order by g.created_at, g.name, m.username""";
    try (Connection connection = connection(); Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        String name = rows.getString(1);
        StoredGroup group = result.computeIfAbsent(name,
            ignored -> new StoredGroup(name, rowsGet(rows, 2), rowsGet(rows, 3), new TreeSet<>()));
        String member = rows.getString(4);
        if (member != null) group.members().add(member);
      }
      return List.copyOf(result.values());
    } catch (SQLException e) {
      throw new IllegalStateException("Không thể tải nhóm từ MySQL", e);
    }
  }

  private static String rowsGet(ResultSet rows, int column) {
    try { return rows.getString(column); }
    catch (SQLException e) { throw new IllegalStateException(e); }
  }

  public void createGroup(String name, String creator, String multicastIp, Collection<String> members)
      throws IOException {
    if (!databaseEnabled) return;
    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement group = connection.prepareStatement(
               "insert into chat_groups(name,creator,multicast_ip,created_at) values (?,?,?,?)");
           PreparedStatement member = connection.prepareStatement(
               "insert into group_members(group_name,username,joined_at) values (?,?,?)")) {
        group.setString(1, name); group.setString(2, creator); group.setString(3, multicastIp);
        group.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now())); group.executeUpdate();
        for (String username : members) {
          member.setString(1, name); member.setString(2, username);
          member.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now())); member.addBatch();
        }
        member.executeBatch();
        connection.commit();
      } catch (SQLException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (SQLException e) { throw new IOException("Không thể lưu nhóm vào MySQL", e); }
  }

  public void addGroupMembers(String group, Collection<String> members) throws IOException {
    if (!databaseEnabled || members.isEmpty()) return;
    try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
        "insert ignore into group_members(group_name,username,joined_at) values (?,?,?)")) {
      for (String username : members) {
        statement.setString(1, group); statement.setString(2, username);
        statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now())); statement.addBatch();
      }
      statement.executeBatch();
    } catch (SQLException e) { throw new IOException("Không thể lưu thành viên nhóm vào MySQL", e); }
  }

  public void removeGroupMember(String group, String username) throws IOException {
    if (!databaseEnabled) return;
    try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
        "delete from group_members where group_name=? and username=?")) {
      statement.setString(1, group); statement.setString(2, username); statement.executeUpdate();
    } catch (SQLException e) { throw new IOException("Không thể cập nhật thành viên nhóm trong MySQL", e); }
  }

  private static byte[] hash(String password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
    catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    finally { spec.clearPassword(); }
  }
}
