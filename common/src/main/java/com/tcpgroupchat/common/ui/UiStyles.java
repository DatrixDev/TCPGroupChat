package com.tcpgroupchat.common.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class UiStyles {
  public static final Color NAVY = new Color(0x082E63),
      PRIMARY = new Color(0x1477F8),
      BACKGROUND = new Color(0xF5F8FC),
      MUTED = new Color(0x64748B),
      BORDER = new Color(0xD7E1EF),
      SUCCESS = new Color(0x16A34A),
      ERROR = new Color(0xEF3340);

  private UiStyles() {}

  public static void install() {
    UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 14));
    UIManager.put("Button.arc", 12);
    UIManager.put("Component.arc", 12);
    UIManager.put("TextComponent.arc", 10);
    UIManager.put("ScrollBar.width", 11);
  }

  public static JButton button(String text, String icon, boolean primary) {
    JButton b = new JButton(text, IconManager.load(icon, 18, 18, primary ? Color.WHITE : PRIMARY));
    b.setBackground(primary ? PRIMARY : Color.WHITE);
    b.setForeground(primary ? Color.WHITE : PRIMARY);
    b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    b.setIconTextGap(8);
    b.setMargin(new Insets(10, 14, 10, 14));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  public static JLabel title(String text, int size) {
    JLabel label = new JLabel(text);
    label.setForeground(NAVY);
    label.setFont(new Font("Segoe UI", Font.BOLD, size));
    return label;
  }

  public static JPanel card(LayoutManager layout) {
    JPanel panel = new JPanel(layout) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D canvas = (Graphics2D) g.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setColor(getBackground());
        canvas.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
        canvas.setColor(BORDER);
        canvas.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
        canvas.dispose();
      }
    };
    panel.setOpaque(false);
    panel.setBackground(Color.WHITE);
    panel.setBorder(new EmptyBorder(16, 18, 16, 18));
    return panel;
  }

  public static JPanel field(String name, JComponent component) {
    JPanel panel = new JPanel(new BorderLayout(0, 7));
    panel.setOpaque(false);
    JLabel label = title(name, 13);
    panel.add(label, BorderLayout.NORTH);
    panel.add(component, BorderLayout.CENTER);
    component.setPreferredSize(new Dimension(180, 42));
    return panel;
  }

  public static String time(String raw) {
    if (raw == null || raw.isBlank()) return "—";
    try {
      return java.time.LocalDateTime.parse(raw)
          .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    } catch (java.time.format.DateTimeParseException e) {
      return raw;
    }
  }

  public static void area(JTextArea area) {
    area.setEditable(false);
    area.setBackground(Color.WHITE);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    area.setForeground(NAVY);
    area.setBorder(new EmptyBorder(12, 12, 12, 12));
  }
}
