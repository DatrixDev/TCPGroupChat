package com.tcpgroupchat.server;

import com.tcpgroupchat.common.ui.IconManager;
import com.tcpgroupchat.common.ui.UiStyles;
import java.awt.*;
import com.tcpgroupchat.common.net.LocalAddresses;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

public final class ServerFrame extends JFrame {
  private final ChatServer server;
  private final JLabel state = new JLabel("ĐÃ DỪNG"), status = new JLabel(), clock = new JLabel();
  private final JLabel[] values = new JLabel[3];
  private final JButton start = UiStyles.button("Khởi động", "play", true),
      stop = UiStyles.button("Dừng server", "square", false);
  private final JSpinner port;
  private final JComboBox<LocalAddresses.Address> addresses =
      new JComboBox<>(LocalAddresses.available().toArray(LocalAddresses.Address[]::new));
  private final DefaultTableModel sessions =
      model(
          "Tài khoản",
          "IP : Cổng client",
          "Đăng nhập",
          "Đăng xuất",
          "Trạng thái",
          "Nhóm",
          "Mã phiên");
  private final DefaultTableModel groups = model("Nhóm", "IP Multicast", "Cổng UDP", "Thành viên", "Danh sách nhận tin");
  private final DefaultTableModel logs = model("Thời gian", "Sự kiện", "Thông tin");
  private final JTable sessionTable = table(sessions), logTable = table(logs);
  private final JTextField search = new JTextField(12);
  private final JComboBox<String> filter =
      new JComboBox<>(
          new String[] {
            "Tất cả",
            "LOGIN",
            "LOGOUT",
            "REGISTER",
            "JOIN",
            "LEAVE",
            "UNICAST",
            "GROUP_MESSAGE",
            "BROADCAST",
            "ERROR",
            "REJECT"
          });
  private final TableRowSorter<DefaultTableModel> logSorter = new TableRowSorter<>(logs);
  private boolean starting, stopping, refreshing;
  private final Timer timer;
  private String snapshotKey = "";

  public ServerFrame(ChatServer server) {
    super("TCPGroupChat | Server Dashboard");
    this.server = server;
    int initialPort = 2006;
    try {
      initialPort =
          Integer.parseInt(
              System.getProperty("chat.port", System.getenv().getOrDefault("CHAT_PORT", "2006")));
    } catch (NumberFormatException ignored) {
    }
    if (initialPort < 1 || initialPort > 65535) initialPort = 2006;
    if (server.isRunning()) initialPort = server.getPort();
    port = new JSpinner(new SpinnerNumberModel(initialPort, 1, 65535, 1));
    port.setEditor(new JSpinner.NumberEditor(port, "0"));
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(1250, 900);
    setMinimumSize(new Dimension(1200, 800));
    setLocationRelativeTo(null);
    setIconImage(IconManager.image("network", 32, 32, UiStyles.PRIMARY));
    JPanel root = new JPanel(new BorderLayout(0, 16));
    root.setBackground(UiStyles.BACKGROUND);
    root.setBorder(new EmptyBorder(20, 20, 14, 20));
    setContentPane(root);
    root.add(header(), BorderLayout.NORTH);
    JPanel center = new JPanel(new BorderLayout(0, 14));
    center.setOpaque(false);
    center.add(metrics(), BorderLayout.NORTH);
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sessionCard(), logCard());
    split.setResizeWeight(.63);
    split.setDividerSize(12);
    split.setBorder(null);
    split.setOpaque(false);
    center.add(split, BorderLayout.CENTER);
    root.add(center, BorderLayout.CENTER);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(Color.WHITE);
    bottom.setBorder(new EmptyBorder(10, 12, 10, 12));
    status.setForeground(UiStyles.NAVY);
    clock.setForeground(UiStyles.MUTED);
    bottom.add(status, BorderLayout.WEST);
    bottom.add(clock, BorderLayout.EAST);
    root.add(bottom, BorderLayout.SOUTH);
    start.addActionListener(e -> startServer());
    stop.addActionListener(e -> stopServer());
    stop.setEnabled(false);
    timer = new Timer(700, e -> refresh());
    timer.start();
    refresh();
    addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            timer.stop();
            Thread t = new Thread(server::stop, "close-server");
            t.setDaemon(true);
            t.start();
          }
        });
  }

  private JPanel header() {
    JPanel panel = new JPanel(new BorderLayout(16, 0));
    panel.setOpaque(false);
    JPanel titleBlock = new JPanel(new BorderLayout(0, 6));
    titleBlock.setOpaque(false);
    JLabel title = UiStyles.title("TCP CHAT SERVER", 27);
    title.setIcon(IconManager.load("network", 44, 44, UiStyles.PRIMARY));
    title.setIconTextGap(14);
    titleBlock.add(title, BorderLayout.NORTH);
    JLabel subtitle = new JLabel("Quản lý kết nối và nhóm chat");
    subtitle.setForeground(UiStyles.MUTED);
    titleBlock.add(subtitle, BorderLayout.SOUTH);
    panel.add(titleBlock, BorderLayout.WEST);
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.setOpaque(false);
    state.setOpaque(true);
    state.setBorder(new EmptyBorder(14, 10, 14, 10));
    state.setFont(new Font("Segoe UI", Font.BOLD, 12));
    controls.add(state);
    JPanel ip = UiStyles.card(new BorderLayout(0, 4));
    ip.setBorder(new EmptyBorder(8, 12, 8, 12));
    ip.add(new JLabel("Địa chỉ IP"), BorderLayout.NORTH);
    addresses.setPreferredSize(new Dimension(140, 30));
    addresses.setFont(new Font("Segoe UI", Font.BOLD, 14));
    addresses.addActionListener(e -> {
      LocalAddresses.Address selected = (LocalAddresses.Address) addresses.getSelectedItem();
      addresses.setToolTipText(selected == null ? "" : selected.adapter());
      refresh();
    });
    addresses.setToolTipText(((LocalAddresses.Address) addresses.getSelectedItem()).adapter());
    ip.add(addresses, BorderLayout.CENTER);
    controls.add(ip);
    JPanel portCard = UiStyles.card(new BorderLayout(0, 4));
    portCard.setBorder(new EmptyBorder(5, 10, 5, 10));
    portCard.add(new JLabel("Port"), BorderLayout.NORTH);
    port.setPreferredSize(new Dimension(85, 28));
    portCard.add(port, BorderLayout.CENTER);
    controls.add(portCard);
    start.setPreferredSize(new Dimension(145, 50));
    stop.setPreferredSize(new Dimension(155, 50));
    stop.setBackground(UiStyles.ERROR);
    stop.setForeground(Color.WHITE);
    stop.setIcon(IconManager.load("square", 18, 18, Color.WHITE));
    controls.add(start);
    controls.add(stop);
    panel.add(controls, BorderLayout.EAST);
    return panel;
  }

  private JPanel metrics() {
    JPanel panel = new JPanel(new GridLayout(1, 3, 14, 0));
    panel.setOpaque(false);
    String[] labels = {"Tài khoản", "Client online", "Lượt tham gia nhóm"};
    String[] descriptions = {"Tổng số tài khoản người dùng", "Số client đang kết nối", "Tổng membership của các nhóm"};
    String[] icons = {"users", "monitor", "network"};
    for (int i = 0; i < 3; i++) {
      JPanel card = UiStyles.card(new BorderLayout(18, 0));
      JLabel icon = new JLabel(IconManager.load(icons[i], 38, 38, i == 1 ? UiStyles.SUCCESS : UiStyles.PRIMARY));
      icon.setOpaque(true);
      icon.setBackground(new Color(0xE7F2FF));
      icon.setPreferredSize(new Dimension(68, 68));
      icon.setHorizontalAlignment(SwingConstants.CENTER);
      card.add(icon, BorderLayout.WEST);
      JPanel text = new JPanel(new BorderLayout(0, 8));
      text.setOpaque(false);
      JPanel main = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
      main.setOpaque(false);
      values[i] = UiStyles.title("0", 30);
      main.add(values[i]);
      main.add(UiStyles.title(labels[i], 16));
      text.add(main, BorderLayout.CENTER);
      JLabel description = new JLabel(descriptions[i]);
      description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
      description.setForeground(UiStyles.MUTED);
      text.add(description, BorderLayout.SOUTH);
      card.add(text, BorderLayout.CENTER);
      panel.add(card);
    }
    return panel;
  }

  private JPanel sessionCard() {
    JPanel panel = UiStyles.card(new BorderLayout(0, 10));
    JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    heading.add(sectionTitle("Danh sách người dùng", "users"), BorderLayout.WEST);
    JButton kick = UiStyles.button("Ngắt phiên đã chọn", "log-out", false);
    kick.addActionListener(
        e -> {
          int row = sessionTable.getSelectedRow();
          if (row < 0) return;
          row = sessionTable.convertRowIndexToModel(row);
          String id = (String) sessions.getValueAt(row, 6);
          new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
              server.disconnectSession(id);
              return null;
            }

            protected void done() {
              refresh();
            }
          }.execute();
        });
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    actions.setOpaque(false);
    JButton roomList = UiStyles.button("Nhóm", "network", false);
    roomList.addActionListener(e -> JOptionPane.showMessageDialog(this, new JScrollPane(table(groups)), "Phòng / Nhóm", JOptionPane.PLAIN_MESSAGE));
    JButton reload = UiStyles.button("Làm mới", "refresh-cw", false);
    reload.addActionListener(e -> refresh());
    actions.add(roomList);
    actions.add(kick);
    actions.add(reload);
    heading.add(actions, BorderLayout.EAST);
    panel.add(heading, BorderLayout.NORTH);
    sessionTable.setAutoCreateRowSorter(true);
    sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    sessionTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    int[] widths = {100, 160, 165, 165, 80, 100, 75};
    for (int i = 0; i < widths.length; i++)
      sessionTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    sessionTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, selected, focus, row, column);
        String id = String.valueOf(value);
        label.setText(id.length() > 8 ? id.substring(0, 8) : id);
        label.setToolTipText(id);
        return label;
      }
    });
    sessionTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, selected, focus, row, column);
        if (!selected) label.setForeground("Online".equals(value) ? UiStyles.SUCCESS : UiStyles.MUTED);
        return label;
      }
    });
    panel.add(new JScrollPane(sessionTable));
    JPanel groupNetwork = new JPanel(new BorderLayout(0, 6));
    groupNetwork.setOpaque(false);
    groupNetwork.add(UiStyles.title("Địa chỉ IP các nhóm Multicast", 14), BorderLayout.NORTH);
    groupNetwork.add(new JScrollPane(table(groups)), BorderLayout.CENTER);
    groupNetwork.setPreferredSize(new Dimension(10, 105));
    panel.add(groupNetwork, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel logCard() {
    JPanel panel = UiStyles.card(new BorderLayout(0, 10));
    JPanel heading = new JPanel(new BorderLayout(10, 0));
    heading.setOpaque(false);
    heading.add(sectionTitle("Nhật ký hoạt động", "file-text"), BorderLayout.WEST);
    JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    tools.setOpaque(false);
    search.putClientProperty("JTextField.placeholderText", "Tìm tài khoản, tin nhắn, IP…");
    tools.add(search);
    tools.add(filter);
    JButton export = UiStyles.button("Xuất log", "file-text", false);
    export.addActionListener(e -> exportLogs());
    tools.add(export);
    heading.add(tools, BorderLayout.EAST);
    panel.add(heading, BorderLayout.NORTH);
    logTable.setRowSorter(logSorter);
    logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    logTable.getColumnModel().getColumn(0).setPreferredWidth(180);
    logTable.getColumnModel().getColumn(0).setMinWidth(180);
    logTable.getColumnModel().getColumn(1).setPreferredWidth(150);
    logTable.getColumnModel().getColumn(1).setMinWidth(150);
    logTable.getColumnModel().getColumn(2).setPreferredWidth(800);
    panel.add(new JScrollPane(logTable), BorderLayout.CENTER);
    filter.addActionListener(e -> filterLogs());
    search
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterLogs();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterLogs();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterLogs();
              }
            });
    return panel;
  }

  private void filterLogs() {
    java.util.List<RowFilter<DefaultTableModel, Integer>> filters = new java.util.ArrayList<>();
    if (!search.getText().isBlank())
      filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(search.getText())));
    if (filter.getSelectedIndex() != 0)
      filters.add(
          RowFilter.regexFilter("^" + Pattern.quote((String) filter.getSelectedItem()) + "$", 1));
    logSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
  }

  private void startServer() {
    if (starting || stopping || server.isRunning()) return;
    starting = true;
    start.setEnabled(false);
    port.setEnabled(false);
    status.setText("Đang khởi động…");
    int selected = (Integer) port.getValue();
    Thread thread =
        new Thread(
            () -> {
              try {
                server.configureUdp(localIp());
                server.start(selected);
              } catch (Exception e) {
                SwingUtilities.invokeLater(
                    () ->
                        JOptionPane.showMessageDialog(
                            this,
                            e.getMessage(),
                            "Không thể khởi động",
                            JOptionPane.ERROR_MESSAGE));
              } finally {
                SwingUtilities.invokeLater(
                    () -> {
                      starting = false;
                      refresh();
                    });
              }
            },
            "tcp-accept");
    thread.setDaemon(true);
    thread.start();
  }

  private void stopServer() {
    stopping = true;
    stop.setEnabled(false);
    start.setEnabled(false);
    new SwingWorker<Void, Void>() {
      protected Void doInBackground() {
        server.stop();
        return null;
      }

      protected void done() {
        stopping = false;
        refresh();
      }
    }.execute();
  }

  private void refresh() {
    boolean running = server.isRunning();
    if (running) starting = false;
    start.setEnabled(!running && !starting && !stopping);
    stop.setEnabled(running && !stopping);
    port.setEnabled(!running && !starting && !stopping);
    addresses.setEnabled(!running && !starting && !stopping);
    state.setText(running ? "ĐANG HOẠT ĐỘNG" : starting ? "ĐANG KHỞI ĐỘNG" : "ĐÃ DỪNG");
    state.setForeground(running ? UiStyles.SUCCESS : UiStyles.ERROR);
    state.setBackground(running ? new Color(0xE5F8EC) : new Color(0xFDEBED));
    status.setText(
        running
            ? "TCP " + localIp() + ":" + server.getPort() + "  ·  UDP " + server.getUdpPort()
                + "  ·  Broadcast " + server.getBroadcastIp() + ":" + server.getGroupPort()
            : starting
                ? "Đang khởi động server…"
                : "Server đã dừng • Dữ liệu tài khoản và log được lưu");
    clock.setText(
        LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
    if (refreshing || !isDisplayable()) return;
    refreshing = true;
    new SwingWorker<Object[], Void>() {
      protected Object[] doInBackground() {
        return new Object[] {
          server.getStats(), server.getSessions(), server.getGroups(), server.getLogs()
        };
      }

      @SuppressWarnings("unchecked")
      protected void done() {
        try {
          Object[] result = get();
          ChatServer.Stats stats = (ChatServer.Stats) result[0];
          long[] counts = {
            stats.accounts(),
            stats.online(),
            stats.members(),
            stats.unicast(),
            stats.group(),
            stats.broadcast()
          };
          for (int i = 0; i < 3; i++) values[i].setText(String.valueOf(counts[i]));
          String key = result[1].toString() + result[2] + result[3];
          if (key.equals(snapshotKey)) return;
          snapshotKey = key;
          String selectedId = null;
          int selected = sessionTable.getSelectedRow();
          if (selected >= 0)
            selectedId =
                (String) sessions.getValueAt(sessionTable.convertRowIndexToModel(selected), 6);
          sessions.setRowCount(0);
          for (ChatServer.SessionInfo s : (List<ChatServer.SessionInfo>) result[1])
            sessions.addRow(
                new Object[] {
                  s.username(),
                  s.address(),
                  UiStyles.time(s.loginTime()),
                  UiStyles.time(s.logoutTime()),
                  s.online() ? "Online" : "Offline",
                  s.groups(),
                  s.id()
                });
          if (selectedId != null)
            for (int i = 0; i < sessions.getRowCount(); i++)
              if (selectedId.equals(sessions.getValueAt(i, 6))) {
                int view = sessionTable.convertRowIndexToView(i);
                sessionTable.setRowSelectionInterval(view, view);
                break;
              }
          groups.setRowCount(0);
          for (ChatServer.GroupInfo g : (List<ChatServer.GroupInfo>) result[2])
            groups.addRow(new Object[] {g.name(), g.ip(), g.port(), g.count(), g.members()});
          logs.setRowCount(0);
          List<ChatServer.LogEntry> entries = (List<ChatServer.LogEntry>) result[3];
          for (int i = entries.size() - 1; i >= 0; i--) {
            ChatServer.LogEntry log = entries.get(i);
            logs.addRow(new Object[] {UiStyles.time(log.time()), log.event(), log.information()});
          }
        } catch (Exception e) {
          status.setText("Không thể cập nhật dashboard: " + e.getMessage());
        } finally {
          refreshing = false;
        }
      }
    }.execute();
  }

  private void exportLogs() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("tcp-chat-log.txt"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    var path = chooser.getSelectedFile().toPath();
    new SwingWorker<Void, Void>() {
      protected Void doInBackground() throws Exception {
        Files.write(path, server.getRecentLogs(), StandardCharsets.UTF_8);
        return null;
      }

      protected void done() {
        try {
          get();
          JOptionPane.showMessageDialog(ServerFrame.this, "Đã xuất 500 dòng log gần nhất.");
        } catch (Exception e) {
          JOptionPane.showMessageDialog(
              ServerFrame.this, e.getMessage(), "Không thể xuất log", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private static DefaultTableModel model(String... columns) {
    return new DefaultTableModel(columns, 0) {
      public boolean isCellEditable(int row, int col) {
        return false;
      }
    };
  }

  private static JTable table(DefaultTableModel model) {
    JTable table =
        new JTable(model) {
          @Override
          public String getToolTipText(java.awt.event.MouseEvent event) {
            int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
            return row < 0 || column < 0 ? null : String.valueOf(getValueAt(row, column));
          }
        };
    table.setRowHeight(36);
    table.setShowVerticalLines(false);
    table.setIntercellSpacing(new Dimension(0, 1));
    table.getTableHeader().setBackground(new Color(0xE7F2FF));
    table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    table.setGridColor(UiStyles.BORDER);
    table.setForeground(UiStyles.NAVY);
    table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
    table.getTableHeader().setPreferredSize(new Dimension(10, 34));
    return table;
  }

  private static JLabel sectionTitle(String text, String icon) {
    JLabel label = UiStyles.title(text, 20);
    label.setIcon(IconManager.load(icon, 25, 25, UiStyles.PRIMARY));
    label.setIconTextGap(12);
    return label;
  }

  private String localIp() {
    LocalAddresses.Address selected = (LocalAddresses.Address) addresses.getSelectedItem();
    return selected == null ? "127.0.0.1" : selected.ip();
  }
}
