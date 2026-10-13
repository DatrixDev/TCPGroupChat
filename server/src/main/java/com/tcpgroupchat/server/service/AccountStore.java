package com.tcpgroupchat.server.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AccountStore {
  private static final int ITERATIONS = 120_000;
  private final Path root;

  public AccountStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new IllegalStateException("Không thể tạo thư mục tài khoản", e);
    }
  }

  public static String username(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[a-z0-9_-]{2,20}"))
      throw new IllegalArgumentException(
          "Tên đăng nhập cần 2–20 ký tự: chữ không dấu, số, _ hoặc -.");
    return value;
  }

  private static void validatePassword(String password) {
    if (password == null || password.length() < 4 || password.length() > 128)
      throw new IllegalArgumentException("Mật khẩu cần từ 4 đến 128 ký tự.");
  }

  public synchronized void register(String raw, String password, String ip) throws IOException {
    String user = username(raw);
    validatePassword(password);
    Path file = root.resolve(user + ".properties");
    if (Files.exists(file)) throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    Properties props = new Properties();
    props.setProperty("username", user);
    props.setProperty("salt", Base64.getEncoder().encodeToString(salt));
    props.setProperty("hash", Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS)));
    props.setProperty("iterations", String.valueOf(ITERATIONS));
    props.setProperty("registeredAt", LocalDateTime.now().toString());
    props.setProperty("registerIp", ip);
    Path temporary = Files.createTempFile(root, "account-", ".tmp");
    try {
      try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        props.store(writer, "TCPGroupChat account");
      }
      Files.move(temporary, file);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public boolean authenticate(String raw, String password) throws IOException {
    String user = username(raw);
    validatePassword(password);
    Path file = root.resolve(user + ".properties");
    if (!Files.isRegularFile(file)) return false;
    Properties props = new Properties();
    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      props.load(reader);
    }
    byte[] expected = Base64.getDecoder().decode(props.getProperty("hash"));
    byte[] actual =
        hash(
            password,
            Base64.getDecoder().decode(props.getProperty("salt")),
            Integer.parseInt(props.getProperty("iterations")));
    return MessageDigest.isEqual(expected, actual);
  }

  public List<String> users() {
    try (var paths = Files.list(root)) {
      return paths
          .map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(".properties"))
          .map(n -> n.substring(0, n.length() - 11))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Không thể đọc tài khoản", e);
    }
  }

  private static byte[] hash(String password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } finally {
      spec.clearPassword();
    }
  }
}
