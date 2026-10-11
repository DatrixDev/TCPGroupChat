package com.tcpgroupchat.common.protocol;

import java.io.BufferedReader;
import java.io.IOException;

/** Limits each TCP frame while retaining support for partial/coalesced socket reads. */
public final class JsonLines {
  public static final int MAX_FRAME = 32_768;

  private JsonLines() {}

  public static String read(BufferedReader reader) throws IOException {
    StringBuilder line = new StringBuilder();
    int ch;
    while ((ch = reader.read()) != -1) {
      if (ch == '\n') return line.toString();
      if (line.length() >= MAX_FRAME)
        throw new IOException("Gói JSON vượt quá " + MAX_FRAME + " ký tự.");
      line.append((char) ch);
    }
    if (line.length() != 0) throw new IOException("Kết nối đóng giữa gói JSON.");
    return null;
  }
}
