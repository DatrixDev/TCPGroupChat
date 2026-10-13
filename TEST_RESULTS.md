# Validation

- Java 17 Maven build succeeds.
- 12 real TCP socket tests pass: authentication, routing, private group creation and membership, outsider access rejection, Leave, reconnect, framing and concurrent sends.
- Private group UI: no sample rooms or room-name input, selected members only, creator-only member management, no public Join, Leave retains the group and existing local history, marks it as left, and disables sending.
- Chat history is separated by person, group and Broadcast within the current session.
- Former members cannot send group messages, including direct TCP requests, and do not receive new group messages. Existing history is only retained for the current client session.
- Windows and multi-machine LAN execution have not been tested in this environment.

## LAN address selection

- Enumerate IPv4 addresses on active network adapters; exclude APIPA (169.254/16), loopback, wildcard and IPv6 addresses from LAN choices.
- Prefer Wi-Fi and Ethernet over virtual adapters; the server IP dropdown allows another available address to be selected. Its tooltip identifies the adapter.
- The selected address is a connection address for display; the TCP server still listens on all local interfaces.
- Two additional tests cover address exclusion and physical/virtual adapter priority. Windows adapter selection still needs verification on the target machine.
