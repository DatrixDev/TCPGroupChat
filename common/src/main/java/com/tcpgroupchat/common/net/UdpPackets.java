package com.tcpgroupchat.common.net;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcpgroupchat.common.protocol.ChatMessage;
import java.io.IOException;
import java.net.*;

/** One UDP datagram contains one UTF-8 JSON message. No newline framing is needed. */
public final class UdpPackets {
  public static final int MAX_BYTES = 60000;
  private static final ObjectMapper JSON = new ObjectMapper();
  private UdpPackets() {}

  public static void send(DatagramSocket socket, ChatMessage message, InetAddress ip, int port)
      throws IOException {
    byte[] bytes = JSON.writeValueAsBytes(message);
    if (bytes.length > MAX_BYTES) throw new IOException("Tin nhắn UDP quá lớn.");
    socket.send(new DatagramPacket(bytes, bytes.length, ip, port));
  }

  public static ChatMessage read(DatagramPacket packet) throws IOException {
    return JSON.readValue(packet.getData(), packet.getOffset(), packet.getLength(), ChatMessage.class);
  }

  public static boolean isChat(String type) {
    return "UNICAST".equals(type) || "GROUP_MESSAGE".equals(type) || "BROADCAST".equals(type);
  }

  public static String broadcastIp(String localIp) throws SocketException, UnknownHostException {
    InetAddress local = InetAddress.getByName(localIp);
    NetworkInterface nic = NetworkInterface.getByInetAddress(local);
    if (nic != null) for (InterfaceAddress address : nic.getInterfaceAddresses())
      if (local.equals(address.getAddress()) && address.getBroadcast() != null)
        return address.getBroadcast().getHostAddress();
    return "255.255.255.255";
  }
}
