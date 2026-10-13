# Validation

- Java 17 Maven clean install and package succeed.
- 16 tests pass: 12 TCP protocol/lifecycle tests, 2 LAN-address tests and 2 integration tests with actual application UDP transports.
- UDP integration covers Unicast to one recipient, multicast to three of five clients, Leave stopping receipt and rejecting sends, Broadcast to all five, independent multicast group IPs, adding a member again, UDP readiness and sender identity. No tests are skipped.
- Swing smoke check with five clients covers real UDP delivery, no duplicate sender bubble when UDP and TCP ACK arrive in either order, visible group IP, old history after Leave, public room discovery and Join, private UDP, Broadcast, and full/minimum layouts.
- Checked on Linux in this environment. Windows and separate computers on a LAN still require a deployment check.
- Group history remains in the current client session only. UDP delivery is not guaranteed; ACK means the server sent/queued the packet, not that it was received/read.
