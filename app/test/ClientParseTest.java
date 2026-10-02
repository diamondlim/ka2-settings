import com.hermes.ka2settings.Json;
import com.hermes.ka2settings.Panel;
import com.hermes.ka2settings.Stepper;
import com.hermes.ka2settings.Steps;
import com.hermes.ka2settings.LaneGeometry;
import com.hermes.ka2settings.Palette;
import com.hermes.ka2settings.BoxName;
import com.hermes.ka2settings.Readouts;
import com.hermes.ka2settings.States;
import com.hermes.ka2settings.ScreenOn;
import com.hermes.ka2settings.Wifi;
import com.hermes.ka2settings.Drives;
import com.hermes.ka2settings.Remote;
import com.hermes.ka2settings.Lens;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map;

/** Client-side parsing tests, run on a plain JVM against REAL captured box output.
 *
 *  The samples come from captured_lines.txt, recorded from the deployed service on the car's box,
 *  so this proves the app's parser against the device rather than against samples written to match
 *  the parser. Synthetic poses cover the onroad path, which cannot be captured while the car is
 *  parked (the box correctly reports "no model" instead).
 */
public class ClientParseTest {

  static List<String> failures = new ArrayList<String>();

  static void check(String name, boolean cond, String detail) {
    System.out.printf("%-58s %s %s%n", name, cond ? "PASS" : "FAIL", cond ? "" : detail);
    if (!cond) failures.add(name);
  }

  public static void main(String[] args) throws Exception {
    String capture = "/config/.hermes/apk/box/captured_lines.txt";
    List<String> infoLines = new ArrayList<String>();
    List<String> schemaRows = new ArrayList<String>();
    List<String> poseLines = new ArrayList<String>();
    for (String line : Files.readAllLines(Paths.get(capture))) {
      if (line.startsWith("I ")) infoLines.add(line);
      else if (line.startsWith("K ")) schemaRows.add(line);
      else if (line.startsWith("P ")) poseLines.add(line);
    }
    check("capture holds a device-info line", infoLines.size() >= 1, "found " + infoLines.size());
    check("capture holds schema rows", schemaRows.size() >= 10, "found " + schemaRows.size());
    check("capture holds pose lines", poseLines.size() >= 1, "found " + poseLines.size());

    // --- the device-info card, from the box's own JSON ------------------------------
    Map<String, Object> info = Json.parseObject(infoLines.get(0).substring(2));
    check("info: dongle id parsed", "c87bd64bfd13255a".equals(Json.str(info, "dongle", "")),
        Json.str(info, "dongle", ""));
    check("info: commit shortened to 8", Json.str(info, "commit", "").length() == 8,
        Json.str(info, "commit", ""));
    check("info: state is a word", "offroad".equals(Json.str(info, "state", "")),
        Json.str(info, "state", ""));
    check("info: storage is a number", Json.num(info, "free_gb", -1) > 0,
        "" + Json.num(info, "free_gb", -1));
    Map<String, Object> poseInfo = Json.obj(info, "pose");
    check("info: nested pose object parsed", poseInfo != null && Json.bool(poseInfo, "ok", true) == false,
        String.valueOf(poseInfo));

    // --- the settings rows ----------------------------------------------------------
    Map<String, Object> quiet = null, experimental = null, personality = null, metered = null;
    for (String row : schemaRows) {
      Map<String, Object> r = Json.parseObject(row.substring(2));
      String k = Json.str(r, "k", "");
      if (k.equals("QuietMode")) quiet = r;
      if (k.equals("ExperimentalMode")) experimental = r;
      if (k.equals("LongitudinalPersonality")) personality = r;
      if (k.equals("NetworkMetered")) metered = r;
    }
    check("schema: a live toggle found", quiet != null && "live".equals(Json.str(quiet, "mode", "")),
        String.valueOf(quiet));
    check("schema: a bool row reads as a boolean", Json.bool(quiet, "v", false), String.valueOf(quiet));
    check("schema: an inert row is not offered as live",
        experimental != null && !"live".equals(Json.str(experimental, "mode", "")),
        String.valueOf(experimental));
    check("schema: an inert row explains itself", experimental != null
        && Json.str(experimental, "desc", "").length() > 20, String.valueOf(experimental));
    check("schema: a read-only row is marked ro",
        metered != null && "ro".equals(Json.str(metered, "mode", "")), String.valueOf(metered));
    check("schema: an enum row carries its options",
        personality != null && Json.list(personality, "opts") != null
            && Json.list(personality, "opts").size() == 3, String.valueOf(personality));
    check("schema: an integer param sent as \"2.0\" still reads as an index",
        Json.looseInt(personality.get("v"), -1) == 2, String.valueOf(personality.get("v")));

    Map<String, Object> warned = Json.parseObject("{\"k\":\"LongitudinalPersonality\",\"t\":\"enum\","
        + "\"mode\":\"live\",\"v\":\"1\",\"title\":\"Driving Personality\",\"warn\":\"stored value "
        + "'2.0' cannot be read as enum; the box uses 1\",\"opts\":[\"Aggressive\",\"Standard\",\"Relaxed\"]}");
    check("schema: a warn field is parsed for the row to show",
        Json.str(warned, "warn", "").length() > 20, String.valueOf(warned));
    check("schema: a warned row still selects the effective option, not the stored text",
        Json.looseInt(warned.get("v"), -1) == 1, String.valueOf(warned.get("v")));

    // --- which rows the app may put a field on ---------------------------------------
    check("a live row is writable", Json.writable("live"), "");
    check("a nextstart row is writable too (the box says when it applies)",
        Json.writable("nextstart"), "");
    check("an inert row is not writable", !Json.writable("inert"), "");
    check("a read-only row is not writable", !Json.writable("ro"), "");
    check("an unknown mode is not writable", !Json.writable("surprise"), "");

    Map<String, Object> skewRow = Json.parseObject("{\"k\":\"DrivePathOffset\",\"t\":\"num\","
        + "\"mode\":\"nextstart\",\"v\":\"0.15\",\"min\":-0.25,\"max\":0.25,\"step\":0.05}");
    check("a row's range and step survive parsing",
        Json.num(skewRow, "min", 9) == -0.25 && Json.num(skewRow, "max", 9) == 0.25
            && Json.num(skewRow, "step", 9) == 0.05, String.valueOf(skewRow));
    Map<String, Object> knob = Json.parseObject("{\"k\":\"LANE_CORRECTION_GAIN\",\"t\":\"num\","
        + "\"mode\":\"live\",\"v\":\"1\",\"min\":0.0,\"max\":1.0,\"step\":0.05}");
    check("a live tuning knob parses as an editable number",
        Json.writable(Json.str(knob, "mode", "")) && Json.num(knob, "max", 0) == 1.0,
        String.valueOf(knob));

    // --- the stepper's arithmetic: this decides the number sent to the car ------------
    check("a press up moves one step", Steps.next(0.25, 1, 0.05, 0.0, 1.0) == 0.3,
        "" + Steps.next(0.25, 1, 0.05, 0.0, 1.0));
    check("a press down moves one step back", Steps.next(0.3, -1, 0.05, 0.0, 1.0) == 0.25,
        "" + Steps.next(0.3, -1, 0.05, 0.0, 1.0));
    check("repeated presses do not drift (20 up then 20 down returns where it started)",
        Math.abs(Steps.nextMany(0.2, 20, 0.05, 0.0, 2.0) - 1.2) < 1e-9,
        "" + Steps.nextMany(0.2, 20, 0.05, 0.0, 2.0));
    check("a press stops at the top of the range", Steps.next(1.0, 1, 0.05, 0.0, 1.0) == 1.0,
        "" + Steps.next(1.0, 1, 0.05, 0.0, 1.0));
    check("a press stops at the bottom of the range", Steps.next(0.0, -1, 0.05, 0.0, 1.0) == 0.0,
        "" + Steps.next(0.0, -1, 0.05, 0.0, 1.0));
    check("an off-grid value is snapped before the step is added",
        Steps.next(0.13, 1, 0.05, -0.25, 0.25) == 0.2,
        "" + Steps.next(0.13, 1, 0.05, -0.25, 0.25));
    check("a single press from a grid value lands exactly on the next one",
        Steps.next(0.15, 1, 0.05, -0.25, 0.25) == 0.2 && Steps.next(-0.05, -1, 0.05, -0.25, 0.25) == -0.1,
        Steps.next(0.15, 1, 0.05, -0.25, 0.25) + "/" + Steps.next(-0.05, -1, 0.05, -0.25, 0.25));
    check("the 0.05 m skew grid holds across its whole range",
        Steps.format(Steps.next(-0.25, 10, 0.05, -0.25, 0.25), 0.05).equals("0.25"),
        Steps.format(Steps.next(-0.25, 10, 0.05, -0.25, 0.25), 0.05));
    check("an unreadable value starts from the range floor, not from zero",
        Steps.next(Steps.parse("oops", -0.25), 1, 0.05, -0.25, 0.25) == -0.2,
        "" + Steps.next(Steps.parse("oops", -0.25), 1, 0.05, -0.25, 0.25));
    check("formatting follows the step's precision",
        Steps.format(0.25, 0.05).equals("0.25") && Steps.format(1.0, 0.25).equals("1.00")
            && Steps.format(7.5, 0.5).equals("7.5") && Steps.format(0.15, 0.025).equals("0.150"),
        Steps.format(0.25, 0.05) + "/" + Steps.format(1.0, 0.25) + "/" + Steps.format(7.5, 0.5)
            + "/" + Steps.format(0.15, 0.025));
    check("the end stops report as reached",
        !Steps.canMove(1.0, 1, 0.05, 0.0, 1.0) && Steps.canMove(0.95, 1, 0.05, 0.0, 1.0)
            && !Steps.canMove(0.0, -1, 0.05, 0.0, 1.0), "");

    // --- a range whose ceiling is not on the step grid (the reported bug) -------------
    // probability is capped at 0.99 with a 0.05 step: the usable top is 0.95
    check("a press at a non-aligned ceiling does not step into a value the box would refuse",
        Steps.next(0.95, 1, 0.05, 0.5, 0.99) == 0.95,
        "" + Steps.next(0.95, 1, 0.05, 0.5, 0.99));
    check("and the box's advertised top is where the stepper stops",
        Steps.usableMax(0.05, 0.5, 0.99) == 0.95, "" + Steps.usableMax(0.05, 0.5, 0.99));
    check("the row below it still moves both ways",
        Steps.next(0.9, 1, 0.05, 0.5, 0.99) == 0.95 && Steps.next(0.95, -1, 0.05, 0.5, 0.99) == 0.9,
        Steps.next(0.9, 1, 0.05, 0.5, 0.99) + "/" + Steps.next(0.95, -1, 0.05, 0.5, 0.99));

    // --- the stepper state machine: one display format, and no late-reply jumps -------
    Stepper gain = new Stepper(0.05, 0.0, 1.0, "1");        // the box echoes %g: "1"
    check("a value from the box is shown in the step's own format, not the box's",
        gain.value().equals("1.00"), gain.value());
    check("a reply with nothing in flight is taken as truth",
        gain.applyReply("0.50") && gain.value().equals("0.50"), gain.value());

    String sent = gain.press(1);                             // 0.50 -> 0.55
    check("a press sends the next value and shows it immediately", "0.55".equals(sent), String.valueOf(sent));
    check("the row reads 0.55 while the box is still answering", gain.value().equals("0.55"), gain.value());
    check("THE BUG: a late reply for the previous press is ignored, not shown",
        !gain.applyReply("0.50") && gain.value().equals("0.55"), gain.value());
    check("and the value actually in flight is applied when it lands",
        gain.applyReply("0.55") && gain.value().equals("0.55") && !gain.hasPending(), gain.value());

    // the same exchange with the box having rounded the number (a snap): still honoured in flight
    String sent2 = gain.press(1);
    check("a different value in flight is accepted from the box (a snap is honoured)",
        gain.applyReply("0.60") && gain.value().equals("0.60"), gain.value());

    // end stops
    Stepper top = new Stepper(0.05, 0.0, 1.0, "1.00");
    check("at the top, the up direction reports no move and sends nothing",
        top.atUpperLimit() && !top.canMove(1) && top.press(1) == null, top.value());
    check("and down still moves", top.canMove(-1) && "0.95".equals(top.press(-1)), top.value());
    Stepper bottom = new Stepper(0.05, 0.0, 1.0, "0.00");
    check("at the bottom, the down direction is the end stop",
        bottom.atLowerLimit() && bottom.press(-1) == null && "0.05".equals(bottom.press(1)),
        bottom.value());
    // a refusal (ERR -> fresh SCHEMA) re-syncs the row rather than leaving it stuck
    top.reset("0.90");
    check("a fresh SCHEMA re-syncs the row", top.value().equals("0.90") && !top.hasPending(), top.value());

    // a slow link: every reply arrives one press late, and the row must never walk backwards
    Stepper prob = new Stepper(0.05, 0.5, 0.95, "0.50");
    java.util.List<String> sentValues = new java.util.ArrayList<String>();
    boolean walkedBack = false;
    String last = prob.value();
    for (int i = 0; i < 20; i++) {
      String v = prob.press(1);
      if (v == null) {
        break;
      }
      sentValues.add(v);
      prob.applyReply(sentValues.get(Math.max(0, sentValues.size() - 2)));   // one press behind
      if (Double.parseDouble(prob.value()) < Double.parseDouble(last)) {
        walkedBack = true;
      }
      last = prob.value();
    }
    check("with replies one press late the row never steps backwards", !walkedBack, prob.value());
    check("and holding to the top lands exactly on the usable maximum",
        prob.value().equals("0.95") && prob.atUpperLimit(), prob.value());

    // --- a repeating button cannot hammer the box (the flood in the audit log) --------
    // The log showed this exact row writing the same value ~29 times a second: the press found
    // nothing to change at the limit and wrote it again anyway, forever. A thousand presses must
    // now produce exactly the steps the range has, and then nothing.
    Stepper flood = new Stepper(0.5, 5.0, 20.0, "5");
    int sends = 0;
    for (int i = 0; i < 1000; i++) {
      if (flood.press(1) != null) {
        sends++;
        flood.applyReply(flood.value());
      }
    }
    check("1000 presses across a 5-20 range send exactly its 30 steps, then stop",
        sends == 30 && flood.value().equals("20.0"), sends + " sends, at " + flood.value());
    int repeats = 0;
    for (int i = 0; i < 1000; i++) {
      if (flood.press(-1) != null) {
        repeats++;
        flood.applyReply(flood.value());
      }
    }
    check("and the same coming back down", repeats == 30 && flood.value().equals("5.0"),
        repeats + " sends, at " + flood.value());

    // --- no dead ends and no unreachable values, for every row the box sends ----------
    double[][] ranges = {
        {0.0, 1.0, 0.05},      // gain
        {1.5, 3.0, 0.25},      // lookahead
        {0.5, 0.95, 0.05},     // min lane prob, as the box advertises it
        {0.2, 1.5, 0.1},       // max offset
        {0.0, 0.3, 0.05},      // max lateral accel
        {5.0, 20.0, 0.5},      // min speed
        {0.5, 2.0, 0.25},      // filter tau
        {0.0, 0.9, 0.1},       // max acc rate
        {0.0, 0.4, 0.05},      // memory hold
        {0.0, 0.15, 0.025},    // yaw rate gate
        {-0.25, 0.25, 0.05},   // path skew offset
    };
    boolean deadEnd = false, offGrid = false, unreachable = false;
    String detail = "";
    for (double[] r : ranges) {
      double value = r[0], step = r[2];
      int guard = 0;
      while (Steps.canMove(value, 1, step, r[0], r[1]) && guard++ < 500) {
        value = Steps.next(value, 1, step, r[0], r[1]);
        if (Math.abs(value / step - Math.round(value / step)) > 1e-6) {
          offGrid = true;
          detail = "off grid at " + value + " for step " + step;
        }
        if (value > r[1] + 1e-9) {
          deadEnd = true;
          detail = "stepped past the limit: " + value + " > " + r[1];
        }
      }
      if (!Steps.canMove(value, 1, step, r[0], r[1]) && Math.abs(value - Steps.usableMax(step, r[0], r[1])) > 1e-9) {
        unreachable = true;
        detail = "stopped before the top: " + value + " vs " + Steps.usableMax(step, r[0], r[1]);
      }
      // and every value on the way back down must be the same set
      double back = value;
      while (Steps.canMove(back, -1, step, r[0], r[1])) {
        back = Steps.next(back, -1, step, r[0], r[1]);
        if (back < r[0] - 1e-9) {
          deadEnd = true;
          detail = "stepped below the limit: " + back;
        }
      }
    }
    boolean stuck = false;
    for (double[] r : ranges) {
      Stepper walk = new Stepper(r[2], r[0], r[1], Steps.format(r[0], r[2]));
      int guard = 0;
      String previous = walk.value();
      while (walk.press(1) != null && guard++ < 500) {
        if (Double.parseDouble(walk.value()) <= Double.parseDouble(previous)) {
          stuck = true;                                   // a press that did not move, or moved back
          detail = "stuck at " + walk.value();
        }
        previous = walk.value();
        walk.applyReply(walk.value());                    // confirmed, as the box would
      }
      if (Math.abs(Double.parseDouble(walk.value()) - Steps.usableMax(r[2], r[0], r[1])) > 1e-9) {
        stuck = true;
        detail = "walk ended at " + walk.value() + " not " + Steps.usableMax(r[2], r[0], r[1]);
      }
      if (!walk.atUpperLimit() || walk.press(1) != null) {
        stuck = true;
        detail = "the top of the range still offers a press: " + walk.value();
      }
    }
    check("a stepper walked from the floor to the top is never stuck and stops exactly at the top",
        !stuck, detail);
    check("stepping never leaves the grid, the range, or a dead end", !deadEnd && !offGrid && !unreachable,
        detail);
    check("every range is walked to exactly its advertised top", !unreachable, detail);

    // --- action and picker rows (reboot, reset calibration, target branch) ------------
    Map<String, Object> action = Json.parseObject("{\"k\":\"ACT_REBOOT\",\"t\":\"action\","
        + "\"act\":\"REBOOT\",\"mode\":\"live\",\"sec\":\"dev\",\"confirm\":1,"
        + "\"title\":\"Reboot the box\",\"desc\":\"Reboots the device.\"}");
    check("an action row parses with its confirm flag",
        Json.bool(action, "confirm", false) && "REBOOT".equals(Json.str(action, "act", "")),
        String.valueOf(action));
    check("an action row is enabled like any live row",
        Json.writable(Json.str(action, "mode", "")), String.valueOf(action));

    Map<String, Object> branch = Json.parseObject("{\"k\":\"UpdaterTargetBranch\",\"t\":\"pick\","
        + "\"mode\":\"live\",\"v\":\"lane_smooth\",\"title\":\"Target Branch\","
        + "\"opts\":[\"lane_fix_v2\",\"lane_safe\",\"lane_smooth\",\"lateral_stock\",\"staging\"]}");
    check("a picker row carries the options and the current choice",
        Json.list(branch, "opts") != null && Json.list(branch, "opts").size() == 5
            && "lane_smooth".equals(Json.str(branch, "v", "")), String.valueOf(branch));
    check("the branch row is writable", Json.writable(Json.str(branch, "mode", "")), "");

    // --- the pose the box actually sent (car parked: it must decline to draw) --------
    Map<String, Object> parked = Json.parseObject(poseLines.get(poseLines.size() - 1).substring(2));
    double now = Json.num(parked, "t", 0.0);
    LaneGeometry.Pose parkedPose = LaneGeometry.fromJson(parked, now);
    check("pose: a parked box reports no lane", !parkedPose.ok, parkedPose.summary());
    check("pose: and says why instead of an empty lane",
        parkedPose.why.length() > 5, parkedPose.why);

    // --- a synthetic onroad pose: geometry conventions and mapping -------------------
    String onroad = "{\"t\":1000.0,\"ok\":1,\"v\":16.0,\"eng\":1,\"off\":-0.30,\"w\":3.20,"
        + "\"curv\":-0.00042,\"lp\":0.98,\"rp\":0.97,"
        + "\"l\":[[0.0,-1.90],[5.0,-1.90],[10.0,-1.90],[20.0,-1.90]],"
        + "\"r\":[[0.0,1.30],[5.0,1.30],[10.0,1.30],[20.0,1.30]]}";
    LaneGeometry.Pose pose = LaneGeometry.fromJson(Json.parseObject(onroad), 1000.4);
    check("pose: a live pose is accepted", pose.ok, pose.summary());
    check("pose: lane lines parsed into arrays", pose.leftX.length == 4 && pose.rightY.length == 4,
        pose.leftX.length + "/" + pose.rightY.length);
    check("pose: offset sign kept (centre to the car's left is negative)", pose.offset == -0.30,
        "" + pose.offset);
    check("pose: summary names the side the car sits", pose.summary().contains("left of centre"),
        pose.summary());
    // rounded to whole km/h: a caption with a decimal in it rewrote itself on every 0.01 m/s of wobble
    check("pose: speed converted for display", pose.summary().contains("58 km/h"), pose.summary());
    check("pose: and the caption is rounded to what a person reads",
        !pose.summary().contains(".6 km/h"), pose.summary());

    // --- how many lanes the box can show -------------------------------------------------
    LaneGeometry.Pose threeLanes = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.0,\"l\":[[0.0,-1.5],[10.0,-1.5]],\"r\":[[0.0,1.5],[10.0,1.5]],"
            + "\"l2\":[[0.0,-4.5],[10.0,-4.5]],\"r2\":[[0.0,4.5],[10.0,4.5]]}"), 1000.1);
    check("three lanes are counted when both neighbours are seen", threeLanes.lanesVisible() == 3,
        "" + threeLanes.lanesVisible());
    LaneGeometry.Pose twoLanes = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.0,\"l\":[[0.0,-1.5],[10.0,-1.5]],\"r\":[[0.0,1.5],[10.0,1.5]],"
            + "\"r2\":[[0.0,4.5],[10.0,4.5]]}"), 1000.1);
    check("only the sides the model is confident about are counted", twoLanes.lanesVisible() == 2,
        "" + twoLanes.lanesVisible());
    LaneGeometry.Pose oneLane = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.0,\"l\":[[0.0,-1.5],[10.0,-1.5]],\"r\":[[0.0,1.5],[10.0,1.5]]}"),
        1000.1);
    check("the car's own lane alone counts as one", oneLane.lanesVisible() == 1,
        "" + oneLane.lanesVisible());
    check("a box that sends no lane lines shows none",
        LaneGeometry.fromJson(Json.parseObject("{\"t\":1000.0,\"ok\":0,\"v\":0.0}"), 1000.1)
            .lanesVisible() == 0, "");
    check("the neighbouring lane's outer edge sits outboard of the car's own lane",
        threeLanes.outerLeftY.length > 0 && threeLanes.leftY.length > 0
        && threeLanes.outerLeftY[0] < threeLanes.leftY[0],
        threeLanes.outerLeftY[0] + " vs " + threeLanes.leftY[0]);

    // --- a vehicle ahead, from the car's own ACC tracking ---------------------------------
    LaneGeometry.Pose withLead = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.4,\"off\":0.1,\"w\":3.05,"
            + "\"lead\":{\"d\":22.5,\"y\":-0.4,\"vr\":-1.2,\"p\":0.93},"
            + "\"l\":[[0.0,-1.5]],\"r\":[[0.0,1.5]]}"), 1000.1);
    check("a lead vehicle reaches the app", withLead.lead.present && withLead.lead.distance == 22.5,
        "" + withLead.lead.distance);
    check("its lateral position comes through", Math.abs(withLead.lead.lateral + 0.4) < 0.001,
        "" + withLead.lead.lateral);
    check("the gap in seconds is computed from the car's own speed",
        Math.abs(withLead.lead.gapSeconds(16.4) - 1.372) < 0.01, "" + withLead.lead.gapSeconds(16.4));
    check("no gap in seconds when barely moving (distance over ~zero is not a number)",
        withLead.lead.gapSeconds(0.4) < 0, "" + withLead.lead.gapSeconds(0.4));
    LaneGeometry.Pose noLead = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.4,\"off\":0.1,\"w\":3.05,"
            + "\"l\":[[0.0,-1.5]],\"r\":[[0.0,1.5]]}"), 1000.1);
    check("no lead in the message means nothing is drawn (not a car at 0 m)",
        !noLead.lead.present && !noLead.lead2.present, "");
    LaneGeometry.Pose twoLeads = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.4,\"off\":0.1,\"w\":3.05,"
            + "\"lead\":{\"d\":12.0,\"y\":0.1},\"lead2\":{\"d\":40.0,\"y\":-0.2},"
            + "\"l\":[[0.0,-1.5]],\"r\":[[0.0,1.5]]}"), 1000.1);
    check("a second lead is carried too",
        twoLeads.lead.distance == 12.0 && twoLeads.lead2.distance == 40.0, "");
    check("a lead with missing optional fields still parses",
        !twoLeads.lead.present || twoLeads.lead.prob == -1.0, "" + twoLeads.lead.prob);
    LaneGeometry.Pose zeroLead = LaneGeometry.fromJson(Json.parseObject(
        "{\"t\":1000.0,\"ok\":1,\"v\":16.4,\"off\":0.1,\"w\":3.05,\"lead\":{\"d\":0.0,\"y\":0.0},"
            + "\"l\":[[0.0,-1.5]],\"r\":[[0.0,1.5]]}"), 1000.1);
    check("a lead at 0 m is not drawn", !zeroLead.lead.present, "" + zeroLead.lead.distance);

    // --- which device is the box (getting this wrong pairs with the car or with headphones) -----
    check("the box's own name is recognised",
        BoxName.looksLikeBoxName("kommu-c87bd64bfd13255a"), "");
    check("case does not matter", BoxName.looksLikeBoxName("KOMMU-c87bd64bfd13255a"), "");
    check("surrounding space does not matter", BoxName.looksLikeBoxName(" kommu-x "), "");
    check("a bare kommu is still the box", BoxName.looksLikeBoxName("kommu"), "");
    check("a name merely containing kommu is not (prefix, not substring)",
        !BoxName.looksLikeBoxName("mykommu-box"), "");
    check("headphones are not the box", !BoxName.looksLikeBoxName("JBL Flip 6"), "");
    check("the car's head unit is not the box", !BoxName.looksLikeBoxName("BYD Sealion"), "");
    check("no name at all is not the box", !BoxName.looksLikeBoxName(null), "");
    check("an empty name is not the box", !BoxName.looksLikeBoxName("   "), "");
    // the box is identified by address, so the address format has to be exactly right
    // the lens is a real trade: the same view cannot reach 100 m and cover three lanes at 10 m
    LaneGeometry.Camera far = new LaneGeometry.Camera(1080f, 1800f, LaneGeometry.Camera.FAR_FOCAL);
    LaneGeometry.Camera balanced = new LaneGeometry.Camera(1080f, 1800f,
        LaneGeometry.Camera.BALANCED_FOCAL);
    LaneGeometry.Camera wide = new LaneGeometry.Camera(1080f, 1800f, LaneGeometry.Camera.WIDE_FOCAL);
    check("the far lens covers only the car's own lane at 10 m", far.halfWidthAt(10) < 4.0,
        "" + far.halfWidthAt(10));
    check("the wide lens covers three lanes at 10 m", wide.halfWidthAt(10) > 5.0,
        "" + wide.halfWidthAt(10));
    check("the wide lens still reaches 100 m on screen", wide.py(100) > wide.horizonY()
        && wide.py(100) < wide.horizonY() + 200, "");
    check("a 100 m lane is widest on, and least legible without, the far lens",
        far.px(100, 1.535) - far.px(100, -1.535) > wide.px(100, 1.535) - wide.px(100, -1.535),
        (far.px(100, 1.535) - far.px(100, -1.535)) + " vs "
            + (wide.px(100, 1.535) - wide.px(100, -1.535)));
    check("the three lenses are ordered, and the default is the far one",
        wide.halfWidthAt(10) > balanced.halfWidthAt(10) && balanced.halfWidthAt(10) > far.halfWidthAt(10)
        && LaneGeometry.Camera.DEFAULT_FOCAL_FRACTION == LaneGeometry.Camera.FAR_FOCAL, "");

    check("the configured box address is a valid address itself",
        BoxName.isMac(BoxName.DEFAULT_BOX_MAC), BoxName.DEFAULT_BOX_MAC);
    check("lower case is accepted", BoxName.isMac("9c:b8:b4:5f:0a:37"), "");
    check("stray spaces are tolerated", BoxName.isMac("  9C:B8:B4:5F:0A:37 "), "");
    check("a remembered lower-case address still matches the configured one",
        BoxName.normalizeMac("9c:b8:b4:5f:0a:37").equals(BoxName.DEFAULT_BOX_MAC), "");
    check("too few pairs is not an address", !BoxName.isMac("9C:B8:B4:5F:0A"), "");
    check("missing separators is not an address", !BoxName.isMac("9CB8B45F0A37"), "");
    check("a non-hex digit is not an address", !BoxName.isMac("9C:B8:B4:5F:0A:3G"), "");
    check("a device name is not an address", !BoxName.isMac("kommu-c87bd64bfd13255a"), "");
    check("an empty string is not an address", !BoxName.isMac(""), "");
    check("a null address is not an address", !BoxName.isMac(null), "");

    check("the serial-port profile identifies the box even when renamed",
        BoxName.hasSerialPort(new String[] {"00001800-0000-1000-8000-00805f9b34fb",
                                            "00001101-0000-1000-8000-00805F9B34FB"}), "");
    check("uuids that are not the serial port do not count",
        !BoxName.hasSerialPort(new String[] {"00001800-0000-1000-8000-00805f9b34fb"}), "");
    check("a device that advertises nothing is not the box", !BoxName.hasSerialPort(null), "");
    check("a null entry among the uuids does not crash it",
        !BoxName.hasSerialPort(new String[] {null}), "");

    // --- the dark mode has to be readable, not just dark ---------------------------------
    // WCAG: 4.5 is the body-text threshold, 3.0 the large-text one. Every pair below is drawn by the
    // app exactly as tested.
    check("dark: body text on a card is readable", Palette.contrast(Palette.DARK_TEXT, Palette.DARK_CARD) >= 4.5,
        "" + Palette.contrast(Palette.DARK_TEXT, Palette.DARK_CARD));
    check("dark: body text on the page background is readable",
        Palette.contrast(Palette.DARK_TEXT, Palette.DARK_BG) >= 4.5,
        "" + Palette.contrast(Palette.DARK_TEXT, Palette.DARK_BG));
    check("dark: the muted row labels are readable",
        Palette.contrast(Palette.DARK_MUTED, Palette.DARK_CARD) >= 4.5,
        "" + Palette.contrast(Palette.DARK_MUTED, Palette.DARK_CARD));
    check("dark: a stepper's number on its chip is readable",
        Palette.contrast(Palette.DARK_TEXT, Palette.DARK_CHIP) >= 4.5,
        "" + Palette.contrast(Palette.DARK_TEXT, Palette.DARK_CHIP));
    check("dark: white on the accent colour is readable",
        Palette.contrast(Palette.WHITE, Palette.ACCENT) >= 4.5,
        "" + Palette.contrast(Palette.WHITE, Palette.ACCENT));
    check("dark: a red action is readable on its wash",
        Palette.contrast(Palette.DARK_DANGER, Palette.DARK_DANGER_WASH) >= 4.5,
        "" + Palette.contrast(Palette.DARK_DANGER, Palette.DARK_DANGER_WASH));
    check("dark: the un-chosen tab is readable on the tab bar",
        Palette.contrast(Palette.DARK_MUTED, Palette.DARK_TAB_BAR) >= 4.5,
        "" + Palette.contrast(Palette.DARK_MUTED, Palette.DARK_TAB_BAR));
    check("light: body text on a card is readable",
        Palette.contrast(Palette.LIGHT_TEXT, Palette.LIGHT_CARD) >= 4.5,
        "" + Palette.contrast(Palette.LIGHT_TEXT, Palette.LIGHT_CARD));
    check("light: the muted row labels are readable",
        Palette.contrast(Palette.LIGHT_MUTED, Palette.LIGHT_CARD) >= 4.5,
        "" + Palette.contrast(Palette.LIGHT_MUTED, Palette.LIGHT_CARD));
    check("light: a red action is readable on its wash",
        Palette.contrast(Palette.LIGHT_DANGER, Palette.LIGHT_DANGER_WASH) >= 4.5,
        "" + Palette.contrast(Palette.LIGHT_DANGER, Palette.LIGHT_DANGER_WASH));
    check("the two modes are genuinely different surfaces",
        Palette.contrast(Palette.DARK_BG, Palette.LIGHT_BG) > 10,
        "" + Palette.contrast(Palette.DARK_BG, Palette.LIGHT_BG));
    check("contrast maths agrees with the known extremes",
        Math.abs(Palette.contrast(0xFFFFFFFF, 0xFF000000) - 21.0) < 0.01,
        "" + Palette.contrast(0xFFFFFFFF, 0xFF000000));

    // --- the perspective view (this is what makes the lane read as a road) ----------------
    // A 1080x1920 view: the projection must behave like a camera on the car, not like a diagram.
    LaneGeometry.Camera cam = new LaneGeometry.Camera(1080f, 1590f);  // portrait, as asked for
    check("a point straight ahead is centred", Math.abs(cam.px(10, 0) - 540f) < 0.01f,
        "" + cam.px(10, 0));
    check("and it stays centred however far ahead it is",
        Math.abs(cam.px(30, 0) - 540f) < 0.01f, "" + cam.px(30, 0));
    check("right of the car is right on screen", cam.px(10, 1) > 540f, "" + cam.px(10, 1));
    check("left of the car is left on screen", cam.px(10, -1) < 540f, "" + cam.px(10, -1));
    check("the road surface is below the horizon", cam.py(10) > cam.horizonY(), "" + cam.py(10));
    check("nearer road is lower on screen (further is higher)",
        cam.py(30) < cam.py(5) && cam.py(5) < cam.py(2),
        cam.py(30) + " " + cam.py(5) + " " + cam.py(2));
    // tolerance proportional to the near-field depth, so this holds for any focal length
    check("the horizon is the limit: 1 km ahead is a fraction of a percent of the near field away",
        Math.abs(cam.py(1000) - cam.horizonY()) < 0.02 * (cam.py(10) - cam.horizonY()),
        cam.py(1000) + " vs " + cam.horizonY() + " (near field " + (cam.py(10) - cam.horizonY()) + ")");
    check("and converging: ten times further is ten times closer to it",
        Math.abs(cam.py(10000) - cam.horizonY())
        < Math.abs(cam.py(1000) - cam.horizonY()) / 5.0,
        cam.py(10000) + " vs " + cam.py(1000) + " (horizon " + cam.horizonY() + ")");
    check("nothing blows up at the car itself (the near plane clamps)",
        cam.py(0) == cam.py(LaneGeometry.Camera.MIN_NEAR_M) && cam.px(0, 0) == 540f,
        cam.py(0) + " " + cam.px(0, 0));
    float nearSpan = cam.px(5, 1.6) - cam.px(5, -1.6);
    float farSpan = cam.px(20, 1.6) - cam.px(20, -1.6);
    check("a lane converges with distance, as a real one does", farSpan < nearSpan && farSpan > 0,
        "at 5 m " + nearSpan + " px, at 20 m " + farSpan + " px");
    float ten = cam.px(10, 1.535) - cam.px(10, -1.535);
    check("a 3.07 m lane at 10 m is a sensible width in pixels", ten > 40 && ten < 1200, "" + ten);
    check("a lane at 100 m is still a readable width, not a hairline",
        cam.px(100, 1.535) - cam.px(100, -1.535) >= 20,
        "" + (cam.px(100, 1.535) - cam.px(100, -1.535)));
    check("everything scales with distance, not with a fixed metre ruler",
        cam.pxPerMetreAt(5) > cam.pxPerMetreAt(20),
        cam.pxPerMetreAt(5) + " " + cam.pxPerMetreAt(20));
    check("the far end of the drawn lane sits near the horizon",
        cam.py(40) < cam.horizonY() + 60, cam.py(40) + " vs horizon " + cam.horizonY());
    check("the drawing starts at the very bottom edge of the frame, not above it",
        Math.abs(cam.py(cam.nearDistance()) - 1590) < 2,
        cam.py(cam.nearDistance()) + " of 1590 (starts at " + cam.nearDistance() + " m)");
    check("and never nearer than the safety floor",
        cam.nearDistance() >= LaneGeometry.Camera.MIN_NEAR_M, "" + cam.nearDistance());
    check("the same projection works in a landscape card too (shorter view, nearer start)",
        new LaneGeometry.Camera(1080f, 670f).nearDistance() > cam.nearDistance()
        && new LaneGeometry.Camera(1080f, 670f).px(10, 0) == cam.px(10, 0),
        new LaneGeometry.Camera(1080f, 670f).nearDistance() + " m vs " + cam.nearDistance() + " m");
    check("by 5 m both lane edges are inside the frame",
        cam.px(5, 1.535) < 1080 && cam.px(5, -1.535) > 0,
        cam.px(5, -1.535) + " .. " + cam.px(5, 1.535));
    check("the 100 m mark is on screen, just under the horizon",
        cam.py(100) > cam.horizonY() && cam.py(100) < cam.horizonY() + 120,
        cam.py(100) + " vs horizon " + cam.horizonY());
    check("the 20-100 m band is drawn at all (the honest limit of a perspective view)",
        cam.py(20) - cam.py(100) > 60, cam.py(20) - cam.py(100) + " px");
    // The old plan-view mapper (equal pixels per metre on both axes) is gone on purpose: that
    // "true proportion" is exactly what made the view read as a diagram. These two assertions pin the
    // properties that replaced it, so a future edit cannot quietly turn the road back into a chart.
    float nearHalf = cam.px(5, 1.535) - cam.px(5, 0);
    float farHalf = cam.px(20, 1.535) - cam.px(20, 0);
    check("the same lane metre covers fewer pixels the further away it is (perspective, not a chart)",
        farHalf < nearHalf * 0.4f, "at 5 m " + nearHalf + " px, at 20 m " + farHalf + " px");
    check("depth compresses: 5-10 m fills far more of the view than 30-40 m",
        cam.py(5) - cam.py(10) > 4 * (cam.py(30) - cam.py(40)),
        (cam.py(5) - cam.py(10)) + " vs " + (cam.py(30) - cam.py(40)));

    // --- a closer-horizon pose must be labelled, not passed off as the car's own ---------
    String fallbackPose = "{\"t\":1000.0,\"ok\":1,\"v\":3.0,\"eng\":0,\"off\":0.11,\"w\":3.05,"
        + "\"look\":6.0,\"car_look\":10.0,\"fallback\":1,"
        + "\"l\":[[0.0,-1.6],[5.0,-1.6]],\"r\":[[0.0,1.45],[5.0,1.45]]}";
    LaneGeometry.Pose near = LaneGeometry.fromJson(Json.parseObject(fallbackPose), 1000.2);
    check("pose: a closer-horizon pose is accepted but labelled", near.ok && near.fallback,
        near.summary());
    check("pose: the label names both distances", near.summary().contains("at 6 m")
        && near.summary().contains("10 m"), near.summary());
    String plainPose = onroad.replace("\"curv\":-0.00042", "\"car_look\":24.0,\"curv\":-0.00042");
    LaneGeometry.Pose plain = LaneGeometry.fromJson(Json.parseObject(plainPose), 1000.4);
    check("pose: a full-horizon pose carries no label", plain.ok && !plain.summary().contains("at "),
        plain.summary());

    // --- staleness and rubbish -------------------------------------------------------
    LaneGeometry.Pose stale = LaneGeometry.fromJson(Json.parseObject(onroad), 1005.0);
    // the set speed the box sends for the HUD
    LaneGeometry.Pose withSet = LaneGeometry.fromJson(
        Json.parseObject("{\"t\":1000.0,\"ok\":1,\"v\":16.4,\"set\":11.1,\"off\":0.1,\"w\":3.05,"
            + "\"l\":[[0.0,-1.5]],\"r\":[[0.0,1.5]]}"), 1000.1);
    check("a set speed from the box reaches the app", Math.abs(withSet.setSpeed - 11.1) < 0.001,
        "" + withSet.setSpeed);
    check("16.4 m/s shows as 59 km/h", Math.abs(withSet.speed * 3.6 - 59.0) < 0.2, "");
    check("11.1 m/s shows as 40 km/h", Math.abs(withSet.setSpeed * 3.6 - 40.0) < 0.2, "");
    LaneGeometry.Pose noSet = LaneGeometry.fromJson(
        Json.parseObject("{\"t\":1000.0,\"ok\":1,\"v\":16.4,\"off\":0.1,\"w\":3.05,"
            + "\"l\":[[0.0,-1.5]],\"r\":[[0.0,1.5]]}"), 1000.1);
    check("an older box that omits the set speed reads as not set (0, not an error)",
        noSet.setSpeed == 0.0 && noSet.speed == 16.4, "" + noSet.setSpeed);

    check("pose: a 5 s old pose is not drawn as live", !stale.ok, stale.summary());
    check("pose: staleness is explained", stale.why.contains("no fresh pose"), stale.why);

    String truncated = onroad.substring(0, 60);
    boolean threw = false;
    try {
      Json.parseObject(truncated);
    } catch (Json.JsonException e) {
      threw = true;
    }
    check("json: truncated input is an error, not a silent default", threw, "");
    threw = false;
    try {
      Json.parseObject("{\"a\":1} trailing");
    } catch (Json.JsonException e) {
      threw = true;
    }
    check("json: trailing junk is an error", threw, "");
    Map<String, Object> escaped = Json.parseObject("{\"why\":\"line\\nbreak \\\"quoted\\\"\"}");
    check("json: escapes decoded", "line\nbreak \"quoted\"".equals(Json.str(escaped, "why", "")),
        Json.str(escaped, "why", ""));
    check("json: a missing field falls back, not throws",
        Json.str(escaped, "nope", "fallback").equals("fallback"),
        Json.str(escaped, "nope", "fallback"));

    // --- the readouts: a frame that changes nothing must touch no view -------------------------
    // The reported lag ("the update of set speed is still very slow") was the pose handler calling
    // setText on sixteen views ten times a second from the Bluetooth reader thread, so the reader fell
    // behind the socket and replayed a backlog seconds later. These checks pin the cure.
    final Map<String, String> onScreen = new java.util.LinkedHashMap<String, String>();
    Readouts.Sink sink = new Readouts.Sink() {
      public void put(String key, String value) {
        onScreen.put(key, value);
      }
    };
    Readouts.Cache shown = new Readouts.Cache();

    check("the set speed shown is the car's own, not the fork's normalised value",
        "set 50 km/h".equals(Readouts.setSpeed(13.89)) && "set 45 km/h".equals(Readouts.setSpeed(12.40)),
        Readouts.setSpeed(13.89) + " / " + Readouts.setSpeed(12.40));
    check("an unset cruise says so", "cruise not set".equals(Readouts.setSpeed(0.0)),
        Readouts.setSpeed(0.0));

    check("a changed value needs setting", shown.changed("set", "set 50 km/h"), "first time");
    check("the same value does not", !shown.changed("set", "set 50 km/h"), "second time");
    check("a different value does again", shown.changed("set", "set 55 km/h"), "55");

    String steadyJson = "{\"ok\":1,\"l\":[[0,1.4],[10,1.4],[20,1.4]],\"r\":[[0,-1.4],[10,-1.4],[20,-1.4]],"
        + "\"t\":1000.0,\"v\":16.44,\"set\":13.89,\"lp\":0.98,\"rp\":0.97,\"curv\":0.0,"
        + "\"look\":40.0,\"eng\":1,\"age\":0.1}";
    LaneGeometry.Pose steady = LaneGeometry.fromJson(Json.parseObject(steadyJson), 1000.1);
    onScreen.clear();
    Readouts.fill(steady, shown, "far", 3.3, sink);
    check("a first frame fills the page", onScreen.size() >= 10, "" + onScreen.size());
    onScreen.clear();
    Readouts.fill(steady, shown, "far", 3.3, sink);
    check("the same frame again touches no view at all (this is the fix)", onScreen.isEmpty(),
        onScreen.toString());

    // a change too small for the driver to see - 16.44 to 16.46 m/s is still 59 km/h - is not a change
    final String json = steadyJson;
    LaneGeometry.Pose nudged = LaneGeometry.fromJson(
        Json.parseObject(json.replace("\"v\":16.44", "\"v\":16.46")), 1000.1);
    onScreen.clear();
    Readouts.fill(nudged, shown, "far", 3.3, sink);
    check("a change too small to see does not touch a view either", onScreen.isEmpty(),
        onScreen.toString());

    LaneGeometry.Pose faster = LaneGeometry.fromJson(
        Json.parseObject(json.replace("\"v\":16.44", "\"v\":20.0")), 1000.1);
    onScreen.clear();
    Readouts.fill(faster, shown, "far", 3.3, sink);
    check("a change the driver can see updates only that row",
        onScreen.size() > 0 && onScreen.size() <= 3 && "72".equals(onScreen.get("speed")),
        onScreen.toString());

    // --- the car's state, in the four words the app shows ---------------------------------------
    check("off: no link to the box", States.OFF.equals(States.name(false, false, false))
        && States.OFF.equals(States.name(false, true, true)),
        States.name(false, true, true));
    check("offroad: connected, car asleep", States.OFFROAD.equals(States.name(true, false, false)),
        States.name(true, false, false));
    check("onroad: car awake, not engaged", States.ONROAD.equals(States.name(true, true, false)),
        States.name(true, true, false));
    check("autodrive: engaged", States.AUTODRIVE.equals(States.name(true, true, true)),
        States.name(true, true, true));
    check("engaged while the box still says offroad still reads as autodrive",
        States.AUTODRIVE.equals(States.name(true, false, true)), States.name(true, false, true));
    check("the detail explains the word rather than repeating it",
        States.detail(true, true, false, true).contains("ready to engage")
        && States.detail(true, true, false, false).contains("not ready")
        && States.detail(true, false, false, false).contains("asleep")
        && States.detail(false, false, false, false).contains("no link"),
        States.detail(true, true, false, true));
    check("and the dot follows the state: muted, wait, accent",
        "muted".equals(States.tone(false, false, false)) && "wait".equals(States.tone(true, true, false))
        && "accent".equals(States.tone(true, true, true)), States.tone(true, true, true));

    // --- what the car's own ACC is doing, straight off its ACC_CMD message ---------------------
    String accJson = "{\"ok\":1,\"l\":[[0,1.4],[10,1.4]],\"r\":[[0,-1.4],[10,-1.4]],\"t\":1000.0,"
        + "\"v\":16.0,\"set\":13.89,\"eng\":1,\"age\":0.1,"
        + "\"acc\":{\"cmd\":-4,\"on\":1,\"on2\":0,\"ctrl\":1,\"ovr\":0,\"still\":0}}";
    LaneGeometry.Pose withAcc = LaneGeometry.fromJson(Json.parseObject(accJson), 1000.1);
    check("the car's acceleration request is read", withAcc.accPresent && withAcc.accCmd == -4,
        String.valueOf(withAcc.accCmd));
    check("its flags come with it", withAcc.accOn && withAcc.accCtrl && !withAcc.accOverride,
        (withAcc.accOn ? 1 : 0) + "/" + (withAcc.accCtrl ? 1 : 0));
    check("and the raw figure converts to m/s2 at a tenth of a unit",
        Math.abs(withAcc.accMetersPerSecondSquared() + 0.4) < 1e-9,
        String.valueOf(withAcc.accMetersPerSecondSquared()));
    check("it is shown as a signed m/s2 figure", "-0.4 m/s2".equals(Readouts.accRequest(withAcc)),
        Readouts.accRequest(withAcc));
    check("the car saying controllable reads as such",
        "on, controllable".equals(Readouts.accState(withAcc)), Readouts.accState(withAcc));

    LaneGeometry.Pose accOff = LaneGeometry.fromJson(
        Json.parseObject(accJson.replace("\"on\":1", "\"on\":0")), 1000.1);
    check("ACC off reads as off", "off".equals(Readouts.accState(accOff)), Readouts.accState(accOff));
    LaneGeometry.Pose accHeld = LaneGeometry.fromJson(
        Json.parseObject(accJson.replace("\"still\":0", "\"still\":1")), 1000.1);
    check("holding at a standstill is stated", Readouts.accState(accHeld).contains("standstill"),
        Readouts.accState(accHeld));

    LaneGeometry.Pose noAcc = LaneGeometry.fromJson(
        Json.parseObject(accJson.substring(0, accJson.indexOf(",\"acc\"")) + "}"), 1000.1);
    check("a pose with no ACC data says so rather than showing a zero",
        !noAcc.accPresent && "not reported".equals(Readouts.accState(noAcc))
        && "-".equals(Readouts.accRequest(noAcc)), Readouts.accState(noAcc));

    // --- a faint lane is shown as a prediction, never as a lane that was read --------------------
    String faintJson = "{\"ok\":0,\"why\":\"faint markings (0.32 / 0.28)\",\"low\":1,\"lp\":0.32,\"rp\":0.28,"
        + "\"l\":[[0,-1.4],[10,-1.4],[20,-1.4]],\"r\":[[0,1.4],[10,1.4],[20,1.4]],\"t\":1000.0,"
        + "\"v\":16.0,\"eng\":1,\"age\":0.1}";
    LaneGeometry.Pose faintPose = LaneGeometry.fromJson(Json.parseObject(faintJson), 1000.1);
    check("a predicted lane is marked as such", faintPose.predicted, String.valueOf(faintPose.predicted));
    check("and it is still drawn, rather than leaving an empty road",
        faintPose.leftX.length == 3 && faintPose.rightX.length == 3,
        faintPose.leftX.length + "/" + faintPose.rightX.length);
    check("the reason says why, so the caption is not just 'no lane data'",
        faintPose.summary().contains("faint markings"), faintPose.summary());
    final java.util.Map<String, String> captioned = new java.util.HashMap<String, String>();
    Readouts.fill(faintPose, new Readouts.Cache(), "far", 3.3, new Readouts.Sink() {
      public void put(String key, String value) {
        captioned.put(key, value);
      }
    });
    check("and the caption says it is a prediction rather than a read lane",
        captioned.get("caption").contains("prediction"), captioned.get("caption"));
    LaneGeometry.Pose readPose = LaneGeometry.fromJson(
        Json.parseObject(faintJson.replace("\"low\":1,", "")), 1000.1);
    check("a read lane is not marked as a prediction", !readPose.predicted, "not predicted");

    // --- keeping the screen on ---------------------------------------------------------------
    check("autodrive keeps the screen on while the car is driving itself",
        ScreenOn.keep(ScreenOn.AUTODRIVE, true, true), "autodrive + engaged");
    check("but not while merely connected and parked",
        !ScreenOn.keep(ScreenOn.AUTODRIVE, true, false), "autodrive + parked");
    check("while-connected holds it on whenever the link is up",
        ScreenOn.keep(ScreenOn.CONNECTED, true, false) && ScreenOn.keep(ScreenOn.CONNECTED, true, true),
        "connected");
    check("off never holds it", !ScreenOn.keep(ScreenOn.OFF, true, true), "off");
    check("nothing holds it without a link to the box",
        !ScreenOn.keep(ScreenOn.CONNECTED, false, true) && !ScreenOn.keep(ScreenOn.AUTODRIVE, false, true),
        "no link");
    check("the button cycles all three choices and comes back",
        ScreenOn.next(ScreenOn.AUTODRIVE).equals(ScreenOn.CONNECTED)
        && ScreenOn.next(ScreenOn.CONNECTED).equals(ScreenOn.OFF)
        && ScreenOn.next(ScreenOn.OFF).equals(ScreenOn.AUTODRIVE), "cycle");
    check("a nonsense stored choice still resolves to the default",
        ScreenOn.keep(ScreenOn.next("nonsense"), true, true), ScreenOn.next("nonsense"));

    // --- Wi-Fi: the payload has to survive an awkward password ----------------------------------
    check("a plain string quotes cleanly", "\"plain\"".equals(Wifi.quote("plain")), Wifi.quote("plain"));
    check("quotes and backslashes are escaped",
        "\"a\\\"b\\\\c\"".equals(Wifi.quote("a\"b\\c")), Wifi.quote("a\"b\\c"));
    check("a newline cannot break the command", !Wifi.quote("a\nb").contains("\n"),
        Wifi.quote("a\nb"));

    String awkward = "p\"w\\d\ne";
    String command = Wifi.connectCommand("My:Net", awkward);
    check("the command is a single line", !command.contains("\n"), command);
    String payload = command.substring("WIFI CONNECT ".length());
    Map<String, Object> parsed = Json.parseObject(payload);
    check("and the box will parse it back to the same SSID",
        "My:Net".equals(Json.str(parsed, "ssid", "")), Json.str(parsed, "ssid", ""));
    check("with the awkward password intact, character for character",
        awkward.equals(Json.str(parsed, "password", "")), Json.str(parsed, "password", ""));
    check("an open network sends no password at all",
        !Wifi.connectCommand("Open", "").contains("password"), Wifi.connectCommand("Open", ""));
    check("forget carries the name", Wifi.forgetCommand("My:Net").contains("My:Net"),
        Wifi.forgetCommand("My:Net"));

    Map<String, Object> lockedRow = Json.parseObject(
        "{\"ssid\":\"Hs\",\"signal\":74,\"sec\":\"WPA2\",\"in_use\":true}");
    Wifi.Network locked = Wifi.parse(lockedRow);
    check("a locked network is known to need a password", locked.needsPassword(), locked.label());
    check("and its row says so, with the signal and that it is the one in use",
        locked.label().contains("WPA2") && locked.label().contains("74")
        && locked.label().contains("connected"), locked.label());
    Wifi.Network open = Wifi.parse(Json.parseObject(
        "{\"ssid\":\"Cafe\",\"signal\":30,\"sec\":\"\",\"in_use\":false}"));
    check("an open network does not", !open.needsPassword() && open.label().contains("open"),
        open.label());

    Map<String, Object> status = Json.parseObject(
        "{\"ssid\":\"Hs\",\"device\":\"wlan0\",\"ip\":\"10.96.216.167\",\"signal\":\"71\","
        + "\"connected\":true}");
    check("the status line names the network, its address and the signal",
        Wifi.statusLine(status).contains("Hs") && Wifi.statusLine(status).contains("10.96.216.167")
        && Wifi.statusLine(status).contains("71%"), Wifi.statusLine(status));
    check("and says so when the box is not on Wi-Fi at all",
        "not on Wi-Fi".equals(Wifi.statusLine(Json.parseObject("{\"connected\":false}"))),
        Wifi.statusLine(Json.parseObject("{\"connected\":false}")));

    // --- the predicted route and the calculated lane are separate things ------------------------
    String routeJson = "{\"ok\":1,\"l\":[[0,-1.4],[10,-1.4],[20,-1.4]],\"r\":[[0,1.4],[10,1.4],[20,1.4]],"
        + "\"path\":[[0,0.0],[10,-0.2],[20,-0.6]],\"path_reach\":20.0,"
        + "\"calc\":[[0,0.0],[10,0.0],[20,0.0]],\"t\":1000.0,\"v\":16.0,\"eng\":1,\"age\":0.1}";
    LaneGeometry.Pose routePose = LaneGeometry.fromJson(Json.parseObject(routeJson), 1000.1);
    check("the model's predicted route is parsed as its own points",
        routePose.routeX.length == 3 && Math.abs(routePose.routeY[2] + 0.6) < 1e-9,
        routePose.routeX.length + " pts, y=" + routePose.routeY[2]);
    check("the calculated lane centre is parsed too",
        routePose.centreX.length == 3 && Math.abs(routePose.centreY[1]) < 1e-9,
        routePose.centreX.length + " pts");
    check("the two are not the same line",
        routePose.routeY[2] != routePose.centreY[2], routePose.routeY[2] + " vs " + routePose.centreY[2]);
    check("the route's reach is reported for the readout",
        "20 m predicted".equals(Readouts.routeReach(routePose)), Readouts.routeReach(routePose));

    LaneGeometry.Pose noRoute = LaneGeometry.fromJson(
        Json.parseObject(routeJson.replace("\"path\":[[0,0.0],[10,-0.2],[20,-0.6]],\"path_reach\":20.0,", "")),
        1000.1);
    check("no route is stated rather than shown as zero",
        !Readouts.routeReach(noRoute).contains("0 m"), Readouts.routeReach(noRoute));
    check("a pose with no route at all still parses", noRoute.routeX.length == 0,
        String.valueOf(noRoute.routeX.length));

    // --- the automatic lens: how the view follows the speed, and why it does not flicker ---------
    double farF = LaneGeometry.Camera.FAR_FOCAL, midF = LaneGeometry.Camera.BALANCED_FOCAL;
    double wideF = LaneGeometry.Camera.WIDE_FOCAL;
    check("at 80 km/h and above it shows as far as it can",
        Lens.focal(Lens.AUTO, 80 / 3.6, midF) == farF
        && Lens.focal(Lens.AUTO, 130 / 3.6, midF) == farF, "80 and 130 km/h");
    check("below 80 it does not", Lens.focal(Lens.AUTO, 79 / 3.6, midF) == midF, "79 km/h");
    check("once far, it stays far down to 74 - a car sitting on 80 must not flicker",
        Lens.focal(Lens.AUTO, 78 / 3.6, farF) == farF && Lens.focal(Lens.AUTO, 73 / 3.6, farF) == midF,
        "78 with far on screen, then 73");
    check("the same holds at the lower step",
        Lens.focal(Lens.AUTO, 42 / 3.6, midF) == midF && Lens.focal(Lens.AUTO, 42 / 3.6, wideF) == wideF,
        "42 km/h from each side");
    check("in town it goes wide", Lens.focal(Lens.AUTO, 20 / 3.6, midF) == wideF, "20 km/h");
    check("a fixed choice ignores the speed entirely",
        Lens.focal(Lens.WIDE, 130 / 3.6, farF) == wideF && Lens.focal(Lens.FAR, 5 / 3.6, wideF) == farF,
        "fixed far and wide");
    check("a nonsense stored choice falls back to automatic",
        Lens.focal("nonsense", 100 / 3.6, midF) == farF, "nonsense choice");
    check("the button cycles auto, far, balanced, wide and back",
        Lens.next(Lens.AUTO).equals(Lens.FAR) && Lens.next(Lens.FAR).equals(Lens.BALANCED)
        && Lens.next(Lens.BALANCED).equals(Lens.WIDE) && Lens.next(Lens.WIDE).equals(Lens.AUTO),
        "cycle");
    check("the label says which lens Auto is using right now",
        "Auto (far)".equals(Lens.label(Lens.AUTO, farF))
        && "Auto (wide)".equals(Lens.label(Lens.AUTO, wideF)), Lens.label(Lens.AUTO, farF));

    // --- how much of the page is sky -----------------------------------------------------------
    LaneGeometry.Camera skyCam = new LaneGeometry.Camera(1080f, 2400f);
    check("the sky is a small strip, leaving the page to the road",
        skyCam.horizonY() == 2400f * 0.14f, String.valueOf(skyCam.horizonY()));
    check("and the skyline sits above the speed readout rather than through it",
        skyCam.horizonY() >= 2400f * 0.10f, String.valueOf(skyCam.horizonY()));

    // --- where the lane data ends ---------------------------------------------------------------
    LaneGeometry.Pose uneven = LaneGeometry.fromJson(Json.parseObject(
        "{\"ok\":1,\"l\":[[0,-1.6],[30,-1.6]],\"r\":[[0,1.6],[80,1.6]],\"t\":1000.0,\"v\":16.0,\"age\":0.1}"),
        1000.1);
    check("the data's reach is the nearer of the two lines, not the further",
        Math.abs(uneven.dataReach() - 30.0) < 1e-9, String.valueOf(uneven.dataReach()));
    LaneGeometry.Pose longLane = LaneGeometry.fromJson(Json.parseObject(
        "{\"ok\":1,\"l\":[[0,-1.6],[190,-1.6]],\"r\":[[0,1.6],[190,1.6]],\"t\":1000.0,\"v\":16.0,\"age\":0.1}"),
        1000.1);
    check("a lane reaching past the drawn range still reports its real reach",
        Math.abs(longLane.dataReach() - 190.0) < 1e-9, String.valueOf(longLane.dataReach()));
    LaneGeometry.Pose noLaneAtAll = LaneGeometry.fromJson(Json.parseObject(
        "{\"ok\":0,\"why\":\"no model (car offroad)\",\"t\":1000.0,\"v\":0.0,\"age\":0.1}"), 1000.1);
    check("no lane at all means no reach to mark", noLaneAtAll.dataReach() == 0.0,
        String.valueOf(noLaneAtAll.dataReach()));

    // --- the centring drift, and the band that shows it ---------------------------------------
    String driftJson = "{\"ok\":1,\"l\":[[0,-1.6],[20,-1.6]],\"r\":[[0,1.6],[20,1.6]],\"off\":0.22,"
        + "\"look\":20.0,\"calc\":[[0,0.10],[10,0.18],[20,0.24]],\"t\":1000.0,\"v\":16.0,\"eng\":1,"
        + "\"age\":0.1}";
    LaneGeometry.Pose driftPose = LaneGeometry.fromJson(Json.parseObject(driftJson), 1000.1);
    check("the drift is the offset the car's correction acts on", Math.abs(driftPose.drift() - 0.22) < 1e-9,
        String.valueOf(driftPose.drift()));
    check("the centre's position is interpolated between its points",
        Math.abs(driftPose.centreOffsetAt(15.0) - 0.21) < 1e-9,
        String.valueOf(driftPose.centreOffsetAt(15.0)));
    check("before the first point it takes the first value, after the last the last",
        Math.abs(driftPose.centreOffsetAt(0.0) - 0.10) < 1e-9
        && Math.abs(driftPose.centreOffsetAt(60.0) - 0.24) < 1e-9,
        driftPose.centreOffsetAt(60.0) + "");
    double[] widths = driftPose.driftAt(new double[] {10, 20});
    check("the band's width at each distance is the drift there",
        Math.abs(widths[0] - 0.18) < 1e-9 && Math.abs(widths[1] - 0.24) < 1e-9,
        widths[0] + " / " + widths[1]);
    check("the readout says how far and which way",
        "0.22 m right".equals(Readouts.drift(driftPose)), Readouts.drift(driftPose));
    LaneGeometry.Pose leftPose = LaneGeometry.fromJson(
        Json.parseObject(driftJson.replace("\"off\":0.22", "\"off\":-0.31")), 1000.1);
    check("a drift the other way says left", "0.31 m left".equals(Readouts.drift(leftPose)),
        Readouts.drift(leftPose));
    LaneGeometry.Pose tightPose = LaneGeometry.fromJson(
        Json.parseObject(driftJson.replace("\"off\":0.22", "\"off\":0.02")), 1000.1);
    check("a car that is centred says so rather than showing a tiny number",
        Readouts.drift(tightPose).contains("centred"), Readouts.drift(tightPose));
    LaneGeometry.Pose noCentre = LaneGeometry.fromJson(
        Json.parseObject("{\"ok\":0,\"why\":\"faint markings (0.32 / 0.28)\",\"low\":1,"
            + "\"l\":[[0,-1.6],[20,-1.6]],\"r\":[[0,1.6],[20,1.6]],\"t\":1000.0,\"v\":16.0,\"age\":0.1}"),
        1000.1);
    check("with no calculated centre there is no drift to show", "-".equals(Readouts.drift(noCentre)),
        Readouts.drift(noCentre));

    // --- which speed is shown ------------------------------------------------------------------
    String bothJson = "{\"ok\":1,\"l\":[[0,-1.6],[20,-1.6]],\"r\":[[0,1.6],[20,1.6]],\"t\":1000.0,"
        + "\"v\":17.2,\"vd\":19.4,\"eng\":1,\"age\":0.1}";
    LaneGeometry.Pose both = LaneGeometry.fromJson(Json.parseObject(bothJson), 1000.1);
    check("the car's displayed speed is what the driver is shown",
        Math.abs(both.displayedSpeed() - 19.4) < 1e-9, String.valueOf(both.displayedSpeed()));
    check("and it is that number which reaches the screen",
        "70".equals(Readouts.speed(both.displayedSpeed())), Readouts.speed(both.displayedSpeed()));
    LaneGeometry.Pose onlyTrue = LaneGeometry.fromJson(
        Json.parseObject(bothJson.replace("\"vd\":19.4,", "")), 1000.1);
    check("with no displayed speed given it falls back to the box's own",
        Math.abs(onlyTrue.displayedSpeed() - 17.2) < 1e-9, String.valueOf(onlyTrue.displayedSpeed()));
    LaneGeometry.Pose standing = LaneGeometry.fromJson(
        Json.parseObject(bothJson.replace("\"vd\":19.4", "\"vd\":0.0")), 1000.1);
    check("a zero displayed speed is not treated as a reading",
        Math.abs(standing.displayedSpeed() - 17.2) < 1e-9, String.valueOf(standing.displayedSpeed()));

    // --- the raised viewpoint -------------------------------------------------------------------
    LaneGeometry.Camera realCam = new LaneGeometry.Camera(1080, 2400, 1.5,
        Lens.cameraHeight("real"));
    LaneGeometry.Camera highCam = new LaneGeometry.Camera(1080, 2400, 1.5,
        Lens.cameraHeight("higher"));
    check("the raised view draws from higher up than the camera really is",
        Lens.cameraHeight("higher") > Lens.cameraHeight("real"),
        String.valueOf(Lens.cameraHeight("higher")));
    check("and the highest is higher again",
        Lens.cameraHeight("highest") > Lens.cameraHeight("higher"),
        String.valueOf(Lens.cameraHeight("highest")));
    check("with an extra high step above that",
        Lens.cameraHeight("extra") > Lens.cameraHeight("highest"),
        String.valueOf(Lens.cameraHeight("extra")));
    check("every height has a label, and the last one says so",
        "Height: extra high".equals(Lens.heightLabel("extra")), Lens.heightLabel("extra"));
    check("a higher viewpoint pushes the same distance further down the page",
        highCam.py(30.0) > realCam.py(30.0),
        String.format("%.0f px vs %.0f px at 30 m", realCam.py(30.0), highCam.py(30.0)));
    check("but the horizon is unmoved - height does not buy distance",
        Math.abs(highCam.horizonY() - realCam.horizonY()) < 1e-9, String.valueOf(highCam.horizonY()));
    check("the choices cycle and wrap",
        "higher".equals(Lens.nextHeight("real")) && "highest".equals(Lens.nextHeight("higher"))
            && "extra".equals(Lens.nextHeight("highest"))
            && "real".equals(Lens.nextHeight("extra")), Lens.nextHeight("highest"));
    check("an unknown height choice falls back to where the camera really is",
        Math.abs(Lens.cameraHeight("nonsense") - LaneGeometry.Camera.CAM_HEIGHT_M) < 1e-9,
        String.valueOf(Lens.cameraHeight("nonsense")));
    check("a camera cannot be told to draw from zero or below",
        Math.abs(new LaneGeometry.Camera(1080, 2400, 1.5, 0).py(30.0) - realCam.py(30.0)) < 1e-9,
        String.valueOf(new LaneGeometry.Camera(1080, 2400, 1.5, 0).py(30.0)));

    // --- the straight-line continuation past the model's last point -----------------------------
    double[] lx = {0, 10, 20};
    double[] ly = {-1.60, -1.55, -1.50};
    double[] g = LaneGeometry.extrapolateTo(lx, ly, 120.0);
    check("the guess runs from the last real point out to the view's range",
        g != null && g[0] == 20.0 && g[2] == 120.0, g == null ? "null" : String.valueOf(g[2]));
    check("and keeps the slope of the last measured segment",
        Math.abs((g[3] - g[1]) / (g[2] - g[0]) - 0.005) < 1e-9,
        String.valueOf((g[3] - g[1]) / (g[2] - g[0])));
    check("nothing is guessed when the lane already reaches that far",
        LaneGeometry.extrapolateTo(new double[] {0, 50, 130}, new double[] {-1.6, -1.5, -1.4}, 120.0) == null,
        "already at range");
    check("nor from a single point",
        LaneGeometry.extrapolateTo(new double[] {10}, new double[] {-1.6}, 120.0) == null, "one point");
    check("nor when the lane data is not even going forwards",
        LaneGeometry.extrapolateTo(new double[] {20, 20}, new double[] {-1.6, -1.5}, 120.0) == null,
        "no forward extent");

    // --- road edges taken from the predicted route -------------------------------------------------
    double[] routeY = {0.0, -0.4, -1.1, -2.0};
    double[] leftEdge = LaneGeometry.routeEdge(routeY, true, 1.5);
    double[] rightEdge = LaneGeometry.routeEdge(routeY, false, 1.5);
    check("the left edge sits half a lane to the left of the route",
        Math.abs(leftEdge[2] - (-1.1 - 1.5)) < 1e-9, String.valueOf(leftEdge[2]));
    check("the right edge sits half a lane to the right",
        Math.abs(rightEdge[2] - (-1.1 + 1.5)) < 1e-9, String.valueOf(rightEdge[2]));
    check("and the edges follow the route's bend rather than running straight",
        Math.abs((leftEdge[3] - leftEdge[2]) - (routeY[3] - routeY[2])) < 1e-9,
        String.valueOf(leftEdge[3] - leftEdge[2]));
    check("the two edges stay a lane apart the whole way",
        Math.abs((rightEdge[1] - leftEdge[1]) - 3.0) < 1e-9 && Math.abs((rightEdge[3] - leftEdge[3]) - 3.0) < 1e-9,
        String.valueOf(rightEdge[3] - leftEdge[3]));

    // --- the sliding readings tab ---------------------------------------------------------------
    check("the tab sits at zero offset when it is open", Panel.offset(true, 570) == 0f, "open");
    check("and one whole panel-width off the page when it is tucked away",
        Math.abs(Panel.offset(false, 570) + 570f) < 1e-6, String.valueOf(Panel.offset(false, 570)));
    check("so the handle is what is left showing at the page edge",
        Math.abs(Panel.offset(false, 570) + 570f) < 1e-6, "handle only");
    check("the handle points left while the tab is out, right while it is away",
        "\u2039".equals(Panel.handle(true)) && "\u203A".equals(Panel.handle(false)),
        Panel.handle(true) + Panel.handle(false));
    check("and it toggles both ways", Panel.next(true) == false && Panel.next(false) == true, "toggle");

    // --- the view scaling itself to the prediction -------------------------------------------------
    check("a long route leaves the scale alone, so the near road is untouched",
        Math.abs(Lens.scaleForReach(300.0) - 1.0) < 1e-9, String.valueOf(Lens.scaleForReach(300.0)));
    check("a normal route is the reference and is also left alone",
        Math.abs(Lens.scaleForReach(120.0) - 1.0) < 1e-9, String.valueOf(Lens.scaleForReach(120.0)));
    check("a short route magnifies the view",
        Lens.scaleForReach(30.0) > 1.5, String.valueOf(Lens.scaleForReach(30.0)));
    check("but never beyond 2.2x",
        Math.abs(Lens.scaleForReach(1.0) - 2.2) < 1e-9, String.valueOf(Lens.scaleForReach(1.0)));
    check("and a stopped car does not zoom to absurdity",
        Lens.scaleForReach(0.0) <= 2.2, String.valueOf(Lens.scaleForReach(0.0)));
    check("the scale only ever grows as the route shortens",
        Lens.scaleForReach(200.0) <= Lens.scaleForReach(60.0)
            && Lens.scaleForReach(60.0) <= Lens.scaleForReach(25.0), "monotonic");

    // --- a manual lens is not scaled by the prediction --------------------------------------------
    String farChoice = Lens.FAR;
    check("a hand-picked far lens is far whatever the prediction does",
        Math.abs(Lens.drawnFocal(farChoice, 30.0, 0.0, 6.0) - LaneGeometry.Camera.FAR_FOCAL) < 1e-9,
        String.valueOf(Lens.drawnFocal(farChoice, 30.0, 0.0, 6.0)));
    check("and so is wide, rather than being magnified into a far",
        Math.abs(Lens.drawnFocal(Lens.WIDE, 30.0, 0.0, 6.0) - LaneGeometry.Camera.WIDE_FOCAL) < 1e-9,
        String.valueOf(Lens.drawnFocal(Lens.WIDE, 30.0, 0.0, 6.0)));
    check("Auto in town with a short route draws wider than the base wide lens",
        Lens.drawnFocal(Lens.AUTO, 8.0, 0.0, 20.0) > LaneGeometry.Camera.WIDE_FOCAL,
        String.valueOf(Lens.drawnFocal(Lens.AUTO, 8.0, 0.0, 20.0)));
    check("Auto at speed with a long route is the far lens, unscaled",
        Math.abs(Lens.drawnFocal(Lens.AUTO, 25.0, 0.0, 300.0) - LaneGeometry.Camera.FAR_FOCAL) < 1e-9,
        String.valueOf(Lens.drawnFocal(Lens.AUTO, 25.0, 0.0, 300.0)));

    // --- the log page's parsing ---------------------------------------------------------------
    String drivesLine = "DRIVES [{\"route\":\"2026-09-21--23-43-56--7\",\"started\":\"2026-09-21 23:43:56\","
        + "\"frames\":6006,\"median_speed_ms\":7.38,\"max_speed_ms\":7.96},"
        + "{\"route\":\"x\",\"started\":\"y\",\"frames\":100,\"median_speed_ms\":0.0,\"max_speed_ms\":0.0}]";
    java.util.List<String[]> drows = Drives.list(drivesLine);
    check("a DRIVES reply becomes one row per drive", drows.size() == 2, String.valueOf(drows.size()));
    check("with speeds converted to km/h for a driver", drows.get(0)[2].startsWith("27 km/h"), drows.get(0)[2]);
    check("and the start time taken from the box", "2026-09-21 23:43:56".equals(drows.get(0)[1]), drows.get(0)[1]);
    check("a drive with no speed is a dash, not zero km/h",
        "-".equals(drows.get(1)[2].split(" / ")[0]), drows.get(1)[2]);
    check("an empty list is not treated as an error", Drives.list("DRIVES []").isEmpty(), "empty");

    String summ = "DRIVES {\"route\":\"r\",\"frames\":1201,\"duration_s\":60.1,\"median_speed_ms\":6.87,"
        + "\"max_speed_ms\":8.71,\"lane_below_threshold_pct\":62.6,"
        + "\"centring\":{\"5m\":{\"median_m\":0.045,\"rms_m\":0.168},\"10m\":{\"median_m\":-0.035,\"rms_m\":0.192}},"
        + "\"computed_at\":\"2026-09-22 03:49:15\"}";
    java.util.List<String[]> pairs = Drives.summary(summ);
    java.util.TreeMap<String, String> byLabel = new java.util.TreeMap<String, String>();
    for (String[] p : pairs) {
      byLabel.put(p[0], p[1]);
    }
    check("the summary reports how long the drive was", byLabel.containsKey("Driven"), String.valueOf(pairs.size()) + " readings");
    check("lane availability is the usable share, not the missing share",
        byLabel.get("Lanes usable") != null && byLabel.get("Lanes usable").startsWith("37%"), byLabel.get("Lanes usable"));
    check("a mostly unreadable lane says so",
        byLabel.get("Lanes usable") != null && byLabel.get("Lanes usable").contains("mostly unreadable"), byLabel.get("Lanes usable"));
    check("the drift keeps its sign and direction",
        byLabel.get("Drift at 5 m") != null && byLabel.get("Drift at 5 m").startsWith("+0.05 m right"), byLabel.get("Drift at 5 m"));
    check("a leftward drift says left",
        byLabel.get("Drift at 10 m") != null && byLabel.get("Drift at 10 m").startsWith("-0.04 m left"), byLabel.get("Drift at 10 m"));
    check("an error reply is recognised rather than parsed",
        Drives.isError("DRIVES ERROR no logs for x") && !Drives.isError("DRIVES []"), "error handling");
    check("and its reason is surfaced", "no logs for x".equals(Drives.errorText("DRIVES ERROR no logs for x")),
        Drives.errorText("DRIVES ERROR no logs for x"));

    // ---- the host archive as a second source: same rows, wrapped into the box's line shape ------
    String hostList = "[{\"route\": \"2026-09-22--10-07-09\", \"started\": \"2026-09-22 10:07:09\", \"frames\": 104955, \"median_speed_ms\": 0.0, \"max_speed_ms\": 9.61}, {\"route\": \"2026-09-21--23-43-56\", \"started\": \"2026-09-21 23:43:56\", \"frames\": 73892, \"median_speed_ms\": 3.75, \"max_speed_ms\": 9.78}]";
    String hostReply = Remote.toReply(hostList);
    check("the host body is wrapped into the line the parser already reads",
        hostReply.startsWith("DRIVES [") && hostReply.indexOf('\n') < 0, hostReply.substring(0, Math.min(40, hostReply.length())));
    java.util.List<String[]> hostRows = Drives.list(hostReply);
    check("a host drive list parses into rows", hostRows.size() == 2, String.valueOf(hostRows.size()) + " rows");
    check("the first row keeps the drive's start and its speeds",
        "2026-09-22 10:07:09".equals(hostRows.get(0)[1]) && hostRows.get(0)[2].indexOf("km/h") >= 0,
        hostRows.get(0)[1] + " / " + hostRows.get(0)[2]);
    check("the row's route is what a tap sends back", hostRows.get(0)[0].startsWith("2026-09-"), hostRows.get(0)[0]);

    String hostSummary = "{\"route\": \"2026-09-18--10-09-06\", \"started\": \"2026-09-18 10:09:06\", \"duration_s\": 4080.2, \"frames\": 407958, \"median_speed_ms\": 20.75, \"max_speed_ms\": 34.25, \"km\": 77.23, \"segments\": 68, \"engaged_share\": 0.859, \"source\": \"host-mirror\", \"computed_at\": \"2026-09-22 19:45:12\"}";
    java.util.Map<String, String> hostByLabel = new java.util.LinkedHashMap<String, String>();
    for (String[] pair : Drives.summary(Remote.toReply(hostSummary))) {
      hostByLabel.put(pair[0], pair[1]);
    }
    check("a host summary reports the drive's length", hostByLabel.containsKey("Driven"), hostByLabel.toString());
    check("...and its speed", hostByLabel.containsKey("Speed"), hostByLabel.get("Speed"));
    check("...and claims nothing about lanes it did not measure",
        !hostByLabel.containsKey("Lanes usable"), hostByLabel.toString());
    check("the box's own summary still reports lanes",
        hasLabel(Drives.summary(boxSummaryLine()), "Lanes usable"), "regression");

    check("the host URL is built for the row shape the app reads",
        Remote.listUrl("https://drives.annovahome.com/").endsWith("/drives?format=legacy&limit=60"),
        Remote.listUrl("https://drives.annovahome.com/"));
    check("a summary URL carries the route",
        Remote.summaryUrl("https://drives.annovahome.com", "2026-09-18--10-09-06").endsWith("/summary/2026-09-18--10-09-06"),
        Remote.summaryUrl("https://drives.annovahome.com", "2026-09-18--10-09-06"));
    // ---- the detail view: distance, segments, ADAS share, pedals, and every minute -------------
    String hostDetail = "{\"route\": \"2026-09-18--10-09-06\", \"started\": \"2026-09-18 18:09:06\", \"started_utc\": \"2026-09-18T10:09:06\", \"duration_s\": 4080.2, \"frames\": 407958, \"median_speed_ms\": 20.75, \"max_speed_ms\": 34.25, \"km\": 77.23, \"segments\": 68, \"engaged_share\": 0.859, \"source\": \"host-mirror\", \"computed_at\": \"2026-09-22 19:50:38\", \"gas_share\": 0.174, \"brake_share\": 0.013, \"segments_moved\": 66, \"segment_rows\": [{\"route\": \"2026-09-18--10-09-06--0\", \"segment\": 0, \"off_s\": 0, \"median_kph\": 0.0, \"max_kph\": 0.0, \"km\": 0.0, \"engaged\": 0.0, \"moved\": false}, {\"route\": \"2026-09-18--10-09-06--1\", \"segment\": 1, \"off_s\": 60, \"median_kph\": 0.0, \"max_kph\": 8.2, \"km\": 0.01, \"engaged\": 0.0, \"moved\": false}, {\"route\": \"2026-09-18--10-09-06--2\", \"segment\": 2, \"off_s\": 120, \"median_kph\": 24.4, \"max_kph\": 26.1, \"km\": 0.37, \"engaged\": 1.0, \"moved\": true}, {\"route\": \"2026-09-18--10-09-06--3\", \"segment\": 3, \"off_s\": 180, \"median_kph\": 25.4, \"max_kph\": 28.6, \"km\": 0.41, \"engaged\": 1.0, \"moved\": true}, {\"route\": \"2026-09-18--10-09-06--4\", \"segment\": 4, \"off_s\": 240, \"median_kph\": 25.5, \"max_kph\": 28.5, \"km\": 0.41, \"engaged\": 1.0, \"moved\": true}, {\"route\": \"2026-09-18--10-09-06--5\", \"segment\": 5, \"off_s\": 300, \"median_kph\": 25.1, \"max_kph\": 26.1, \"km\": 0.4, \"engaged\": 1.0, \"moved\": true}]}";
    java.util.List<String[]> detailRows = Drives.summary(Remote.toReply(hostDetail));
    java.util.Map<String, String> byLabel2 = new java.util.LinkedHashMap<String, String>();
    for (String[] pair : detailRows) {
      byLabel2.put(pair[0], pair[1]);
    }
    check("the detail shows the distance driven", byLabel2.containsKey("Distance") && byLabel2.get("Distance").endsWith("km"),
        byLabel2.get("Distance"));
    check("...how many minutes it is made of", byLabel2.containsKey("Segments"), byLabel2.get("Segments"));
    check("...how much of it the ADAS drove", byLabel2.containsKey("Autodrive") && byLabel2.get("Autodrive").indexOf('%') >= 0,
        byLabel2.get("Autodrive"));
    check("...and how much the driver pedalled", byLabel2.containsKey("Your pedals"), byLabel2.get("Your pedals"));
    java.util.List<String[]> hostMinutes = Drives.segments(Remote.toReply(hostDetail));
    check("every minute of the drive is listed", hostMinutes.size() >= 5, String.valueOf(hostMinutes.size()) + " rows");
    check("a minute is stamped from the drive's own start", hostMinutes.get(0)[0].matches("\\d\\d:\\d\\d"), hostMinutes.get(0)[0]);
    check("a parked minute says so rather than showing a fake speed",
        hostMinutes.get(0)[1].indexOf("parked") >= 0, hostMinutes.get(0)[1]);
    String moving = null;
    for (String[] row : hostMinutes) {
      if (row[1].indexOf("km/h") >= 0) {
        moving = row[1];
        break;
      }
    }
    check("a minute that moved carries its distance and speed",
        moving != null && moving.indexOf("km") >= 0, String.valueOf(moving));
    check("a box reply has no minutes to list, and says nothing rather than inventing them",
        Drives.segments("DRIVES {\"duration_s\": 60.0, \"frames\": 6000}").isEmpty(), "box reply");

    check("a short token is not treated as configured",
        !Remote.configured("https://x.y", "short") && Remote.configured("https://x.y", "0123456789012345678901"), "");

    System.out.println();
    if (!failures.isEmpty()) {
      System.out.println("FAILED: " + failures.size() + " -> " + failures);
      System.exit(1);
    }

    // ---- the map: the drive's position now comes from the box -----------------------------------------
    check("the map URL asks for the drive and the source",
        Remote.mapUrl("https://drives.annovahome.com/", "2026-09-18--10-09-06", "box")
            .equals("https://drives.annovahome.com/map/2026-09-18--10-09-06.png?source=box"),
        Remote.mapUrl("https://drives.annovahome.com/", "2026-09-18--10-09-06", "box"));
    check("...and defaults to auto when no source is given",
        Remote.mapUrl("https://x.y", "r", "").endsWith("?source=auto"), Remote.mapUrl("https://x.y", "r", ""));

    // what the host answered for a drive recorded before the box had its own GPS: a phone track present,
    // the box's own absent. The app must still render that, and prefer the box once one exists.
    String hostTracked = "DRIVES {\"route\":\"2026-09-18--10-09-06\",\"started\":\"2026-09-18 18:09:06\","
        + "\"duration_s\":4080.2,\"frames\":407958,\"km\":77.23,\"segments\":68,\"segment_rows\":[],"
        + "\"track\":{\"phone\":{\"id\":\"x\",\"points\":820,\"km\":18.18,\"overlap_s\":819},"
        + "\"box\":{\"points\":0,\"source\":\"box\",\"reason\":\"no box position for this drive\"},"
        + "\"selected\":\"phone\"}}";
    java.util.Map<String, String> tracked = new java.util.LinkedHashMap<String, String>();
    for (String[] pair : Drives.summary(hostTracked)) {
      tracked.put(pair[0], pair[1]);
    }
    check("a drive with a phone track says so, with its size and match",
        tracked.containsKey("Phone track") && tracked.get("Phone track").contains("820 points")
            && tracked.get("Phone track").contains("18.2 km") && tracked.get("Phone track").contains("matched"),
        tracked.get("Phone track"));
    check("...and reports the box having none, rather than hiding the gap",
        tracked.containsKey("Box track") && tracked.get("Box track").contains("no satellite signal"),
        tracked.get("Box track"));

    String hostUntracked = "DRIVES {\"route\":\"r\",\"duration_s\":60.0,\"frames\":6000,\"segment_rows\":[],"
        + "\"track\":{\"phone\":null,\"box\":{\"points\":0},\"selected\":null}}";
    java.util.Map<String, String> untracked = new java.util.LinkedHashMap<String, String>();
    for (String[] pair : Drives.summary(hostUntracked)) {
      untracked.put(pair[0], pair[1]);
    }
    check("a drive with no track at all says none, not zero points",
        "none for this drive".equals(untracked.get("Phone track")), untracked.get("Phone track"));


    System.out.println("all client parsing/geometry tests passed");
  }

  /** True when the summary carries this label. */
  private static boolean hasLabel(java.util.List<String[]> pairs, String label) {
    for (String[] pair : pairs) {
      if (label.equals(pair[0])) {
        return true;
      }
    }
    return false;
  }

  /** A box-shaped summary line, so the lane row's regression is exercised alongside the host path. */
  private static String boxSummaryLine() {
    return "DRIVES {\"duration_s\": 60.1, \"frames\": 6010, \"median_speed_ms\": 12.0,"
        + " \"max_speed_ms\": 20.0, \"lane_below_threshold_pct\": 63.0}";
  }
}
