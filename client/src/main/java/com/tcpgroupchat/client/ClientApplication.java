package com.tcpgroupchat.client;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientApplication {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(ClientApplication::showLogin);
    }

    private static void showLogin() {
        String username = JOptionPane.showInputDialog(null,
                "Nhập tên người dùng (2–20 ký tự: chữ không dấu, số, _ hoặc -):",
                "TCPGroupChat - Đăng nhập", JOptionPane.QUESTION_MESSAGE);

        if (username == null) return;
        username = username.trim();
        if (!username.matches("[A-Za-z0-9_-]{2,20}")) {
            JOptionPane.showMessageDialog(null,
                    "Tên người dùng không hợp lệ. Hãy nhập 2–20 ký tự: chữ không dấu, số, _ hoặc -.",
                    "Tên người dùng không hợp lệ", JOptionPane.WARNING_MESSAGE);
            showLogin();
            return;
        }

        String host = System.getenv().getOrDefault("CHAT_HOST", "127.0.0.1");
        String portText = System.getenv().getOrDefault("CHAT_PORT", "2006");
        int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) throw new NumberFormatException("Cổng ngoài phạm vi.");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "CHAT_PORT không hợp lệ: " + portText,
                    "Lỗi cấu hình", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 4000);
            ChatFrame frame = new ChatFrame(username, socket);
            frame.setVisible(true);
            frame.connect();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Không thể kết nối tới " + host + ":" + port + "\n" + ex.getMessage(),
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }
}