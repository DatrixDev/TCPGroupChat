# Validation

- `mvn clean verify` succeeds on JDK 17.
- All 16 tests pass, including 12 TCP protocol and lifecycle tests, 2 LAN-address tests, and 2 integration tests using the same `ClientConnection` as the Swing app.
- The integration tests cover Unicast to one recipient, Group to three of five clients, Leave, Broadcast to all five clients, independent groups, adding a member again, and sender identity.
- Tests ran on Linux. Windows and two-machine LAN deployment require a separate check.
