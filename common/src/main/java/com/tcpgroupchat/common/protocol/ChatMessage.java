package com.tcpgroupchat.common.protocol;

/** JSON-line protocol message exchanged between a TCP client and the server. */
public class ChatMessage {
  private String requestId;
  private String password;
  private String sessionId;
  private String timestamp;
  private java.util.List<String> users;
  private java.util.List<String> members;
  private java.util.List<String> joinedGroups;
  private String type;
  private String sender;
  private String recipient;
  private String group;
<<<<<<< HEAD
=======
  private String groupCreator;
>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
  private String content;

  public ChatMessage() {}

  public ChatMessage(String type, String sender, String recipient, String group, String content) {
    this.type = type;
    this.sender = sender;
    this.recipient = recipient;
    this.group = group;
    this.content = content;
  }

  public static ChatMessage of(String type, String sender, String content) {
    return new ChatMessage(type, sender, null, null, content);
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String value) {
    requestId = value;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String value) {
    password = value;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String value) {
    sessionId = value;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String value) {
    timestamp = value;
  }

  public java.util.List<String> getUsers() {
    return users;
  }

  public void setUsers(java.util.List<String> value) {
    users = value;
  }

  public java.util.List<String> getMembers() {
    return members;
  }

  public void setMembers(java.util.List<String> value) {
    members = value;
  }

  public java.util.List<String> getJoinedGroups() {
    return joinedGroups;
  }

  public void setJoinedGroups(java.util.List<String> value) {
    joinedGroups = value;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getSender() {
    return sender;
  }

  public void setSender(String sender) {
    this.sender = sender;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

<<<<<<< HEAD
=======
  public String getGroupCreator() { return groupCreator; }

  public void setGroupCreator(String value) { groupCreator = value; }

>>>>>>> 56afbe5ae3e5f6365b0735b3d5d46f40c310b68c
  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
