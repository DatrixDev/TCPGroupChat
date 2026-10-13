package com.tcpgroupchat.server;

import com.tcpgroupchat.common.net.LocalAddresses;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalAddressesTest {
  @Test void excludesApipaLoopbackWildcardAndIpv6() throws Exception {
    assertTrue(LocalAddresses.usable(InetAddress.getByName("172.26.52.66")));
    for (String ip : new String[]{"169.254.83.107", "127.0.0.1", "0.0.0.0", "::1"})
      assertFalse(LocalAddresses.usable(InetAddress.getByName(ip)), ip);
  }
  @Test void prefersWifiAndEthernetOverVirtualAdapters() throws Exception {
    InetAddress lan = InetAddress.getByName("172.26.52.66");
    int wifi = LocalAddresses.priority("Wireless LAN adapter Wi-Fi", false, lan);
    int ethernet = LocalAddresses.priority("Ethernet", false, lan);
    int virtual = LocalAddresses.priority("vEthernet Hyper-V", true, lan);
    assertTrue(wifi > ethernet);
    assertTrue(ethernet > virtual);
  }
}
