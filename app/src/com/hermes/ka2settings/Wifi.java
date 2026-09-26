package com.hermes.ka2settings;

import java.util.Map;

/**
 * The Wi-Fi payloads, kept out of the activity so the escaping is testable.
 *
 * A Wi-Fi password may contain quotes, backslashes or newlines, and it travels to the box as a JSON
 * string inside the command line. Anything that mangles it silently fails to connect, so the quoting is
 * done here and checked by tests. The password itself is never logged by the app, and the box redacts
 * these commands before writing them down.
 */
public final class Wifi {
  private Wifi() {
  }

  /** One network as the box reports it. */
  public static final class Network {
    public final String ssid;
    public final int signal;
    public final String security;
    public final boolean inUse;

    public Network(String ssid, int signal, String security, boolean inUse) {
      this.ssid = ssid;
      this.signal = signal;
      this.security = security;
      this.inUse = inUse;
    }

    public boolean needsPassword() {
      return security != null && !security.isEmpty();
    }

    /** What the picker row says: the name, how strong, and whether it is locked. */
    public String label() {
      String lock = needsPassword() ? security : "open";
      String here = inUse ? "  (connected)" : "";
      return String.format("%s   %d%%  %s%s", ssid, signal, lock, here);
    }
  }

  public static Network parse(Map<String, Object> row) {
    return new Network(Json.str(row, "ssid", ""), Json.intOr(row, "signal", 0),
        Json.str(row, "sec", ""), Json.bool(row, "in_use", false));
  }

  /** `WIFI CONNECT {"ssid":"...","password":"..."}` - quotes and backslashes escaped properly. */
  public static String connectCommand(String ssid, String password) {
    StringBuilder out = new StringBuilder("WIFI CONNECT {\"ssid\":");
    out.append(quote(ssid));
    if (password != null && !password.isEmpty()) {
      out.append(",\"password\":").append(quote(password));
    }
    out.append('}');
    return out.toString();
  }

  public static String forgetCommand(String ssid) {
    return "WIFI FORGET {\"ssid\":" + quote(ssid) + "}";
  }

  /** A JSON string literal. Enough for the payloads here, and tested against awkward input. */
  public static String quote(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"' || c == '\\') {
        out.append('\\').append(c);
      } else if (c == '\n') {
        out.append("\\n");
      } else if (c == '\r') {
        out.append("\\r");
      } else if (c == '\t') {
        out.append("\\t");
      } else if (c < 0x20) {
        out.append(String.format("\\u%04x", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.append('"').toString();
  }

  /** How the status line reads, from the box's own status object. */
  public static String statusLine(Map<String, Object> status) {
    if (status == null || !Json.bool(status, "connected", false)) {
      return "not on Wi-Fi";
    }
    String ssid = Json.str(status, "ssid", "?");
    String ip = Json.str(status, "ip", "");
    String signal = Json.str(status, "signal", "");
    StringBuilder out = new StringBuilder(ssid);
    if (!ip.isEmpty()) {
      out.append("  ·  ").append(ip);
    }
    if (!signal.isEmpty()) {
      out.append("  ·  signal ").append(signal).append("%");
    }
    return out.toString();
  }
}
