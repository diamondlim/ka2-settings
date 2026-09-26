# KA2 Settings — what every row is and how to set it

The app is a front panel for the box: every row you see was sent by the box itself, with its own range,
step size and description. Nothing is hard-coded in the app, so a row appears only when the deployed code
on the box really reads that setting.

## How to set anything

- **Numeric rows**: `−` and `+` move one step of that row's own size (speeds 5 km/h, times 0.5 s,
  confidence 0.05, radius 25 m). Press and hold to repeat.
- **The range printed under a row is a hard limit.** The box clamps every value into it, so you cannot set
  something outside — that is deliberate, and it is where the safety margins live.
- **"Raise only" / "Lower only" in a description is guidance, not a lock.** It tells you which direction
  makes the car act *less*. The range is what is actually enforced.
- **Changes take effect within about a second** — the box re-reads its tuning file once a second, and so
  does the car. No reboot is ever needed for a value.
- **"Restore shipped defaults"** deletes the override file, so every tuning row goes back to the values the
  code ships with (and every override, including the lane-correction ones, is cleared).
- **A missing row means the deployed code does not read that key** — the box reports that instead of
  offering you a slider that does nothing.

## APPEARANCE

Local to the phone; nothing is sent to the box.

- **Theme** — dark, light, or whatever the phone is set to. The lane view keeps its own dark road because
  it is a picture of the road, not a surface of the app.
- **Screen** — how long the phone keeps the screen awake while the app is open: useful when the phone is
  the dashboard.

## STOCK ACC

- **Set speed → `ACC −` / `ACC +`** — sends one press of the car's own ACC setpoint buttons, immediately.
  This is the same lever the automatic slowing uses. The box refuses while ACC is off, and it never touches
  lane-keeping. 5 km/h per press, because that is what one press does on this car.

## CONNECTED DEVICE

- **Status dot and text** — whether the phone's Bluetooth link to the box is up, and which box.
- **Connect** — pairs/attaches. Only one phone at a time should talk to the box.

## SOFTWARE SETTINGS

The box's own list: each row says when a change applies — *live* (now), *next start*, or *inert* (a build
that does not read it). **Refresh from box** re-reads everything (the state line, this list, the Wi-Fi
status).

- **Wi-Fi → Scan networks** — the box joins the network you pick. Your Bluetooth link is unaffected and the
  box keeps its address on this network, so switching does not disconnect the app.
- **Target branch, device APN, drive path offset** — updater and startup settings. They apply on the
  updater's next check or at the next boot, as the row says.

## Bend auto-slow — the `VIS_TURN_ACC_*` rows

This is the vision → stock-ACC bridge. It reads the model's view of the road, and when the road ahead
demands less speed than you have set, it presses the setpoint down 5 km/h at a time and hands the speed
back afterwards. **It never actuates the brakes** — on this car the setpoint is the only lever the box has,
and the car's own ACC does the decelerating.

| Row | What it does | Range / step | Current |
|---|---|---|---|
| Auto-slow for bends (on/off) | Master switch. 0 = the bridge never moves the setpoint for a bend | 0–1 | 1 |
| Auto-slow floor (km/h) | The lowest the setpoint will ever be walked down. Also the bound for the car-ahead policy | 30–90, 5 | 30 |
| Auto-raise ceiling (km/h) | Never hands speed back above this, nor above what you set yourself | 60–130, 5 | 130 |
| Max auto-slow steps per bend | How much may be taken off for one bend: 4 = 20 km/h | 0–6, 1 | 4 |
| Seconds between auto-slow steps | The cadence: how long it waits between steps down, and between steps back up. Longer = gentler. 2.5 s is the fastest the car tolerates | 2.5–15, 0.5 | 2.5 |
| Start slowing this long before a bend | How early the first step is taken | 1–8 s, 0.5 | 6 |
| How far ahead to look for bends | How much of the model's path is scanned | 80–320 m, 10 | 200 |
| Bend comfort (m/s² lateral) | How much cornering force it allows: comfort speed = sqrt(A_LAT / curvature). Lower = slower in bends | 1.2–2.5, 0.1 | 1.2 |
| Ignore bends gentler than this radius | Wider bends produce no slowing at all — the way to leave gentle motorway curves alone | 250–600 m, 25 | 250 |
| Don't auto-slow below this speed | Below this, bends are yours — useful in town | 25–70, 5 | 25 |
| Only slow if the bend needs this much less | How far under your setpoint the bend must be before it acts: kills small nuisance steps | 5–20, 1 | 5 |
| Speed-back headroom | How much faster the road must allow before a step back up | 5–25, 1 | 10 |
| Hand the speed back after a bend (on/off) | 0 = it only ever slows; you raise the speed yourself. The clean way to stop the automatic raising | 0–1 | 1 |

## Car ahead — the `VIS_LEAD_ACC_*` rows

The camera model also predicts the cars in front. Acting on that lets the box start slowing *before* the
car's own ACC has resolved the car ahead, so the deceleration begins earlier and more gently. Same lever:
setpoint only, the ACC still does the following. It takes the **lower** of the bend limit and the car-ahead
limit, and it will never hand speed back while a slower car is still in front.

| Row | What it does | Range / step | Current |
|---|---|---|---|
| Slow for a car ahead (on/off) | Master switch. Ships off | 0–1 | 1 |
| How far ahead a car is acted on | Further than this is ignored | 60–200 m, 10 | 120 |
| Aim this much faster than the car ahead | The setpoint walks toward that car's speed plus this | 0–20, 1 | 5 |
| Confidence before a car counts as your lead | How sure the model must be. Raise it if anything spurious ever triggers a slow-down | 0.3–0.9, 0.05 | 0.5 |
| Max steps per car ahead | How much may be taken off for one car: 4 = 20 km/h. Re-arms once that car is no longer in front | 0–8, 1 | 4 |

## Lane correction — the `LANE_*` rows

A separate, experimental feature: it nudges the steering to hold the lane centre better. Its shipped
defaults are the gentlest allowed, and the file can only move them toward gentler — turning one *up* is a
code change, not a slider.

## DEVICE SETTINGS

Rows the box writes itself, plus settings with side effects beyond a value.

- **Car Name / Features Package / SSH Keys** — read-only; taken from the car the box fingerprinted, the
  vendor feature bundle, and the GitHub account whose keys are trusted.
- **CPU temperature (SoC), CPU cores, GPU / NPU, Thermal state** — read live from the box's thermal zones
  each time the screen is read. Nothing is cached, so the numbers move between refreshes (they hop by the
  sensor's own ~0.9 °C step). The SoC starts throttling at its first trip point (75 °C); "Thermal state"
  says how far away that is, or warns when it is throttling right now.
- **Reset calibration** — clears the stored camera calibration; it re-learns as you drive. Parked only.
- **Reboot** — reboots the box; refused while autodrive is armed.
