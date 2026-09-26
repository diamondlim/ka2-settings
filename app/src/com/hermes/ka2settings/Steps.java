package com.hermes.ka2settings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The arithmetic behind a +/- stepper - pure, so it can be tested off the phone.
 *
 *  It decides the number that gets sent to the car's controller, which makes it worth more care than
 *  a UI helper usually gets: repeated floating-point adds to 0.05 drift (0.30000000000000004), and a
 *  value between steps would be snapped by the box anyway, so it is snapped here to what will be
 *  stored. Nothing here trusts the typed text either - a row with an unreadable value starts from
 *  the bottom of its range rather than from a half-parsed zero.
 */
public final class Steps {

  private Steps() { }

  /** Parse a value from the box; unreadable text yields the caller's fallback. */
  public static double parse(String text, double fallback) {
    if (text == null) {
      return fallback;
    }
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** One press: move a step in `direction`, snap to the grid, clamp to the declared range.

   *  The snapping is done twice on purpose. Rounding to a whole number of steps fixes the drift of
   *  repeated adds, and rounding *decimally* afterwards fixes the representation: 6 x 0.05 is
   *  "0.30000000000000004" as a double, which would be displayed and sent to the car as that.
   */
  public static double next(double current, int direction, double step, double min, double max) {
    if (!(step > 0) || !Double.isFinite(current)) {
      return current;
    }
    // Clamp to whole steps that fit inside the limits, not to the limits themselves. A ceiling is
    // not always a whole number of steps - a probability capped at 0.99 on a 0.05 grid tops out at
    // 0.95 - and a value clamped to 0.99 is one the box snaps to 1.00 and refuses, which left the
    // row unable to move at all at the top of its range. Same rule as the box, so the two agree.
    double firstStep = Math.ceil(min / step - 1e-9);
    double lastStep = Math.floor(max / step + 1e-9);
    if (lastStep < firstStep) {
      firstStep = lastStep = Math.round(min / step);
    }
    double index = Math.round((current + direction * step) / step);
    index = Math.min(lastStep, Math.max(firstStep, index));
    return tidy(index * step, step);
  }

  /** The top of a range: the largest whole step inside it. */
  public static double usableMax(double step, double min, double max) {
    return next(max, 1, step, min, max);
  }

  /** Decimal rounding at the step's own precision, so 0.25 stays 0.25 and not 0.25000000000000006. */
  private static double tidy(double value, double step) {
    return BigDecimal.valueOf(value).setScale(decimals(step), RoundingMode.HALF_UP).doubleValue();
  }

  /** How many decimals one step needs ("0.05" -> 2, "0.025" -> 3, "1" -> 0). */
  private static int decimals(double step) {
    String probe = BigDecimal.valueOf(step).stripTrailingZeros().toPlainString();
    int dot = probe.indexOf('.');
    return dot < 0 ? 0 : probe.length() - dot - 1;
  }

  /** Print with exactly the precision of one step: 0.05 steps read "0.25", 0.5 m/s reads "7.5". */
  public static String format(double value, double step) {
    if (step >= 1 && value == Math.rint(value)) {
      return String.valueOf((long) value);
    }
    return String.format("%." + decimals(step) + "f", value);
  }

  /** Repeat `count` presses in one direction - what holding the button does. */
  public static double nextMany(double start, int count, double step, double min, double max) {
    double value = start;
    int direction = count < 0 ? -1 : 1;
    for (int i = 0; i < Math.abs(count); i++) {
      value = next(value, direction, step, min, max);
    }
    return value;
  }

  /** True when another press in this direction would still change the value. */
  public static boolean canMove(double current, int direction, double step, double min, double max) {
    return next(current, direction, step, min, max) != current;
  }
}
