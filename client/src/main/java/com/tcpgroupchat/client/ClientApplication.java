package com.tcpgroupchat.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.tcpgroupchat.common.ui.UiStyles;
import javax.swing.SwingUtilities;

public final class ClientApplication {
  public static void main(String[] args) {
    FlatLightLaf.setup();
    UiStyles.install();
    SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
  }
}
