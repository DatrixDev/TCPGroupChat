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
<<<<<<< HEAD
  private String activeGroup = "study";
=======
  private String activeGroup = "";
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
  private final DefaultListModel<String> knownGroups = new DefaultListModel<>();
  private final JButton addPeople = UiStyles.button("Thêm người", "users", false);
  private String groupStatus = "Đang tải nhóm…";
  private final Map<String, java.util.List<JPanel>> histories = new LinkedHashMap<>();
<<<<<<< HEAD
  private String activeConversation = "group:study";
  private boolean leaving;
=======
  private String activeConversation = "group:";
  private boolean leaving;
  private boolean isGroupCreator;
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
  private final javax.swing.Timer pendingTimer;

  private final JPanel bubbles = new JPanel();
  private final JPanel memberChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
  private final JLabel chatTitle = UiStyles.title("Nhóm #study", 22);
  private final CardLayout workspaceLayout = new CardLayout();
  private final JPanel workspace = new JPanel(workspaceLayout);
  private final JTabbedPane modeTabs = new JTabbedPane();
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
<<<<<<< HEAD
    request("GET_GROUP", activeGroup);
=======
    request("GET_USERS", null);
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
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
<<<<<<< HEAD
    JPanel selector = new JPanel(new BorderLayout(6, 0));
    selector.setOpaque(false);
    groupName.putClientProperty("JTextField.placeholderText", "Tên nhóm");
    groupName.setPreferredSize(new Dimension(100, 40));
    applyGroup.setText("");
    applyGroup.setToolTipText("Mở nhóm hoặc tạo nhóm mới khi Join");
    selector.add(groupName, BorderLayout.CENTER);
    selector.add(applyGroup, BorderLayout.EAST);
    roomSelection.add(selector, BorderLayout.CENTER);
    knownGroups.addElement("study");
    knownGroups.addElement("general");
=======
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
    JList<String> roomList = new JList<>(knownGroups);
    roomList.setFixedCellHeight(36);
    roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    roomList.setCellRenderer(new DefaultListCellRenderer() {
      @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, "#" + value, index, selected, focus);
        label.setIcon(IconManager.load("users", 18, 18, UiStyles.PRIMARY));
        label.setIconTextGap(10);
        return label;
      }
    });
    roomList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && roomList.getSelectedValue() != null) {
        groupName.setText(roomList.getSelectedValue());
        selectGroup();
        mode.setSelectedIndex(1);
      }
    });
    JPanel groupActions = new JPanel(new BorderLayout(0, 8));
    groupActions.setOpaque(false);
    JScrollPane roomScroll = scroll(roomList);
    roomScroll.setPreferredSize(new Dimension(10, 108));
    groupActions.add(roomScroll, BorderLayout.CENTER);
    JButton createGroup = UiStyles.button("Tạo nhóm", "users", true);
    createGroup.addActionListener(e -> chooseGroupMembers(true));
    groupActions.add(createGroup, BorderLayout.SOUTH);
    roomSelection.add(groupActions, BorderLayout.SOUTH);
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
    recipient.setColumns(12);
    recipient.putClientProperty("JTextField.placeholderText", "Người nhận");
    recipient.setPreferredSize(new Dimension(140, 38));
    heading.add(recipient, BorderLayout.CENTER);
    chatHeader.add(heading, BorderLayout.CENTER);
    JPanel membershipActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    membershipActions.setOpaque(false);
    leave.setForeground(UiStyles.ERROR);
    addPeople.addActionListener(e -> chooseGroupMembers(false));
    addPeople.setToolTipText("Chọn người để thêm ngay vào nhóm");
<<<<<<< HEAD
    membershipActions.add(join);
=======
    // Membership is assigned by the creator; no public Join control.
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
    membershipActions.add(leave);
    chatHeader.add(membershipActions, BorderLayout.EAST);
    memberChips.setOpaque(false);
    memberChips.setPreferredSize(new Dimension(10, 42));
    JPanel groupDetails = new JPanel(new BorderLayout(8, 0));
    groupDetails.setOpaque(false);
    groupDetails.add(memberChips, BorderLayout.CENTER);
    groupDetails.add(addPeople, BorderLayout.EAST);
    chatHeader.add(groupDetails, BorderLayout.SOUTH);
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
    String[] names = {"Unicast", "Group", "Broadcast"};
    for (String name : names) {
      JPanel page = new JPanel(new BorderLayout());
      page.setOpaque(false);
      modeTabs.addTab(name, page);
    }
    ((JPanel) modeTabs.getComponentAt(0)).add(chat, BorderLayout.CENTER);
    modeTabs.putClientProperty("JTabbedPane.tabHeight", 44);
    modeTabs.putClientProperty("JTabbedPane.showContentSeparator", false);
    modeTabs.addChangeListener(e -> {
      JPanel page = (JPanel) modeTabs.getSelectedComponent();
      page.add(chat, BorderLayout.CENTER);
      page.revalidate();
      page.repaint();
      if (mode.getSelectedIndex() != modeTabs.getSelectedIndex()) mode.setSelectedIndex(modeTabs.getSelectedIndex());
    });
    chatWorkspace.add(modeTabs, BorderLayout.CENTER);
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
<<<<<<< HEAD
    refresh.addActionListener(e -> { request("GET_USERS", null); request("GET_GROUP", activeGroup); });
=======
    refresh.addActionListener(e -> { request("GET_USERS", null); if (!activeGroup.isEmpty()) request("GET_GROUP", activeGroup); });
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
    reload.addActionListener(e -> refresh.doClick());
    logout.addActionListener(e -> logout());
    join.setEnabled(false);
    leave.setEnabled(false);
    addPeople.setEnabled(false);
    join.addActionListener(e -> request("JOIN_GROUP", activeGroup));
    leave.addActionListener(e -> request("LEAVE_GROUP", activeGroup));
    applyGroup.addActionListener(e -> selectGroup());
    groupName.addActionListener(e -> selectGroup());
    mode.addActionListener(e -> updateDestination());
    recipient.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      public void insertUpdate(javax.swing.event.DocumentEvent e) { updateDestination(); }
      public void removeUpdate(javax.swing.event.DocumentEvent e) { updateDestination(); }
      public void changedUpdate(javax.swing.event.DocumentEvent e) { updateDestination(); }
    });
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

  private void chooseGroupMembers(boolean create) {
    if (connection.isClosed()) return;
    JPanel form = new JPanel(new BorderLayout(0, 12));
    JTextField name = new JTextField(create ? "" : activeGroup);
    name.setEditable(create);
    name.putClientProperty("JTextField.placeholderText", "Tên nhóm: chữ không dấu, số, _ hoặc -");
    form.add(UiStyles.field("Tên nhóm", name), BorderLayout.NORTH);
    JPanel choices = new JPanel();
    choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
    java.util.List<JCheckBox> checks = new ArrayList<>();
    for (int i = 0; i < users.size(); i++) {
      String user = users.get(i);
      if (!create && members.contains(user)) continue;
      JCheckBox check = new JCheckBox(user);
      checks.add(check);
      choices.add(check);
    }
    if (checks.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Chưa có người online phù hợp để thêm vào nhóm.");
      return;
    }
    JScrollPane scroll = new JScrollPane(choices);
    scroll.setPreferredSize(new Dimension(350, 220));
    form.add(scroll, BorderLayout.CENTER);
    form.add(new JLabel(create ? "Bạn và những người được chọn sẽ vào nhóm ngay." : "Người được chọn sẽ vào nhóm ngay."), BorderLayout.SOUTH);
    while (JOptionPane.showConfirmDialog(this, form, create ? "Tạo nhóm" : "Thêm người vào nhóm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
      String group = name.getText().trim().toLowerCase(Locale.ROOT);
      java.util.List<String> selected = checks.stream().filter(JCheckBox::isSelected).map(JCheckBox::getText).toList();
      if (!group.matches("[a-z0-9_-]{1,30}") || selected.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Nhập tên nhóm hợp lệ và chọn ít nhất một người.");
        continue;
      }
      ChatMessage request = ChatMessage.of(create ? "CREATE_GROUP" : "ADD_GROUP_MEMBERS", connection.username, null);
      request.setGroup(group);
      request.setUsers(selected);
      transmit(request);
      return;
    }
  }

  private void selectGroup() {
    String value = groupName.getText().trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[a-z0-9_-]{1,30}")) {
      JOptionPane.showMessageDialog(this, "Tên nhóm cần 1–30 ký tự: chữ không dấu, số, _ hoặc -.");
      return;
    }
    activeGroup = value;
<<<<<<< HEAD
=======
    isGroupCreator = false;
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
    members.clear();
    memberChips.removeAll();
    memberChips.revalidate();
    memberChips.repaint();
    groupStatus = "Đang tải #" + value;
    membership.setText(groupStatus);
    join.setEnabled(false);
    leave.setEnabled(false);
    addPeople.setEnabled(false);
    request("GET_GROUP", value);
    updateDestination();
  }

  private void updateDestination() {
    recipient.setEnabled(mode.getSelectedIndex() == 0 && !connection.isClosed());
    recipient.setVisible(mode.getSelectedIndex() == 0);
    if (modeTabs.getSelectedIndex() != mode.getSelectedIndex()) modeTabs.setSelectedIndex(mode.getSelectedIndex());
    boolean groupMode = mode.getSelectedIndex() == 1;
<<<<<<< HEAD
    join.setVisible(groupMode);
    leave.setVisible(groupMode);
    memberChips.setVisible(groupMode);
    addPeople.setVisible(groupMode);
=======
    if (activeGroup.isEmpty()) groupStatus = "Chọn nhóm trong danh sách hoặc bấm Tạo nhóm.";
    join.setVisible(false);
    leave.setVisible(groupMode);
    memberChips.setVisible(groupMode);
    addPeople.setVisible(groupMode && isGroupCreator);
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
    membership.setText(groupMode ? groupStatus : mode.getSelectedIndex() == 2
        ? "Gửi đến tất cả client đang đăng nhập"
        : recipient.getText().isBlank() ? "Chọn một người trong danh sách bên trái"
        : users.contains(recipient.getText().trim().toLowerCase(Locale.ROOT)) ? "Đang online" : "Đang offline");
    membership.setForeground(UiStyles.MUTED);
    String key = switch (mode.getSelectedIndex()) {
      case 0 -> "private:" + recipient.getText().trim().toLowerCase(Locale.ROOT);
      case 1 -> "group:" + activeGroup;
      default -> "broadcast";
    };
    if (!key.equals(activeConversation)) {
      activeConversation = key;
      bubbles.removeAll();
      for (JPanel row : histories.getOrDefault(key, java.util.List.of())) bubbles.add(row);
      reflowBubbles();
      bubbles.repaint();
      SwingUtilities.invokeLater(() -> bubbleScroll.getVerticalScrollBar().setValue(bubbleScroll.getVerticalScrollBar().getMaximum()));
    }
    chatTitle.setToolTipText(mode.getSelectedIndex() == 1 ? "Nhóm #" + activeGroup : recipient.getText());
    chatTitle.setText(switch (mode.getSelectedIndex()) {
      case 0 -> recipient.getText().isBlank() ? "Chọn người để chat" : recipient.getText().trim();
<<<<<<< HEAD
      case 1 -> "Nhóm #" + activeGroup;
=======
      case 1 -> activeGroup.isEmpty() ? "Chọn hoặc tạo nhóm" : "Nhóm #" + activeGroup;
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
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
      if (user.equals(connection.username)) {
        JOptionPane.showMessageDialog(this, "Hãy chọn một người khác để gửi tin riêng.");
        return;
      }
      message.setRecipient(user);
    }
<<<<<<< HEAD
    if (mode.getSelectedIndex() == 1) message.setGroup(activeGroup);
=======
    if (mode.getSelectedIndex() == 1) {
      if (activeGroup.isEmpty() || !knownGroups.contains(activeGroup)) {
        JOptionPane.showMessageDialog(this, "Hãy chọn nhóm bạn đang tham gia hoặc tạo nhóm mới.");
        return;
      }
      message.setGroup(activeGroup);
    }
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
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
        if (message.getUsers() != null) message.getUsers().stream().filter(user -> !connection.username.equals(user)).forEach(users::addElement);
        updateDestination();
      }
      case "GROUP_STATE" -> {
<<<<<<< HEAD
        if (message.getGroup() != null && !knownGroups.contains(message.getGroup())) knownGroups.addElement(message.getGroup());
        if (activeGroup.equals(message.getGroup())) {
=======
        boolean isMember = message.getJoinedGroups() != null && message.getJoinedGroups().contains(message.getGroup());
        if (!isMember) return;
        if (message.getGroup() != null && !knownGroups.contains(message.getGroup())) knownGroups.addElement(message.getGroup());
        if (activeGroup.equals(message.getGroup())) {
          isGroupCreator = connection.username.equals(message.getGroupCreator());
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
          members.clear();
          if (message.getMembers() != null) message.getMembers().forEach(members::addElement);
          boolean joined =
              message.getJoinedGroups() != null && message.getJoinedGroups().contains(activeGroup);
          groupStatus = (joined ? "Đã Join" : "Ngoài nhóm")
                  + " #"
                  + activeGroup
                  + " • "
                  + members.size()
                  + " người";
          if (mode.getSelectedIndex() == 1) membership.setText(groupStatus);
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
<<<<<<< HEAD
          addPeople.setEnabled(joined && !connection.isClosed());
        }
        if (id != null) pending.remove(id);
      }
=======
          addPeople.setEnabled(joined && isGroupCreator && !connection.isClosed());
          addPeople.setVisible(mode.getSelectedIndex() == 1 && isGroupCreator);
        }
        if (id != null) pending.remove(id);
      }
      case "GROUP_REMOVED" -> {
        String group = message.getGroup();
        knownGroups.removeElement(group);
        histories.remove("group:" + group);
        if (group.equals(activeGroup)) {
          activeGroup = "";
          isGroupCreator = false;
          members.clear();
          memberChips.removeAll();
          groupStatus = "Bạn đã rời nhóm. Chọn hoặc tạo nhóm khác.";
          leave.setEnabled(false);
          addPeople.setEnabled(false);
          updateDestination();
        }
        log("LEAVE", message.getContent());
      }
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
      case "GROUP_ADDED" -> {
        if (!knownGroups.contains(message.getGroup())) knownGroups.addElement(message.getGroup());
        log("GROUP_ADDED", message.getContent());
        appendTo("group:" + message.getGroup(), "HỆ THỐNG", message.getContent());
        status.setText("Được thêm vào #" + message.getGroup());
      }
      case "CREATE_GROUP_OK", "ADD_GROUP_MEMBERS_OK" -> {
        pending.remove(id);
        groupName.setText(message.getGroup());
        selectGroup();
        mode.setSelectedIndex(1);
        log(message.getType(), message.getContent());
      }
      case "JOIN_OK", "LEAVE_OK" -> {
        pending.remove(id);
        log(message.getType(), message.getContent());
        appendTo("group:" + message.getGroup(), "HỆ THỐNG", message.getContent());
      }
      case "ERROR" -> {
        Pending failed = pending.remove(id);
        received.remove(id);
        log("ERROR", message.getContent());
        if (failed != null && ("CREATE_GROUP".equals(failed.message().getType())
            || "ADD_GROUP_MEMBERS".equals(failed.message().getType())))
          JOptionPane.showMessageDialog(this, message.getContent(), "Không thể cập nhật nhóm", JOptionPane.WARNING_MESSAGE);
        appendTo(failed == null ? activeConversation : conversationKey(failed.message()), "LỖI", message.getContent());
        if (failed != null && conversationKey(failed.message()).equals(activeConversation)
            && failed.message().getContent() != null && input.getText().isEmpty())
          input.setText(failed.message().getContent());
      }
      case "ACK" -> {
        Pending sent = pending.remove(id);
        if (sent != null && !received.remove(id)) {
          ChatMessage own = sent.message();
          appendTo(conversationKey(own), "ĐÃ GỬI " + label(own), "Bạn: " + own.getContent());
        }
        log("ACK", message.getContent());
      }
      case "UNICAST", "GROUP_MESSAGE", "BROADCAST" -> {
        if (connection.username.equals(message.getSender()) && pending.containsKey(id))
          received.add(id);
        appendTo(conversationKey(message), label(message), message.getSender() + ": " + message.getContent());
        log(
            "RECEIVE",
            message.getType() + " từ " + message.getSender() + " | " + message.getTimestamp());
      }
      case "SYSTEM" ->
          appendTo(message.getGroup() == null ? "broadcast" : "group:" + message.getGroup(),
              "HỆ THỐNG" + (message.getGroup() == null ? "" : " #" + message.getGroup()), message.getContent());
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
    addPeople.setEnabled(false);
    for (int i = 0; i < modeTabs.getTabCount(); i++) modeTabs.setEnabledAt(i, false);
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

  private String conversationKey(ChatMessage message) {
    return switch (message.getType()) {
      case "UNICAST" -> "private:" + (connection.username.equals(message.getSender())
          ? message.getRecipient() : message.getSender());
      case "GROUP_MESSAGE", "JOIN_GROUP", "LEAVE_GROUP", "GET_GROUP" -> "group:" + message.getGroup();
      default -> "broadcast";
    };
  }

  private void appendTo(String key, String label, String text) {
    append(conversation, label, text, key);
  }

  private void append(JTextArea area, String label, String text) {
    append(area, label, text, activeConversation);
  }

  private void append(JTextArea area, String label, String text, String key) {
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
      java.util.List<JPanel> history = histories.computeIfAbsent(key, ignored -> new ArrayList<>());
      history.add(row);
      if (history.size() > 300) history.remove(0);
      if (key.equals(activeConversation)) {
        bubbles.removeAll();
        for (JPanel item : history) bubbles.add(item);
        reflowBubbles();
        bubbles.repaint();
        SwingUtilities.invokeLater(() -> bubbleScroll.getVerticalScrollBar().setValue(bubbleScroll.getVerticalScrollBar().getMaximum()));
      }
      return;
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
