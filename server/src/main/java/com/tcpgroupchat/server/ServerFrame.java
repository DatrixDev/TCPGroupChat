package com.tcpgroupchat.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ServerFrame extends JFrame {
    private static final Color NAVY = new Color(0x07, 0x1B, 0x41);
    private static final Color PRIMARY = new Color(0x14, 0x77, 0xF8);
    private static final Color BG = new Color(0xF5, 0xF8, 0xFC);
    private static final Color BORDER = new Color(0xD7, 0xE1, 0xEF);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);

    private final ChatServer server;
    private final JLabel state = new JLabel("ĐÃ DỪNG");
    private final JLabel onlineCount = metricValue("0");
    private final JLabel portValue = metricValue("2006");
    private final JLabel clock = new JLabel();
    private final JLabel status = new JLabel("Server chưa khởi động");
    private final JButton start = new JButton("Khởi động Server");
    private final JButton stop = new JButton("Dừng Server");
    private final DefaultTableModel usersModel = new DefaultTableModel(
            new Object[]{"STT", "Tên người dùng", "Trạng thái"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel logsModel = new DefaultTableModel(
            new Object[]{"Nhật ký hoạt động"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public ServerFrame(ChatServer server) {
        super("TCPGroupChat | Server Dashboard");
        this.server = server;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1180, 780);
        setMinimumSize(new Dimension(940, 650));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(20, 22, 0, 22));
        setContentPane(root);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        start.addActionListener(event -> startServer());
        stop.addActionListener(event -> server.stop());
        stop.setEnabled(false);

        Timer timer = new Timer(700, event -> refreshDashboard());
        timer.start();
        refreshDashboard();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);

        JPanel branding = new JPanel();
        branding.setOpaque(false);
        branding.setLayout(new BoxLayout(branding, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("TCPGroupChat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(NAVY);
        JLabel subtitle = new JLabel("Bảng điều khiển máy chủ • TCP Client–Server");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        branding.add(title);
        branding.add(Box.createVerticalStrut(4));
        branding.add(subtitle);
        branding.add(Box.createVerticalStrut(10));
        state.setOpaque(true);
        state.setFont(new Font("Segoe UI", Font.BOLD, 12));
        state.setBorder(new EmptyBorder(7, 12, 7, 12));
        styleState(false);
        state.setAlignmentX(Component.LEFT_ALIGNMENT);
        branding.add(state);
        header.add(branding, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 4));
        controls.setOpaque(false);
        controls.add(infoCard("ĐỊA CHỈ IP", localIp()));
        controls.add(infoCard("CỔNG TCP", "2006"));
        styleButton(start, PRIMARY, Color.WHITE);
        styleButton(stop, new Color(0xEF, 0x33, 0x40), Color.WHITE);
        controls.add(start);
        controls.add(stop);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JComponent buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        JPanel metrics = new JPanel(new GridLayout(1, 2, 14, 0));
        metrics.setOpaque(false);
        metrics.add(metricCard("CLIENT ĐANG ONLINE", onlineCount, "Số kết nối đã đăng nhập"));
        metrics.add(metricCard("CỔNG ĐANG CẤU HÌNH", portValue, "Cổng giao tiếp TCP"));
        content.add(metrics, BorderLayout.NORTH);

        JPanel lower = new JPanel(new GridLayout(1, 2, 14, 0));
        lower.setOpaque(false);
        lower.add(tableCard("Người dùng đang kết nối", usersModel));
        lower.add(tableCard("Nhật ký hoạt động", logsModel));
        content.add(lower, BorderLayout.CENTER);
        return content;
    }

    private JComponent tableCard(String title, DefaultTableModel model) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(14, 14, 14, 14)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 17));
        heading.setForeground(NAVY);
        card.add(heading, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setForeground(NAVY);
        table.setGridColor(BORDER);
        table.setSelectionBackground(new Color(0xE5, 0xF1, 0xFF));
        table.getTableHeader().setPreferredSize(new Dimension(10, 34));
        table.getTableHeader().setBackground(new Color(0xEA, 0xF3, 0xFE));
        table.getTableHeader().setForeground(NAVY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    private JComponent metricCard(String heading, JLabel value, String description) {
        JPanel card = new JPanel(new BorderLayout(12, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 18, 16, 18)));
        JLabel title = new JLabel(heading);
        title.setForeground(MUTED);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        value.setForeground(new Color(0x08, 0x47, 0xA6));
        value.setFont(new Font("Segoe UI", Font.BOLD, 30));
        JLabel desc = new JLabel(description);
        desc.setForeground(MUTED);
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(title);
        words.add(Box.createVerticalStrut(5));
        words.add(value);
        words.add(desc);
        card.add(words, BorderLayout.CENTER);
        return card;
    }

    private JComponent infoCard(String title, String value) {
        JPanel card = new JPanel(new BorderLayout(0, 3));
        card.setBackground(new Color(0xF0, 0xF6, 0xFE));
        card.setBorder(new EmptyBorder(8, 11, 8, 11));
        card.setPreferredSize(new Dimension(120, 54));
        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        label.setForeground(MUTED);
        JLabel data = new JLabel(value);
        data.setFont(new Font("Segoe UI", Font.BOLD, 14));
        data.setForeground(NAVY);
        card.add(label, BorderLayout.NORTH);
        card.add(data, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(10, 2, 10, 2)));
        status.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        status.setForeground(NAVY);
        clock.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clock.setForeground(MUTED);
        bar.add(status, BorderLayout.WEST);
        bar.add(clock, BorderLayout.EAST);
        return bar;
    }

    private void startServer() {
        start.setEnabled(false);
        status.setText("Đang khởi động server...");
        Thread thread = new Thread(() -> {
            try {
                server.start(2006);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Không thể khởi động server tại cổng 2006.\n" + ex.getMessage(),
                            "Lỗi khởi động Server", JOptionPane.ERROR_MESSAGE);
                    status.setText("Khởi động thất bại: " + ex.getMessage());
                    refreshDashboard();
                });
            }
        }, "tcp-server-main");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshDashboard() {
        boolean running = server.isRunning();
        start.setEnabled(!running);
        stop.setEnabled(running);
        styleState(running);
        onlineCount.setText(String.valueOf(server.getOnlineUsers().size()));
        portValue.setText("2006");
        status.setText(running ? "TCP Server đang lắng nghe và chờ client kết nối" : "Server chưa khởi động");
        clock.setText("Thời gian hệ thống: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        usersModel.setRowCount(0);
        int index = 1;
        for (String username : server.getOnlineUsers()) {
            usersModel.addRow(new Object[]{index++, username, "Online"});
        }
        logsModel.setRowCount(0);
        for (String line : server.getRecentLogs().reversed()) {
            logsModel.addRow(new Object[]{line});
        }
    }

    private void styleState(boolean running) {
        state.setText(running ? "ĐANG HOẠT ĐỘNG" : "ĐÃ DỪNG");
        state.setBackground(running ? new Color(0xE5, 0xF8, 0xEC) : new Color(0xFD, 0xEB, 0xED));
        state.setForeground(running ? new Color(0x11, 0x86, 0x3C) : new Color(0xEF, 0x33, 0x40));
    }

    private static JLabel metricValue(String text) {
        return new JLabel(text);
    }

    private static void styleButton(JButton button, Color background, Color foreground) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(11, 15, 11, 15));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static String localIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }
}