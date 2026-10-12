package com.tcpgroupchat.common.net;

import java.net.*;
import java.util.*;

/** LAN addresses for display; does not change the server's wildcard socket binding. */
public final class LocalAddresses {
  public record Address(String ip, String adapter, int priority) {
    @Override public String toString() { return ip; }
  }
  private LocalAddresses() {}

  public static List<Address> available() {
    List<Address> result = new ArrayList<>();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      if (interfaces != null) while (interfaces.hasMoreElements()) {
        NetworkInterface nic = interfaces.nextElement();
        try {
          if (!nic.isUp() || nic.isLoopback()) continue;
          String label = Objects.toString(nic.getDisplayName(), nic.getName());
          String name = (nic.getName() + " " + label).toLowerCase(Locale.ROOT);
          Enumeration<InetAddress> addresses = nic.getInetAddresses();
          while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            if (!usable(address)) continue;
            result.add(new Address(address.getHostAddress(), label, priority(name, nic.isVirtual(), address)));
          }
        } catch (SocketException ignored) { /* A disappearing adapter must not hide the others. */ }
      }
    } catch (SocketException | SecurityException ignored) { /* Local-only fallback below. */ }
    result.sort(Comparator.comparingInt(Address::priority).reversed()
        .thenComparing(Address::adapter).thenComparing(Address::ip));
    if (result.isEmpty()) result.add(new Address("127.0.0.1", "Chỉ kết nối trên máy này", 0));
    return List.copyOf(result);
  }

  public static String preferredIp() { return available().get(0).ip(); }

  public static boolean usable(InetAddress address) {
    return address instanceof Inet4Address && !address.isLoopbackAddress()
        && !address.isLinkLocalAddress() && !address.isAnyLocalAddress();
  }

  public static int priority(String name, boolean virtual, InetAddress address) {
    String n = name.toLowerCase(Locale.ROOT);
    int score = address.isSiteLocalAddress() ? 10 : 0;
    if (n.contains("wi-fi") || n.contains("wifi") || n.contains("wireless") || n.startsWith("wlan")) score += 100;
    else if (n.contains("ethernet") || n.startsWith("eth") || n.startsWith("en")) score += 80;
    if (virtual || n.matches(".*(virtual|vmware|hyper-v|vethernet|vpn|tunnel|docker|wsl).*")) score -= 200;
    return score;
  }
}
