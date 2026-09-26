# KA2 Settings — what every row is and how to set it

The app is a front panel for the box: every row you see was sent by the box itself, with its own range,
step size and description. Nothing is hard-coded in the app, so a row appears only when the deployed code
on the box really reads that setting.

The app has three pages, switched by the bar at the top: **Settings** (this document), **Lane view** (what the model sees, drawn as a road) and **Logs** (what the box accepted). They share one live stream from the box, so the numbers on the Lane view and the rows in Settings are the same measurements.

On the Settings page the ADAS rows sit under their own headings - **BEND AUTO-SLOW**, **CAR AHEAD**, **LANE CENTRING**, and **TUNING DEFAULTS** for the single override file they share - so the sections below match what you see on the phone, in that order. A heading appears only when the box actually reports rows for it.

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

*App section: BEND AUTO-SLOW.*

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

*App section: CAR AHEAD.*

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

## How lane centring works — and what the `LANE_CORRECTION_*` rows change

*App section: LANE CENTRING.*

Worth knowing before the rows, because it explains why those rows exist at all.

**Who actually steers.** On this car the box's lane centring is an *angle* controller: it works out a
steering angle from the curvature the model wants, through a learned model of the car. There is **no term
anywhere in it that looks at where the car sits between the lines** — it is feedforward, not a position
loop. That is why the car can feel perfectly steady while sitting slightly off-centre, or drift a little
toward one line through a long bend. Nothing is broken; the controller simply never checks.

**What the correction adds.** A small extra curvature, computed from **the lane lines themselves**:

- the left and right line of your own lane are read at a lookahead of about 1.5 s of travel (never closer
  than 10 m), and the lane centre is the midpoint between them at that distance;
- the offset becomes a curvature with `κ = 2 · offset / L²`, bounded by a **lateral-acceleration budget**
  (~0.3 m/s² of extra cornering force) rather than a flat curvature cap, so it stays gentle as speed rises;
- it applies only while lane centring is active, above about 5 m/s, and outside lane changes.

It deliberately does **not** use the model's planned path. That path is already lane-centred by
construction, so a correction derived from it would just re-add its own curvature — a curvature amplifier,
not a centring loop.

**When it refuses to act.** No correction is always safer than a wrong one, so it bails out and does
nothing when: either line is missing or less likely than the confidence row; the lane width it measures
looks implausible (under ~1.5 m or over ~6 m); the centre offset is beyond the plausibility row; or the
lines it has are not the pair bounding your own lane. Lane lines are noisy frame to frame — worse at night
— so the offset is low-passed and its rate of change is capped, which is what keeps the injected
correction from being felt as a twitch of the wheel. After a brief dropout the last value is held for a
moment **only while the car is going straight**: carrying a stale offset into a corner would steer toward
where the lane used to be, so above the yaw-rate row it is discarded instead.

**The rows.** As with the ACC rows, the box clamps every value into its range, and these ranges only allow
the correction to get *gentler* than the code it is running — turning one up is a code change, not a
slider. The app shows each row's live value.

- **Correction gain** — overall strength of the extra curvature. The gentler direction is down, to 0.
- **Lookahead (s)** — how far ahead (in time) the lane centre is measured; longer = smoother, less immediate.
- **Minimum lookahead (m)** — the floor on that distance at low speed, so the arithmetic never divides by a
  tiny L.
- **Lane confidence needed** — how sure the model must be about *both* lines before any correction is used.
  Raise it to make the correction act less often.
- **Maximum offset (m)** — the plausibility limit on the lane-centre offset; beyond it the reading is
  treated as a mis-detection rather than a real error.
- **Maximum extra lateral acceleration (m/s²)** — the gentle-at-speed budget above. Lower = less authority.
- **Correction active above (m/s)** — the speed below which it does nothing, for town driving.
- **Filter time constant (s)** — how long it averages the lane offset over. Longer = smoother but slower;
  0 means act on every frame raw.
- **Maximum change rate** — how fast the correction itself may change (the anti-jerk cap). Lower = gentler.
- **Memory hold (s)** — how long a good offset survives a brief dropout, and only while going straight.
- **Memory yaw-rate limit (rad/s)** — above this rate of turning, a remembered offset is never trusted.

## The Lane view page

A picture of what the box's model is seeing at that instant, drawn as a road rather than a diagram: the
camera's real height and perspective are used, so the lane converges the way a road does.

On it: your own lane's two lines, the lanes either side faintly, the model's planned-path ribbon, the car's
position between the lines, and the lane centre the car computes. A dashed, faint line means the model
carried that line on past where it actually stopped reading it. If there is no trustworthy pose it draws
**no lane at all** and says why — an invented lane would be worse than an empty one.

Under it, live numbers pulled from the same stream:

- **width / speed** — the lane, and your speed.
- **model plan** — the curvature the model wants; **lane confidence** — how sure it is about the lines.
- **measured at** — the distance the lanes were measured at, and **pose age** — how stale that measurement
  is. A large pose age is why the view sometimes shows nothing.
- **lane centring** — whether the box's lane keeping is actually engaged right now.
- **centring drift** — how far off centre the car is, and which way: this is the number the correction
  above is working against, so it is the honest way to judge whether the correction is helping.
- **lead vehicle / lead 2** — the cars ahead the model can see (this is what the car-ahead rows act on).
- **lanes shown / predicted route** — how many lanes the model is reporting, and the fork's predicted path.
- **your car's ACC / ACC requesting** — the car's own ACC state, and what the box last asked it for. If the
  automatic slowing is running, this is where you see it ask.

## The Logs page

One line per command, in order, in the box's own words: what it accepted and what it refused, with the
reason (a value clamped into its range, "ACC is off", a gate such as refusing while driving or while
autodrive is armed). It is the record of what you changed and when, for this session — the box keeps its
own permanent audit log of the same exchanges, so nothing is lost when you close the app. The text is
selectable, so you can copy a line out into a message.

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
