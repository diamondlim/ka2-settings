package com.hermes.ka2settings;

/** Which Bluetooth devices are the box.
 *
 *  Deliberately Android-free so the rule can be tested: the cost of getting it wrong is an app that
 *  silently pairs with the car's head unit or a pair of headphones, and that is not something to
 *  discover in a car park.
 */
public final class BoxName {

  /** Serial Port Profile, the service the box listens on. */
  public static final String SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb";

  /** The box's own Bluetooth address. A name is a label anyone can copy; the address is the device,
   *  and the app is only ever allowed to connect to this one. Set from the box's own adapter
   *  (`bluetoothctl show` -> Controller 9C:B8:B4:5F:0A:37). */
  public static final String DEFAULT_BOX_MAC = "9C:B8:B4:5F:0A:37";

  private BoxName() {
  }

  /** The box advertises as kommu-<dongle id>. A prefix test, so "mykommu" is not our box. */
  public static boolean looksLikeBoxName(String name) {
    return name != null && name.trim().toLowerCase().startsWith("kommu");
  }

  /** A Bluetooth address in the one format Android accepts: six hex pairs, colon separated. */
  public static boolean isMac(String address) {
    if (address == null) {
      return false;
    }
    String value = address.trim().toUpperCase();
    if (value.length() != 17) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean separator = (i % 3) == 2;
      if (separator) {
        if (c != ':') {
          return false;
        }
      } else if (Character.digit(c, 16) < 0) {
        return false;
      }
    }
    return true;
  }

  /** Upper-case and trimmed, so a typed or remembered address compares equal to the box's own. */
  public static String normalizeMac(String address) {
    return address == null ? "" : address.trim().toUpperCase();
  }

  /** True if any advertised UUID is the serial-port profile (the box is findable even if renamed). */
  public static boolean hasSerialPort(String[] uuids) {
    if (uuids == null) {
      return false;
    }
    for (String uuid : uuids) {
      if (uuid != null && uuid.toLowerCase().equals(SPP_UUID)) {
        return true;
      }
    }
    return false;
  }
}
