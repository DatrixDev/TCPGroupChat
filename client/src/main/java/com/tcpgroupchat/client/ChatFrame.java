package com.tcpgroupchat.client;

import com.tcpgroupchat.common.protocol.ChatMessage;
import com.tcpgroupchat.common.ui.IconManager;
import com.tcpgroupchat.common.ui.UiStyles;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class ChatFrame extends JFrame {
  private record Pending(ChatMessage message, long time) {}

  private final ClientConnection connection;
  private final Runnable onLogout;
  private final JTextArea conversation = new JTextArea(), activity = new JTextArea();
  private final JTextField input = new JTextField(),
      recipient = new JTextField(),
      groupName = new JTextField("study");
  private final JComboBox<String> mode =
      new JComboBox<>(
          new String[] {"Unicast — Gửi riêng", "Group — Gửi nhóm", "Broadcast — Tất cả"});
  private final DefaultListModel<String> users = new DefaultListModel<>(),
      members = new DefaultListModel<>();
  private final JLabel status = new JLabel("Đã đăng nhập"),
      membership = new JLabel("Đang tải nhóm…"),
      destination = new JLabel();
  private final JButton join = UiStyles.button("Join", "log-in", true),
      leave = UiStyles.button("Leave", "log-out", false),
      send = UiStyles.button("Gửi", "send", true),
      applyGroup = UiStyles.button("Xem nhóm", "hash", false),
      refresh = UiStyles.button("Làm mới", "refresh-cw", false);
  private final Map<String, Pending> pending = new HashMap<>();
  private final Set<String> received = new HashSet<>();
  private String activeGroup = "study";
  private boolean leaving;
  private final javax.swing.Timer pendingTimer;

  public ChatFrame(ClientConnection connection, Runnable onLogout) {
    super("TCPGroupChat | " + connection.username);
    this.connection = connection;
    this.onLogout = onLogout;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(1200, 800);
    setMinimumSize(new Dimension(1000, 700));
    setLocationRelativeTo(null);
    setIconImage(IconManager.image("message-square", 32, 32, UiStyles.PRIMARY));
    buildUi();
    mode.setSelectedIndex(1);
    updateDestination();
    connection.listen(
        message -> SwingUtilities.invokeLater(() -> handle(message)),
        reason -> SwingUtilities.invokeLater(() -> disconnected(reason)));
    request("GET_GROUP", activeGroup);
    log("SESSION", "Đăng nhập " + connection.username + " | phiên " + connection.sessionId);
    append(
        conversation,
        "HỆ THỐNG",
        "Chào " + connection.username + ". Chọn chế độ gửi trước khi nhấn Gửi / Enter.");
    pendingTimer =
        new javax.swing.Timer(
            1000,
            e -> {
              var iterator = pending.entrySet().iterator();
              while (iterator.hasNext()) {
                var entry = iterator.next();
                if (System.currentTimeMillis() - entry.getValue().time() > 15_000) {
                  log("TIMEOUT", "Chưa nhận xác nhận cho yêu cầu " + entry.getKey());
                  received.remove(entry.getKey());
                  iterator.remove();
                }
              }
            });
    pendingTimer.start();
    addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            pendingTimer.stop();
            connection.close();
          }
        });
  }

  private void buildUi() {
    JPanel root = new JPanel(new BorderLayout(16, 16));
    root.setBackground(UiStyles.BACKGROUND);
    root.setBorder(new EmptyBorder(20, 20, 16, 20));
    setContentPane(root);
    JPanel header = new JPanel(new BorderLayout(12, 8));
    header.setOpaque(false);
    JLabel title = UiStyles.title("TCPGroupChat", 28);
    title.setIcon(IconManager.load("message-square", 30, 30, UiStyles.PRIMARY));
    title.setIconTextGap(12);
    header.add(title, BorderLayout.WEST);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
    actions.setOpaque(false);
    status.setForeground(UiStyles.SUCCESS);
    JButton logout = UiStyles.button("Đăng xuất", "log-out", false);
    logout.addActionListener(e -> logout());
    actions.add(status);
    actions.add(logout);
    header.add(actions, BorderLayout.EAST);
    JLabel session =
        new JLabel(
            "Tài khoản: "
                + connection.username
                + "    •    Server: "
                + connection.endpoint
                + "    •    Đăng nhập: "
                + UiStyles.time(connection.loginTime));
    session.setForeground(UiStyles.MUTED);
    session.setToolTipText("Mã phiên: " + connection.sessionId);
    header.add(session, BorderLayout.SOUTH);
    root.add(header, BorderLayout.NORTH);

    JPanel center = new JPanel(new BorderLayout(14, 0));
    center.setOpaque(false);
    JTabbedPane tabs = new JTabbedPane();
    UiStyles.area(conversation);
    UiStyles.area(activity);
    activity.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    tabs.addTab(
        "Cuộc trò chuyện",
        IconManager.load("message-square", 17, 17, UiStyles.PRIMARY),
        new JScrollPane(conversation));
    tabs.addTab(
        "Nhật ký phiên",
        IconManager.load("file-text", 17, 17, UiStyles.PRIMARY),
        new JScrollPane(activity));
    center.add(tabs, BorderLayout.CENTER);
    JPanel sidebar = new JPanel(new BorderLayout());
    sidebar.setOpaque(false);
    sidebar.setPreferredSize(new Dimension(250, 0));
    JList<String> onlineList = new JList<>(users);
    onlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    onlineList.setFixedCellHeight(26);
    onlineList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && onlineList.getSelectedValue() != null) {
            recipient.setText(onlineList.getSelectedValue());
            mode.setSelectedIndex(0);
          }
        });
    JPanel online = UiStyles.card(new BorderLayout(0, 10));
    online.add(UiStyles.title("Client online", 17), BorderLayout.NORTH);
    online.add(new JScrollPane(onlineList), BorderLayout.CENTER);
    online.add(refresh, BorderLayout.SOUTH);
    refresh.addActionListener(
        e -> {
          request("GET_USERS", null);
          request("GET_GROUP", activeGroup);
        });

    JPanel group = UiStyles.card(new BorderLayout(0, 10));
    JPanel controls = new JPanel(new BorderLayout(0, 8));
    controls.setOpaque(false);
    controls.add(UiStyles.title("Phòng / Nhóm", 17), BorderLayout.NORTH);
    JPanel selection = new JPanel(new BorderLayout(6, 0));
    selection.setOpaque(false);
    selection.add(groupName, BorderLayout.CENTER);
    selection.add(applyGroup, BorderLayout.EAST);
    controls.add(selection, BorderLayout.CENTER);
    controls.add(membership, BorderLayout.SOUTH);
    group.add(controls, BorderLayout.NORTH);
    JList<String> memberList = new JList<>(members);
    memberList.setFixedCellHeight(26);
    group.add(new JScrollPane(memberList), BorderLayout.CENTER);
    JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
    buttons.setOpaque(false);
    buttons.add(join);
    buttons.add(leave);
    group.add(buttons, BorderLayout.SOUTH);
    join.setEnabled(false);
    leave.setEnabled(false);
    join.addActionListener(e -> request("JOIN_GROUP", activeGroup));
    leave.addActionListener(e -> request("LEAVE_GROUP", activeGroup));
    applyGroup.addActionListener(e -> selectGroup());
    groupName.addActionListener(e -> selectGroup());
    JSplitPane people = new JSplitPane(JSplitPane.VERTICAL_SPLIT, online, group);
    people.setBorder(null);
    people.setResizeWeight(.4);
    people.setDividerLocation(180);
    people.setDividerSize(8);
    online.setMinimumSize(new Dimension(200, 130));
    group.setMinimumSize(new Dimension(200, 235));
    sidebar.add(people, BorderLayout.CENTER);
    center.add(sidebar, BorderLayout.EAST);
    root.add(center, BorderLayout.CENTER);

    JPanel compose = UiStyles.card(new BorderLayout(10, 12));
    JPanel choices = new JPanel(new BorderLayout(12, 4));
    choices.setOpaque(false);
    JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    fields.setOpaque(false);
    fields.add(new JLabel("Chế độ:"));
    fields.add(mode);
    fields.add(new JLabel("Người nhận:"));
    recipient.setColumns(12);
    fields.add(recipient);
    choices.add(fields, BorderLayout.WEST);
    destination.setForeground(UiStyles.PRIMARY);
    choices.add(destination, BorderLayout.SOUTH);
    mode.addActionListener(e -> updateDestination());
    compose.add(choices, BorderLayout.NORTH);
    JPanel message = new JPanel(new BorderLayout(10, 0));
    message.setOpaque(false);
    input.setPreferredSize(new Dimension(300, 44));
    input.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn (tối đa 4000 ký tự)…");
    input.addActionListener(e -> sendText());
    send.addActionListener(e -> sendText());
    message.add(input, BorderLayout.CENTER);
    message.add(send, BorderLayout.EAST);
    compose.add(message, BorderLayout.CENTER);
    JLabel hint =
        new JLabel(
            "Join / Leave quyết định ai nhận tin nhóm. Sau Leave vẫn gửi được tới các thành viên"
                + " còn lại, theo đề bài.");
    hint.setForeground(UiStyles.MUTED);
    hint.setFont(hint.getFont().deriveFont(12f));
    compose.add(hint, BorderLayout.SOUTH);
    root.add(compose, BorderLayout.SOUTH);
  }

  private void selectGroup() {
    String value = groupName.getText().trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[a-z0-9_-]{1,30}")) {
      JOptionPane.showMessageDialog(this, "Tên nhóm cần 1–30 ký tự: chữ không dấu, số, _ hoặc -.");
      return;
    }
    activeGroup = value;
    members.clear();
    membership.setText("Đang tải #" + value);
    join.setEnabled(false);
    leave.setEnabled(false);
    request("GET_GROUP", value);
    updateDestination();
  }

  private void updateDestination() {
    recipient.setEnabled(mode.getSelectedIndex() == 0 && !connection.isClosed());
    destination.setText(
        switch (mode.getSelectedIndex()) {
          case 0 -> "Unicast: chỉ người nhận được bản tin; người gửi thấy xác nhận gửi.";
          case 1 -> "Group: gửi tới các thành viên hiện tại của #" + activeGroup;
          default -> "Broadcast: gửi tới tất cả client đang đăng nhập.";
        });
  }

  private void sendText() {
    if (connection.isClosed()) return;
    String text = input.getText().trim();
    if (text.isBlank()) return;
    if (text.length() > 4000) {
      JOptionPane.showMessageDialog(this, "Tin nhắn tối đa 4000 ký tự.");
      return;
    }
    String type =
        switch (mode.getSelectedIndex()) {
          case 0 -> "UNICAST";
          case 1 -> "GROUP_MESSAGE";
          default -> "BROADCAST";
        };
    ChatMessage message = ChatMessage.of(type, connection.username, text);
    if (mode.getSelectedIndex() == 0) {
      String user = recipient.getText().trim().toLowerCase(Locale.ROOT);
      if (!user.matches("[a-z0-9_-]{2,20}")) {
        JOptionPane.showMessageDialog(this, "Chọn client online hoặc nhập tên người nhận hợp lệ.");
        return;
      }
      message.setRecipient(user);
    }
    if (mode.getSelectedIndex() == 1) message.setGroup(activeGroup);
    transmit(message);
    input.setText("");
    input.requestFocusInWindow();
  }

  private void request(String type, String group) {
    ChatMessage request = ChatMessage.of(type, connection.username, null);
    request.setGroup(group);
    transmit(request);
  }

  private void transmit(ChatMessage message) {
    if (connection.isClosed()) return;
    message.setRequestId(UUID.randomUUID().toString());
    pending.put(message.getRequestId(), new Pending(message, System.currentTimeMillis()));
    log("SEND", message.getType() + (message.getGroup() == null ? "" : " #" + message.getGroup()));
    connection.send(message);
  }

  private void handle(ChatMessage message) {
    if (message == null || message.getType() == null || leaving) return;
    String id = message.getRequestId();
    switch (message.getType()) {
      case "USER_LIST" -> {
        if (id != null) pending.remove(id);
        users.clear();
        if (message.getUsers() != null) message.getUsers().forEach(users::addElement);
      }
      case "GROUP_STATE" -> {
        if (activeGroup.equals(message.getGroup())) {
          members.clear();
          if (message.getMembers() != null) message.getMembers().forEach(members::addElement);
          boolean joined =
              message.getJoinedGroups() != null && message.getJoinedGroups().contains(activeGroup);
          membership.setText(
              (joined ? "Đã Join" : "Ngoài nhóm")
                  + " #"
                  + activeGroup
                  + " • "
                  + members.size()
                  + " người");
          membership.setForeground(joined ? UiStyles.SUCCESS : UiStyles.MUTED);
          join.setEnabled(!joined);
          leave.setEnabled(joined);
        }
        if (id != null) pending.remove(id);
      }
      case "JOIN_OK", "LEAVE_OK" -> {
        pending.remove(id);
        log(message.getType(), message.getContent());
        append(conversation, "HỆ THỐNG", message.getContent());
      }
      case "ERROR" -> {
        Pending failed = pending.remove(id);
        received.remove(id);
        log("ERROR", message.getContent());
        append(conversation, "LỖI", message.getContent());
        if (failed != null && failed.message().getContent() != null && input.getText().isEmpty())
          input.setText(failed.message().getContent());
      }
      case "ACK" -> {
        Pending sent = pending.remove(id);
        if (sent != null && !received.remove(id)) {
          ChatMessage own = sent.message();
          append(conversation, "ĐÃ GỬI " + label(own), "Bạn: " + own.getContent());
        }
        log("ACK", message.getContent());
      }
      case "UNICAST", "GROUP_MESSAGE", "BROADCAST" -> {
        if (connection.username.equals(message.getSender()) && pending.containsKey(id))
          received.add(id);
        append(conversation, label(message), message.getSender() + ": " + message.getContent());
        log(
            "RECEIVE",
            message.getType() + " từ " + message.getSender() + " | " + message.getTimestamp());
      }
      case "SYSTEM" ->
          append(
              conversation,
              "HỆ THỐNG" + (message.getGroup() == null ? "" : " #" + message.getGroup()),
              message.getContent());
      default -> log(message.getType(), Objects.toString(message.getContent(), ""));
    }
  }

  private static String label(ChatMessage message) {
    return switch (message.getType()) {
      case "UNICAST" -> "RIÊNG → " + message.getRecipient();
      case "GROUP_MESSAGE" -> "NHÓM #" + message.getGroup();
      default -> "BROADCAST";
    };
  }

  private void disconnected(String reason) {
    status.setText("Đã ngắt kết nối");
    status.setForeground(UiStyles.ERROR);
    for (JComponent c :
        new JComponent[] {
          input, send, mode, recipient, join, leave, applyGroup, refresh, groupName
        }) c.setEnabled(false);
    users.clear();
    members.clear();
    pending.clear();
    received.clear();
    membership.setText("Đã ngắt kết nối");
    log("DISCONNECT", reason);
    append(conversation, "HỆ THỐNG", reason + " Nhấn Đăng xuất để kết nối lại.");
  }

  private void logout() {
    if (leaving) return;
    leaving = true;
    connection.close();
    dispose();
    onLogout.run();
  }

  private void log(String event, String text) {
    append(activity, event, text);
  }

  private static void append(JTextArea area, String label, String text) {
    area.append(
        "["
            + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            + "] ["
            + label
            + "] "
            + text
            + System.lineSeparator());
    if (area.getDocument().getLength() > 200_000)
      try {
        area.getDocument().remove(0, 50_000);
      } catch (javax.swing.text.BadLocationException ignored) {
      }
    area.setCaretPosition(area.getDocument().getLength());
  }
}
