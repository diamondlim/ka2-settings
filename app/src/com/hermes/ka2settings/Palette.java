package com.hermes.ka2settings;

/** The two palettes, with no Android dependency so the contrast can be tested rather than trusted.
 *
 *  A dark mode is easy to get wrong in a way that only shows up on a phone in sunlight: a muted grey
 *  that looked fine on a monitor at midnight is unreadable on a windscreen-mounted screen. Every pair
 *  the app actually draws is checked in the test suite against the WCAG contrast ratio, so a future
 *  colour tweak that quietly makes a row unreadable fails the build instead.
 */
public final class Palette {

  // dark
  public static final int DARK_BG = 0xFF0E1116;
  public static final int DARK_CARD = 0xFF171C23;
  public static final int DARK_TEXT = 0xFFE8EDF2;
  public static final int DARK_MUTED = 0xFF93A0B0;
  public static final int DARK_HAIRLINE = 0xFF232A34;
  public static final int DARK_DANGER = 0xFFF2745F;
  public static final int DARK_CHIP = 0xFF232A34;
  public static final int DARK_CHIP_SELECTED = 0xFF1B2129;
  public static final int DARK_TAB_BAR = 0xFF0A0D11;
  public static final int DARK_FIELD = 0xFF1B2129;
  public static final int DARK_DANGER_WASH = 0xFF3A1E1C;

  // light
  public static final int LIGHT_BG = 0xFFF2F4F7;
  public static final int LIGHT_CARD = 0xFFFFFFFF;
  public static final int LIGHT_TEXT = 0xFF1F2430;
  /** The light theme's hints used to be #8892A4, which is 3.1:1 on a white card - too pale for the
   *  small print it is used for. */
  public static final int LIGHT_MUTED = 0xFF5F6B7C;
  public static final int LIGHT_HAIRLINE = 0xFFEDEFF3;
  public static final int LIGHT_DANGER = 0xFFC0392B;
  public static final int LIGHT_CHIP = 0xFFE4E8EE;
  public static final int LIGHT_CHIP_SELECTED = 0xFF1E2126;
  public static final int LIGHT_TAB_BAR = 0xFF15171B;
  public static final int LIGHT_FIELD = 0xFFF2F4F7;
  public static final int LIGHT_DANGER_WASH = 0xFFFDECEA;

  /** Green that carries white text (selected tab, active toggle): the obvious #2E9E5B only reached
   *  3.4:1 against white, below the 4.5 body-text threshold, so it is a shade deeper. */
  public static final int ACCENT = 0xFF1E7A46;
  public static final int WAIT = 0xFFE0A32E;
  public static final int WHITE = 0xFFFFFFFF;

  private Palette() {
  }

  /** WCAG relative luminance of an 0xAARRGGBB colour, alpha ignored. */
  public static double luminance(int color) {
    double r = channel((color >> 16) & 0xFF);
    double g = channel((color >> 8) & 0xFF);
    double b = channel(color & 0xFF);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double channel(int value) {
    double c = value / 255.0;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
  public static double contrast(int foreground, int background) {
    double a = luminance(foreground);
    double b = luminance(background);
    double lighter = Math.max(a, b);
    double darker = Math.min(a, b);
    return (lighter + 0.05) / (darker + 0.05);
  }
}
