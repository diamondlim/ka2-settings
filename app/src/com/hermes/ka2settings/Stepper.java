package com.hermes.ka2settings;

/** The state machine behind one +/- row.
 *
 *  Two bugs came out of doing this with a bare value string, and both were visible on the phone:
 *
 *  1. **Format flicker.** The app renders a value with its step's precision ("0.50"), the box echoes
 *     the same number through `%g` ("0.5"), so every reply changed the shape of the number on screen.
 *     Here the display string is always produced by one formatter, whatever arrived over the link.
 *  2. **A late reply moved the display backwards.** Presses are optimistic so the button feels
 *     immediate, but on this link a reply can arrive after the *next* press has already gone out
 *     (the lane-pose stream shares the socket). Applying that stale echo put the number back a step
 *     - and since the end stops are derived from the displayed value, the row could end up looking
 *     stuck at the top or bottom of its range. A reply is now applied only if it matches the value
 *     that is actually in flight.
 *
 *  Pure and unit-tested: this decides what gets sent to a control loop and what the owner reads back.
 */
public final class Stepper {

  private final double step;
  private final double min;
  private final double max;
  private String value;      // what the row shows, always in the step's own precision
  private String pending;    // the value last sent, still waiting for the box to confirm it

  public Stepper(double step, double min, double max, String initial) {
    this.step = step;
    this.min = min;
    this.max = max;
    this.value = Steps.format(Steps.parse(initial, min), step);
  }

  public String value() {
    return value;
  }

  public boolean canMove(int direction) {
    return Steps.canMove(Steps.parse(value, min), direction, step, min, max);
  }

  public boolean atLowerLimit() {
    return !canMove(-1);
  }

  public boolean atUpperLimit() {
    return !canMove(1);
  }

  /** One press: returns the value to send, or null when that direction is already at its end. */
  public String press(int direction) {
    double next = Steps.next(Steps.parse(value, min), direction, step, min, max);
    String formatted = Steps.format(next, step);
    if (formatted.equals(value)) {
      return null;
    }
    value = formatted;
    pending = formatted;
    return formatted;
  }

  /** A reply from the box. Returns true if it was applied, false if it was a stale echo.
   *
   *  `counted` is how many presses are outstanding: only the value in flight is honoured, so a late
   *  reply cannot undo a newer press.
   */
  public boolean applyReply(String boxValue) {
    double reported = Steps.parse(boxValue, Steps.parse(value, min));
    String formatted = Steps.format(reported, step);
    if (pending == null) {
      value = formatted;                      // nothing in flight: the box is the truth
      return true;
    }
    if (formatted.equals(pending)) {
      pending = null;
      value = formatted;
      return true;
    }
    return false;                             // an older reply: ignore it rather than jump back
  }

  /** Re-sync from a fresh SCHEMA (after a refusal, a reconnect, or an external change). */
  public void reset(String boxValue) {
    pending = null;
    value = Steps.format(Steps.parse(boxValue, min), step);
  }

  public boolean hasPending() {
    return pending != null;
  }
}
