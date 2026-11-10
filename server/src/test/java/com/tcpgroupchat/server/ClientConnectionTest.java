package com.tcpgroupchat.server;

import com.tcpgroupchat.client.ClientConnection;
import com.tcpgroupchat.common.protocol.ChatMessage;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the same TCP connection used by the Swing client against the real server. */
class ClientConnectionTest {
  @TempDir Path data;
  ChatServer server;
  Thread accept;
  final List<Peer> peers = new ArrayList<>();

  @BeforeEach void start() throws Exception {
    server = new ChatServer(data);
    accept = new Thread(() -> { try { server.start(0); } catch (Exception e) { throw new RuntimeException(e); } });
    accept.start();
    long end = System.currentTimeMillis() + 5000;
    while (!server.isRunning() && System.currentTimeMillis() < end) Thread.sleep(10);
    assertTrue(server.isRunning());
  }
  @AfterEach void stop() throws Exception {
    for (Peer p : peers) p.connection.close();
    server.stop();accept.join(3000);
  }

  Peer peer(String name) throws Exception {
    try (var registered = ClientConnection.open("127.0.0.1", server.getPort(), name, "password", true)) {}
    Peer p = new Peer(ClientConnection.open("127.0.0.1", server.getPort(), name, "password", false));
    peers.add(p);return p;
  }

  @Test void unicastGroupBroadcastAndLeaveOverTcp() throws Exception {
    Peer[] p = {peer("client1"),peer("client2"),peer("client3"),peer("client4"),peer("client5")};
    ChatMessage create = new ChatMessage("CREATE_GROUP", "client1", null, "study", null);
    create.setUsers(List.of("client2","client3"));p[0].send(create);
    p[0].next("CREATE_GROUP_OK");
    for (int i=0;i<3;i++) {
      ChatMessage state=p[i].next("GROUP_STATE");
      assertEquals("study", state.getGroup());
      assertTrue(state.getJoinedGroups().contains("study"));
      assertEquals(3,state.getMembers().size());
    }
    p[0].chat("GROUP_MESSAGE",null,"study","Tin nhóm tiếng Việt");p[0].next("ACK");
    for(int i=0;i<3;i++)assertEquals("Tin nhóm tiếng Việt",p[i].next("GROUP_MESSAGE").getContent());
    p[3].noChat();p[4].noChat();
    p[0].send(new ChatMessage("LEAVE_GROUP","client1",null,"study",null));p[0].next("GROUP_REMOVED");
    p[1].chat("GROUP_MESSAGE",null,"study","After Leave");p[1].next("ACK");
    p[1].next("GROUP_MESSAGE");p[2].next("GROUP_MESSAGE");p[0].noChat();
    p[0].chat("GROUP_MESSAGE",null,"study","Blocked");p[0].next("ERROR");
    p[0].chat("UNICAST","client4",null,"Tin riêng");p[0].next("ACK");
    assertEquals("Tin riêng",p[3].next("UNICAST").getContent());
    p[0].noChat();p[1].noChat();p[2].noChat();p[4].noChat();
    p[4].chat("BROADCAST",null,null,"Tin toàn hệ thống");p[4].next("ACK");
    for(Peer peer:p)assertEquals("Tin toàn hệ thống",peer.next("BROADCAST").getContent());
    assertEquals(2,server.getStats().group());assertEquals(1,server.getStats().unicast());assertEquals(1,server.getStats().broadcast());
  }

  @Test void groupsAreIndependentAndMemberCanBeAddedAgain() throws Exception {
    Peer owner=peer("owner"), member=peer("member"), other=peer("other");
    ChatMessage a=new ChatMessage("CREATE_GROUP","owner",null,"room_a",null);a.setUsers(List.of("member"));owner.send(a);owner.next("CREATE_GROUP_OK");
    assertEquals(List.of("room_a"),member.nextGroupState("room_a").getJoinedGroups());
    owner.nextGroupState("room_a");
    ChatMessage b=new ChatMessage("CREATE_GROUP","owner",null,"room_b",null);b.setUsers(List.of("other"));owner.send(b);owner.next("CREATE_GROUP_OK");
    assertEquals(List.of("room_b"),other.nextGroupState("room_b").getJoinedGroups());
    owner.nextGroupState("room_b");
    owner.chat("GROUP_MESSAGE",null,"room_a","Only A");owner.next("ACK");owner.next("GROUP_MESSAGE");member.next("GROUP_MESSAGE");other.noChat();
    member.send(new ChatMessage("LEAVE_GROUP", "member", null, "room_a", null));
    member.next("GROUP_REMOVED");
    assertTrue(member.nextGroupState("room_a").getJoinedGroups().isEmpty());
    owner.nextGroupState("room_a");
    ChatMessage add=new ChatMessage("ADD_GROUP_MEMBERS","owner",null,"room_a",null);add.setUsers(List.of("member"));owner.send(add);owner.next("ADD_GROUP_MEMBERS_OK");
    assertEquals(List.of("room_a"),member.nextGroupState("room_a").getJoinedGroups());
    owner.nextGroupState("room_a");
    owner.chat("GROUP_MESSAGE",null,"room_a","Joined again");owner.next("ACK");owner.next("GROUP_MESSAGE");assertEquals("Joined again",member.next("GROUP_MESSAGE").getContent());other.noChat();
    member.send(new ChatMessage("UNICAST","forged-user","owner",null,"identity"));member.next("ACK");assertEquals("member",owner.next("UNICAST").getSender());
  }

  class Peer {
    final ClientConnection connection;
    final BlockingQueue<ChatMessage> inbox=new LinkedBlockingQueue<>();
    final List<ChatMessage> saved=new ArrayList<>();
    Peer(ClientConnection connection) {
      this.connection=connection;
      connection.listen(inbox::add, reason -> inbox.add(ChatMessage.of("DISCONNECTED","SERVER",reason)));
    }
    void send(ChatMessage message) {message.setRequestId(UUID.randomUUID().toString());connection.send(message);}
    void chat(String type,String recipient,String group,String text){send(new ChatMessage(type,connection.username,recipient,group,text));}
    ChatMessage next(String type) throws Exception {
      for(var it=saved.iterator();it.hasNext();){ChatMessage m=it.next();if(type.equals(m.getType())){it.remove();return m;}}
      long end=System.currentTimeMillis()+4000;
      while(System.currentTimeMillis()<end){ChatMessage m=inbox.poll(100,TimeUnit.MILLISECONDS);if(m==null)continue;
        if("DISCONNECTED".equals(m.getType()))fail(m.getContent());
        if(type.equals(m.getType()))return m;saved.add(m);}
      fail("No "+type+" for "+connection.username+"; saved="+saved.stream().map(ChatMessage::getType).toList());return null;
    }
    ChatMessage nextGroupState(String group) throws Exception {
      long end=System.currentTimeMillis()+4000;
      while(System.currentTimeMillis()<end){
        ChatMessage message=next("GROUP_STATE");
        if(group.equals(message.getGroup()))return message;
      }
      fail("No GROUP_STATE for "+group+" and "+connection.username);return null;
    }
    void noChat() throws Exception {
      Set<String> chatTypes = Set.of("UNICAST", "GROUP_MESSAGE", "BROADCAST");
      for(ChatMessage m:saved)assertFalse(chatTypes.contains(m.getType()),m.getType());saved.clear();
      long end=System.currentTimeMillis()+180;
      while(System.currentTimeMillis()<end){ChatMessage m=inbox.poll(30,TimeUnit.MILLISECONDS);if(m!=null)assertFalse(chatTypes.contains(m.getType()),m.getType());}
    }
  }
}
