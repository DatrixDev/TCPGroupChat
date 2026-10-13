package com.tcpgroupchat.server.net;

import com.tcpgroupchat.common.net.UdpPackets;
import com.tcpgroupchat.common.protocol.ChatMessage;
import java.io.IOException;
import java.net.*;
import java.util.function.BiConsumer;

/** The server receives UDP requests and sends real IP unicast/multicast/broadcast packets. */
public final class UdpServerTransport implements AutoCloseable {
  private final MulticastSocket socket;
  public final String localIp, broadcastIp;
  public final int groupPort;

  public UdpServerTransport(String localIp, int serverPort, int groupPort,
      BiConsumer<DatagramPacket, ChatMessage> onRequest) throws IOException {
    if (serverPort < 0 || serverPort > 65535 || groupPort < 1 || groupPort > 65535 || serverPort == groupPort)
      throw new IllegalArgumentException("Cổng UDP server và cổng nhóm phải hợp lệ và khác nhau.");
    this.localIp = localIp;
    this.groupPort = groupPort;
    broadcastIp = UdpPackets.broadcastIp(localIp);
    socket = new MulticastSocket(null);
    try {
      socket.bind(new InetSocketAddress(InetAddress.getByName(localIp), serverPort));
      socket.setBroadcast(true);
      socket.setTimeToLive(1); // Keep group traffic on the local network.
      NetworkInterface nic = NetworkInterface.getByInetAddress(InetAddress.getByName(localIp));
      if (nic != null) socket.setNetworkInterface(nic);
      socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
    } catch (IOException | RuntimeException e) { socket.close(); throw e; }
    Thread reader = new Thread(() -> {
      while (!socket.isClosed()) {
        try {
          DatagramPacket packet = new DatagramPacket(new byte[UdpPackets.MAX_BYTES], UdpPackets.MAX_BYTES);
          socket.receive(packet);
          ChatMessage request = UdpPackets.read(packet);
          if (request != null && (UdpPackets.isChat(request.getType()) || "UDP_HELLO".equals(request.getType()))) onRequest.accept(packet, request);
        } catch (IOException ignored) { /* Invalid datagrams do not terminate the receiver. */ }
      }
    }, "server-udp-receiver");
    reader.setDaemon(true);
    reader.start();
  }

  public int port() { return socket.getLocalPort(); }

  public void unicast(ChatMessage message, InetAddress clientIp, int clientPort) throws IOException {
    UdpPackets.send(socket, message, clientIp, clientPort);
  }

  public void multicast(ChatMessage message, String groupIp) throws IOException {
    UdpPackets.send(socket, message, InetAddress.getByName(groupIp), groupPort);
  }

  public void broadcast(ChatMessage message) throws IOException {
    UdpPackets.send(socket, message, InetAddress.getByName(broadcastIp), groupPort);
  }

  @Override public void close() { socket.close(); }
}
