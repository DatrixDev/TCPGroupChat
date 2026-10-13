# Validation

- Java 17 Maven build succeeds.
- 12 real TCP socket tests pass: authentication, routing, private group creation and membership, outsider access rejection, Leave, reconnect, framing and concurrent sends.
- Private group UI: no sample rooms or room-name input, selected members only, creator-only member management, no public Join, Leave removes the group from the client.
- Chat history is separated by person, group and Broadcast within the current session.
- Former members can still send through the TCP protocol for the assignment's send-after-Leave case, but the private-group UI removes the conversation after Leave.
- Windows and multi-machine LAN execution have not been tested in this environment.

## LAN address selection

- Enumerate IPv4 addresses on active network adapters; exclude APIPA (169.254/16), loopback, wildcard and IPv6 addresses from LAN choices.
- Prefer Wi-Fi and Ethernet over virtual adapters; the server IP dropdown allows another available address to be selected. Its tooltip identifies the adapter.
- The selected address is a connection address for display; the TCP server still listens on all local interfaces.
- Two additional tests cover address exclusion and physical/virtual adapter priority. Windows adapter selection still needs verification on the target machine.
