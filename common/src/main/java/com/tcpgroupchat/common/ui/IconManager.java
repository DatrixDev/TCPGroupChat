package com.tcpgroupchat.common.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;

public final class IconManager {
  private static final ConcurrentMap<IconKey, Icon> CACHE = new ConcurrentHashMap<>();

  private IconManager() {}

  public static Icon load(String name, int width, int height) {
    return load(name, width, height, null);
  }

  public static Icon load(String name, int width, int height, Color color) {
    String normalized = normalize(name);
    int rgb = color == null ? Integer.MIN_VALUE : color.getRGB();
    return CACHE.computeIfAbsent(
        new IconKey(normalized, width, height, rgb), key -> create(key, color));
  }

  public static Image image(String name, int width, int height, Color color) {
    Icon icon = load(name, width, height, color);
    return icon instanceof ImageIcon imageIcon ? imageIcon.getImage() : null;
  }

  private static Icon create(IconKey key, Color color) {
    String resource = "/icons/" + key.name() + ".svg";
    URL url = IconManager.class.getResource(resource);
    if (url == null) {
      System.err.println("Không tìm thấy icon SVG: " + resource);
      return new EmptyIcon(key.width(), key.height());
    }
    try {
      FlatSVGIcon icon =
          new FlatSVGIcon(
              "icons/" + key.name() + ".svg",
              key.width(),
              key.height(),
              IconManager.class.getClassLoader());
      if (!icon.hasFound()) {
        System.err.println("Không thể tải icon SVG: " + resource);
        return new EmptyIcon(key.width(), key.height());
      }
      if (color != null) {
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(original -> color));
      }
      return icon;
    } catch (RuntimeException exception) {
      System.err.println("Lỗi tải icon SVG " + resource + ": " + exception.getMessage());
      return new EmptyIcon(key.width(), key.height());
    }
  }

  private static String normalize(String name) {
    String value = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    return value.endsWith(".svg") ? value.substring(0, value.length() - 4) : value;
  }

  private record IconKey(String name, int width, int height, int rgb) {}

  private record EmptyIcon(int width, int height) implements Icon {
    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {}
  }
}
