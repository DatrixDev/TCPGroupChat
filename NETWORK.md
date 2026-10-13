# Network code and demo

TCP keeps login, accounts, sessions, group management and ACKs. Application chat requests and deliveries use real UDP packets. The server authenticates UDP requests with the TCP session ID, client IP and registered UDP port.

| Traffic | Default address / port | Code |
| --- | --- | --- |
| TCP control | Selected server IPv4, TCP 2006 | `ChatServer.start`, `ClientConnection.open` |
| UDP requests to server | Selected server IPv4, UDP 2008 | `UdpClientTransport.send` |
| UDP Unicast delivery | Recipient IPv4, automatically assigned client UDP port | `UdpServerTransport.unicast` |
| UDP Multicast delivery | One assigned group IP, starting at 239.255.1.1, UDP 2007 | `UdpServerTransport.multicast` |
| UDP Broadcast delivery | Selected adapter's broadcast IP, UDP 2007 | `UdpServerTransport.broadcast` |

The client sends a chat request to the server by UDP unicast. The server checks the session and group membership, then sends to the requested recipient, multicast IP or broadcast IP. Each multicast group has a distinct IP; the server sends one datagram to that IP, rather than one TCP message per member.

`UdpPackets.send` contains the actual `DatagramPacket` and `socket.send` call. `UdpClientTransport.join` and `leave` call `MulticastSocket.joinGroup` and `leaveGroup` on the selected local network interface. Its receive loop validates the server endpoint and ignores groups the client has not joined. A small UDP hello exchange establishes the client return endpoint.

The server displays group IPs in the main dashboard and the group dialog. The client displays the multicast endpoint in Group and the broadcast endpoint in Broadcast. Leave keeps previous messages in the current client session, but disables sending and stops receiving new group messages.

## Run on a LAN

1. Build both server and client with `mvn clean install`.
2. Start the server, choose its active Wi-Fi/Ethernet IPv4, and click Start.
3. On the other computers enter that server IPv4 and the TCP port in the login form.
4. Allow Java through the Windows firewall on the private network. The server needs UDP 2008; multicast/broadcast listeners need UDP 2007, and clients also use their automatic unicast UDP ports.
5. Create a group and select its members. Compare the group IP shown on server and client, then test Unicast, Group and Broadcast.

The computers must share a network that permits multicast and broadcast. These packets are not an Internet messaging service. UDP can lose or reorder packets; the TCP ACK confirms the server attempted the send, not that a recipient received or read it. Multicast membership is not encryption.

Optional JVM properties: `chat.udp.port` (server UDP port), `chat.group.port` (shared multicast/broadcast port), `chat.port` (TCP port). The server advertises the UDP settings to clients at login.

Raw TCP-only protocol peers remain supported for the existing routing/framing tests. The desktop application always advertises its UDP endpoint and uses the UDP transport for chat.
