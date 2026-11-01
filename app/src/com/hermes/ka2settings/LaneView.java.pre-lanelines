package com.hermes.ka2settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The lane view: what the model sees, drawn as a road rather than a diagram.
 *
 *  Everything on it is real data from the box - the model's own lane lines, its planned curvature,
 *  and the car's position between the lines. The perspective is a genuine projection (see
 *  LaneGeometry.Camera), so the lane converges the way a road does rather than being drawn to look
 *  like one. When there is no trustworthy pose it says so and draws no lane, because an invented
 *  lane is worse than an empty one.
 */
public class LaneView extends View {

  private static final int SKY_TOP = Color.parseColor("#101A2B");
  private static final int SKY_BOTTOM = Color.parseColor("#2A3B55");
  private static final int GROUND = Color.parseColor("#23262C");
  private static final int ASPHALT = Color.parseColor("#3A3F47");
  private static final int ASPHALT_FAR = Color.parseColor("#2F343B");
  private static final int EDGE = Color.parseColor("#E8EDF2");
  private static final int ROUTE = Color.parseColor("#4FA3FF");       // the model's own trajectory
  private static final int CALCULATED = Color.parseColor("#8FE3B0");  // the lane centre the car computes
  // The centring drift band, warm as it grows: under 0.2 m, under 0.5 m, and beyond.
  private static final int DRIFT_SMALL = Color.parseColor("#558FE3B0");
  private static final int DRIFT_MEDIUM = Color.parseColor("#55FFC24F");
  private static final int DRIFT_LARGE = Color.parseColor("#55FF6B6B");
  private static final int CENTRE = Color.parseColor("#9AA6B4");
  private static final int RING = Color.parseColor("#4A5058");
  private static final int NEIGHBOUR = Color.parseColor("#2E333B");     // the lanes either side
  private static final int OUTER_EDGE = Color.parseColor("#8A94A2");
  private static final int LEAD = Color.parseColor("#F0C64A");        // a vehicle ahead: the car's own
  private static final int LEAD_WASH = Color.parseColor("#33F0C64A"); // ACC lead, vision-sourced
  private static final int PLAN = Color.parseColor("#39B0E8");
  /** Dotted, faint: a lane carried straight on past where the model stopped reading it. */
  private Paint guessPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private LaneGeometry.Pose drawn;       // the pose being drawn, for the road past the lanes
  private static final double CAR_HALF_WIDTH_M = 0.95;   // the car's own width, for the plan ribbon
  /** Where the planned-path ribbon starts. Starting it at the frame's near edge (about 0.9 m) made a
   *  car-width band project ~1000 px wide, so its wash smeared across the whole lane; a plan for the
   *  next metre is meaningless anyway. */
  private static final double PLAN_START_M = 6.0;
  /** How far out the planned path is drawn: a plan that stops at 25 m says nothing about a bend that
   *  is 80 m away, which is the sort of distance the owner asked to see. */
  private static final double PLAN_END_M = 120.0;    // out to the view's own range, so neither the ribbon
                                                     // nor the predicted route stops short of the road
  /** A real lane line's width: drawn as geometry, so it tapers with distance instead of staying a
   *  constant number of pixels wide. */
  private static final double LANE_LINE_WIDTH_M = 0.12;
  /** Nominal sizes for the marker: the message gives no size, only a position. */
  private static final double NOMINAL_CAR_LENGTH_M = 4.5;
  private static final double NOMINAL_CAR_WIDTH_M = 1.8;

  /** A car seen from above, in metres from its own centre: body outline, then the roof between the
   *  screens. Drawn as lines, so it reads as a vehicle rather than as a filled box. */
  private static final double[][] CAR_BODY_M = {
      {2.25, -0.70}, {1.55, -0.92}, {-1.50, -0.92}, {-2.25, -0.74},
      {-2.25, 0.74}, {-1.50, 0.92}, {1.55, 0.92}, {2.25, 0.70},
  };
  private static final double[][] CAR_ROOF_M = {
      {0.90, -0.58}, {-1.25, -0.60}, {-1.25, 0.60}, {0.90, 0.58},
  };
  private static final int TEXT = Color.parseColor("#E8EDF2");
  private static final int MUTED = Color.parseColor("#93A0B0");

  private final Paint skyPaint = new Paint();
  private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint centrePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint neighbourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint outerEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint predictedEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // faint markings: dimmer
  private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);           // the model's trajectory
  private final Paint calculatedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);      // the lane centre, as computed
  private final Paint driftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);           // and how far the car sits from it
  private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);            // where the lane data ends
  private final Paint leadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint leadWashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint planPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint planFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint messagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private LaneGeometry.Pose pose;
  /** Where the view is drawn from. 1.35 m is the real camera height; higher is a raised viewpoint. */
  private double cameraHeightM = LaneGeometry.Camera.CAM_HEIGHT_M;

  public void setCameraHeight(double metres) {
    if (metres > 0 && metres != cameraHeightM) {
      cameraHeightM = metres;
      invalidate();
    }
  }

  private double focalFraction = LaneGeometry.Camera.DEFAULT_FOCAL_FRACTION;

  public LaneView(Context context) {
    super(context);
    init();
  }

  public LaneView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    groundPaint.setStyle(Paint.Style.FILL);
    groundPaint.setColor(GROUND);
    roadPaint.setStyle(Paint.Style.FILL);
    edgePaint.setStyle(Paint.Style.FILL);
    edgePaint.setColor(EDGE);
    centrePaint.setStyle(Paint.Style.STROKE);
    centrePaint.setColor(CENTRE);
    centrePaint.setPathEffect(new DashPathEffect(new float[] {dp(10), dp(14)}, 0));
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setColor(RING);
    ringPaint.setStrokeWidth(dp(1));
    neighbourPaint.setStyle(Paint.Style.FILL);
    neighbourPaint.setColor(NEIGHBOUR);
    outerEdgePaint.setStyle(Paint.Style.FILL);
    outerEdgePaint.setColor(OUTER_EDGE);
    leadPaint.setStyle(Paint.Style.STROKE);
    leadPaint.setColor(LEAD);
    leadPaint.setStrokeWidth(dp(2));
    leadPaint.setStrokeJoin(Paint.Join.ROUND);
    leadWashPaint.setStyle(Paint.Style.FILL);
    leadWashPaint.setColor(LEAD_WASH);
    planPaint.setStyle(Paint.Style.STROKE);
    planPaint.setColor(PLAN);
    planPaint.setStrokeWidth(dp(2));
    planPaint.setPathEffect(new DashPathEffect(new float[] {dp(4), dp(6)}, 0));
    planFill.setStyle(Paint.Style.FILL);
    planFill.setColor(Color.argb(24, 0x39, 0xB0, 0xE8));   // a wash, so it cannot be mistaken for
                                                          // the lane itself
    planPaint.setColor(Color.argb(150, 0x39, 0xB0, 0xE8));
    labelPaint.setColor(MUTED);
    labelPaint.setTextSize(dp(11));
    messagePaint.setColor(MUTED);
    messagePaint.setTextSize(dp(13));
    messagePaint.setTextAlign(Paint.Align.CENTER);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }

  public void setPose(LaneGeometry.Pose value) {
    this.pose = value;
    invalidate();
  }

  /** Choose the lens: see further, or see wider. */
  public void setFocalFraction(double value) {
    this.focalFraction = value;
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth();
    float h = getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    LaneGeometry.Camera camera = new LaneGeometry.Camera(w, h, focalFraction, cameraHeightM);

    drawSkyAndGround(canvas, w, h, camera);

    LaneGeometry.Pose p = pose;
    if (p != null && p.ok) {
      drawDistanceRings(canvas, camera, p, w, h);
    }
    if (p == null) {
      drawCentred(canvas, "waiting for the box", w, h);
      return;
    }
    // A lane the car will not steer on still has geometry worth drawing - that was the whole point of
    // sending it - so the view draws whenever there is anything to draw, and the caption carries the
    // reason. Only an empty pose becomes a message.
    boolean drawable = p.ok || p.leftX.length > 1 || p.rightX.length > 1 || p.routeX.length > 1
        || p.centreX.length > 1;
    if (!drawable) {
      drawCentred(canvas, p.why, w, h);
      return;
    }
    drawRoad(canvas, camera, p, h);
    drawDriftBand(canvas, camera, p);
    drawCalculatedLane(canvas, camera, p);
    drawFarMarker(canvas, camera, p);
    if (p.routeX.length > 1) {
      drawn = p;                             // the guess work below needs the pose's route and width
      drawRoute(canvas, camera, p);          // the model's own path, when it has one
    } else {
      drawPlan(canvas, camera, p);           // otherwise the curvature approximation
    }
    drawLead(canvas, camera, p, p.lead, true);
    drawLead(canvas, camera, p, p.lead2, false);
    // No text readout here: the readings sit in the overlay above these pixels, and painting them
    // twice only put numbers in the way of the lane.
  }

  private void drawSkyAndGround(Canvas canvas, float w, float h, LaneGeometry.Camera camera) {
    skyPaint.setShader(new LinearGradient(0, 0, 0, camera.horizonY(), SKY_TOP, SKY_BOTTOM,
        Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, w, camera.horizonY(), skyPaint);
    skyPaint.setShader(null);
    canvas.drawRect(0, camera.horizonY(), w, h, groundPaint);
  }

  /** Constant-distance ticks across the lane, with their distance beside them.
   *
   *  Drawn across the *lane* rather than the whole frame: at a long focal an edge-to-edge line every
   *  50 px turns the view into a wireframe, and a scale is only meaningful where the lane is.
   */
  private void drawDistanceRings(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p,
      float w, float h) {
    float lastLabel = -1000;
    int index = 0;
    for (double ahead : new double[] {5, 10, 20, 50, 100}) {
      float y = camera.py(ahead);
      if (y < camera.horizonY() + dp(3) || y > h) {
        continue;
      }
      Double leftY = laneEdgeAt(p.leftX, p.leftY, ahead);
      Double rightY = laneEdgeAt(p.rightX, p.rightY, ahead);
      if (leftY == null || rightY == null) {
        continue;                       // no lane geometry at this distance: no scale to draw
      }
      float x0 = camera.px(ahead, leftY);
      float x1 = camera.px(ahead, rightY);
      if (x1 < x0) {
        float swap = x0;
        x0 = x1;
        x1 = swap;
      }
      x0 = Math.max(0, x0 - dp(6));
      x1 = Math.min(w, x1 + dp(6));
      canvas.drawLine(x0, y, x1, y, ringPaint);
      // Far rings crowd onto each other - at this focal length 50 m and 100 m are 22 px apart, and a
      // single side plus a tall threshold silently hid the 100 m label entirely. Alternate the side and
      // the two stay distinguishable.
      boolean onLeft = (index % 2) == 0;
      if (Math.abs(y - lastLabel) >= dp(8)) {
        float labelX = onLeft ? Math.max(dp(6), x0) : Math.min(w - dp(6), x1);
        labelPaint.setTextAlign(onLeft ? Paint.Align.LEFT : Paint.Align.RIGHT);
        canvas.drawText(String.format("%.0f m", ahead), labelX, y - dp(4), labelPaint);
        lastLabel = y;
      }
      index++;
    }
  }

  /** The lane edge's lateral position at a given distance, interpolated between the model's points. */
  private Double laneEdgeAt(double[] xs, double[] ys, double ahead) {
    for (int i = 0; i + 1 < xs.length && i + 1 < ys.length; i++) {
      if (xs[i] <= ahead && ahead <= xs[i + 1]) {
        double span = xs[i + 1] - xs[i];
        if (span <= 1e-9) {
          return ys[i];
        }
        double t = (ahead - xs[i]) / span;
        return ys[i] + t * (ys[i + 1] - ys[i]);
      }
    }
    return null;
  }

  private void drawRoad(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p, float h) {
    Path road = new Path();
    boolean started = false;
    float firstY = 0;
    final double near = camera.nearDistance();
    for (int i = 0; i < p.leftX.length; i++) {
      if (p.leftX[i] < near) {
        continue;
      }
      float x = camera.px(p.leftX[i], p.leftY[i]);
      float y = camera.py(p.leftX[i]);
      if (!started) {
        road.moveTo(x, y);
        firstY = y;
        started = true;
      } else {
        road.lineTo(x, y);
      }
    }
    if (!started) {
      return;
    }
    float lastX = 0;
    for (int i = p.rightX.length - 1; i >= 0; i--) {
      if (p.rightX[i] < near) {
        continue;
      }
      lastX = camera.px(p.rightX[i], p.rightY[i]);
      road.lineTo(lastX, camera.py(p.rightX[i]));
    }
    road.close();
    // Shade the tarmac towards the horizon so distance reads without extra geometry.
    roadPaint.setShader(new LinearGradient(0, firstY, 0, h, ASPHALT_FAR, ASPHALT,
        Shader.TileMode.CLAMP));
    canvas.drawPath(road, roadPaint);
    roadPaint.setShader(null);

    // Neighbouring lanes: a dim wash with a dimmer edge, so they read as context beside the lane the
    // car is in rather than as competitors for attention.
    fillNeighbourLane(canvas, camera, p.outerLeftX, p.outerLeftY, p.leftX, p.leftY, near);
    fillNeighbourLane(canvas, camera, p.rightX, p.rightY, p.outerRightX, p.outerRightY, near);
    // On faint markings the box sends the lane the model predicts, marked as such: drawn broken and
    // dimmed, so it reads as a prediction and never as a lane that was measured.
    predictedEdgePaint.setColor(EDGE);
    predictedEdgePaint.setAlpha(p.predicted ? 110 : 255);
    Paint laneEdge = p.predicted ? predictedEdgePaint : edgePaint;
    drawLaneLine(canvas, camera, p.leftX, p.leftY, near, laneEdge, p.predicted);
    drawLaneLine(canvas, camera, p.rightX, p.rightY, near, laneEdge, p.predicted);
    drawLaneLine(canvas, camera, p.outerLeftX, p.outerLeftY, near, outerEdgePaint, false);
    drawLaneLine(canvas, camera, p.outerRightX, p.outerRightY, near, outerEdgePaint, false);
    drawCentreLine(canvas, camera, p, near);
  }

  /** A lane line as a tapering wedge rather than a stroke.
   *
   *  A lane line is about 0.12 m wide, which at a long focal is ~32 px on screen - and a constant
   *  stroke of that width breaks into a ladder of blobs at the far end, where 32 px is metres of
   *  roadway. Drawing the line's real width in metres, projected, gives the taper a road actually has.
   */
  /** The strip between the car's lane and the neighbouring lane's far edge. */
  private void fillNeighbourLane(Canvas canvas, LaneGeometry.Camera camera, double[] innerX,
      double[] innerY, double[] outerX, double[] outerY, double near) {
    Path strip = new Path();
    int n = 0;
    for (int i = 0; i < innerX.length && i < innerY.length; i++) {
      if (innerX[i] < near) {
        continue;
      }
      float x = camera.px(innerX[i], innerY[i]);
      float y = camera.py(innerX[i]);
      if (n == 0) {
        strip.moveTo(x, y);
      } else {
        strip.lineTo(x, y);
      }
      n++;
    }
    for (int i = outerX.length - 1; i >= 0; i--) {
      if (outerX[i] < near) {
        continue;
      }
      strip.lineTo(camera.px(outerX[i], outerY[i]), camera.py(outerX[i]));
    }
    if (n < 2) {
      return;
    }
    strip.close();
    canvas.drawPath(strip, neighbourPaint);
  }

  /** One short piece of lane line, used for the broken drawing of a predicted lane. */
  private void drawSegment(Canvas canvas, LaneGeometry.Camera camera, Paint paint,
      double x0, double y0, double x1, double y1) {
    double half = LANE_LINE_WIDTH_M / 2.0;
    Path segment = new Path();
    segment.moveTo(camera.px(x0, y0 - half), camera.py(x0));
    segment.lineTo(camera.px(x1, y1 - half), camera.py(x1));
    segment.lineTo(camera.px(x1, y1 + half), camera.py(x1));
    segment.lineTo(camera.px(x0, y0 + half), camera.py(x0));
    segment.close();
    canvas.drawPath(segment, paint);
  }

  /** The straight-line continuation past the model's own points, dotted so it never reads as measured. */
  private void drawGuess(Canvas canvas, LaneGeometry.Camera camera, double[] xs, double[] ys, double near) {
    if (xs.length < 2 || drawn == null) {
      return;
    }
    double fromX = xs[xs.length - 1];
    if (fromX < near) {
      return;
    }
    guessPaint.setStyle(Paint.Style.STROKE);
    guessPaint.setStrokeWidth(dp(2));
    guessPaint.setColor(Color.WHITE);
    guessPaint.setAlpha(90);
    guessPaint.setPathEffect(new DashPathEffect(new float[] {dp(2), dp(9)}, 0));

    // Past the lanes, the road comes from the model's own predicted route, shifted half a lane
    // sideways. It bends with the prediction, which is what a straight line cannot do.
    boolean leftSide = ys[ys.length - 1] < 0;
    double half = drawn.width > 0.5 ? drawn.width / 2.0 : 1.5;
    double[] edgeY = LaneGeometry.routeEdge(drawn.routeY, leftSide, half);
    boolean drew = false;
    for (int i = 0; i + 1 < drawn.routeX.length; i++) {
      if (drawn.routeX[i] < fromX) {
        continue;
      }
      drawSegment(canvas, camera, guessPaint,
          drawn.routeX[i], edgeY[i], drawn.routeX[i + 1], edgeY[i + 1]);
      drew = true;
    }
    if (!drew) {                    // no route out there either: carry the last segment straight on
      double[] guess = LaneGeometry.extrapolateTo(xs, ys, LaneGeometry.RANGE_M);
      if (guess != null && guess[0] >= near) {
        drawSegment(canvas, camera, guessPaint, guess[0], guess[1], guess[2], guess[3]);
      }
    }
  }

  private void drawLaneLine(Canvas canvas, LaneGeometry.Camera camera, double[] xs, double[] ys,
      double near, Paint paint, boolean predicted) {
    drawGuess(canvas, camera, xs, ys, near);
    if (predicted) {
      for (int i = 0; i + 1 < xs.length; i += 2) {          // every other span: a broken line
        if (xs[i] < near || xs[i + 1] < near) {
          continue;
        }
        drawSegment(canvas, camera, paint, xs[i], ys[i], xs[i + 1], ys[i + 1]);
      }
      return;
    }
    Path left = new Path();
    Path right = new Path();
    int n = 0;
    double half = LANE_LINE_WIDTH_M / 2.0;
    for (int i = 0; i < xs.length; i++) {
      if (xs[i] < near) {
        continue;
      }
      float xl = camera.px(xs[i], ys[i] - half);
      float xr = camera.px(xs[i], ys[i] + half);
      float y = camera.py(xs[i]);
      if (n == 0) {
        left.moveTo(xl, y);
        right.moveTo(xr, y);
      } else {
        left.lineTo(xl, y);
        right.lineTo(xr, y);
      }
      n++;
    }
    if (n < 2) {
      return;
    }
    Path wedge = new Path();
    wedge.addPath(left);
    // back along the far side
    for (int i = xs.length - 1; i >= 0; i--) {
      if (xs[i] < near) {
        continue;
      }
      wedge.lineTo(camera.px(xs[i], ys[i] + half), camera.py(xs[i]));
    }
    wedge.close();
    canvas.drawPath(wedge, paint);
  }

  private void drawCentreLine(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p,
      double near) {
    int n = Math.min(p.leftX.length, p.rightX.length);
    Path path = new Path();
    boolean started = false;
    for (int i = 0; i < n; i++) {
      if (p.leftX[i] < near) {
        continue;
      }
      float x = camera.px(p.leftX[i], (p.leftY[i] + p.rightY[i]) / 2.0);
      float y = camera.py(p.leftX[i]);
      if (!started) {
        path.moveTo(x, y);
        started = true;
      } else {
        path.lineTo(x, y);
      }
    }
    if (started) {
      centrePaint.setStrokeWidth(Math.max(dp(1), camera.pxPerMetreAt(6) * 0.06f));
      canvas.drawPath(path, centrePaint);
    }
  }

  /** The path the model is steering for, from its own curvature: y = 0.5 * k * x^2.
   *
   *  Drawn as a ribbon the width of the car rather than a line, because a line down the middle of the
   *  lane is indistinguishable from the lane's own centre line - and left off entirely when the plan
   *  is within a car's width of straight, since then the ribbon would sit exactly on that centre line
   *  and tell the driver nothing.
   */
  private void drawLead(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p,
      LaneGeometry.Lead lead, boolean primary) {
    if (lead == null || !lead.present || lead.distance > LaneGeometry.RANGE_M) {
      return;
    }
    // The position is real; the size is nominal, because the message says nothing about the vehicle.
    Path body = projectShape(camera, lead, CAR_BODY_M);
    canvas.drawPath(body, leadWashPaint);      // a faint wash, so the outline is not a floating line
    canvas.drawPath(body, leadPaint);
    canvas.drawPath(projectShape(camera, lead, CAR_ROOF_M), leadPaint);

    double nearEdge = Math.max(lead.distance - NOMINAL_CAR_LENGTH_M / 2.0,
        LaneGeometry.Camera.MIN_NEAR_M);
    double far = lead.distance + NOMINAL_CAR_LENGTH_M / 2.0;
    String label = String.format("%.0f m", lead.distance);
    double gap = lead.gapSeconds(p.speed);
    if (gap >= 0) {
      label += String.format("  %.1f s", gap);
    }
    textPaint.setTextSize(dp(12));
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setColor(LEAD);
    // The nearer lead's label sits above its roof; the further one's sits below it, because at this
    // focal length two cars 30-70 m out are a couple of dozen pixels apart and the labels otherwise
    // landed on top of each other.
    float labelY = primary ? camera.py(far) - dp(6) : camera.py(nearEdge) + dp(14);
    canvas.drawText(label, camera.px(far, lead.lateral), labelY, textPaint);
  }

  /** One of the shapes below, placed at the lead's tracked position and projected.
   *
   *  The shapes are drawn as an outline of a car seen from above - a nose narrower than the body, and
   *  a roof between the screens - because a filled rectangle reads as an obstacle rather than as a
   *  vehicle. The *position* is still the real datum; only the shape is nominal.
   */
  private Path projectShape(LaneGeometry.Camera camera, LaneGeometry.Lead lead, double[][] shape) {
    Path path = new Path();
    for (int i = 0; i < shape.length; i++) {
      double forward = Math.max(lead.distance + shape[i][0], LaneGeometry.Camera.MIN_NEAR_M);
      double lateral = lead.lateral + shape[i][1];
      float x = camera.px(forward, lateral);
      float y = camera.py(forward);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.close();
    return path;
  }

  /** The model's own predicted trajectory: where it expects the car to be, point by point. */
  private void drawRoute(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p) {
    if (p.routeX.length < 2) {
      return;
    }
    routePaint.setStyle(Paint.Style.STROKE);
    routePaint.setStrokeWidth(dp(3));
    routePaint.setColor(ROUTE);
    routePaint.setAlpha(210);
    boolean last = false;
    for (int i = 0; i + 1 < p.routeX.length; i++) {
      // broken, so it never reads as a lane marking or as the car's own lane
      if (i % 2 == 1) {
        continue;
      }
      canvas.drawLine(camera.px(p.routeX[i], p.routeY[i]), camera.py(p.routeX[i]),
          camera.px(p.routeX[i + 1], p.routeY[i + 1]), camera.py(p.routeX[i + 1]), routePaint);
      last = true;
    }
    if (last) {
      int tip = p.routeX.length - 1;
      labelPaint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText(String.format("route %d m", Math.round(p.pathReach)),
          camera.px(p.routeX[tip], p.routeY[tip]), camera.py(p.routeX[tip]) - dp(6), labelPaint);
    }
  }

  /**
   * The centring drift, as a band from the car's own axis out to the lane centre at each distance.
   *
   * Its width *is* the drift: a constant-width corridor when the car is parallel to the centre and
   * simply sitting off it, tapering or opening as the car's heading diverges. Drawn under the centre
   * line, so the line being measured against stays visible.
   */
  private void drawDriftBand(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p) {
    if (p.centreX.length < 2) {
      return;
    }
    final double near = camera.nearDistance();
    int first = -1;
    int last = -1;
    for (int i = 0; i < p.centreX.length; i++) {
      if (p.centreX[i] < near) {
        continue;
      }
      if (first < 0) {
        first = i;
      }
      last = i;
    }
    if (first < 0 || last <= first) {
      return;
    }
    Path band = new Path();
    band.moveTo(camera.px(p.centreX[first], 0.0), camera.py(p.centreX[first]));
    for (int i = first + 1; i <= last; i++) {                    // out along the car's own axis
      band.lineTo(camera.px(p.centreX[i], 0.0), camera.py(p.centreX[i]));
    }
    for (int i = last; i >= first; i--) {                        // back along the lane centre
      band.lineTo(camera.px(p.centreX[i], p.centreY[i]), camera.py(p.centreX[i]));
    }
    band.close();
    double drift = Math.abs(p.drift());
    driftPaint.setColor(drift >= 0.5 ? DRIFT_LARGE : (drift >= 0.2 ? DRIFT_MEDIUM : DRIFT_SMALL));
    canvas.drawPath(band, driftPaint);
  }

  /**
   * Where the lane data ends, marked and named.
   *
   * The distance ticks say how far the view reaches; this says how far the *data* reaches, which is the
   * shorter of the two whenever the model stops reading the lines - a faint-marking road ends at 30 m
   * however far the lens can draw. Clamped to the drawn range, since a marker off the page is useless.
   */
  private void drawFarMarker(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p) {
    double reach = p.dataReach();
    if (reach < 5.0) {
      return;
    }
    double ahead = Math.min(reach, LaneGeometry.RANGE_M);
    float y = (float) camera.py(ahead);
    if (y < camera.horizonY() + dp(6)) {
      return;                              // too close to the skyline to read
    }
    double left = p.leftX.length > 0 ? p.leftY[p.leftY.length - 1] : -1.6;
    double right = p.rightX.length > 0 ? p.rightY[p.rightY.length - 1] : 1.6;
    markPaint.setStyle(Paint.Style.STROKE);
    markPaint.setStrokeWidth(dp(2));
    markPaint.setColor(ROUTE);
    markPaint.setAlpha(200);
    canvas.drawLine(camera.px(ahead, left), y, camera.px(ahead, right), y, markPaint);
    labelPaint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(String.format("data to %.0f m", reach), camera.px(ahead, 0.0),
        y - dp(6), labelPaint);
  }

  /** The lane centre the car's own correction works from - not a lane line, not the route. */
  private void drawCalculatedLane(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p) {
    if (p.centreX.length < 2) {
      return;
    }
    calculatedPaint.setStyle(Paint.Style.STROKE);
    calculatedPaint.setStrokeWidth(dp(2));
    calculatedPaint.setColor(CALCULATED);
    calculatedPaint.setAlpha(190);
    for (int i = 0; i + 1 < p.centreX.length; i++) {
      canvas.drawLine(camera.px(p.centreX[i], p.centreY[i]), camera.py(p.centreX[i]),
          camera.px(p.centreX[i + 1], p.centreY[i + 1]), camera.py(p.centreX[i + 1]), calculatedPaint);
    }
  }

  private void drawPlan(Canvas canvas, LaneGeometry.Camera camera, LaneGeometry.Pose p) {
    if (Math.abs(p.curvature) < 1e-6) {
      return;
    }
    double departure = Math.abs(0.5 * p.curvature * 20.0 * 20.0);   // how far off straight by 20 m
    if (departure < CAR_HALF_WIDTH_M) {
      return;
    }
    Path ribbon = new Path();
    boolean started = false;
    double[] aheads = new double[100];
    int n = 0;
    for (double ahead = Math.max(camera.nearDistance(), PLAN_START_M);
         ahead <= PLAN_END_M && n < aheads.length; ahead += 1.0) {
      aheads[n++] = ahead;
    }
    for (int pass = 0; pass < 2; pass++) {
      double side = pass == 0 ? 1 : -1;
      for (int i = 0; i < n; i++) {
        double ahead = aheads[pass == 0 ? i : n - 1 - i];
        double lateral = 0.5 * p.curvature * ahead * ahead + side * CAR_HALF_WIDTH_M;
        float x = camera.px(ahead, lateral);
        float y = camera.py(ahead);
        if (!started) {
          ribbon.moveTo(x, y);
          started = true;
        } else {
          ribbon.lineTo(x, y);
        }
      }
    }
    if (!started) {
      return;
    }
    ribbon.close();
    canvas.drawPath(ribbon, planFill);       // a very light wash, and two dashed edges: on a tall
                                             // page a filled band reads as if the lane itself were blue
    canvas.drawPath(ribbon, planPaint);
  }

  // There is deliberately no mark drawn for the car itself. The view is taken *from* the car, so the
  // car is the viewpoint: the lane's position relative to it is what the lane lines, the centre dash
  // and the "Car in lane" reading show. Earlier versions drew a bonnet band and then a footprint 1-2 m
  // ahead, and both put marks where no car is - the second of them behind the readings panel, where
  // it could not be seen at all.

  @SuppressWarnings("unused")
  private void drawReadout(Canvas canvas, LaneGeometry.Pose p, float w, float h) {
    int alpha = 0;
    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setColor(TEXT);
    text.setTextSize(dp(13));
    text.setShadowLayer(dp(3), 0, dp(1), Color.argb(200, 0, 0, 0));
    float y = h * 0.10f;
    canvas.drawText(p.summary(), dp(12), y, text);
    if (p.look > 0 && p.fallback) {
      y += dp(18);
      text.setTextSize(dp(11));
      canvas.drawText(String.format("measured %.0f m ahead (car's horizon %.0f m has no lane)",
          p.look, p.carLook), dp(12), y, text);
    }
    if (Math.abs(p.curvature) > 1e-6) {
      y += dp(18);
      text.setTextSize(dp(11));
      double radius = 1.0 / Math.abs(p.curvature);
      canvas.drawText(String.format("plan: %s %.0f m radius", p.curvature > 0 ? "right" : "left", radius),
          dp(12), y, text);
    }
    if (p.leftProb >= 0 && p.rightProb >= 0) {
      text.setTextSize(dp(11));
      text.setTextAlign(Paint.Align.RIGHT);
      canvas.drawText(String.format("lanes %.2f / %.2f", p.leftProb, p.rightProb), w - dp(12),
          h * 0.10f, text);
    }
  }

  private void drawCentred(Canvas canvas, String message, float w, float h) {
    float y = h * 0.52f;
    for (String line : wrap(message, 34)) {
      canvas.drawText(line, w / 2f, y, messagePaint);
      y += dp(18);
    }
  }

  private String[] wrap(String text, int width) {
    if (text == null || text.isEmpty()) {
      return new String[] {""};
    }
    java.util.List<String> lines = new java.util.ArrayList<String>();
    StringBuilder current = new StringBuilder();
    for (String word : text.split(" ")) {
      if (current.length() > 0 && current.length() + word.length() + 1 > width) {
        lines.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) {
        current.append(' ');
      }
      current.append(word);
    }
    if (current.length() > 0) {
      lines.add(current.toString());
    }
    return lines.toArray(new String[0]);
  }
}
