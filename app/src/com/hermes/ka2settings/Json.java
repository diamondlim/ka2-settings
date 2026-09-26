package com.hermes.ka2settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A tiny JSON reader, small enough to have no dependency at all.
 *
 *  The device speaks JSON per line (INFO / SCHEMA rows / POSE), and Android ships org.json, but a
 *  hand-written reader keeps the whole app buildable and unit-testable on a plain JVM - which is
 *  how the client-side parsing is tested against real captured box output rather than invented
 *  samples. Only what the protocol uses is supported: objects, arrays, strings, numbers, booleans
 *  and null. Anything else is a parse error, not a silent default.
 */
public final class Json {

  public static class JsonException extends Exception {
    public JsonException(String message) { super(message); }
  }

  private final String src;
  private int pos;

  private Json(String src) { this.src = src; }

  /** Parse one JSON object. Throws rather than returning a half-filled map. */
  public static Map<String, Object> parseObject(String text) throws JsonException {
    Json parser = new Json(text);
    parser.skipWhitespace();
    Object value = parser.readValue();
    parser.skipWhitespace();
    if (!(value instanceof Map)) {
      throw new JsonException("not an object: " + preview(text));
    }
    if (parser.pos != text.length()) {
      throw new JsonException("trailing data at " + parser.pos + ": " + preview(text));
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) value;
    return map;
  }

  private static String preview(String text) {
    return text.length() > 60 ? text.substring(0, 60) + "..." : text;
  }

  private Object readValue() throws JsonException {
    if (pos >= src.length()) {
      throw new JsonException("unexpected end of input");
    }
    char c = src.charAt(pos);
    switch (c) {
      case '{': return readObject();
      case '[': return readArray();
      case '"': return readString();
      case 't':
        expect("true");
        return Boolean.TRUE;
      case 'f':
        expect("false");
        return Boolean.FALSE;
      case 'n':
        expect("null");
        return null;
      default: return readNumber();
    }
  }

  private Map<String, Object> readObject() throws JsonException {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    pos++;                                   // '{'
    skipWhitespace();
    if (pos < src.length() && src.charAt(pos) == '}') { pos++; return out; }
    while (true) {
      skipWhitespace();
      if (pos >= src.length() || src.charAt(pos) != '"') {
        throw new JsonException("expected a key at " + pos);
      }
      String key = readString();
      skipWhitespace();
      if (pos >= src.length() || src.charAt(pos) != ':') {
        throw new JsonException("expected ':' after " + key);
      }
      pos++;
      skipWhitespace();
      out.put(key, readValue());
      skipWhitespace();
      if (pos >= src.length()) {
        throw new JsonException("unterminated object");
      }
      char next = src.charAt(pos);
      if (next == ',') { pos++; continue; }
      if (next == '}') { pos++; return out; }
      throw new JsonException("expected ',' or '}' at " + pos);
    }
  }

  private List<Object> readArray() throws JsonException {
    List<Object> out = new ArrayList<Object>();
    pos++;                                   // '['
    skipWhitespace();
    if (pos < src.length() && src.charAt(pos) == ']') { pos++; return out; }
    while (true) {
      skipWhitespace();
      out.add(readValue());
      skipWhitespace();
      if (pos >= src.length()) {
        throw new JsonException("unterminated array");
      }
      char next = src.charAt(pos);
      if (next == ',') { pos++; continue; }
      if (next == ']') { pos++; return out; }
      throw new JsonException("expected ',' or ']' at " + pos);
    }
  }

  private String readString() throws JsonException {
    pos++;                                   // opening quote
    StringBuilder out = new StringBuilder();
    while (pos < src.length()) {
      char c = src.charAt(pos++);
      if (c == '"') {
        return out.toString();
      }
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (pos >= src.length()) {
        break;
      }
      char esc = src.charAt(pos++);
      switch (esc) {
        case '"': out.append('"'); break;
        case '\\': out.append('\\'); break;
        case '/': out.append('/'); break;
        case 'b': out.append('\b'); break;
        case 'f': out.append('\f'); break;
        case 'n': out.append('\n'); break;
        case 'r': out.append('\r'); break;
        case 't': out.append('\t'); break;
        case 'u':
          if (pos + 4 > src.length()) {
            throw new JsonException("truncated \\u escape");
          }
          out.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
          pos += 4;
          break;
        default: throw new JsonException("bad escape \\" + esc);
      }
    }
    throw new JsonException("unterminated string");
  }

  private Double readNumber() throws JsonException {
    int start = pos;
    if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
      pos++;
    }
    while (pos < src.length()) {
      char c = src.charAt(pos);
      if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
        pos++;
      } else {
        break;
      }
    }
    if (start == pos) {
      throw new JsonException("expected a value at " + start);
    }
    try {
      return Double.valueOf(src.substring(start, pos));
    } catch (NumberFormatException e) {
      throw new JsonException("bad number '" + src.substring(start, pos) + "'");
    }
  }

  private void expect(String literal) throws JsonException {
    if (!src.startsWith(literal, pos)) {
      throw new JsonException("expected " + literal + " at " + pos);
    }
    pos += literal.length();
  }

  private void skipWhitespace() {
    while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
      pos++;
    }
  }

  // --- typed accessors: the box's own field names, with explicit defaults ---------------

  public static String str(Map<String, Object> o, String key, String fallback) {
    Object value = o.get(key);
    return value instanceof String ? (String) value : fallback;
  }

  public static double num(Map<String, Object> o, String key, double fallback) {
    Object value = o.get(key);
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value instanceof String) {          // the box may send "1" as a string for a param
      try {
        return Double.parseDouble((String) value);
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  public static int intOr(Map<String, Object> o, String key, int fallback) {
    return (int) Math.round(num(o, key, fallback));
  }

  public static boolean bool(Map<String, Object> o, String key, boolean fallback) {
    Object value = o.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue() != 0.0;
    }
    if (value instanceof String) {
      String s = ((String) value).trim().toLowerCase();
      if (s.equals("1") || s.equals("true") || s.equals("on") || s.equals("yes")) return true;
      if (s.equals("0") || s.equals("false") || s.equals("off") || s.equals("no")) return false;
    }
    return fallback;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> obj(Map<String, Object> o, String key) {
    Object value = o.get(key);
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> list(Map<String, Object> o, String key) {
    Object value = o.get(key);
    return value instanceof List ? (List<Object>) value : null;
  }

  /** Whether the box will accept a write for a row in this mode.

   *  `live` takes effect now, `nextstart` is a real write that the relevant process picks up when
   *  it next starts (modeld, for instance, reads its path offset once at startup). `inert` and `ro`
   *  are not writable, and the app must not offer a field for them.
   */
  public static boolean writable(String mode) {
    return "live".equals(mode) || "nextstart".equals(mode);
  }

  /** A JSON number that must be an integer (the personality index arrives as 2 or "2.0"). */
  public static int looseInt(Object value, int fallback) {
    if (value instanceof Number) {
      return (int) Math.round(((Number) value).doubleValue());
    }
    if (value instanceof String) {
      try {
        return (int) Math.round(Double.parseDouble((String) value));
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  /** Pair of doubles from a [x, y] array, as the lane-line points arrive. */
  public static double[] pair(Object value) {
    if (!(value instanceof List)) {
      return null;
    }
    List<?> list = (List<?>) value;
    if (list.size() < 2) {
      return null;
    }
    Object a = list.get(0);
    Object b = list.get(1);
    if (!(a instanceof Number) || !(b instanceof Number)) {
      return null;
    }
    return new double[] {((Number) a).doubleValue(), ((Number) b).doubleValue()};
  }
}
