package com.hermes.ka2settings;

/**
 * The car's state, in the four words the owner asked for: off, offroad, onroad, autodrive.
 *
 * "Off" is the app's own view - it means there is no link to the box, so nothing can be read. The other
 * three come from the box, which knows its own two facts: whether openpilot is onroad (the car is awake
 * and openpilot is running) and whether autodrive is engaged. Kept here, and not in the activity, so
 * the mapping is testable without a phone.
 */
public final class States {
  public static final String OFF = "Off";
  public static final String OFFROAD = "Offroad";
  public static final String ONROAD = "Onroad";
  public static final String AUTODRIVE = "Autodrive";

  private States() {
  }

  /** The one word for the current state. */
  public static String name(boolean connected, boolean onroad, boolean engaged) {
    if (!connected) {
      return OFF;
    }
    if (engaged) {
      return AUTODRIVE;             // engaged implies onroad: report what autodrive is doing
    }
    return onroad ? ONROAD : OFFROAD;
  }

  /** What the word means, so the page never leaves the reader guessing. */
  public static String detail(boolean connected, boolean onroad, boolean engaged, boolean controlsReady) {
    if (!connected) {
      return "no link to the box";
    }
    if (engaged) {
      return "autodrive is engaged and driving the car";
    }
    if (onroad) {
      return controlsReady ? "car awake, openpilot watching - ready to engage"
                           : "car awake, but openpilot is not ready to take over yet";
    }
    return "car asleep - openpilot is not running";
  }

  /** The colour key for the dot beside it: see Palette. */
  public static String tone(boolean connected, boolean onroad, boolean engaged) {
    if (!connected) {
      return "muted";
    }
    if (engaged) {
      return "accent";
    }
    return onroad ? "wait" : "muted";
  }
}
