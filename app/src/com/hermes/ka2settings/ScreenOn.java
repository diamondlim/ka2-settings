package com.hermes.ka2settings;

/**
 * Whether the phone's screen should stay on.
 *
 * The owner asked for the app to stay up while autodrive is driving. It is not only a display
 * preference: Android suspends a backgrounded app, which is what drops the Bluetooth link, so keeping
 * the screen on while the car is being driven is what keeps the readouts - and the state line - live.
 * Kept here rather than in the activity so the rule is testable without a phone.
 */
public final class ScreenOn {
  public static final String OFF = "off";
  public static final String CONNECTED = "connected";
  public static final String AUTODRIVE = "autodrive";

  private ScreenOn() {
  }

  /** The three choices, in the order the picker shows them. */
  public static final String[] CHOICES = {AUTODRIVE, CONNECTED, OFF};

  public static String label(String choice) {
    if (CONNECTED.equals(choice)) {
      return "While connected";
    }
    if (OFF.equals(choice)) {
      return "Off";
    }
    return "While autodrive";
  }

  /** The choice after this one, so a single button can cycle the three. */
  public static String next(String choice) {
    for (int i = 0; i < CHOICES.length; i++) {
      if (CHOICES[i].equals(choice)) {
        return CHOICES[(i + 1) % CHOICES.length];
      }
    }
    return CHOICES[0];
  }

  /**
   * Whether to hold the screen on right now. Autodrive is the default because that is when a sleeping
   * phone costs the most: it is the one case where the app going quiet costs a live readout of a car
   * that is driving itself.
   */
  public static boolean keep(String choice, boolean connected, boolean engaged) {
    if (OFF.equals(choice) || !connected) {
      return false;
    }
    return CONNECTED.equals(choice) || engaged;
  }
}
