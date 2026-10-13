package com.tcpgroupchat.client;

import com.tcpgroupchat.common.ui.IconManager;
import com.tcpgroupchat.common.ui.UiStyles;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class LoginFrame extends JFrame {
  private final JTextField user = new JTextField(),
      host =
          new JTextField(
              System.getProperty(
                  "chat.host", System.getenv().getOrDefault("CHAT_HOST", "127.0.0.1")));
  private final JPasswordField password = new JPasswordField(), confirm = new JPasswordField();
  private final JTextField port =
      new JTextField(
          System.getProperty("chat.port", System.getenv().getOrDefault("CHAT_PORT", "2006")));
  private final JButton submit = UiStyles.button("Đăng nhập", "log-in", true);
  private final JButton toggle = new JButton("Chưa có tài khoản? Đăng ký");
  private final JPanel confirmField = UiStyles.field("Nhập lại mật khẩu", confirm);
  private final JLabel title = UiStyles.title("Đăng nhập", 32), feedback = new JLabel(" ");
  private boolean registering;
  private ClientConnection pending;

  public LoginFrame() {
    super("TCPGroupChat | Đăng nhập");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(1000, 700);
    setMinimumSize(new Dimension(900, 650));
    setLocationRelativeTo(null);
    setIconImage(IconManager.image("message-square", 32, 32, UiStyles.PRIMARY));
    JPanel root = new JPanel(new GridLayout(1, 2));
    setContentPane(root);
    JPanel brand = new JPanel(new GridBagLayout());
    brand.setBackground(new Color(0x082E63));
    JPanel words = new JPanel();
    words.setOpaque(false);
    words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
    words.setBorder(new EmptyBorder(36, 36, 36, 36));
    JLabel icon = new JLabel(IconManager.load("message-square", 64, 64, Color.WHITE));
    words.add(icon);
    JLabel name = new JLabel("TCPGroupChat");
    name.setFont(new Font("Segoe UI", Font.BOLD, 34));
    name.setForeground(Color.WHITE);
    words.add(Box.createVerticalStrut(24));
    words.add(name);
    for (String text :
        new String[] {
          "Kết nối • Trò chuyện • Chia sẻ",
                }) {
      JLabel label = new JLabel(text);
      label.setForeground(new Color(0xDBEAFE));
      label.setBorder(new EmptyBorder(7, 0, 7, 0));
      words.add(label);
    }
    brand.add(words);
    root.add(brand);
    JPanel right = new JPanel(new GridBagLayout());
    right.setBackground(Color.WHITE);
    right.setBorder(new EmptyBorder(25, 35, 25, 35));
    JPanel form = new JPanel();
    form.setOpaque(false);
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setPreferredSize(new Dimension(400, 580));
    form.add(title);
    form.add(Box.createVerticalStrut(18));
    addField(form, UiStyles.field("Tên đăng nhập (2–20 ký tự)", user));
    addField(form, UiStyles.field("Mật khẩu (4–128 ký tự)", password));
    addField(form, confirmField);
    confirmField.setVisible(false);
    JPanel address = new JPanel(new GridLayout(1, 2, 12, 0));
    address.setOpaque(false);
    address.add(UiStyles.field("IP / Host Server", host));
    address.add(UiStyles.field("Cổng TCP", port));
    addField(form, address);
    feedback.setForeground(UiStyles.ERROR);
    feedback.setAlignmentX(LEFT_ALIGNMENT);
    form.add(feedback);
    form.add(Box.createVerticalStrut(12));
    submit.setAlignmentX(LEFT_ALIGNMENT);
    submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
    form.add(submit);
    toggle.setForeground(UiStyles.PRIMARY);
    toggle.setBorderPainted(false);
    toggle.setContentAreaFilled(false);
    toggle.setAlignmentX(LEFT_ALIGNMENT);
    form.add(Box.createVerticalStrut(12));
    form.add(toggle);
    right.add(form);
    root.add(right);
    user.putClientProperty(
        "JTextField.leadingIcon", IconManager.load("user", 18, 18, UiStyles.MUTED));
    password.putClientProperty("JTextField.showRevealButton", true);
    confirm.putClientProperty("JTextField.showRevealButton", true);
    submit.addActionListener(e -> submit());
    getRootPane().setDefaultButton(submit);
    toggle.addActionListener(
        e -> {
          registering = !registering;
          title.setText(registering ? "Tạo tài khoản" : "Đăng nhập");
          submit.setText(registering ? "Đăng ký" : "Đăng nhập");
          confirmField.setVisible(registering);
          toggle.setText(registering ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký");
          feedback.setText(" ");
          form.revalidate();
        });
    addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            if (pending != null) pending.close();
          }
        });
  }

  private static void addField(JPanel form, JPanel field) {
    field.setAlignmentX(LEFT_ALIGNMENT);
    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
    form.add(field);
    form.add(Box.createVerticalStrut(14));
  }

  private void submit() {
    String name = user.getText().trim(),
        hostname = host.getText().trim(),
        pass = new String(password.getPassword());
    final int portNumber;
    try {
      if (!name.matches("[A-Za-z0-9_-]{2,20}"))
        throw new IllegalArgumentException("Tên đăng nhập không hợp lệ.");
      if (pass.length() < 4 || pass.length() > 128)
        throw new IllegalArgumentException("Mật khẩu cần 4–128 ký tự.");
      if (registering && !pass.equals(new String(confirm.getPassword())))
        throw new IllegalArgumentException("Mật khẩu nhập lại chưa khớp.");
      if (hostname.isBlank()) throw new IllegalArgumentException("Hãy nhập IP/Host server.");
      portNumber = Integer.parseInt(port.getText().trim());
      if (portNumber < 1 || portNumber > 65535)
        throw new IllegalArgumentException("Cổng cần từ 1 đến 65535.");
    } catch (IllegalArgumentException e) {
      feedback.setText(e instanceof NumberFormatException ? "Cổng phải là số." : e.getMessage());
      return;
    }
    final boolean register = registering;
    busy(true);
    feedback.setText("Đang kết nối tới server…");
    new SwingWorker<ClientConnection, Void>() {
      @Override
      protected ClientConnection doInBackground() throws Exception {
        return ClientConnection.open(hostname, portNumber, name, pass, register);
      }

      @Override
      protected void done() {
        try {
          pending = get();
          if (!isDisplayable()) {
            pending.close();
            return;
          }
          if (register) {
            pending.close();
            pending = null;
            busy(false);
            toggle.doClick();
            feedback.setForeground(UiStyles.SUCCESS);
            feedback.setText("Đăng ký thành công. Hãy đăng nhập.");
            password.setText("");
            confirm.setText("");
          } else {
            ChatFrame chat = new ChatFrame(pending, () -> new LoginFrame().setVisible(true));
            pending = null;
            chat.setVisible(true);
            dispose();
          }
        } catch (Exception e) {
          feedback.setForeground(UiStyles.ERROR);
          feedback.setText("Kết nối / đăng nhập thất bại");
          JOptionPane.showMessageDialog(
              LoginFrame.this,
              e.getCause() == null ? e.getMessage() : e.getCause().getMessage(),
              "Không thể tiếp tục",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          busy(false);
        }
      }
    }.execute();
  }

  private void busy(boolean value) {
    for (JComponent component :
        new JComponent[] {user, password, confirm, host, port, submit, toggle})
      component.setEnabled(!value);
    submit.setText(value ? "Đang xử lý…" : registering ? "Đăng ký" : "Đăng nhập");
  }
}
