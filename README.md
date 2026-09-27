# KA2 Settings

An Android front panel for a **KA2** — the openpilot-family device fitted to a BYD Sealion 7.
It speaks to the box over a Bluetooth SPP link and shows what the box is doing, lets you press the car's own
ACC buttons, and exposes every live tuning knob the box's code actually reads.

No Gradle and no Android Studio: `build.sh` drives `aapt2 → javac → d8 → zipalign → apksigner` directly
against a plain SDK, so a full build takes seconds.

## What it shows

- **Settings** — the box's own software settings list (each row declares whether a change is live, applies
  at the next start, or is inert), the Wi-Fi join flow, the ADAS tuning rows, and read-only device rows
  including the box's live SoC / CPU / GPU / NPU temperatures and whether it is throttling.
- **Lane view** — the model's lane geometry as the box sees it, drawn from the live message stream.
- **Logs** — what the box accepted, one line per command.

## The box is the source of truth

The app hard-codes no setting names. On connect it sends `SCHEMA` and renders whatever comes back: each row
arrives with its key, type, section, description, range, step size and current value. A row therefore
appears only when the code deployed on the box really reads that key, and the box can hide a knob it no
longer honours rather than offering a control that does nothing.

## Protocol

One line per command over the SPP stream; one line per reply, mostly `K {…json…}`. The subset the app uses:

| Command | Meaning |
|---|---|
| `STATE` | live vehicle/device state line (speed, ACC, engagement, onroad) |
| `SCHEMA` | every setting row, as JSON, one per line |
| `GET <key>` / `SET <key> <value>` | read or write one row (the box range-checks and audits both) |
| `ACT <action>` | actions such as resetting the tuning overrides, rebooting, clearing calibration |
| `ACC UP` / `ACC DOWN` | press the car's own ACC setpoint button, immediately |
| `WIFI LIST` / `WIFI CONNECT <ssid> <password>` | join a network from the phone |
| `INFO`, `VER`, `HELP` | device info and the command list |

## Building

```sh
./setup_toolchain.sh                 # fetches a JDK and the Android command-line tools
export KA2_KEYSTORE_PASS=...         # the release keystore is deliberately not in this repo
./build.sh                           # -> out/KA2Settings.apk
```

`build.sh` expects `jdk/` and `sdk/` next to it (both fetched by `setup_toolchain.sh`) and a keystore at
`ka2.keystore`. Keep the same keystore across releases: Android refuses to update an app whose signing key
changed, so the phone would need a reinstall and would lose its pairing.

## Guide

`docs/KA2Settings-guide.md` explains every row: what it does, how to set it, and why the ranges are what
they are.

## Notes

- The app talks to one box at a time; the box's Bluetooth address is a setting, with the build's own default.
- Nothing here talks to the internet.
