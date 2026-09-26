package com.hermes.ka2settings;

/**
 * Where the sliding readings tab sits. Kept away from the screen so the arithmetic can be tested:
 * the tab is open at zero offset and closed by exactly its own width, which is what leaves the
 * handle showing at the edge of the page.
 */
public final class Panel {

  private Panel() {
  }

  public static float offset(boolean open, int widthPx) {
    return open ? 0f : -widthPx;
  }

  /** The handle points left while the tab is out, and right while it is tucked away. */
  public static String handle(boolean open) {
    return open ? "\u2039" : "\u203A";
  }

  public static boolean next(boolean open) {
    return !open;
  }
}
