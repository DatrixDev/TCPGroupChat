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

  private final JPanel bubbles = new JPanel();
  private final JPanel memberChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
  private final JLabel chatTitle = UiStyles.title("Nhóm #study", 22);
  private final CardLayout workspaceLayout = new CardLayout();
  private final JPanel workspace = new JPanel(workspaceLayout);
  private final JToggleButton[] modeButtons = new JToggleButton[3];
  private JScrollPane bubbleScroll;

  public ChatFrame(ClientConnection connection, Runnable onLogout) {
    super("TCPGroupChat | " + connection.username);
    this.connection = connection;
    this.onLogout = onLogout;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(1300, 800);
    setMinimumSize(new Dimension(1030, 650));
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
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(UiStyles.BACKGROUND);
    setContentPane(root);
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(UiStyles.NAVY);
    header.setBorder(new EmptyBorder(18, 24, 18, 24));
    JLabel brand = UiStyles.title("TCP Group Chat", 26);
    brand.setForeground(Color.WHITE);
    brand.setIcon(IconManager.load("message-square", 36, 36, Color.WHITE));
    brand.setIconTextGap(14);
    header.add(brand, BorderLayout.WEST);
    JPanel identity = new JPanel(new FlowLayout(FlowLayout.RIGHT, 22, 0));
    identity.setOpaque(false);
    status.setText("Đã kết nối");
    status.setForeground(UiStyles.SUCCESS);
    status.setIcon(IconManager.load("circle-check-big", 23, 23, UiStyles.SUCCESS));
    identity.add(status);
    JLabel user = UiStyles.title(connection.username, 16);
    user.setForeground(Color.WHITE);
    user.setIcon(IconManager.load("user", 26, 26, Color.WHITE));
    identity.add(user);
    header.add(identity, BorderLayout.EAST);
    root.add(header, BorderLayout.NORTH);

    JPanel navigation = new JPanel();
    navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
    navigation.setBackground(UiStyles.NAVY);
    navigation.setBorder(new EmptyBorder(18, 12, 18, 12));
    navigation.setPreferredSize(new Dimension(220, 0));
    JButton chats = navButton("Trò chuyện", "message-square");
    JButton rooms = navButton("Nhóm chat", "users");
    JButton journal = navButton("Nhật ký phiên", "file-text");
    JButton reload = navButton("Làm mới", "refresh-cw");
    JButton logout = navButton("Đăng xuất", "log-out");
    chats.setBackground(UiStyles.PRIMARY);
    for (JButton button : new JButton[]{chats, rooms, journal, reload, logout}) {
      navigation.add(button);
      navigation.add(Box.createVerticalStrut(8));
    }
    navigation.add(Box.createVerticalGlue());
    root.add(navigation, BorderLayout.WEST);

    JPanel content = new JPanel(new BorderLayout());
    content.setOpaque(false);
    content.setBorder(new EmptyBorder(18, 16, 18, 18));
    JPanel chatWorkspace = new JPanel(new BorderLayout(16, 0));
    chatWorkspace.setOpaque(false);
    JPanel listCard = UiStyles.card(new BorderLayout(0, 16));
    listCard.setPreferredSize(new Dimension(270, 0));
    listCard.add(sectionTitle("Cuộc trò chuyện", "file-text", 20), BorderLayout.NORTH);
    JPanel listBody = new JPanel(new BorderLayout(0, 18));
    listBody.setOpaque(false);
    JPanel roomSelection = new JPanel(new BorderLayout(0, 8));
    roomSelection.setOpaque(false);
    roomSelection.add(UiStyles.title("Nhóm", 15), BorderLayout.NORTH);
    JPanel selector = new JPanel(new BorderLayout(6, 0));
    selector.setOpaque(false);
    groupName.putClientProperty("JTextField.placeholderText", "Tên nhóm");
    groupName.setPreferredSize(new Dimension(100, 40));
    applyGroup.setText("");
    applyGroup.setToolTipText("Mở nhóm hoặc tạo nhóm mới khi Join");
    selector.add(groupName, BorderLayout.CENTER);
    selector.add(applyGroup, BorderLayout.EAST);
    roomSelection.add(selector, BorderLayout.CENTER);
    JPanel shortcuts = new JPanel(new GridLayout(2, 1, 0, 6));
    shortcuts.setOpaque(false);
    for (String name : new String[]{"study", "general"}) {
      JButton room = UiStyles.button("#" + name, "users", false);
      room.setHorizontalAlignment(SwingConstants.LEFT);
      room.addActionListener(e -> { groupName.setText(name); selectGroup(); mode.setSelectedIndex(1); });
      shortcuts.add(room);
    }
    roomSelection.add(shortcuts, BorderLayout.SOUTH);
    listBody.add(roomSelection, BorderLayout.NORTH);
    JPanel online = new JPanel(new BorderLayout(0, 10));
    online.setOpaque(false);
    online.add(UiStyles.title("Đang online", 15), BorderLayout.NORTH);
    JList<String> onlineList = new JList<>(users);
    onlineList.setFixedCellHeight(42);
    onlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    onlineList.setCellRenderer(new DefaultListCellRenderer() {
      @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
        JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, selected, focus);
        label.setBorder(new EmptyBorder(6, 8, 6, 8));
        label.setIcon(IconManager.load("circle-check-big", 16, 16, UiStyles.SUCCESS));
        label.setIconTextGap(12);
        return label;
      }
    });
    onlineList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && onlineList.getSelectedValue() != null) {
        recipient.setText(onlineList.getSelectedValue());
        mode.setSelectedIndex(0);
      }
    });
    online.add(scroll(onlineList), BorderLayout.CENTER);
    listBody.add(online, BorderLayout.CENTER);
    listCard.add(listBody, BorderLayout.CENTER);
    chatWorkspace.add(listCard, BorderLayout.WEST);

    JPanel chat = UiStyles.card(new BorderLayout(0, 16));
    JPanel chatHeader = new JPanel(new BorderLayout(8, 12));
    chatHeader.setOpaque(false);
    JPanel heading = new JPanel(new BorderLayout(0, 8));
    heading.setOpaque(false);
    chatTitle.setIcon(IconManager.load("users", 30, 30, UiStyles.PRIMARY));
    chatTitle.setIconTextGap(12);
    heading.add(chatTitle, BorderLayout.NORTH);
    membership.setForeground(UiStyles.MUTED);
    heading.add(membership, BorderLayout.SOUTH);
    chatHeader.add(heading, BorderLayout.CENTER);
    JPanel membershipActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    membershipActions.setOpaque(false);
    leave.setForeground(UiStyles.ERROR);
    membershipActions.add(join);
    membershipActions.add(leave);
    chatHeader.add(membershipActions, BorderLayout.EAST);
    memberChips.setOpaque(false);
    memberChips.setPreferredSize(new Dimension(10, 42));
    chatHeader.add(memberChips, BorderLayout.SOUTH);
    chat.add(chatHeader, BorderLayout.NORTH);
    bubbles.setLayout(new BoxLayout(bubbles, BoxLayout.Y_AXIS));
    bubbles.setBackground(Color.WHITE);
    bubbleScroll = scroll(bubbles);
    bubbleScroll.getVerticalScrollBar().setUnitIncrement(20);
    bubbleScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    bubbleScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent event) { reflowBubbles(); }
    });
    chat.add(bubbleScroll, BorderLayout.CENTER);

    JPanel compose = new JPanel(new BorderLayout(0, 12));
    compose.setOpaque(false);
    JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    choices.setOpaque(false);
    ButtonGroup modes = new ButtonGroup();
    String[] names = {"Unicast", "Group", "Broadcast"};
    for (int i = 0; i < 3; i++) {
      final int index = i;
      JToggleButton button = new JToggleButton(names[i]);
      button.setPreferredSize(new Dimension(100, 40));
      button.setMargin(new Insets(6, 8, 6, 8));
      button.addActionListener(e -> mode.setSelectedIndex(index));
      modeButtons[i] = button;
      modes.add(button);
      choices.add(button);
    }
    recipient.setColumns(10);
    recipient.putClientProperty("JTextField.placeholderText", "Người nhận");
    recipient.setPreferredSize(new Dimension(140, 40));
    choices.add(Box.createHorizontalStrut(10));
    choices.add(recipient);
    choices.setPreferredSize(new Dimension(10, 40));
    compose.add(choices, BorderLayout.NORTH);
    JPanel message = new JPanel(new BorderLayout(10, 0));
    message.setOpaque(false);
    input.setPreferredSize(new Dimension(300, 48));
    input.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn…");
    input.addActionListener(e -> sendText());
    send.addActionListener(e -> sendText());
    message.add(input, BorderLayout.CENTER);
    message.add(send, BorderLayout.EAST);
    compose.add(message, BorderLayout.CENTER);
    chat.add(compose, BorderLayout.SOUTH);
    chatWorkspace.add(chat, BorderLayout.CENTER);
    workspace.setOpaque(false);
    workspace.add(chatWorkspace, "chat");
    UiStyles.area(activity);
    JPanel journalCard = UiStyles.card(new BorderLayout(0, 16));
    journalCard.add(sectionTitle("Nhật ký phiên", "file-text", 22), BorderLayout.NORTH);
    journalCard.add(scroll(activity), BorderLayout.CENTER);
    workspace.add(journalCard, "journal");
    content.add(workspace);
    root.add(content, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout());
    footer.setBackground(UiStyles.NAVY);
    footer.setBorder(new EmptyBorder(10, 22, 10, 22));
    JLabel endpoint = new JLabel("Server: " + connection.endpoint);
    endpoint.setForeground(Color.WHITE);
    JLabel session = new JLabel("IP client: " + connection.localIp + "    •    Đăng nhập: " + UiStyles.time(connection.loginTime));
    session.setForeground(Color.WHITE);
    session.setToolTipText("IP client: " + connection.localIp + " | Mã phiên: " + connection.sessionId);
    footer.add(endpoint, BorderLayout.WEST);
    footer.add(session, BorderLayout.EAST);
    root.add(footer, BorderLayout.SOUTH);
    chats.addActionListener(e -> { workspaceLayout.show(workspace, "chat"); chats.setBackground(UiStyles.PRIMARY); journal.setBackground(UiStyles.NAVY); });
    rooms.addActionListener(e -> { chats.doClick(); mode.setSelectedIndex(1); groupName.requestFocusInWindow(); });
    journal.addActionListener(e -> { workspaceLayout.show(workspace, "journal"); journal.setBackground(UiStyles.PRIMARY); chats.setBackground(UiStyles.NAVY); });
    refresh.addActionListener(e -> { request("GET_USERS", null); request("GET_GROUP", activeGroup); });
    reload.addActionListener(e -> refresh.doClick());
    logout.addActionListener(e -> logout());
    join.setEnabled(false);
    leave.setEnabled(false);
    join.addActionListener(e -> request("JOIN_GROUP", activeGroup));
    leave.addActionListener(e -> request("LEAVE_GROUP", activeGroup));
    applyGroup.addActionListener(e -> selectGroup());
    groupName.addActionListener(e -> selectGroup());
    mode.addActionListener(e -> updateDestination());
  }

  private void reflowBubbles() {
    int width = Math.max(100, Math.min(440, bubbleScroll.getViewport().getWidth() - 55));
    for (Component component : bubbles.getComponents()) {
      JPanel row = (JPanel) component;
      JPanel bubble = (JPanel) row.getComponent(0);
      JTextArea body = (JTextArea) ((BorderLayout) bubble.getLayout()).getLayoutComponent(BorderLayout.CENTER);
      body.setPreferredSize(null);
      body.setSize(width, Integer.MAX_VALUE);
      body.setPreferredSize(new Dimension(width, body.getPreferredSize().height));
      row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    }
    bubbles.revalidate();
  }

  private static JScrollPane scroll(Component component) {
    JScrollPane scroll = new JScrollPane(component);
    scroll.setBorder(null);
    scroll.getViewport().setBackground(Color.WHITE);
    return scroll;
  }

  private static JLabel sectionTitle(String text, String icon, int size) {
    JLabel label = UiStyles.title(text, size);
    label.setIcon(IconManager.load(icon, 24, 24, UiStyles.PRIMARY));
    label.setIconTextGap(12);
    return label;
  }

  private static JButton navButton(String text, String icon) {
    JButton button = UiStyles.button(text, icon, true);
    button.setBackground(UiStyles.NAVY);
    button.setHorizontalAlignment(SwingConstants.LEFT);
    button.setIcon(IconManager.load(icon, 24, 24, Color.WHITE));
    button.setIconTextGap(14);
    button.setBorder(new EmptyBorder(10, 12, 10, 12));
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
    button.setAlignmentX(Component.LEFT_ALIGNMENT);
    return button;
  }

  private void selectGroup() {
    String value = groupName.getText().trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[a-z0-9_-]{1,30}")) {
      JOptionPane.showMessageDialog(this, "Tên nhóm cần 1–30 ký tự: chữ không dấu, số, _ hoặc -.");
      return;
    }
    activeGroup = value;
    members.clear();
    memberChips.removeAll();
    memberChips.revalidate();
    memberChips.repaint();
    membership.setText("Đang tải #" + value);
    join.setEnabled(false);
    leave.setEnabled(false);
    request("GET_GROUP", value);
    updateDestination();
  }

  private void updateDestination() {
    recipient.setEnabled(mode.getSelectedIndex() == 0 && !connection.isClosed());
    recipient.setVisible(mode.getSelectedIndex() == 0);
    for (int i = 0; i < modeButtons.length; i++) {
      modeButtons[i].setSelected(i == mode.getSelectedIndex());
      modeButtons[i].setBackground(i == mode.getSelectedIndex() ? UiStyles.PRIMARY : Color.WHITE);
      modeButtons[i].setForeground(i == mode.getSelectedIndex() ? Color.WHITE : UiStyles.NAVY);
    }
    chatTitle.setText(switch (mode.getSelectedIndex()) {
      case 0 -> "Tin nhắn riêng";
      case 1 -> "Nhóm #" + activeGroup;
      default -> "Broadcast";
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
          memberChips.removeAll();
          for (int i = 0; i < members.size(); i++) {
            JLabel chip = new JLabel(members.get(i));
            chip.setIcon(IconManager.load("circle-check-big", 14, 14, UiStyles.SUCCESS));
            chip.setOpaque(true);
            chip.setBackground(UiStyles.BACKGROUND);
            chip.setBorder(new EmptyBorder(8, 10, 8, 10));
            memberChips.add(chip);
          }
          memberChips.revalidate();
          memberChips.repaint();
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
    for (JToggleButton button : modeButtons) button.setEnabled(false);
    memberChips.removeAll();
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

  private void append(JTextArea area, String label, String text) {
    if (area == conversation) {
      boolean own = text.startsWith(connection.username + ": ") || text.startsWith("Bạn: ");
      boolean system = label.startsWith("HỆ THỐNG") || label.equals("LỖI");
      JPanel row = new JPanel(new FlowLayout(own ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
      row.setOpaque(false);
      row.setBorder(new EmptyBorder(0, 4, 16, 4));
      JPanel bubble = UiStyles.card(new BorderLayout(0, 7));
      bubble.setBackground(own ? UiStyles.PRIMARY : UiStyles.BACKGROUND);
      JLabel meta = new JLabel(label + " · " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
      meta.setFont(new Font("Segoe UI", Font.PLAIN, 11));
      meta.setForeground(own ? Color.WHITE : UiStyles.MUTED);
      JTextArea body = new JTextArea(text);
      body.setEditable(false);
      body.setLineWrap(true);
      body.setWrapStyleWord(true);
      body.setOpaque(false);
      body.setForeground(own ? Color.WHITE : system ? UiStyles.MUTED : UiStyles.NAVY);
      body.setFont(new Font("Segoe UI", Font.PLAIN, 14));
      int width = Math.max(180, Math.min(440, bubbleScroll.getViewport().getWidth() - 55));
      body.setSize(width, Integer.MAX_VALUE);
      body.setPreferredSize(new Dimension(width, body.getPreferredSize().height));
      bubble.add(meta, BorderLayout.NORTH);
      bubble.add(body, BorderLayout.CENTER);
      row.add(bubble);
      row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
      bubbles.add(row);
      if (bubbles.getComponentCount() > 300) bubbles.remove(0);
      reflowBubbles();
      bubbles.repaint();
      SwingUtilities.invokeLater(() -> bubbleScroll.getVerticalScrollBar().setValue(bubbleScroll.getVerticalScrollBar().getMaximum()));
    }
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
