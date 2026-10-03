package com.hermes.ka2settings;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The host-side drive archive, as a second source for the log page.
 *
 * The box answers `DRIVES` over Bluetooth by re-reading its own rlogs: newest 24 one-minute segments,
 * grouped as segments, and it deletes as it goes (437 segments / 4 days when this was written). This
 * host holds the mirror instead - every segment ever pulled, grouped into drives, seven days and
 * counting - and answers over HTTPS from anywhere, including when the car and the box are off.
 *
 * The trick that keeps the app's parsing untouched: the host answers bare JSON, and the box's own
 * line protocol wraps JSON in a `DRIVES ` prefix, so {@link #toReply} re-wraps the host body into the
 * line the app already knows how to read. Nothing downstream needs to know which source answered.
 *
 * Kept free of Android so the URL building, the wrapping and the parsing can be tested on a JVM.
 */
public final class Remote {

  /** Where the drives API is published (dedicated Cloudflare tunnel, bearer auth required). */
  public static final String DEFAULT_BASE = "https://drives.annovahome.com";

  /**
   * The bearer token the API demands. This build carries it, so the app needs no setup - and the APK
   * is therefore a credential: do not share the file, and rotating the token means a rebuild (change
   * this one line). Both constants are here rather than in the UI on purpose: a driver should not be
   * typing a 43-character secret at the wheel.
   */
  public static final String DEFAULT_TOKEN = "BWyxYGEkThd9JZu13XeIGl2mHTidxttgUrxvn1ydq7s";

  public static final int LIMIT = 20;          // drives per page: the newest twenty; the host honours ?limit=
  public static final int TIMEOUT_MS = 12000;

  /**
   * The same API as the NAS serves it on the home LAN - same endpoints, same bearer token, no
   * Cloudflare in the path. This is the machine that actually holds the logs (the box uploads to it
   * over SMB), so at home this base is the shortest possible route to them.
   */
  public static final String LAN_BASE = "http://10.0.1.243:8791";

  /** How long the LAN attempt may take. The NAS answers in milliseconds when it is there at all. */
  public static final int LAN_TIMEOUT_MS = 2500;

  /** Which source answered last, for the log page to name: the NAS on the LAN, or the host archive. */
  public static volatile String lastSource = "";

  private Remote() {
  }

  /** True when both a base and a plausible token are present, i.e. the host path can be tried. */
  public static boolean configured(String base, String token) {
    return base != null && base.startsWith("http") && base.indexOf(".") > 0
        && token != null && token.length() >= 20;
  }

  private static String trim(String base) {
    String b = base == null ? "" : base.trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    return b;
  }

  /** The request paths, so one call can be aimed at whichever base answers. */
  public static String listPath() {
    return "/drives?format=legacy&limit=" + LIMIT;
  }

  /** One drive's summary path; `route` is what the list row carried back. */
  public static String summaryPath(String route) {
    return "/summary/" + route;
  }

  /** The drive list, in the row shape the box has always used (`format=legacy`). */
  public static String listUrl(String base) {
    return trim(base) + listPath();
  }

  /** One drive's summary; `route` is what the list row carried back. */
  public static String summaryUrl(String base, String route) {
    return trim(base) + summaryPath(route);
  }

  /**
   * The bases to try, in order: the NAS first (it holds the logs and is one hop away at home), then
   * the published host, which answers from anywhere including when the car and the NAS are off. One
   * entry when there is nothing to choose between them.
   */
  public static String[] candidates(String base) {
    String b = trim(base);
    String lan = trim(LAN_BASE);
    if (lan.isEmpty() || lan.equals(b)) {
      return new String[] { b };
    }
    return new String[] { lan, b };
  }

  /** A short name for whichever base answered, for the log page to show. */
  public static String sourceLabel(String base) {
    return trim(LAN_BASE).equals(trim(base)) ? "the NAS on your home LAN" : "the host archive";
  }

  /**
   * GET `path` from the first base that answers, with the LAN attempt on the short timeout so a
   * phone away from home is not left waiting on an address it cannot reach. The failure reported is
   * the published host's, because that is the one that is supposed to work.
   */
  public static String fetchPath(String[] bases, String path, String token) throws Exception {
    Exception last = null;
    for (int i = 0; i < bases.length; i++) {
      if (bases[i] == null || bases[i].isEmpty()) {
        continue;
      }
      try {
        String body = fetch(bases[i] + path, token,
            i == 0 && bases.length > 1 ? LAN_TIMEOUT_MS : TIMEOUT_MS);
        lastSource = sourceLabel(bases[i]);
        return body;
      } catch (Exception exc) {
        last = exc;
      }
    }
    throw last == null ? new IllegalStateException("no host configured") : last;
  }

  /** The same, for one image (the map). */
  public static byte[] fetchBytesPath(String[] bases, String path, String token, int timeoutMs)
      throws Exception {
    Exception last = null;
    for (int i = 0; i < bases.length; i++) {
      if (bases[i] == null || bases[i].isEmpty()) {
        continue;
      }
      try {
        byte[] out = fetchBytes(bases[i] + path, token, timeoutMs);
        lastSource = sourceLabel(bases[i]);
        return out;
      } catch (Exception exc) {
        last = exc;
      }
    }
    throw last == null ? new IllegalStateException("no host configured") : last;
  }

  /**
   * Wrap a bare JSON body into the box's own reply line, so the existing parser reads it unchanged.
   * Whitespace is collapsed because the line protocol is one line per reply.
   */
  public static String toReply(String body) {
    if (body == null) {
      return null;
    }
    return "DRIVES " + body.replace('\n', ' ').replace('\r', ' ').trim();
  }

  /** GET the URL with the bearer token; returns the body, or throws with a reason worth showing. */
  public static String fetch(String url, String token, int timeoutMs) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    try {
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(timeoutMs);
      conn.setReadTimeout(timeoutMs);
      conn.setRequestProperty("Authorization", "Bearer " + token);
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("User-Agent", "KA2Settings");
      int code = conn.getResponseCode();
      InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
      StringBuilder sb = new StringBuilder();
      if (in != null) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);            // JSON: line breaks are inter-token whitespace
        }
        reader.close();
      }
      if (code != HttpURLConnection.HTTP_OK) {
        String why = sb.length() > 0 ? sb.substring(0, Math.min(90, sb.length())) : "";
        throw new IllegalStateException("HTTP " + code + (why.isEmpty() ? "" : " " + why));
      }
      return sb.toString();
    } finally {
      conn.disconnect();
    }
  }

  /**
   * A map image for one drive. `source` is auto or box - the selection is made per request, so the
   * owner can compare sources for a drive without a rebuild.
   */
  public static String mapPath(String route, String source) {
    return "/map/" + route + ".png?source=" + (source == null || source.isEmpty() ? "auto" : source);
  }

  public static String mapUrl(String base, String route, String source) {
    return trim(base) + mapPath(route, source);
  }

  /** POST a JSON body; returns the reply, or throws with a reason worth showing. */
  public static String post(String url, String token, String body, int timeoutMs) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    try {
      byte[] payload = body.getBytes("UTF-8");
      conn.setRequestMethod("POST");
      conn.setConnectTimeout(timeoutMs);
      conn.setReadTimeout(timeoutMs);
      conn.setDoOutput(true);
      conn.setRequestProperty("Authorization", "Bearer " + token);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setFixedLengthStreamingMode(payload.length);
      java.io.OutputStream out = conn.getOutputStream();
      out.write(payload);
      out.close();
      int code = conn.getResponseCode();
      InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
      StringBuilder sb = new StringBuilder();
      if (in != null) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
        reader.close();
      }
      if (code != HttpURLConnection.HTTP_OK) {
        String why = sb.length() > 0 ? sb.substring(0, Math.min(90, sb.length())) : "";
        throw new IllegalStateException("HTTP " + code + (why.isEmpty() ? "" : " " + why));
      }
      return sb.toString();
    } finally {
      conn.disconnect();
    }
  }

  /** GET an image (the map) as bytes, for a Bitmap. */
  public static byte[] fetchBytes(String url, String token, int timeoutMs) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    try {
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(timeoutMs);
      conn.setReadTimeout(timeoutMs);
      conn.setRequestProperty("Authorization", "Bearer " + token);
      conn.setRequestProperty("User-Agent", "KA2Settings");
      int code = conn.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) {
        InputStream err = conn.getErrorStream();
        StringBuilder why = new StringBuilder();
        if (err != null) {
          BufferedReader reader = new BufferedReader(new InputStreamReader(err, "UTF-8"));
          String line;
          while ((line = reader.readLine()) != null && why.length() < 200) {
            why.append(line);
          }
          reader.close();
        }
        // the host answers a JSON body for "no track", which is worth passing straight through
        throw new IllegalStateException(why.length() > 0 ? why.toString() : "HTTP " + code);
      }
      InputStream in = conn.getInputStream();
      java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
      byte[] chunk = new byte[16384];
      int read;
      while ((read = in.read(chunk)) > 0) {
        buffer.write(chunk, 0, read);
      }
      in.close();
      return buffer.toByteArray();
    } finally {
      conn.disconnect();
    }
  }
}
