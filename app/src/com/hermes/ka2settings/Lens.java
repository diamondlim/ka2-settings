package com.hermes.ka2settings;

/**
 * Which lens the lane view uses, including an automatic one that follows the speed.
 *
 * The owner asked for a view mode where the lens changes with speed and, at 80 km/h and above, shows as
 * far as it can. That is what AUTO does, with a margin below each threshold so a car sitting exactly on
 * 80 km/h does not flicker between two lenses - climbing at the threshold, coming back down a little
 * under it. Kept out of the activity so the ladder can be tested without a phone.
 */
public final class Lens {
  public static final String AUTO = "auto";
  public static final String FAR = "far";
  public static final String BALANCED = "balanced";
  public static final String WIDE = "wide";

  /** In the order the button cycles them, the automatic one first. */
  public static final String[] CHOICES = {AUTO, FAR, BALANCED, WIDE};
  public static final String HEIGHT_HIGHER = "higher";

  // Climb at the threshold, fall back only below the margin.
  public static final double AUTO_FAR_KPH = 80.0;
  public static final double AUTO_FAR_BACK_KPH = 74.0;
  public static final double AUTO_MID_KPH = 45.0;
  public static final double AUTO_MID_BACK_KPH = 40.0;

  private Lens() {
  }

  public static String next(String choice) {
    for (int i = 0; i < CHOICES.length; i++) {
      if (CHOICES[i].equals(choice)) {
        return CHOICES[(i + 1) % CHOICES.length];
      }
    }
    return CHOICES[0];
  }

  /** The focal length for a fixed choice, or 0 when the choice is automatic. */
  public static double fixed(String choice) {
    if (FAR.equals(choice)) {
      return LaneGeometry.Camera.FAR_FOCAL;
    }
    if (BALANCED.equals(choice)) {
      return LaneGeometry.Camera.BALANCED_FOCAL;
    }
    if (WIDE.equals(choice)) {
      return LaneGeometry.Camera.WIDE_FOCAL;
    }
    return 0;
  }

  /**
   * The focal length to draw with now. `previous` is what is on screen, which is what makes the
   * hysteresis work: a value only moves when it is past the threshold in the direction it is going.
   */
  public static double focal(String choice, double speedMps, double previous) {
    double fixedFocal = fixed(choice);
    if (fixedFocal > 0) {
      return fixedFocal;
    }
    double kph = speedMps * 3.6;
    double current = previous > 0 ? previous : LaneGeometry.Camera.BALANCED_FOCAL;
    // 80 km/h and above: as far as it can show. Staying until 74 is what stops the flicker.
    if (kph >= AUTO_FAR_KPH || (current >= LaneGeometry.Camera.FAR_FOCAL && kph >= AUTO_FAR_BACK_KPH)) {
      return LaneGeometry.Camera.FAR_FOCAL;
    }
    // In town: the wider view, which is what makes three lanes visible at close range.
    if (kph >= AUTO_MID_KPH || (current >= LaneGeometry.Camera.BALANCED_FOCAL && kph >= AUTO_MID_BACK_KPH)) {
      return LaneGeometry.Camera.BALANCED_FOCAL;
    }
    return LaneGeometry.Camera.WIDE_FOCAL;
  }

  /** Raised viewpoints. Only "real" is where the camera actually is. */
  public static final String[] HEIGHT_CHOICES = {"real", "higher", "highest", "extra"};

  public static String heightLabel(String choice) {
    if ("higher".equals(choice)) {
      return "Height: higher";
    }
    if ("highest".equals(choice)) {
      return "Height: highest";
    }
    return "extra".equals(choice) ? "Height: extra high" : "Height: real";
  }

  public static String nextHeight(String choice) {
    for (int i = 0; i < HEIGHT_CHOICES.length; i++) {
      if (HEIGHT_CHOICES[i].equals(choice)) {
        return HEIGHT_CHOICES[(i + 1) % HEIGHT_CHOICES.length];
      }
    }
    return HEIGHT_CHOICES[0];
  }

  /** Metres the view is drawn from: the camera's real height, then two raised viewpoints. */
  public static double cameraHeight(String choice) {
    if ("higher".equals(choice)) {
      return 2.4;
    }
    if ("highest".equals(choice)) {
      return 3.8;
    }
    return "extra".equals(choice) ? 5.2 : LaneGeometry.Camera.CAM_HEIGHT_M;
  }

  /** The route reach the view is scaled around: the model's ten-second horizon at a normal road speed. */
  public static final double REFERENCE_REACH_M = 120.0;

  /**
   * How much to magnify the view for a route of this reach.
   *
   * The camera height and the focal length together set one number - how much distance the page spans
   * - and it is a trade: spread a distant stretch over more of the page and the near road goes off the
   * bottom. So this only ever magnifies (never above 2.2x) and only as the prediction shortens. A car
   * crawling with a 7 m route gets a view of the road it is actually about to cover; at speed the
   * scale stays at 1 and the near road is untouched.
   */
  public static double scaleForReach(double reachM) {
    double reach = Math.max(reachM, 20.0);          // parked creep must not zoom to absurdity
    double scale = Math.sqrt(REFERENCE_REACH_M / reach);
    return Math.max(1.0, Math.min(2.2, scale));
  }

  /**
   * The focal length to draw with. The prediction scale belongs to Auto alone: a lens the driver
   * picked by hand means exactly what it says, and multiplying it would make "wide" as narrow as
   * "far" without the button admitting it.
   */
  public static double drawnFocal(String choice, double speedMps, double previous, double reachM) {
    double base = focal(choice, speedMps, previous);
    return AUTO.equals(choice) ? base * scaleForReach(reachM) : base;
  }

  /** What a focal length is called. */
  public static String name(double focal) {
    if (focal == LaneGeometry.Camera.FAR_FOCAL) {
      return "far";
    }
    return focal == LaneGeometry.Camera.WIDE_FOCAL ? "wide" : "balanced";
  }

  /** The button's text: the choice, and for the automatic one which lens it is using right now. */
  public static String label(String choice, double applied) {
    if (AUTO.equals(choice)) {
      return "Auto (" + name(applied) + ")";
    }
    if (FAR.equals(choice)) {
      return "View: far";
    }
    return BALANCED.equals(choice) ? "View: balanced" : "View: wide";
  }
}
