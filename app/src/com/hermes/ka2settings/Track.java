package com.hermes.ka2settings;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Records where the phone goes while it is in the car, so a drive can be drawn on a map.
 *
 * The box logs no position of its own - its modem's GNSS receiver reports satellites with no signal at
 * all, and the board's other GPS module cannot be powered from software - so the phone is the position
 * source that works today. It is in the car anyway, and a track matched to a drive by time is enough to
 * put that drive on a map without labelling anything by hand.
 *
 * Only real fixes are kept: an accuracy-less or 0,0 location is dropped here as well as on the host,
 * because a made-up point is worse than a gap.
 */
public class Track implements LocationListener {

  /** Called whenever the recording's state or size changes, on the main thread. */
  public interface Listener {
    void onTrackChanged(int points, String note);

    /** Every fix, recording or not: this is what lets the app notice the car moving on its own. */
    void onSpeed(double speedKph, boolean recording, boolean hasFix);
  }

  private static final long MIN_TIME_MS = 1000L;
  private static final float MIN_DISTANCE_M = 3f;
  // While merely watching for movement the app does not need a fix a second: a slower, coarser request
  // keeps the GPS (and the battery) quiet until there is something worth recording.
  private static final long SNIFF_TIME_MS = 2000L;
  private static final float SNIFF_DISTANCE_M = 10f;

  private final Context context;
  private final Listener listener;
  private final List<double[]> points = new ArrayList<double[]>();
  private LocationManager manager;
  private boolean recording;
  private boolean listening;              // updates requested, whether or not points are kept
  private int rejected;

  public Track(Context context, Listener listener) {
    this.context = context.getApplicationContext();
    this.listener = listener;
  }

  public boolean isRecording() {
    return recording;
  }

  public int size() {
    return points.size();
  }

  public int rejectedCount() {
    return rejected;
  }

  public List<double[]> points() {
    return points;
  }

  /**
   * Watch for movement without recording: a slow, coarse stream whose only job is to tell the app when
   * the car is being driven. Safe to call repeatedly.
   */
  public String sniff() {
    if (listening) {
      return null;
    }
    return request(SNIFF_TIME_MS, SNIFF_DISTANCE_M);
  }

  /** Starts recording. Returns null when it started, or the reason it did not. */
  public String start() {
    if (recording) {
      return null;
    }
    String why = request(MIN_TIME_MS, MIN_DISTANCE_M);
    if (why != null && !listening) {
      return why;
    }
    recording = true;
    if (listener != null) {
      listener.onTrackChanged(points.size(), null);
    }
    return null;
  }

  private String request(long minTimeMs, float minDistanceM) {
    manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    if (manager == null) {
      return "this device has no location service";
    }
    // GPS is the honest provider; a phone that granted only approximate location rejects it, and the
    // network provider is a degraded but real fallback rather than a refusal.
    String accepted = null;
    for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
      try {
        manager.requestLocationUpdates(provider, minTimeMs, minDistanceM, this, Looper.getMainLooper());
        accepted = provider;
        break;
      } catch (Exception exc) {
        accepted = null;
      }
    }
    if (accepted == null) {
      return "no location provider accepted - grant precise location for this app, and turn Location on";
    }
    listening = true;
    return null;
  }

  /** Stop recording but keep watching: the next drive should start on its own. */
  public void stop() {
    recording = false;
    if (listener != null) {
      listener.onTrackChanged(points.size(), null);
    }
  }

  /** Stop everything, for the activity going away. */
  public void shutdown() {
    if (manager != null) {
      try {
        manager.removeUpdates(this);
      } catch (Exception exc) {
        // the service is going away; nothing useful to do about it
      }
    }
    listening = false;
    recording = false;
  }

  @Override
  public void onLocationChanged(Location location) {
    if (location == null || !location.hasAccuracy()
        || Double.isNaN(location.getLatitude()) || Double.isNaN(location.getLongitude())
        || (Math.abs(location.getLatitude()) < 1e-9 && Math.abs(location.getLongitude()) < 1e-9)) {
      rejected++;
      if (listener != null) {
        listener.onSpeed(-1, recording, false);
      }
      return;
    }
    double speedMs = location.hasSpeed() ? location.getSpeed() : 0.0;
    if (recording) {
      double[] point = new double[] {location.getTime(), location.getLatitude(), location.getLongitude(),
          speedMs, location.getAccuracy()};
      points.add(point);
      if (listener != null) {
        listener.onTrackChanged(points.size(), null);
      }
    }
    if (listener != null) {
      listener.onSpeed(speedMs * 3.6, recording, true);
    }
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
  }

  @Override
  public void onProviderEnabled(String provider) {
  }

  @Override
  public void onProviderDisabled(String provider) {
    if (listener != null) {
      listener.onTrackChanged(points.size(), "GPS was turned off");
    }
  }
}
