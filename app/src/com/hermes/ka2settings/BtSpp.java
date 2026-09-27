package com.hermes.ka2settings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelUuid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.util.Log;

/** Classic Bluetooth serial client for the box's settings endpoint.
 *
 *  It connects by itself: the app remembers the box's address, and if this phone has never seen it,
 *  it scans for the box, learns its address, and connects - no trip through Android's Bluetooth
 *  settings, and no picking a device out of a list. The owner's phone had no way to find the box at
 *  all (the box was not discoverable, so a scan returned nothing) and the app's old fallback would
 *  have connected to whichever bonded device came first - headphones, or the car.
 *
 *  One worker thread owns the socket: connect, read lines, and on any drop report it and retry while
 *  the app still wants a connection.
 */
public class BtSpp {
  public interface Listener {
    void onLine(String line);

    void onState(String state, String detail);   // "connecting" | "connected" | "disconnected"
  }

  private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  private static final int RETRY_MS = 3000;
  private static final int RESCAN_MS = 5000;
  private static final String TAG = "KA2Settings";
  private static final String PREFS = "ka2settings";
  private static final String KEY_ADDRESS = "box_address";

  private final Listener listener;
  private final Object writeLock = new Object();
  private volatile boolean wanted;
  private volatile boolean connected;
  private BluetoothSocket socket;
  private OutputStream out;
  private Thread worker;
  private BroadcastReceiver scout;
  private Context scoutContext;
  private boolean scouting;

  public BtSpp(Listener listener) {
    this.listener = listener;
  }

  /** The box names itself kommu-<dongle id>; anything else is not ours. The rule lives in BoxName so
   *  it can be tested without a phone. */
  private static boolean looksLikeBox(BluetoothDevice device) {
    try {
      if (BoxName.looksLikeBoxName(device.getName())) {
        return true;
      }
      ParcelUuid[] uuids = device.getUuids();
      if (uuids == null) {
        return false;
      }
      String[] raw = new String[uuids.length];
      for (int i = 0; i < uuids.length; i++) {
        raw[i] = uuids[i] == null ? null : uuids[i].getUuid().toString();
      }
      return BoxName.hasSerialPort(raw);
    } catch (SecurityException ignored) {
      return false;
    }
  }

  /** The box's address: what the owner set, else the one this build was configured with. */
  public static String configuredMac(Context context) {
    String saved = prefs(context).getString(KEY_ADDRESS, "");
    return BoxName.normalizeMac(saved == null || saved.isEmpty() ? BoxName.DEFAULT_BOX_MAC : saved);
  }

  public static void rememberMac(Context context, String address) {
    prefs(context).edit().putString(KEY_ADDRESS, BoxName.normalizeMac(address)).apply();
  }

  /** The device at the configured address, or null if the address is malformed.
   *
   *  The app connects to an address, not to a name: it used to take "the first bonded device" when it
   *  could not find a kommu one, which on the owner's phone meant attaching to some other device
   *  entirely - the app then looked connected while the box saw nothing at all. Names can also be
   *  changed by anyone; the address is the device.
   */
  public static BluetoothDevice boxAt(BluetoothAdapter adapter, String mac) {
    if (adapter == null || !BoxName.isMac(mac)) {
      return null;
    }
    try {
      return adapter.getRemoteDevice(BoxName.normalizeMac(mac));
    } catch (IllegalArgumentException | SecurityException e) {
      Log.w(TAG, "boxAt: " + e.getMessage());
      return null;
    }
  }

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public boolean isConnected() {
    return connected;
  }

  public boolean isBusy() {
    return wanted;
  }

  /** Connect without being asked: the remembered box, or scan to learn the address of one. */

  // No pairing: the connection is unauthenticated RFCOMM, so Android neither bonds nor prompts.
  public void autoConnect(Context context) {
    if (wanted) {
      return;
    }
    Context app = context.getApplicationContext();
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      listener.onState("disconnected", "this phone has no Bluetooth");
      return;
    }
    if (!adapter.isEnabled()) {
      listener.onState("disconnected", "Bluetooth is off - switch it on and the app connects itself");
      return;
    }
    final String mac = configuredMac(app);
    BluetoothDevice box = boxAt(adapter, mac);
    if (box == null) {
      listener.onState("disconnected", "box address " + mac + " is not a Bluetooth address - "
          + "set it under Box address");
      return;
    }
    Log.i(TAG, "autoConnect: box at " + mac + " (no pairing needed)");
    start(box);
  }

  /** Scan for the box - for a replaced box, or when the configured address is wrong. It learns the
   *  address of whatever it finds, so the next launch goes straight back to an address.
   *
   *  Unreachable from the UI since v7.8: this box's Bluetooth is hidden, so a discovery scan returns
   *  nothing and the only honest path is the configured address. Kept because a replacement box that
   *  does advertise itself would make it useful again. */
  public void findByScan(Context context) {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null || !adapter.isEnabled()) {
      listener.onState("disconnected", "Bluetooth is off");
      return;
    }
    if (wanted || scouting) {
      return;
    }
    scoutForBox(context.getApplicationContext(), adapter);
  }

  /** Look for the box without leaving the app: scan, learn the address, connect - no pairing. */
  private void scoutForBox(final Context context, final BluetoothAdapter adapter) {
    if (scouting) {
      return;
    }
    scouting = true;
    scoutContext = context;
    listener.onState("connecting", "looking for the box");
    scout = new BroadcastReceiver() {
      @Override public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        try {
          if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            BluetoothDevice found = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (found == null || !looksLikeBox(found)) {
              return;
            }
            String name = found.getName() == null ? found.getAddress() : found.getName();
            adapter.cancelDiscovery();
            Log.i(TAG, "found " + name + " - learning its address, no pairing");
            done(found);
          } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            if (!connected) {
              scheduleRescan(adapter);
            }
          }
        } catch (SecurityException e) {
          listener.onState("disconnected", "Bluetooth permission denied");
        }
      }
    };
    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothDevice.ACTION_FOUND);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    if (Build.VERSION.SDK_INT >= 33) {
      context.registerReceiver(scout, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      context.registerReceiver(scout, filter);
    }
    startDiscovery(adapter);
  }

  private void startDiscovery(BluetoothAdapter adapter) {
    try {
      if (!adapter.isDiscovering()) {
        adapter.startDiscovery();
      }
    } catch (SecurityException e) {
      listener.onState("disconnected", "Bluetooth scan permission denied");
    }
  }

  /** A scan takes about twelve seconds; keep looking until the box appears or the app gives up. */
  private void scheduleRescan(final BluetoothAdapter adapter) {
    if (!scouting) {
      return;
    }
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
      public void run() {
        if (scouting && !connected) {
          startDiscovery(adapter);
        }
      }
    }, RESCAN_MS);
  }

  /** Found: remember its address so the next launch is instant, then connect. */
  private void done(BluetoothDevice device) {
    try {
      prefs(scoutContext).edit().putString(KEY_ADDRESS, device.getAddress()).apply();
    } catch (Exception ignored) {
    }
    stopScouting();
    start(device);
  }

  private void stopScouting() {
    scouting = false;
    if (scout != null && scoutContext != null) {
      try {
        scoutContext.unregisterReceiver(scout);
      } catch (IllegalArgumentException ignored) {
      }
    }
    scout = null;
    scoutContext = null;
  }

  /** Stop looking, and forget the remembered box (the "find it again" path). */
  public void forget() {
    if (scoutContext != null) {
      prefs(scoutContext).edit().remove(KEY_ADDRESS).apply();
    }
    stopScouting();
  }

  /** Start (or keep) trying to stay connected to this device. */
  public void start(final BluetoothDevice device) {
    if (wanted) {
      return;
    }
    wanted = true;
    worker = new Thread(new Runnable() {
      public void run() {
        loop(device);
      }
    }, "bt-spp");
    worker.setDaemon(true);
    worker.start();
  }

  public void stop() {
    wanted = false;
    stopScouting();
    closeSocket();
  }

  private void loop(BluetoothDevice device) {
    while (wanted) {
      BluetoothSocket sock = null;
      try {
        String name = device.getName() == null ? "box" : device.getName();
        listener.onState("connecting", "connecting to " + name);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isDiscovering()) {
          adapter.cancelDiscovery();
        }
        // Insecure RFCOMM: no authentication, so Android does not bond (and therefore does not show
        // its pairing prompt) and the link connects straight away. The box's own service authorises
        // any connection - it registers a NoInputNoOutput agent and logs "authorized service" for each
        // one - so pairing was never what made this work. What it costs: an unauthenticated link is
        // not encrypted, so anyone in range with the right tools could in principle read or inject
        // these settings messages. Inside your own car that is a small risk, and it is the trade for
        // a connection that just happens.
        sock = device.createInsecureRfcommSocketToServiceRecord(SPP);
        Log.i(TAG, "connecting SPP to " + device.getAddress());
        sock.connect();
        Log.i(TAG, "SPP connected");
        synchronized (writeLock) {
          socket = sock;
          out = sock.getOutputStream();
        }
        connected = true;
        try {
          if (scoutContext != null) {
            prefs(scoutContext).edit().putString(KEY_ADDRESS, device.getAddress()).apply();
          }
        } catch (Exception ignored) {
        }
        listener.onState("connected", name + " (" + device.getAddress() + ")");
        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()), 4096);
        String line;
        while (wanted && (line = in.readLine()) != null) {
          listener.onLine(line);
        }
      } catch (IOException e) {
        Log.w(TAG, "SPP link failed: " + e.getMessage());
        if (wanted) {
          listener.onState("disconnected", "cannot reach " + device.getAddress() + " ("
              + e.getMessage() + ") - check the box is powered and that the address is right");
        }
      } catch (SecurityException e) {
        wanted = false;
        listener.onState("disconnected", "Bluetooth permission denied");
      } finally {
        connected = false;
        synchronized (writeLock) {
          try {
            if (sock != null) {
              sock.close();
            }
          } catch (IOException ignored) {
          }
          socket = null;
          out = null;
        }
        if (wanted) {
          listener.onState("disconnected", "link lost - retrying");
        }
      }
      if (wanted) {
        try {
          Thread.sleep(RETRY_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      } else {
        listener.onState("disconnected", "disconnected");
      }
    }
  }

  public void send(String command) {
    synchronized (writeLock) {
      if (out == null) {
        listener.onState("disconnected", "not connected");
        return;
      }
      try {
        out.write((command + "\n").getBytes("UTF-8"));
        out.flush();
        listener.onLine("> " + command);
      } catch (IOException e) {
        listener.onState("disconnected", "send failed: " + e.getMessage());
      }
    }
  }

  private void closeSocket() {
    synchronized (writeLock) {
      try {
        if (socket != null) {
          socket.close();
        }
      } catch (IOException ignored) {
      }
      socket = null;
      out = null;
    }
    connected = false;
  }

  public void close() {
    stop();
  }
}
