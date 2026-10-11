package com.tcpgroupchat.server;

import com.tcpgroupchat.common.ui.IconManager;
import com.tcpgroupchat.common.ui.UiStyles;
import java.awt.*;
import java.net.InetAddress;
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
  private final JLabel[] values = new JLabel[6];
  private final JButton start = UiStyles.button("Khởi động", "play", true),
      stop = UiStyles.button("Dừng Server", "square", false);
  private final JSpinner port;
  private final DefaultTableModel sessions =
      model(
          "Tài khoản",
          "IP : Cổng client",
          "Đăng nhập",
          "Đăng xuất",
          "Trạng thái",
          "Nhóm",
          "Mã phiên");
  private final DefaultTableModel groups = model("Nhóm", "Thành viên", "Danh sách nhận tin");
  private final DefaultTableModel logs = model("Thời gian", "Sự kiện", "Thông tin");
  private final JTable sessionTable = table(sessions), logTable = table(logs);
  private final JTextField search = new JTextField(24);
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
    port = new JSpinner(new SpinnerNumberModel(initialPort, 1, 65535, 1));
    port.setEditor(new JSpinner.NumberEditor(port, "0"));
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(1320, 900);
    setMinimumSize(new Dimension(1100, 780));
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
    JTabbedPane topTabs = new JTabbedPane();
    topTabs.addTab(
        "Phiên đăng nhập", IconManager.load("users", 18, 18, UiStyles.PRIMARY), sessionCard());
    JPanel groupCard = UiStyles.card(new BorderLayout());
    groupCard.add(new JScrollPane(table(groups)));
    topTabs.addTab(
        "Phòng / Nhóm", IconManager.load("network", 18, 18, UiStyles.PRIMARY), groupCard);
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topTabs, logCard());
    split.setResizeWeight(.46);
    split.setBorder(null);
    center.add(split, BorderLayout.CENTER);
    root.add(center, BorderLayout.CENTER);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setOpaque(false);
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
    JPanel panel = new JPanel(new BorderLayout(0, 12));
    panel.setOpaque(false);
    JLabel title = UiStyles.title("TCPGroupChat Server", 29);
    title.setIcon(IconManager.load("network", 38, 38, UiStyles.PRIMARY));
    title.setIconTextGap(12);
    panel.add(title, BorderLayout.WEST);
    state.setOpaque(true);
    state.setBorder(new EmptyBorder(9, 14, 9, 14));
    panel.add(state, BorderLayout.EAST);
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    controls.setOpaque(false);
    controls.add(new JLabel("TCP Client–Server    •    IP LAN: " + localIp()));
    controls.add(new JLabel("Cổng:"));
    port.setPreferredSize(new Dimension(90, 40));
    controls.add(port);
    controls.add(start);
    controls.add(stop);
    JButton refresh = UiStyles.button("Làm mới", "refresh-cw", false);
    refresh.addActionListener(e -> refresh());
    controls.add(refresh);
    panel.add(controls, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel metrics() {
    JPanel panel = new JPanel(new GridLayout(1, 6, 10, 0));
    panel.setOpaque(false);
    String[] labels = {
      "TÀI KHOẢN", "CLIENT ONLINE", "LƯỢT JOIN HIỆN TẠI", "UNICAST", "GROUP", "BROADCAST"
    };
    String[] icons = {"users", "monitor", "network", "user", "message-square", "ethernet-port"};
    for (int i = 0; i < 6; i++) {
      JPanel card = UiStyles.card(new BorderLayout(0, 8));
      JLabel name = new JLabel(labels[i]);
      name.setForeground(UiStyles.MUTED);
      name.setFont(name.getFont().deriveFont(Font.BOLD, 11f));
      card.add(name, BorderLayout.NORTH);
      values[i] = UiStyles.title("0", 30);
      values[i].setIcon(IconManager.load(icons[i], 28, 28, UiStyles.PRIMARY));
      values[i].setIconTextGap(10);
      card.add(values[i], BorderLayout.CENTER);
      panel.add(card);
    }
    return panel;
  }

  private JPanel sessionCard() {
    JPanel panel = UiStyles.card(new BorderLayout(0, 10));
    JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    heading.add(UiStyles.title("Phiên online và lịch sử đăng nhập", 17), BorderLayout.WEST);
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
    heading.add(kick, BorderLayout.EAST);
    panel.add(heading, BorderLayout.NORTH);
    sessionTable.setAutoCreateRowSorter(true);
    sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    sessionTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    int[] widths = {100, 155, 175, 175, 85, 100, 140};
    for (int i = 0; i < widths.length; i++) {
      sessionTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
      sessionTable.getColumnModel().getColumn(i).setMinWidth(widths[i]);
    }
    sessionTable
        .getColumnModel()
        .getColumn(6)
        .setCellRenderer(
            new DefaultTableCellRenderer() {
              @Override
              public Component getTableCellRendererComponent(
                  JTable table,
                  Object value,
                  boolean selected,
                  boolean focus,
                  int row,
                  int column) {
                JLabel label =
                    (JLabel)
                        super.getTableCellRendererComponent(
                            table, value, selected, focus, row, column);
                String id = String.valueOf(value);
                label.setText(id.length() > 8 ? id.substring(0, 8) : id);
                label.setToolTipText(id);
                return label;
              }
            });
    panel.add(new JScrollPane(sessionTable));
    return panel;
  }

  private JPanel logCard() {
    JPanel panel = UiStyles.card(new BorderLayout(0, 10));
    JPanel heading = new JPanel(new BorderLayout(10, 0));
    heading.setOpaque(false);
    heading.add(UiStyles.title("Nhật ký hoạt động", 18), BorderLayout.WEST);
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
    state.setText(running ? "ĐANG HOẠT ĐỘNG" : starting ? "ĐANG KHỞI ĐỘNG" : "ĐÃ DỪNG");
    state.setForeground(running ? UiStyles.SUCCESS : UiStyles.ERROR);
    state.setBackground(running ? new Color(0xE5F8EC) : new Color(0xFDEBED));
    status.setText(
        running
            ? "TCP Server lắng nghe cổng " + server.getPort()
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
          for (int i = 0; i < 6; i++) values[i].setText(String.valueOf(counts[i]));
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
            groups.addRow(new Object[] {g.name(), g.count(), g.members()});
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
    table.setRowHeight(32);
    table.setGridColor(UiStyles.BORDER);
    table.setForeground(UiStyles.NAVY);
    table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
    table.getTableHeader().setPreferredSize(new Dimension(10, 34));
    return table;
  }

  private static String localIp() {
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      return "127.0.0.1";
    }
  }
}
