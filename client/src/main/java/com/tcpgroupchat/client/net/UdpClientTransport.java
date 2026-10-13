package com.tcpgroupchat.client.net;

import com.tcpgroupchat.common.net.UdpPackets;
import com.tcpgroupchat.common.protocol.ChatMessage;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Separate unicast port per client; shared receive port for multicast and broadcast. */
public final class UdpClientTransport implements AutoCloseable {
  private final DatagramSocket unicast;
  private final MulticastSocket shared;
  private final InetAddress serverIp;
  private final int serverPort, groupPort;
  private final NetworkInterface nic;
  private final Map<String, String> joined = new ConcurrentHashMap<>();
  private final String sessionId;
  private volatile boolean closed;

  public UdpClientTransport(DatagramSocket unicast, ChatMessage login, String localIp) throws IOException {
    this.unicast = unicast;
    serverIp = InetAddress.getByName(login.getNetworkIp());
    serverPort = login.getUdpPort();
    groupPort = login.getGroupPort();
    sessionId = login.getSessionId();
    nic = NetworkInterface.getByInetAddress(InetAddress.getByName(localIp));
    shared = new MulticastSocket(null);
    try {
      shared.setReuseAddress(true); // Allow several clients on one demo computer.
      shared.bind(new InetSocketAddress(groupPort));
      shared.setBroadcast(true);
      if (nic != null) shared.setNetworkInterface(nic);
    } catch (IOException | RuntimeException e) { shared.close(); throw e; }
  }

  public void start(Consumer<ChatMessage> received) {
    receiveLoop(unicast, false, received);
    receiveLoop(shared, true, received);
  }

  private void receiveLoop(DatagramSocket socket, boolean groupSocket, Consumer<ChatMessage> received) {
    Thread thread = new Thread(() -> {
      while (!closed) {
        try {
          DatagramPacket packet = new DatagramPacket(new byte[UdpPackets.MAX_BYTES], UdpPackets.MAX_BYTES);
          socket.receive(packet);
          if (!serverIp.equals(packet.getAddress()) || packet.getPort() != serverPort) continue;
          ChatMessage message = UdpPackets.read(packet);
          if (message == null) continue;
          if (!groupSocket && "UDP_READY".equals(message.getType())) { received.accept(message); continue; }
          if (!UdpPackets.isChat(message.getType())) continue;
          if (groupSocket && "UNICAST".equals(message.getType())) continue;
          if (!groupSocket && !"UNICAST".equals(message.getType())) continue;
          if ("GROUP_MESSAGE".equals(message.getType()) && !joined.containsKey(message.getGroup())) continue;
          received.accept(message);
        } catch (IOException ignored) { /* Closed sockets wake the receiver; malformed packets are ignored. */ }
      }
    }, groupSocket ? "client-multicast-broadcast" : "client-unicast");
    thread.setDaemon(true);
    thread.start();
  }

  public void send(ChatMessage message) throws IOException {
    message.setSessionId(sessionId);
    UdpPackets.send(unicast, message, serverIp, serverPort);
  }

  public synchronized void join(String group, String groupIp) throws IOException {
    if (groupIp == null || joined.containsKey(group)) return;
    InetAddress ip = InetAddress.getByName(groupIp);
    if (!ip.isMulticastAddress()) throw new IOException("IP nhóm không phải multicast.");
    shared.joinGroup(new InetSocketAddress(ip, groupPort), nic);
    joined.put(group, groupIp);
  }

  public synchronized void leave(String group) throws IOException {
    String ip = joined.remove(group); // Stop displaying packets immediately, even if leaveGroup fails.
    if (ip != null) shared.leaveGroup(new InetSocketAddress(ip, groupPort), nic);
  }

  @Override public void close() { closed = true; unicast.close(); shared.close(); joined.clear(); }
}
