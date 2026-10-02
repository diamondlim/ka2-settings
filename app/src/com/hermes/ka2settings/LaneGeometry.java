package com.hermes.ka2settings;

import java.util.List;
import java.util.Map;

/** Turn the box's lane pose into something drawable - and nothing else.
 *
 *  Kept apart from the View on purpose: the geometry is the part that can be wrong in a way a
 *  screenshot hides (a mirrored lane, a stale pose drawn as live), so it is pure Java, unit-tested
 *  on a plain JVM against real captured box lines.
 *
 *  Conventions, taken from the box's own controller rather than chosen here: +x is forward from the
 *  car, +y is to the car's RIGHT, and the car sits at the origin. A pose older than two seconds is
 *  treated as no pose at all - the box may be offroad, asleep, or the publisher dead.
 */
public final class LaneGeometry {

  public static final double POSE_STALE_S = 2.0;
  public static final double RANGE_M = 200.0;       // metres of road the view can reach; the predicted
                                                    // route runs to the model's own ten-second horizon,
                                                    // which is further the faster the car is going

  /**
   * Carries the last segment of a line on to `toX`, for showing a lane where the model has stopped
   * reading it. This is a straight-line guess, not a measurement: it is right on a straight road and
   * wrong on a bend, which is why the view draws it dotted and dimmer than the real thing.
   *
   * Returns {x0, y0, x1, y1} for the added stretch, or null when there is nothing to add.
   */
  /**
   * Road edges taken from the model's predicted route rather than from its lane lines: the route is
   * shifted sideways by half a lane. This is why it is better than a straight line - the route already
   * bends where the model expects the road to bend, so the edges bend with it.
   */
  public static double[] routeEdge(double[] routeY, boolean leftSide, double halfWidth) {
    double[] out = new double[routeY.length];
    for (int i = 0; i < routeY.length; i++) {
      out[i] = routeY[i] + (leftSide ? -halfWidth : halfWidth);
    }
    return out;
  }

  public static double[] extrapolateTo(double[] xs, double[] ys, double toX) {
    int n = Math.min(xs.length, ys.length);
    if (n < 2) {
      return null;
    }
    double xLast = xs[n - 1], yLast = ys[n - 1];
    double xPrev = xs[n - 2], yPrev = ys[n - 2];
    if (!(xLast > xPrev) || xLast >= toX) {
      return null;                                    // flat, backwards or already reaching
    }
    double slope = (yLast - yPrev) / (xLast - xPrev);
    return new double[] {xLast, yLast, toX, yLast + slope * (toX - xLast)};
  }

  public static final class Pose {
    public boolean ok;
    public String why = "";
    public double age;
    public double speed;          // m/s
    public double setSpeed;       // the car's own ACC set speed, m/s, 0 when cruise is not set
    public boolean engaged;
    public double offset;         // lane centre minus car position, + = centre is to the right
    public double width;          // lane width at the lookahead
    public double curvature;
    public double leftProb = -1, rightProb = -1;
    public double look;                    // distance the offset was measured at
    public double carLook;                 // the horizon the car's own correction uses
    public boolean fallback;               // measured closer than the car's own horizon
    public Lead lead = new Lead();
    public Lead lead2 = new Lead();
    // The model's own predicted trajectory ("route") and the lane centre the car's correction works
    // from ("calc"). Different things from the lane lines: one is where the model expects to go, the
    // other is the geometry the car's own lane-centre logic derives.
    public double[] routeX = new double[0], routeY = new double[0];
    public double[] centreX = new double[0], centreY = new double[0];
    public double pathReach;
    // The speed the car's own dashboard shows, which is not the true speed: see the box's note.
    public double dashSpeed;

    /**
     * The speed to show a driver: the car's own displayed speed when the box sends one.
     *
     * The two differ by the fork's HUD factor, and on this car the dashboard is the real one - the
     * wheel-speed calibration openpilot was given reads about 12% low. The geometry still works in the
     * box's own metres; this is only about the number on the screen.
     */
    public double displayedSpeed() {
      return dashSpeed > 0.05 ? dashSpeed : speed;
    }

    public double[] leftX = new double[0], leftY = new double[0];
    public double[] rightX = new double[0], rightY = new double[0];
    /** The far edge of the lane on each side, which is what makes three lanes visible rather than
     *  one. Empty when the model is not confident in that side. */
    public double[] outerLeftX = new double[0], outerLeftY = new double[0];
    public double[] outerRightX = new double[0], outerRightY = new double[0];

    /** How many lanes the box is showing: the car's own, plus a neighbour for each side whose outer
     *  edge the model is confident in. Three is the model's limit - it emits four lane lines). */
    public int lanesVisible() {
      int lanes = (leftX.length > 0 && rightX.length > 0) ? 1 : 0;
      if (outerLeftX.length > 0) {
        lanes++;
      }
      if (outerRightX.length > 0) {
        lanes++;
      }
      return lanes;
    }

    // The car's own acceleration request, read off its ACC_CMD message. The byte is in tenths of a
    // m/s^2: the port's own packer clamps it to [-80, +30], i.e. -8.0 to +3.0 m/s^2, which is exactly
    // an acceleration envelope. Shown as the raw figure so nothing is claimed beyond the reading.
    // The lane the model predicts when the markings are too faint for the car to steer on. Drawn as
    // a prediction, never as a lane that was read.
    public boolean predicted;

    public boolean accPresent;
    public int accCmd;
    public boolean accOn;
    public boolean accCtrl;
    public boolean accOverride;
    public boolean accStandstill;

    /** How much the car's own ACC is asking for, in tenths of a m/s^2. */
    public double accMetersPerSecondSquared() {
      return accCmd / 10.0;
    }

    /**
     * How far ahead both lane lines actually carry data: the nearer of their two ends, so the marker
     * means "both lines reach here", not "one of them does". Zero when there is no lane at all.
     */
    public double dataReach() {
      if (leftX.length == 0 || rightX.length == 0) {
        return 0;
      }
      return Math.min(leftX[leftX.length - 1], rightX[rightX.length - 1]);
    }

    /**
     * How far the car sits from the lane centre, at each point of the centre line: the width of the
     * centring drift. Positive is to the right, because +y is to the car's right everywhere here.
     */
    public double[] driftAt(double[] distances) {
      double[] out = new double[distances.length];
      if (centreX.length < 2) {
        return out;
      }
      for (int i = 0; i < distances.length; i++) {
        out[i] = centreOffsetAt(distances[i]);
      }
      return out;
    }

    /** The lane centre's lateral position at a distance ahead, interpolated between its points. */
    public double centreOffsetAt(double ahead) {
      if (centreX.length == 0) {
        return Double.NaN;
      }
      if (ahead <= centreX[0]) {
        return centreY[0];
      }
      for (int i = 1; i < centreX.length; i++) {
        if (ahead <= centreX[i]) {
          double span = centreX[i] - centreX[i - 1];
          if (span <= 0) {
            return centreY[i];
          }
          double t = (ahead - centreX[i - 1]) / span;
          return centreY[i - 1] + t * (centreY[i] - centreY[i - 1]);
        }
      }
      return centreY[centreY.length - 1];
    }

    /** The drift the car's own correction acts on: the offset at its lookahead, in metres. */
    public double drift() {
      return ok ? offset : centreOffsetAt(look > 0 ? look : 20.0);
    }

    public String summary() {
      if (!ok) {
        return why.isEmpty() ? "no lane data" : why;
      }
      // rounded to what a person reads, not to what the arithmetic produces: a caption that changes
      // on every 0.01 m of lane wobble is a layout pass ten times a second for nothing
      String side = Math.abs(offset) < 0.05 ? "centred"
          : (offset > 0 ? String.format("%.1f m right of centre", offset)
                        : String.format("%.1f m left of centre", -offset));
      // A pose measured closer than the car's own horizon is labelled as such: the number is
      // real, but it is not the geometry the car is steering from at speed.
      String horizon = fallback && carLook > 0
          ? String.format(" (at %.0f m - the lane is not visible at the car's %.0f m)", look, carLook)
          : "";
      return String.format("%s - lane %.1f m - %.0f km/h%s%s", side, width, speed * 3.6,
                           engaged ? " - engaged" : "", horizon);
    }
  }

  /** Parse one POSE line's JSON. Age is recomputed here from the box's own timestamp, so a
   *  publisher that has died shows up as "stale" instead of a frozen lane drawn as if live. */
  /** A vehicle ahead, as the car's own adaptive cruise is tracking it.
   *
   *  The position is the real datum. The marker the view draws for it is a nominal car-sized box,
   *  because nothing in the message says how big the vehicle is.
   */
  public static final class Lead {
    public boolean present;
    public double distance;     // metres ahead
    public double lateral;      // metres right of the car's centre line
    public double relSpeed;     // m/s, negative = closing
    public double prob = -1;    // the model's confidence in this being the lead, -1 if not sent

    /** Seconds of gap at the current speed, or -1 when too slow for that to mean anything. */
    public double gapSeconds(double speed) {
      return speed > 1.0 ? distance / speed : -1;
    }
  }

  private static Lead leadFrom(Map<String, Object> row, String key) {
    Lead lead = new Lead();
    Object raw = row.get(key);
    if (!(raw instanceof Map)) {
      return lead;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> fields = (Map<String, Object>) raw;
    lead.distance = Json.num(fields, "d", 0.0);
    lead.lateral = Json.num(fields, "y", 0.0);
    lead.relSpeed = Json.num(fields, "vr", 0.0);
    lead.prob = Json.num(fields, "p", -1.0);
    lead.present = lead.distance > 0.5;
    return lead;
  }

  /** A GNSS state older than this is not shown as a fix - the box's publisher writes about once a
   *  second, so this only ever catches a publisher that has stopped or a link that has gone quiet. */
  public static final double GPS_STALE_S = 15.0;

  /** The box's own GNSS state, as the lane page shows it.
   *
   *  Nothing here is worked out on the phone: the box decides whether it has a fix and says why not.
   *  The reason matters more than the fix does - "12 of 14 satellites report signal" is a car parked
   *  under cover, while "GNSS publisher not running" is the box's service being down, and those want
   *  different reactions from whoever is looking at the screen. The position is gated on `ok` for the
   *  same reason a stale pose is: a coordinate left behind by a fix that has since been lost is a lie
   *  that looks like data.
   */
  public static final class Gps {
    public boolean ok;
    public String why = "";
    public String source = "";      // which publisher answered: "modem-at", or a serial bridge
    public double age = -1.0;       // seconds since the publisher wrote it, -1 = not given
    public double lat, lon, alt, hdop, bearing;
    public int sats;
    public boolean stale;           // the box said the state itself was too old to trust

    public boolean hasFix() {
      return ok && !stale && (lat != 0.0 || lon != 0.0);
    }

    /** The one-line verdict for the GPS row: what the box has, not what the phone wishes it had. */
    public String summary() {
      if (stale) {
        return "not answering";
      }
      if (!ok) {
        return sats > 0 ? String.format("no fix · %d sats", sats) : "no fix";
      }
      return String.format("fix · %d sats", sats);
    }

    /** The position, or a dash when there is no fix to place - never a coordinate from a lost fix. */
    public String position() {
      return hasFix() ? String.format("%.5f, %.5f", lat, lon) : "-";
    }

    /** The long form, for the caption: the box's own reason when there is none to show. */
    public String detail() {
      if (stale) {
        return why.isEmpty() ? "no GNSS state" : why;
      }
      if (!ok) {
        return why.isEmpty() ? "no fix" : why;
      }
      String where = hasFix() ? String.format(" at %.5f, %.5f", lat, lon) : "";
      String hdop = this.hdop > 0.0 ? String.format(", HDOP %.1f", this.hdop) : "";
      String ageText = age >= 0.0 ? String.format(", %.0f s ago", age) : "";
      return String.format("%d sats%s%s%s", sats, hdop, ageText, where);
    }
  }

  /** Parse one GPS line's JSON, applying the same staleness rule the box applies to a pose. */
  public static Gps gpsFromJson(Map<String, Object> row, double nowSeconds) {
    Gps gps = new Gps();
    gps.ok = Json.bool(row, "ok", false);
    gps.why = Json.str(row, "why", "");
    gps.source = Json.str(row, "source", "");
    gps.stale = Json.bool(row, "stale", false) || Json.intOr(row, "stale", 0) == 1;
    double age = Json.num(row, "age", -1.0);
    if (age < 0.0 && Json.num(row, "at", 0.0) > 0.0) {
      age = nowSeconds - Json.num(row, "at", 0.0);      // a publisher's raw file, if one ever lands
    }
    gps.age = age;
    gps.lat = Json.num(row, "lat", 0.0);
    gps.lon = Json.num(row, "lon", 0.0);
    gps.alt = Json.num(row, "alt", 0.0);
    gps.hdop = Json.num(row, "hdop", 0.0);
    gps.bearing = Json.num(row, "bearing", 0.0);
    gps.sats = Json.intOr(row, "sats", 0);
    if (gps.age > GPS_STALE_S) {                        // the link is up but this row is cold
      gps.ok = false;
      gps.stale = true;
      gps.why = String.format("no fresh GNSS state (last was %.0f s ago)", gps.age);
    }
    return gps;
  }

  public static Pose fromJson(Map<String, Object> row, double nowSeconds) {
    Pose pose = new Pose();
    pose.age = nowSeconds - Json.num(row, "t", 0.0);
    pose.speed = Json.num(row, "v", 0.0);
    pose.setSpeed = Json.num(row, "set", 0.0);
    pose.lead = leadFrom(row, "lead");
    pose.lead2 = leadFrom(row, "lead2");
    pose.engaged = Json.bool(row, "eng", false);
    pose.predicted = Json.bool(row, "low", false);
    Map<String, Object> acc = Json.obj(row, "acc");
    if (acc != null) {
      pose.accPresent = true;
      pose.accCmd = Json.intOr(acc, "cmd", 0);
      pose.accOn = Json.bool(acc, "on", false) || Json.bool(acc, "on2", false);
      pose.accCtrl = Json.bool(acc, "ctrl", false);
      pose.accOverride = Json.bool(acc, "ovr", false);
      pose.accStandstill = Json.bool(acc, "still", false);
    }
    pose.offset = Json.num(row, "off", 0.0);
    pose.width = Json.num(row, "w", 0.0);
    pose.curvature = Json.num(row, "curv", 0.0);
    pose.look = Json.num(row, "look", 0.0);
    pose.carLook = Json.num(row, "car_look", 0.0);
    pose.fallback = Json.bool(row, "fallback", false);
    pose.leftProb = Json.num(row, "lp", -1.0);
    pose.rightProb = Json.num(row, "rp", -1.0);
    pose.routeX = points(row, "path", 0);
    pose.routeY = points(row, "path", 1);
    pose.centreX = points(row, "calc", 0);
    pose.centreY = points(row, "calc", 1);
    pose.pathReach = Json.num(row, "path_reach", 0.0);
    pose.dashSpeed = Json.num(row, "vd", 0.0);
    pose.leftX = points(row, "l", 0);
    pose.leftY = points(row, "l", 1);
    pose.outerLeftX = points(row, "l2", 0);
    pose.outerLeftY = points(row, "l2", 1);
    pose.outerRightX = points(row, "r2", 0);
    pose.outerRightY = points(row, "r2", 1);
    pose.rightX = points(row, "r", 0);
    pose.rightY = points(row, "r", 1);

    pose.why = Json.str(row, "why", "");
    boolean flaggedOk = Json.bool(row, "ok", false);
    if (pose.age > POSE_STALE_S) {
      pose.ok = false;
      pose.why = String.format("no fresh pose (last was %.1f s ago)", pose.age);
      return pose;
    }
    if (pose.age < -5.0) {                       // box clock ahead of the phone's
      pose.ok = false;
      pose.why = "clock mismatch between phone and box";
      return pose;
    }
    pose.ok = flaggedOk && pose.leftX.length >= 2 && pose.rightX.length >= 2;
    if (!pose.ok && pose.why.isEmpty()) {
      pose.why = "no lane data yet";
    }
    return pose;
  }

  private static double[] points(Map<String, Object> row, String key, int index) {
    List<Object> list = Json.list(row, key);
    if (list == null) {
      return new double[0];
    }
    double[] out = new double[list.size()];
    int n = 0;
    for (Object item : list) {
      double[] pair = Json.pair(item);
      if (pair != null) {
        out[n++] = pair[index];
      }
    }
    if (n == out.length) {
      return out;
    }
    double[] trimmed = new double[n];
    System.arraycopy(out, 0, trimmed, 0, n);
    return trimmed;
  }

  /** Perspective projection: a camera on the car looking forward over a flat road.
   *
   *  This is what makes the lane read as a road rather than a diagram, and it is real geometry, not a
   *  drawing trick: the car sits at the origin with +x forward and +y to its right, the camera is
   *  CAM_HEIGHT_M above the tarmac, and everything is projected with one focal length. The
   *  consequences fall out for free and are what the eye recognises - lane edges converging to a
   *  vanishing point on the horizon, a constant-distance line drawn straight across the road, and
   *  lane width in pixels shrinking with distance. NEAR_M keeps a division by a vanishing x from
   *  throwing a line off the screen.
   */
  public static final class Camera {
    public static final double CAM_HEIGHT_M = 1.35;
    private final double camHeight;      // set per view: see the raised-viewpoint constructor
    /** Nothing nearer than this is ever drawn, so a line can never be thrown off screen by a
     *  division by ~zero. */
    public static final double MIN_NEAR_M = 0.8;
    /** How much of the view above the horizon is sky. */
    // How much of the page is sky. It is pure overhead: the horizon is the vanishing point, so a
    // smaller sky gives the same perspective more screen to spread over - more road, no distortion.
    // 0.14 still leaves the speed readout clear of the tarmac.
    private static final double SKY_FRACTION = 0.14;

    /** Three lenses, because two of the owner's wishes pull against each other: magnification puts
     *  distance on the screen but narrows how much road either side is in frame. At 10 m ahead the
     *  frame covers +/- (width/2) * 10 / focal metres - about 3.3 m on the far lens (the car's lane
     *  only) and 10.5 m on the wide one (three lanes and the verge). */
    public static final double FAR_FOCAL = 1.50;
    public static final double BALANCED_FOCAL = 1.10;
    public static final double WIDE_FOCAL = 0.75;
    public static final double DEFAULT_FOCAL_FRACTION = FAR_FOCAL;

    private final float width;
    private final float height;
    private final float focal;
    private final float horizon;

    public Camera(float viewWidth, float viewHeight) {
      this(viewWidth, viewHeight, DEFAULT_FOCAL_FRACTION, CAM_HEIGHT_M);
    }

    public Camera(float viewWidth, float viewHeight, double focalFraction) {
      this(viewWidth, viewHeight, focalFraction, CAM_HEIGHT_M);
    }

    /**
     * `heightM` is where the view is drawn *from*. 1.35 m is where the camera really sits; anything
     * higher is a raised viewpoint - the road opens out towards a plan view, which is a reading aid,
     * not a claim about the car.
     */
    public Camera(float viewWidth, float viewHeight, double focalFraction, double heightM) {
      this.camHeight = heightM > 0 ? heightM : CAM_HEIGHT_M;
      this.width = viewWidth;
      this.height = viewHeight;
      // 1.50 rather than 0.62 of the width. The owner asked to see 100 m rather than 20 m, and on a
      // flat road the only lever is magnification. Note what it does and does not buy: a 20 m lane
      // goes from 103 px to 249 px wide, but the 50-100 m band is still only ~22 px tall, because
      // perspective compresses distance as 1/x and no focal length changes that. Making 100 m truly
      // legible needs a non-linear vertical scale, which stops looking like a road.
      this.focal = (float) (viewWidth * focalFraction);
      this.horizon = (float) (viewHeight * SKY_FRACTION); // sky above, road below
    }

    public float horizonY() { return horizon; }

    /** How far either side of the centre line the frame reaches at a given distance. */
    public double halfWidthAt(double ahead) {
      return (width / 2.0) * ahead / focal;
    }

    /** The nearest road actually inside the frame, which depends on how tall the view is rather than
     *  on a fixed number of metres.
     *
     *  A tall portrait view looks steeply down at the bottom, so the nearest visible tarmac is nearer
     *  than the car's own bonnet: draw from a fixed distance instead and the bottom of the screen is
     *  a dead grey band with the road floating above it. Derived per frame, the road runs off the
     *  bottom edge in any shape of view - portrait, landscape or a short card.
     */
    public double nearDistance() {
      return Math.max(MIN_NEAR_M, focal * camHeight / (height - horizon));
    }

    /** Screen x of a point on the road plane. */
    public float px(double forward, double lateral) {
      return (float) (width / 2.0 + focal * lateral / Math.max(forward, MIN_NEAR_M));
    }

    /** Screen y of a point on the road plane; the ground recedes towards the horizon. */
    public float py(double forward) {
      return (float) (horizon + focal * camHeight / Math.max(forward, MIN_NEAR_M));
    }

    /** Pixels per metre at a given distance - the size of the lane at that depth. */
    public float pxPerMetreAt(double forward) {
      return (float) (focal / Math.max(forward, MIN_NEAR_M));
    }

    public float centreX() { return width / 2f; }
  }
}
