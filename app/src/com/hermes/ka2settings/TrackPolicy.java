package com.hermes.ka2settings;

/**
 * When to start and stop recording the phone's track, automatically.
 *
 * The owner's ask was "start recording when the app is open and moving", which is a decision, not a
 * gesture - so it lives here as plain arithmetic that can be tested without a phone, a car or a GPS.
 *
 * Two thresholds, deliberately apart (start high, stop low) so a car creeping in traffic does not
 * rattle the state machine on and off, and a dwell before stopping so a red light or a fuel stop does
 * not chop one drive into three recordings. A manual stop suppresses automatic starts for the rest of
 * the session: automation that argues with the driver is worse than no automation.
 */
public final class TrackPolicy {

  /** Recording starts at or above this, in km/h. */
  public static final double START_KPH = 12.0;

  /** ...and stops only once it has stayed below this for the dwell below. */
  public static final double STOP_KPH = 5.0;

  /** How long the car must stay slow before a recording is closed off (five minutes). */
  public static final long STOP_AFTER_MS = 5 * 60 * 1000L;

  public enum Decision {
    START,
    STOP,
    HOLD
  }

  private TrackPolicy() {
  }

  /**
   * @param autoOn      the owner wants automatic recording
   * @param recording   a recording is running right now
   * @param suppressed  a manual stop happened this session (or automatic recording was switched off)
   * @param speedKph    the phone's current speed, or negative when there is no fix yet
   * @param msBelowStop how long the speed has been under STOP_KPH
   */
  public static Decision decide(boolean autoOn, boolean recording, boolean suppressed,
                                double speedKph, long msBelowStop) {
    if (!autoOn || suppressed || speedKph < 0) {
      return Decision.HOLD;                 // no fix is not evidence of movement, or of standing still
    }
    if (!recording) {
      return speedKph >= START_KPH ? Decision.START : Decision.HOLD;
    }
    return msBelowStop >= STOP_AFTER_MS ? Decision.STOP : Decision.HOLD;
  }

  /** What to say under the button, so the driver can tell automation from his own tap. */
  public static String describe(boolean autoOn, boolean suppressed) {
    if (!autoOn) {
      return "Auto-record is off: tap the button to record by hand.";
    }
    if (suppressed) {
      return "You stopped this recording by hand, so auto-record is paused until you reopen the app.";
    }
    return "Auto-record is on: recording starts once you are moving (" + (int) START_KPH
        + " km/h) and stops after " + (STOP_AFTER_MS / 60000L) + " minutes stopped.";
  }
}
