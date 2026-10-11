package com.tcpgroupchat.client;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientApplication {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(null,
                    "Nhập tên người dùng (2–20 ký tự: chữ, số, _ hoặc -):",
                    "TCPGroupChat - Đăng nhập", JOptionPane.QUESTION_MESSAGE);
            if (username == null || username.trim().isEmpty()) return;
            String host = System.getenv().getOrDefault("CHAT_HOST", "127.0.0.1");
            int port;
            try {
                port = Integer.parseInt(System.getenv().getOrDefault("CHAT_PORT", "2006"));
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 4000);
                ChatFrame frame = new ChatFrame(username.trim(), socket);
                frame.setVisible(true);
                frame.connect();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Không thể kết nối " + host + ":" +
                        System.getenv().getOrDefault("CHAT_PORT", "2006") + "\n" + ex.getMessage(),
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
