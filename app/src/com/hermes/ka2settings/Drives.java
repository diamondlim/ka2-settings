package com.hermes.ka2settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the box's drive replies into rows the log page can display.
 *
 * The box answers `DRIVES` with one line of JSON - a list of recent drives - and `DRIVES <route>`
 * with the summary for a single drive. Both arrive as a single line because that is what the
 * Bluetooth protocol carries, so the shape has to be understood here rather than by a convention
 * elsewhere. Kept free of Android so it can be tested off the device.
 */
public final class Drives {

  public static final String LIST_PREFIX = "DRIVES ";
  public static final String ERROR_PREFIX = "DRIVES ERROR";

  private Drives() {
  }

  /** Is this line the box's answer to a DRIVES command at all? */
  public static boolean isDrives(String line) {
    return line != null && line.startsWith("DRIVES");
  }

  public static boolean isError(String line) {
    return line != null && line.startsWith(ERROR_PREFIX);
  }

  /** The reason the box gave, for showing rather than swallowing. */
  public static String errorText(String line) {
    return line == null ? "" : line.substring(Math.min(ERROR_PREFIX.length(), line.length())).trim();
  }

  /**
   * The recent drives, as rows of {route, when, speed, length} ready for a list.
   * An empty result is never an error: it means no drive has moved since the logs began.
   */
  public static List<String[]> list(String reply) {
    List<String[]> rows = new ArrayList<String[]>();
    for (String obj : objects(reply)) {
      Map<String, Object> m;
      try {
        m = Json.parseObject(obj);
      } catch (Exception exc) {
        continue;                      // one unreadable drive must not lose the others
      }
      if (m == null) {
        continue;
      }
      rows.add(new String[] {
          str(m, "route"),
          str(m, "started"),
          speed(str(m, "median_speed_ms")) + " / " + speed(str(m, "max_speed_ms")),
          frames(m),
      });
    }
    return rows;
  }

  /** The readings for one drive, as {label, value} pairs. */
  public static List<String[]> summary(String reply) {
    List<String[]> out = new ArrayList<String[]>();
    List<String> objs = objects(reply);
    if (objs.isEmpty()) {
      return out;
    }
    Map<String, Object> m;
    try {
      m = Json.parseObject(objs.get(0));
    } catch (Exception exc) {
      return out;
    }
    if (m == null) {
      return out;
    }
    double duration = num(m, "duration_s");
    out.add(new String[] {"Driven", seconds(duration) + "  ·  " + frames(m)});
    out.add(new String[] {"Speed", speed(str(m, "median_speed_ms")) + " median, "
        + speed(str(m, "max_speed_ms")) + " max"});

    // How often the lane pair was below the threshold the car itself uses. This is the number that
    // explains a poor centring figure: on some roads there is little for the car to steer by. Only the
    // box's own analysis carries it - a host summary omits the field, and an absent field must not be
    // read as "always usable" (num() would return 0 and print a confident 100%).
    if (m.containsKey("lane_below_threshold_pct")) {
      double below = num(m, "lane_below_threshold_pct");
      out.add(new String[] {"Lanes usable", String.format("%.0f%% of the time%s",
          100.0 - below, below > 50 ? "  (mostly unreadable)" : "")});
    }

    Object centring = m.get("centring");
    if (centring instanceof Map) {
      Map<?, ?> c = (Map<?, ?>) centring;
      for (String key : new String[] {"5m", "10m"}) {
        Object entry = c.get(key);
        if (entry instanceof Map) {
          Map<?, ?> e = (Map<?, ?>) entry;
          out.add(new String[] {"Drift at " + key.replace("m", " m"),
              drift(num(e, "median_m")) + "  (rms " + String.format("%.2f", num(e, "rms_m")) + " m)"});
        }
      }
    }
    // Fields the host archive carries and the box does not (it never computes them): distance,
    // segment count, how much of the drive the ADAS was engaged, and pedal use. Shown only when
    // present, so a box reply is unaffected.
    double km = num(m, "km");
    if (km > 0) {
      out.add(new String[] {"Distance", String.format("%.1f km", km)});
    }
    double segments = num(m, "segments");
    if (segments > 0) {
      // no Map.of here: it is Java 9 and this app runs from API 26
      out.add(new String[] {"Segments", String.format("%.0f (%s moved)", segments,
          m.containsKey("segments_moved") ? String.format("%.0f", num(m, "segments_moved")) : "?")});
    }
    if (m.containsKey("engaged_share")) {
      out.add(new String[] {"Autodrive", String.format("%.0f%% of the drive", 100.0 * num(m, "engaged_share"))});
    }
    // Where the drive can be drawn: the phone's own track (works today) and the box's own positions
    // (only once its GNSS has a signal), each reported separately so neither hides the other.
    Object track = m.get("track");
    if (track instanceof Map) {
      Map<?, ?> t = (Map<?, ?>) track;
      Object phone = t.get("phone");
      if (phone instanceof Map) {
        Map<?, ?> ph = (Map<?, ?>) phone;
        out.add(new String[] {"Phone track", String.format("%.0f points · %.1f km  (matched %s)",
            num(ph, "points"), num(ph, "km"), seconds(num(ph, "overlap_s")))});
      } else {
        out.add(new String[] {"Phone track", "none for this drive"});
      }
      Object box = t.get("box");
      if (box instanceof Map) {
        Map<?, ?> b = (Map<?, ?>) box;
        double boxPoints = num(b, "points");
        out.add(new String[] {"Box track", boxPoints > 0 ? String.format("%.0f points", boxPoints)
            : "none - its GPS has no satellite signal yet"});
      }
    }
    if (m.containsKey("gas_share") || m.containsKey("brake_share")) {
      out.add(new String[] {"Your pedals", String.format("%.0f%% throttle, %.0f%% brake",
          100.0 * num(m, "gas_share"), 100.0 * num(m, "brake_share"))});
    }
    String computed = computedAt(m);
    if (!computed.isEmpty()) {
      out.add(new String[] {"Computed", computed});
    }
    return out;
  }

  /**
   * The drive's own minutes, one row each: when it happened (from the drive's start), how far, how
   * fast. Reads the host's `segment_rows`; a box reply has none, so the list is simply empty.
   */
  public static List<String[]> segments(String reply) {
    List<String[]> out = new ArrayList<String[]>();
    List<String> objs = objects(reply);
    if (objs.isEmpty()) {
      return out;
    }
    Map<String, Object> m;
    try {
      m = Json.parseObject(objs.get(0));
    } catch (Exception exc) {
      return out;
    }
    Object rows = m == null ? null : m.get("segment_rows");
    if (!(rows instanceof List)) {
      return out;
    }
    for (Object o : (List<?>) rows) {
      if (!(o instanceof Map)) {
        continue;
      }
      Map<?, ?> r = (Map<?, ?>) o;
      double off = num(r, "off_s");
      int total = (int) Math.round(off);
      String when = String.format("%02d:%02d", total / 60, total % 60);
      String speed = speed(String.valueOf(num(r, "median_kph") / 3.6));
      boolean moved = Boolean.TRUE.equals(r.get("moved")) || num(r, "km") > 0.02;
      out.add(new String[] {when,
          String.format("%.2f km", num(r, "km")) + "  ·  " + speed + (moved ? "" : "  ·  parked")});
    }
    return out;
  }

  private static String computedAt(Map<String, Object> m) {
    Object o = m.get("computed_at");
    return o == null ? "" : o.toString();
  }

  private static String drift(double metres) {
    return String.format("%+.2f m %s", metres, metres > 0 ? "right" : "left");
  }

  private static String speed(String metresPerSecond) {
    double v = numOf(metresPerSecond);
    if (v <= 0.0) {
      return "-";
    }
    // shown in km/h: the unit a driver reads, from the metres per second the logs hold
    return String.format("%.0f km/h", v * 3.6);
  }

  private static String frames(Map<?, ?> m) {
    double f = num(m, "frames");
    return f <= 0 ? "-" : String.format("%.0f frames", f);
  }

  private static String seconds(double s) {
    if (s <= 0) {
      return "-";
    }
    int total = (int) Math.round(s);
    return String.format("%d:%02d", total / 60, total % 60);
  }

  /**
   * Every top-level object in the reply, brace matched, so no JSON array support is needed.
   *
   * Which shape the reply is, is decided by the first brace-vs-bracket, not by the first bracket:
   * a single object can carry an array *inside* it (the host's `segment_rows`), and looking for the
   * first `[` then parsed those inner rows as if they were the drive.
   */
  private static List<String> objects(String reply) {
    List<String> out = new ArrayList<String>();
    if (reply == null) {
      return out;
    }
    int objStart = reply.indexOf('{');
    int arrStart = reply.indexOf('[');
    if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
      int depth = 0;
      for (int i = objStart; i < reply.length(); i++) {
        char c = reply.charAt(i);
        if (c == '{') {
          depth++;
        } else if (c == '}') {
          depth--;
          if (depth == 0) {
            out.add(reply.substring(objStart, i + 1));
            return out;
          }
        }
      }
      return out;
    }
    int open = reply.indexOf('[');
    int close = reply.lastIndexOf(']');
    if (open < 0 || close < open) {
      close = reply.length() - 1;          // a single object rather than a list
      open = reply.indexOf('{') - 1;
      if (open < -1) {
        return out;
      }
    }
    int depth = 0;
    int start = -1;
    for (int i = open + 1; i <= close; i++) {
      char c = reply.charAt(i);
      if (c == '{') {
        if (depth == 0) {
          start = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          out.add(reply.substring(start, i + 1));
          start = -1;
        }
      }
    }
    return out;
  }

  private static String str(Map<?, ?> m, String key) {
    Object o = m.get(key);
    return o == null ? "" : o.toString();
  }

  private static double num(Map<?, ?> m, String key) {
    Object o = m.get(key);
    if (o instanceof Number) {
      return ((Number) o).doubleValue();
    }
    return numOf(o == null ? "" : o.toString());
  }

  private static double numOf(String s) {
    try {
      return Double.parseDouble(s.trim());
    } catch (Exception exc) {
      return 0.0;
    }
  }
}
