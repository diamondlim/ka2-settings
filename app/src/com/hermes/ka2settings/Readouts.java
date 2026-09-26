package com.hermes.ka2settings;

import java.util.HashMap;
import java.util.Map;

/**
 * The strings the lane page puts on screen, and the rule for when a view needs setting again.
 *
 * Two faults came out of doing this the obvious way. Every pose frame called setText on sixteen views,
 * ten times a second, from the Bluetooth reader thread: each setText makes its parent lay out again, so
 * the reader fell behind the socket and the page ended up showing data that was seconds old - which is
 * what a set speed readout that "only changes after a few seconds" actually was.
 *
 * So the strings are built here, in one place, and Cache.changed() answers whether a view's text
 * differs from what is already on it. An unchanged readout then costs nothing at all.
 */
public final class Readouts {
  private Readouts() {
  }

  /** Remembers what each view is showing, and reports whether a new value needs setting. */
  public static final class Cache {
    private final Map<String, String> shown = new HashMap<>();

    public boolean changed(String key, String value) {
      String last = shown.get(key);
      if (last != null && last.equals(value)) {
        return false;                     // already on screen: leave the view, and the layout, alone
      }
      shown.put(key, value);
      return true;
    }

    public void forget() {
      shown.clear();                      // the views are new (or the page was rebuilt)
    }
  }

  /** Where the strings go: the lane page passes its TextViews, the tests pass a map. */
  public interface Sink {
    void put(String key, String value);
  }

  /** The big number: whole km/h. */
  public static String speed(double metresPerSecond) {
    return String.format("%.0f", metresPerSecond * 3.6);
  }

  /**
   * The ACC set speed, which is the car's own displayed value - not the fork's internal one, which is
   * divided by its HUD multiplier (1.12 on this car) and so reads several km/h lower than the dash.
   */
  public static String setSpeed(double metresPerSecond) {
    if (metresPerSecond <= 0.05) {
      return "cruise not set";
    }
    return String.format("set %.0f km/h", metresPerSecond * 3.6);
  }

  /** The centring drift: how far the car sits from the lane centre, and which way. */
  public static String drift(LaneGeometry.Pose pose) {
    boolean have = pose.ok || pose.centreX.length >= 2;
    if (!have || Double.isNaN(pose.drift())) {
      return "-";
    }
    double metres = pose.drift();
    if (Math.abs(metres) < 0.05) {
      return String.format("%.2f m centred", metres);
    }
    return String.format("%.2f m %s", Math.abs(metres), metres > 0 ? "right" : "left");
  }

  /** The car's own displayed speed, shown beside the true one so the two never look like an error. */
  public static String dashSpeed(LaneGeometry.Pose pose) {
    return pose.dashSpeed > 0.05 ? String.format("dash %.0f km/h", pose.dashSpeed * 3.6) : "";
  }

  /** How far ahead the model's own predicted route reaches. */
  public static String routeReach(LaneGeometry.Pose pose) {
    return pose.routeX.length >= 2 && pose.pathReach > 0
        ? String.format("%.0f m predicted", pose.pathReach) : "not given";
  }

  /** What the car's own ACC is doing, from its own transmissions. */
  public static String accState(LaneGeometry.Pose pose) {
    if (!pose.accPresent) {
      return "not reported";
    }
    if (!pose.accOn) {
      return "off";
    }
    String state = pose.accCtrl ? "on, controllable" : "on";
    return pose.accStandstill ? state + ", standstill" : state;
  }

  /** What the car is asking for: tenths of a m/s^2, shown as the car's own figure. */
  public static String accRequest(LaneGeometry.Pose pose) {
    if (!pose.accPresent) {
      return "-";
    }
    return String.format("%+.1f m/s2", pose.accMetersPerSecondSquared());
  }

  /** The gap to a vehicle ahead: metres, and seconds at the current speed. */
  public static String gapTo(LaneGeometry.Lead lead, double speed) {
    if (lead == null || !lead.present) {
      return "none";
    }
    double gap = lead.gapSeconds(speed);
    return gap >= 0 ? String.format("%.0f m  %.1f s", lead.distance, gap)
                    : String.format("%.0f m", lead.distance);
  }

  /** Everything the lane page shows, keyed by the view it belongs to. */
  public static void fill(LaneGeometry.Pose pose, Cache cache, String lens, double halfWidthAt10m,
      Sink sink) {
    put(cache, sink, "speed", pose.ok || pose.displayedSpeed() > 0.05
        ? speed(pose.displayedSpeed()) : "-");
    put(cache, sink, "set", setSpeed(pose.setSpeed));
    put(cache, sink, "caption", pose.summary()
        + (pose.predicted ? "  ·  drawn as the model's prediction, not a lane that was read" : "")
        + (pose.lead.present ? "  ·  lead: ACC tracking (vision)" : "")
        + String.format("  ·  view %s, covers \u00b1%.0f m at 10 m", lens, halfWidthAt10m));
    put(cache, sink, "offset", pose.ok ? String.format("%+.2f m", pose.offset) : "-");
    put(cache, sink, "width", pose.ok ? String.format("%.2f m", pose.width) : "-");
    put(cache, sink, "speed_row", pose.displayedSpeed() > 0.05
        ? String.format("%.0f km/h", pose.displayedSpeed() * 3.6) : "-");
    // The model's plan is a path radius, not a distance ahead. A radius of kilometres means the road
    // is effectively straight, and printing "100000 m" made it look like a plan reaching 100 km.
    double planRadius = Math.abs(pose.curvature) > 1e-9 ? 1.0 / Math.abs(pose.curvature) : 1e9;
    put(cache, sink, "plan", planRadius > 5000.0 ? "straight"
        : String.format("%s, r %.0f m", pose.curvature > 0 ? "right" : "left", planRadius));
    put(cache, sink, "lines", (pose.leftProb < 0 || pose.rightProb < 0) ? "-"
        : String.format("%.2f / %.2f", pose.leftProb, pose.rightProb));
    put(cache, sink, "horizon", pose.ok ? String.format("%.0f m ahead%s", pose.look,
        pose.fallback ? " (closer than the car's own)" : "") : "-");
    put(cache, sink, "age", String.format("%.1f s ago", pose.age));
    put(cache, sink, "engaged", pose.engaged ? "yes" : "no");
    put(cache, sink, "lanes", pose.lanesVisible() + " of 3");
    put(cache, sink, "lead", gapTo(pose.lead, pose.speed));
    put(cache, sink, "lead2", gapTo(pose.lead2, pose.speed));
    put(cache, sink, "drift", drift(pose));
    put(cache, sink, "route", routeReach(pose));
    put(cache, sink, "acc", accState(pose));
    put(cache, sink, "acc_cmd", accRequest(pose));
    if (pose.ok && pose.age > LaneGeometry.POSE_STALE_S) {
      put(cache, sink, "status", String.format("connected, but the lane data is %.0f s old", pose.age));
    }
  }

  private static void put(Cache cache, Sink sink, String key, String value) {
    if (cache.changed(key, value)) {
      sink.put(key, value);
    }
  }
}
