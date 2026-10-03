package com.hermes.ka2settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class SettingsActivity extends Activity implements BtSpp.Listener {
    private static final String APP_VERSION = "7.14";
    private static final String PREFS = "ka2settings";
    private static final String PREF_HEIGHT = "height_choice";
    private static final String PREF_LENS = "lens";
    private static final String PREF_LENS_CHOICE = "lens_choice";
    private static final String PREF_PANEL = "panel_open";
    private static final String PREF_SCREEN_ON = "screen_on";
    private static final String PREF_THEME = "theme";
    private static final int SIDE_W_DP = 300;
    private static final String TAG = "KA2Settings";
    private int BG;
    private int CARD;
    private int CHIP;
    private int CHIP_SELECTED;
    private int DANGER;
    private int DANGER_WASH;
    private int FIELD;
    private int HAIRLINE;
    private int MUTED;
    private int SCRIM;
    private int TAB_BAR;
    private int TEXT;
    private double appliedBaseFocal;
    private double appliedFocal;
    private BtSpp bt;
    private boolean carEngaged;
    private boolean carKnown;
    private boolean carOnroad;
    private boolean carReady;
    private Button connectButton;
    private boolean connectedNow;
    private boolean dark;
    private LinearLayout deviceCard;
    private Button handleButton;
    private Button heightButton;
    private TextView laneCaption;
    private View laneDot;
    private FrameLayout lanePage;
    private TextView laneStatusText;
    private LaneView laneView;
    private double lastPathReach;
    private double lastSpeedMps;
    private Button lensButton;
    private TextView logText;
    private TextView macValue;
    private String pendingPoseBody;
    private boolean poseApplyQueued;
    private TextView screenButton;
    private boolean screenOnApplied;
    private TextView setSpeedText;
    private ScrollView settingsScroll;
    private LinearLayout sidePanel;
    private LinearLayout sideWrap;
    private LinearLayout softwareCard;
    private TextView speedUnit;
    private TextView speedValue;
    private View statusDot;
    private TextView statusText;
    private Button tabLane;
    private Button tabSettings;
    private Button themeButton;
    private LinearLayout tuningCard;
    private LinearLayout tuningSection;
    private LinearLayout bendCard;
    private LinearLayout bendSection;
    private LinearLayout leadCard;
    private LinearLayout leadSection;
    private LinearLayout overridesCard;
    private LinearLayout overridesSection;
    private TextView accStatus;
    private TextView wifiButton;
    private boolean wifiCollecting;
    private TextView wifiStatusText;
    private int ACCENT = Palette.ACCENT;
    private int WAIT = Palette.WAIT;
    private final Map<String, Row> rows = new LinkedHashMap();
    private final Map<String, TextView> infoValues = new LinkedHashMap();
    private final List<Wifi.Network> wifiNetworks = new ArrayList();
    private final Map<String, TextView> laneStats = new LinkedHashMap();
    private boolean updatePose = true;
    private String lastPoseVerdict = "";
    /** The newest GNSS state from the box, shown on the lane page beside the pose's own readouts. */
    private LaneGeometry.Gps gps = new LaneGeometry.Gps();
    /** The last pose drawn, so a GPS line that arrives between poses can refresh the panel. */
    private LaneGeometry.Pose lastPose;
    private final Object poseGate = new Object();
    private final Readouts.Sink laneSink = new Readouts.Sink() { // from class: com.hermes.ka2settings.SettingsActivity.20
        @Override // com.hermes.ka2settings.Readouts.Sink
        public void put(String str, String str2) {
            if ("speed".equals(str)) {
                SettingsActivity.this.speedValue.setText(str2);
                return;
            }
            if ("set".equals(str)) {
                SettingsActivity.this.setSpeedText.setText(str2);
                return;
            }
            if ("caption".equals(str)) {
                SettingsActivity.this.laneCaption.setText(str2);
            } else {
                if ("status".equals(str)) {
                    SettingsActivity.this.laneStatusText.setText(str2);
                    SettingsActivity settingsActivity = SettingsActivity.this;
                    settingsActivity.setLaneDot(settingsActivity.WAIT);
                    return;
                }
                SettingsActivity.this.setLaneStat(str, str2);
            }
        }
    };
    private final Readouts.Cache poseCache = new Readouts.Cache();

    private class Row {
        Button action;
        String desc;
        TextView description;
        EditText field;
        String key;
        String lastWarn;
        double max;
        double min;
        Button minus;
        String mode;
        List<Button> optionButtons;
        LinearLayout options;
        Button picker;
        Button plus;
        double step;
        Stepper stepper;
        boolean suppressCallback;
        Switch toggle;
        String type;
        String value;
        TextView valueText;

        private Row() {
            this.type = "str";
            this.mode = "ro";
            this.value = "";
            this.desc = "";
            this.lastWarn = "";
            this.optionButtons = new ArrayList();
        }
    }

    private void applyTheme() {
        String string = getSharedPreferences(PREFS, 0).getString(PREF_THEME, "dark");
        if ("system".equals(string)) {
            this.dark = (getResources().getConfiguration().uiMode & 48) == 32;
        } else {
            this.dark = !"light".equals(string);
        }
        android.app.Activity a = this;
        setTheme(this.dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        boolean z = this.dark;
        this.BG = z ? Palette.DARK_BG : -854793;
        this.CARD = z ? Palette.DARK_CARD : -1;
        this.TEXT = z ? Palette.DARK_TEXT : Palette.LIGHT_TEXT;
        this.MUTED = z ? Palette.DARK_MUTED : Palette.LIGHT_MUTED;
        this.HAIRLINE = z ? -14472652 : Palette.LIGHT_HAIRLINE;
        this.DANGER = z ? Palette.DARK_DANGER : Palette.LIGHT_DANGER;
        this.CHIP = z ? -14472652 : Palette.LIGHT_CHIP;
        this.CHIP_SELECTED = z ? -14999255 : Palette.LIGHT_CHIP_SELECTED;
        this.TAB_BAR = z ? Palette.DARK_TAB_BAR : Palette.LIGHT_TAB_BAR;
        this.FIELD = z ? -14999255 : -854793;
        this.DANGER_WASH = z ? Palette.DARK_DANGER_WASH : Palette.LIGHT_DANGER_WASH;
        this.SCRIM = Color.parseColor("#C8101418");
        getWindow().setStatusBarColor(this.BG);
        getWindow().setNavigationBarColor(this.BG);
        try {
            View decorView = getWindow().getDecorView();
            int systemUiVisibility = decorView.getSystemUiVisibility();
            decorView.setSystemUiVisibility(this.dark ? systemUiVisibility & (-8193) : systemUiVisibility | 8192);
        } catch (Throwable th) {
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        applyTheme();
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(this.BG);
        linearLayout.addView(buildTabBar());
        ScrollView scrollView = new ScrollView(this);
        this.settingsScroll = scrollView;
        scrollView.setBackgroundColor(this.BG);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setPadding(dp(16.0f), dp(6.0f), dp(16.0f), dp(28.0f));
        scrollView.addView(linearLayout2);
        linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        linearLayout2.addView(buildUpdateCard());
        linearLayout2.addView(sectionHeader("APPEARANCE"));
        LinearLayout linearLayoutCard = card();
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        linearLayout3.setPadding(0, dp(4.0f), 0, dp(4.0f));
        TextView textView = new TextView(this);
        textView.setText("Theme");
        textView.setTextColor(this.TEXT);
        textView.setTextSize(15.0f);
        linearLayout3.addView(textView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button button = new Button(this);
        this.themeButton = button;
        button.setAllCaps(false);
        this.themeButton.setText(themeChoiceLabel());
        this.themeButton.setTextColor(this.TEXT);
        this.themeButton.setBackground(roundRect(this.CHIP, 8));
        this.themeButton.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        this.themeButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.chooseTheme();
            }
        });
        linearLayout3.addView(this.themeButton);
        TextView textView2 = new TextView(this);
        this.screenButton = textView2;
        textView2.setText("Screen: " + ScreenOn.label(savedScreenOn()));
        this.screenButton.setTextColor(this.TEXT);
        this.screenButton.setBackground(roundRect(this.CHIP, 8));
        this.screenButton.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        this.screenButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.chooseScreenOn();
            }
        });
        linearLayout3.addView(this.screenButton);
        linearLayoutCard.addView(linearLayout3);
        TextView textView3 = new TextView(this);
        textView3.setText("Dark, light, or whatever the phone is set to. The lane view keeps its own dark road either way, since that is a picture of the road rather than a surface of the app.");
        textView3.setTextColor(this.MUTED);
        textView3.setTextSize(12.0f);
        textView3.setPadding(0, dp(6.0f), 0, 0);
        linearLayoutCard.addView(textView3);
        linearLayout2.addView(linearLayoutCard);
        linearLayout2.addView(sectionHeader("STOCK ACC"));
        LinearLayout accCard = card();
        LinearLayout accRow = new LinearLayout(this);
        accRow.setOrientation(0);
        accRow.setGravity(16);
        accRow.setPadding(0, dp(6.0f), 0, dp(6.0f));
        TextView accLabel = new TextView(this);
        accLabel.setText("Set speed");
        accLabel.setTextColor(this.TEXT);
        accLabel.setTextSize(15.0f);
        accRow.addView(accLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button accDown = new Button(this);
        accDown.setAllCaps(false);
        accDown.setText("ACC \u2212");
        accDown.setTextColor(this.TEXT);
        accDown.setBackground(roundRect(this.CHIP, 8));
        accDown.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        accDown.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.accDown
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.bt.send("ACC DOWN");
            }
        });
        accRow.addView(accDown);
        Button accUp = new Button(this);
        accUp.setAllCaps(false);
        accUp.setText("ACC +");
        accUp.setTextColor(this.TEXT);
        accUp.setBackground(roundRect(this.CHIP, 8));
        accUp.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        accUp.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.accUp
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.bt.send("ACC UP");
            }
        });
        accRow.addView(accUp);
        accCard.addView(accRow);
        this.accStatus = new TextView(this);
        this.accStatus.setText("Press acts on the car's own ACC immediately. The box refuses while ACC is off, and never touches LKAS.");
        this.accStatus.setTextColor(this.MUTED);
        this.accStatus.setTextSize(12.0f);
        this.accStatus.setPadding(0, dp(6.0f), 0, 0);
        accCard.addView(this.accStatus);
        linearLayout2.addView(accCard);
        linearLayout2.addView(sectionHeader("CONNECTED DEVICE"));
        LinearLayout linearLayoutCard2 = card();
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(16);
        linearLayout4.setPadding(0, dp(6.0f), 0, dp(6.0f));
        this.statusDot = new View(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(10.0f), dp(10.0f));
        layoutParams.setMargins(0, 0, dp(10.0f), 0);
        this.statusDot.setLayoutParams(layoutParams);
        setDot(this.MUTED);
        TextView textView4 = new TextView(this);
        this.statusText = textView4;
        textView4.setText("not connected");
        this.statusText.setTextColor(this.TEXT);
        this.statusText.setTextSize(15.0f);
        linearLayout4.addView(this.statusDot);
        linearLayout4.addView(this.statusText);
        linearLayoutCard2.addView(linearLayout4);
        linearLayoutCard2.addView(divider());
        String[][] strArr = {new String[]{"dongle", "Dongle"}, new String[]{"serial", "Serial"}, new String[]{"version", "Version"}, new String[]{"commit", "Build"}, new String[]{"state", "State"}, new String[]{"car", "Car"}, new String[]{"network", "Network"}, new String[]{"storage", "Storage"}, new String[]{"uptime", "Uptime"}, new String[]{"model", "Model"}};
        for (int i = 0; i < 10; i++) {
            String[] strArr2 = strArr[i];
            this.infoValues.put(strArr2[0], addValueRow(linearLayoutCard2, strArr2[1], "-"));
        }
        linearLayoutCard2.addView(divider());
        LinearLayout linearLayout5 = new LinearLayout(this);
        linearLayout5.setOrientation(0);
        linearLayout5.setGravity(16);
        linearLayout5.setPadding(0, dp(6.0f), 0, dp(2.0f));
        TextView textView5 = new TextView(this);
        textView5.setText("Box address");
        textView5.setTextColor(this.TEXT);
        textView5.setTextSize(15.0f);
        linearLayout5.addView(textView5, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView6 = new TextView(this);
        this.macValue = textView6;
        textView6.setText(BtSpp.configuredMac(this));
        this.macValue.setTextColor(this.MUTED);
        this.macValue.setTextSize(13.0f);
        this.macValue.setTypeface(Typeface.MONOSPACE);
        linearLayout5.addView(this.macValue);
        linearLayoutCard2.addView(linearLayout5);
        TextView textView7 = new TextView(this);
        textView7.setText("The app connects to this address and to nothing else. Change it if the box is ever replaced.");
        textView7.setTextColor(this.MUTED);
        textView7.setTextSize(12.0f);
        textView7.setPadding(0, 0, 0, dp(6.0f));
        linearLayoutCard2.addView(textView7);
        Button button2 = new Button(this);
        button2.setText("Change address");
        button2.setAllCaps(false);
        button2.setTextColor(this.TEXT);
        button2.setBackground(roundRect(this.CHIP, 8));
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.editBoxAddress();
            }
        });
        linearLayoutCard2.addView(button2);
        Button button4 = new Button(this);
        this.connectButton = button4;
        button4.setText("Connect to box");
        this.connectButton.setAllCaps(false);
        this.connectButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                if (SettingsActivity.this.bt.isConnected()) {
                    SettingsActivity.this.bt.stop();
                    SettingsActivity.this.updatePose = false;
                } else {
                    SettingsActivity.this.updatePose = true;
                    SettingsActivity.this.doConnect();
                }
            }
        });
        linearLayoutCard2.addView(this.connectButton);
        linearLayout2.addView(linearLayoutCard2);
        this.bendSection = addTuningSection(linearLayout2, "BEND AUTO-SLOW",
                "Ahead of a bend, the box walks the car's own ACC set point down a step at a time and hands it back on the way out. It never touches the brakes - the set point is its only lever, and the car's ACC does the slowing. Tap - or + to step a row by its own size; the box clamps every value into the range shown.");
        this.bendCard = (LinearLayout) this.bendSection.getChildAt(1);
        this.leadSection = addTuningSection(linearLayout2, "CAR AHEAD",
                "Slows for a car the camera can see before the car's own ACC has reacted to it - the set point only, so the ACC still does the following. If both this and a bend ask for less speed, the lower of the two wins.");
        this.leadCard = (LinearLayout) this.leadSection.getChildAt(1);
        this.tuningSection = addTuningSection(linearLayout2, "LANE CENTRING",
                "A small extra steering correction that holds the car nearer the middle of its lane. These change it within about a second (the box writes them to a file the car re-reads). The file can only make the correction gentler than the code it is running - turning one up is a code change, not a slider.");
        this.tuningCard = (LinearLayout) this.tuningSection.getChildAt(1);
        this.overridesSection = addTuningSection(linearLayout2, "TUNING DEFAULTS",
                "The three sections above share one override file on the box. This clears all of it at once, so every row in them returns to the value the running code ships with.");
        this.overridesCard = (LinearLayout) this.overridesSection.getChildAt(1);
        linearLayout2.addView(sectionHeader("SOFTWARE SETTINGS"));
        this.softwareCard = card();
        TextView textView8 = new TextView(this);
        textView8.setText("The box sends this list, with each row declaring whether a change takes effect.");
        textView8.setTextColor(this.MUTED);
        textView8.setTextSize(12.0f);
        textView8.setPadding(0, dp(4.0f), 0, dp(8.0f));
        this.softwareCard.addView(textView8);
        linearLayout2.addView(this.softwareCard);
        Button button5 = new Button(this);
        button5.setText("Refresh from box");
        button5.setAllCaps(false);
        button5.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.bt.send("INFO");
                SettingsActivity.this.bt.send("SCHEMA");
                SettingsActivity.this.bt.send("STATE");
                SettingsActivity.this.bt.send("WIFI STATUS");
            }
        });
        linearLayout2.addView(button5);
        LinearLayout linearLayoutCard3 = card();
        TextView textView9 = new TextView(this);
        textView9.setText("Wi-Fi");
        textView9.setTextColor(this.TEXT);
        textView9.setTextSize(16.0f);
        linearLayoutCard3.addView(textView9);
        TextView textView10 = new TextView(this);
        this.wifiStatusText = textView10;
        textView10.setText("asking the box...");
        this.wifiStatusText.setTextColor(this.MUTED);
        this.wifiStatusText.setTextSize(13.0f);
        linearLayoutCard3.addView(this.wifiStatusText);
        TextView textView11 = new TextView(this);
        this.wifiButton = textView11;
        textView11.setText("Scan networks");
        this.wifiButton.setTextColor(this.TEXT);
        this.wifiButton.setBackground(roundRect(this.CHIP, 8));
        this.wifiButton.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        this.wifiButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.wifiStatusText.setText("scanning...");
                SettingsActivity.this.wifiNetworks.clear();
                SettingsActivity.this.wifiCollecting = true;
                SettingsActivity.this.bt.send("WIFI LIST");
            }
        });
        linearLayoutCard3.addView(this.wifiButton);
        TextView textView12 = new TextView(this);
        textView12.setText("The box joins the network you pick here. Your Bluetooth link is unaffected, and the box keeps its address on this network too - switching does not disconnect the app.");
        textView12.setTextColor(this.MUTED);
        textView12.setTextSize(12.0f);
        linearLayoutCard3.addView(textView12);
        linearLayout2.addView(linearLayoutCard3);
        linearLayout2.addView(sectionHeader("DEVICE SETTINGS"));
        this.deviceCard = card();
        TextView textView14 = new TextView(this);
        textView14.setText("Read-only rows are ones the box writes itself or that carry side effects beyond a setting. Editable rows with a Set button are real settings; the box says when each one applies.");
        textView14.setTextColor(this.MUTED);
        textView14.setTextSize(12.0f);
        textView14.setPadding(0, dp(4.0f), 0, dp(8.0f));
        this.deviceCard.addView(textView14);
        linearLayout2.addView(this.deviceCard);
        linearLayout2.addView(sectionHeader("CHANGES"));
        LinearLayout linearLayoutCard4 = card();
        TextView textView15 = new TextView(this);
        this.logText = textView15;
        textView15.setTextColor(this.MUTED);
        this.logText.setTextSize(11.0f);
        this.logText.setTypeface(Typeface.MONOSPACE);
        this.logText.setTextIsSelectable(true);
        linearLayoutCard4.addView(this.logText);
        linearLayout2.addView(linearLayoutCard4);
        linearLayout.addView(buildLanePage());
        applyLens(0.0d);
        applyHeight();
        setContentView(linearLayout);
        showTab(0);
        this.bt = new BtSpp(this);
        autoConnectSoon();
        appendLog("ready - tap Connect to talk to the box over Bluetooth");
    }

    private Button stepButton(String str) {
        Button button = new Button(this);
        button.setText(str);
        button.setTextSize(18.0f);
        button.setAllCaps(false);
        button.setTextColor(this.TEXT);
        button.setBackground(roundRect(this.CHIP, 8));
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setWidth(dp(42.0f));
        button.setHeight(dp(38.0f));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42.0f), dp(38.0f)));
        return button;
    }

    private View.OnTouchListener stepperTouch(final Row row, final int i) {
        return new View.OnTouchListener() { // from class: com.hermes.ka2settings.SettingsActivity.8
            private final Handler handler = new Handler();
            private int ticks = 0;
            private final Runnable repeat = new Runnable() { // from class: com.hermes.ka2settings.SettingsActivity.8.1
                @Override // java.lang.Runnable
                public void run() {
                    if (SettingsActivity.this.nudge(row, i)) {
                        int i2 = ticks;
                        ticks = i2 + 1;
                        if (i2 <= 400) {
                            handler.postDelayed(this, 140L);
                            return;
                        }
                    }
                    handler.removeCallbacks(this);
                }
            };

            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == 0) {
                    this.handler.removeCallbacks(this.repeat);
                    this.ticks = 0;
                    if (SettingsActivity.this.nudge(row, i)) {
                        this.handler.postDelayed(this.repeat, 500L);
                    }
                    return true;
                }
                if (motionEvent.getAction() != 1 && motionEvent.getAction() != 3) {
                    return false;
                }
                this.handler.removeCallbacks(this.repeat);
                return true;
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean nudge(Row row, int i) {
        String strPress;
        if (row.stepper == null || !Json.writable(row.mode) || (strPress = row.stepper.press(i)) == null) {
            return false;
        }
        row.value = strPress;
        bindValue(row, strPress);
        this.bt.send("SET " + row.key + " " + strPress);
        return true;
    }

    private String actionLabel(Map<String, Object> map) {
        String str = Json.str(map, "label", "");
        if (!str.isEmpty()) {
            return str;
        }
        String lowerCase = Json.str(map, "act", "run").replace('_', ' ').trim().toLowerCase();
        if (lowerCase.isEmpty()) {
            return "Run";
        }
        return Character.toUpperCase(lowerCase.charAt(0)) + lowerCase.substring(1).split(" ")[0];
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void confirmAndRun(final String str, String str2, String str3, String str4) {
        String str5;
        String lowerCase = str.replace("ACT_", "").replace('_', ' ').toLowerCase();
        if ("engaged".equals(str4)) {
            str5 = "\n\nThe box refuses this while autodrive is armed - the car simply being on is fine.";
        } else if ("parked".equals(str4)) {
            str5 = "\n\nThe box refuses this while the car is on.";
        } else {
            str5 = "\n\nThis takes effect within a second.";
        }
        AlertDialog.Builder negativeButton = new AlertDialog.Builder(this).setTitle(str2).setMessage(str3 + str5).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null);
        if (lowerCase.isEmpty()) {
            lowerCase = "Run";
        }
        negativeButton.setPositiveButton(lowerCase, new DialogInterface.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.9
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.bt.send("ACT " + str.replace("ACT_", ""));
            }
        }).show();
    }

    private static String trim(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(Math.round(d * 1000.0d) / 1000.0d);
    }

    private int dp(float f) {
        return (int) (f * getResources().getDisplayMetrics().density);
    }

    private View title(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.TEXT);
        textView.setTextSize(24.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setPadding(0, 0, 0, dp(6.0f));
        return textView;
    }

    /** The card that opens a tuning section: one sentence on what the rows below do and how to set
     *  them. Kept per section rather than per row, so the rows themselves stay short. */
    private LinearLayout blurbCard(String str) {
        LinearLayout c = card();
        TextView t = new TextView(this);
        t.setText(str);
        t.setTextColor(this.MUTED);
        t.setTextSize(12.0f);
        t.setPadding(0, dp(4.0f), 0, dp(8.0f));
        c.addView(t);
        return c;
    }

    /** One tuning section: heading, blurb, and the card the box's rows are added to. Hidden until a
     *  row actually arrives, so a box that reports nothing leaves no empty headings behind. */
    private LinearLayout addTuningSection(LinearLayout parent, String title, String blurb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(1);
        wrap.setVisibility(8);
        wrap.addView(sectionHeader(title));
        wrap.addView(blurbCard(blurb));
        parent.addView(wrap);
        return wrap;
    }

    private View sectionHeader(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.MUTED);
        textView.setTextSize(12.0f);
        textView.setLetterSpacing(0.08f);
        textView.setPadding(dp(4.0f), dp(18.0f), 0, dp(6.0f));
        return textView;
    }

    private void showWifiPicker() {
        if (this.wifiNetworks.isEmpty()) {
            this.wifiStatusText.setText("no networks found - the box may be out of range of any");
            return;
        }
        final Wifi.Network[] networkArr = (Wifi.Network[]) this.wifiNetworks.toArray(new Wifi.Network[0]);
        String[] strArr = new String[networkArr.length];
        for (int i = 0; i < networkArr.length; i++) {
            strArr[i] = networkArr[i].label();
        }
        new AlertDialog.Builder(this).setTitle("Join a network").setItems(strArr, new DialogInterface.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.10
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i2) {
                Wifi.Network network = networkArr[i2];
                if (network.needsPassword()) {
                    SettingsActivity.this.askWifiPassword(network);
                } else {
                    SettingsActivity.this.connectWifi(network, "");
                }
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void askWifiPassword(final Wifi.Network network) {
        final EditText editText = new EditText(this);
        editText.setInputType(129);
        editText.setHint("password for " + network.ssid);
        new AlertDialog.Builder(this).setTitle(network.ssid).setView(editText).setPositiveButton("Join", new DialogInterface.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.11
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.connectWifi(network, editText.getText().toString());
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void connectWifi(Wifi.Network network, String str) {
        this.wifiStatusText.setText("joining " + network.ssid + "...");
        this.bt.send(Wifi.connectCommand(network.ssid, str));
    }

    private LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(this.CARD);
        gradientDrawable.setCornerRadius(dp(14.0f));
        linearLayout.setBackground(gradientDrawable);
        linearLayout.setPadding(dp(14.0f), dp(10.0f), dp(14.0f), dp(10.0f));
        return linearLayout;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(this.HAIRLINE);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1.0f)));
        return view;
    }

    private TextView addValueRow(LinearLayout linearLayout, String str, String str2) {
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(0, dp(7.0f), 0, dp(7.0f));
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(this.TEXT);
        textView.setTextSize(14.0f);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 0.45f));
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(this.MUTED);
        textView2.setTextSize(14.0f);
        textView2.setGravity(8388613);
        textView2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 0.55f));
        linearLayout2.addView(textView);
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2);
        linearLayout.addView(divider());
        return textView2;
    }

    private void applyCarState(Map<String, Object> map) {
        this.carOnroad = Json.bool(map, "onroad", false);
        this.carEngaged = Json.bool(map, "engaged", false);
        this.carReady = Json.bool(map, "controls_ready", false);
        this.carKnown = true;
        renderState(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void renderState(boolean z) {
        int i;
        boolean z2 = z && this.carKnown && this.carOnroad;
        boolean z3 = z && this.carKnown && this.carEngaged;
        String strName = States.name(z, z2, z3);
        String strDetail = States.detail(z, z2, z3, this.carReady);
        String neVar = States.tone(z, z2, z3);
        this.statusText.setText(strName + " — " + strDetail);
        this.laneStatusText.setText(strName);
        if ("accent".equals(neVar)) {
            i = this.ACCENT;
        } else {
            i = "wait".equals(neVar) ? this.WAIT : this.MUTED;
        }
        setDot(i);
        setLaneDot(i);
        applyScreenOn();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDot(int i) {
        this.statusDot.setBackground(oval(i));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setLaneDot(int i) {
        View view = this.laneDot;
        if (view != null) {
            view.setBackground(oval(i));
        }
    }

    private GradientDrawable oval(int i) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(i);
        return gradientDrawable;
    }

    private void queuePose(String str) {
        synchronized (this.poseGate) {
            this.pendingPoseBody = str;
            if (this.poseApplyQueued) {
                return;
            }
            this.poseApplyQueued = true;
            runOnUiThread(new Runnable() { // from class: com.hermes.ka2settings.SettingsActivity.12
                @Override // java.lang.Runnable
                public void run() {
                    String str2;
                    synchronized (SettingsActivity.this.poseGate) {
                        str2 = SettingsActivity.this.pendingPoseBody;
                        SettingsActivity.this.pendingPoseBody = null;
                        SettingsActivity.this.poseApplyQueued = false;
                    }
                    if (str2 == null) {
                        return;
                    }
                    try {
                        SettingsActivity.this.showPose(Json.parseObject(str2));
                    } catch (Json.JsonException e) {
                        SettingsActivity.this.appendLog("bad pose: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void queueGps(final String str) {
        // The GPS line rides the same stream as the pose but arrives about once a second, so it needs no
        // coalescing of its own: it is posted to the UI thread and applied there.
        runOnUiThread(new Runnable() { // from class: com.hermes.ka2settings.SettingsActivity.12b
            @Override // java.lang.Runnable
            public void run() {
                try {
                    SettingsActivity.this.showGps(Json.parseObject(str));
                } catch (Json.JsonException e) {
                    SettingsActivity.this.appendLog("bad gps: " + e.getMessage());
                }
            }
        });
    }

    @Override // com.hermes.ka2settings.BtSpp.Listener
    public void onLine(final String str) {
        if (Drives.isDrives(str)) {
            // Lines arrive on the reader thread and this one builds views, so it has to hop to the UI
            // thread like every other reply does. Without the hop, a drives list answered by the *box*
            // died as CalledFromWrongThreadException - invisible while the host archive answered first,
            // and then the crash the owner saw the moment that path went down.
            runOnUiThread(new Runnable() { // from class: com.hermes.ka2settings.SettingsActivity.11b
                @Override // java.lang.Runnable
                public void run() {
                    SettingsActivity.this.showDrives(str);
                }
            });
            return;
        }
        if (str.startsWith("G ")) {
            queueGps(str.substring(2));
            return;
        }
        if (str.startsWith("P ")) {
            queuePose(str.substring(2));
        } else {
            runOnUiThread(new Runnable() { // from class: com.hermes.ka2settings.SettingsActivity.13
                @Override // java.lang.Runnable
                public void run() {
                    SettingsActivity.this.handleLine(str);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleLine(String str) {
        if (str.startsWith("#")) {
            return;
        }
        if (str.startsWith("ACC ")) {
            // The box answers with its own words: availability, engagement and set speed. Shown
            // verbatim so a refusal reason is visible instead of being paraphrased away.
            this.accStatus.setText(str.substring(4));
            return;
        }
        if (str.startsWith("S ")) {
            try {
                applyCarState(Json.parseObject(str.substring(2)));
                return;
            } catch (Json.JsonException e) {
                appendLog("bad state line: " + e.getMessage());
                return;
            }
        }
        if (str.startsWith("> ")) {
            appendLog(str);
            return;
        }
        if (str.startsWith("KA2 SETTINGS")) {
            Log.i(TAG, "box banner: " + str);
            appendLog("box: " + str);
            this.bt.send("VER " + APP_VERSION);
            this.bt.send("INFO");
            this.bt.send("SCHEMA");
            this.bt.send("ACC STATE");
            if (this.updatePose) {
                this.bt.send("POSE 1");
                return;
            }
            return;
        }
        if (str.startsWith("I ")) {
            try {
                showInfo(Json.parseObject(str.substring(2)));
                return;
            } catch (Json.JsonException e2) {
                appendLog("bad INFO line: " + e2.getMessage());
                return;
            }
        }
        if (str.startsWith("K ")) {
            try {
                upsertRow(Json.parseObject(str.substring(2)));
                return;
            } catch (Json.JsonException e3) {
                appendLog("bad row: " + e3.getMessage());
                return;
            }
        }
        if (str.startsWith("W ")) {
            try {
                Map<String, Object> object = Json.parseObject(str.substring(2));
                if (object.containsKey("sec") || object.containsKey("in_use")) {
                    this.wifiNetworks.add(Wifi.parse(object));
                } else {
                    this.wifiStatusText.setText(Wifi.statusLine(object));
                }
                return;
            } catch (Json.JsonException e4) {
                appendLog("bad wifi row: " + e4.getMessage());
                return;
            }
        }
        if (str.startsWith("E ")) {
            if (this.wifiCollecting) {
                this.wifiCollecting = false;
                showWifiPicker();
            }
            appendLog(countRows());
            return;
        }
        if (str.startsWith("G ")) {
            try {
                showGps(Json.parseObject(str.substring(2)));
                return;
            } catch (Json.JsonException e4) {
                appendLog("bad gps: " + e4.getMessage());
                return;
            }
        }
        if (str.startsWith("P ")) {
            try {
                showPose(Json.parseObject(str.substring(2)));
                return;
            } catch (Json.JsonException e5) {
                appendLog("bad pose: " + e5.getMessage());
                return;
            }
        }
        if (str.startsWith("ERR ")) {
            Log.w(TAG, "box refused: " + str.substring(4));
            if (str.substring(4).startsWith("WIFI")) {
                this.wifiStatusText.setText("Wi-Fi: " + str.substring(4));
            }
            appendLog("box refused: " + str.substring(4));
            this.bt.send("SCHEMA");
            return;
        }
        if (str.startsWith("OK ")) {
            Log.i(TAG, "box accepted: " + str.substring(3));
            if (str.substring(3).startsWith("WIFI ")) {
                this.wifiStatusText.setText("connected - asking the box what it is on now");
                this.bt.send("WIFI STATUS");
                appendLog(str);
                return;
            } else {
                applySetReply(str.substring(3));
                appendLog(str);
                return;
            }
        }
        if (!str.isEmpty()) {
            appendLog(str);
        }
    }

    private void applySetReply(String str) {
        Row row;
        int iIndexOf = str.indexOf(32);
        if (iIndexOf > 0) {
            str = str.substring(0, iIndexOf);
        }
        int iIndexOf2 = str.indexOf(61);
        if (iIndexOf2 <= 0 || (row = this.rows.get(str.substring(0, iIndexOf2))) == null) {
            return;
        }
        if (row.action != null) {
            appendLog("action acknowledged");
            this.bt.send("SCHEMA");
            return;
        }
        String strSubstring = str.substring(iIndexOf2 + 1);
        if (row.stepper != null) {
            if (row.stepper.applyReply(strSubstring)) {
                bindValue(row, row.stepper.value());
                return;
            } else {
                Log.i(TAG, "ignored a late reply for " + row.key + " (" + strSubstring + "): the row is already at " + row.stepper.value());
                return;
            }
        }
        bindValue(row, strSubstring);
    }

    private String countRows() {
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        for (Row row : this.rows.values()) {
            if ("live".equals(row.mode)) {
                i++;
            } else if ("ro".equals(row.mode)) {
                i3++;
            } else {
                i2++;
            }
        }
        return String.format("box declared %d settings: %d live, %d not offered, %d read-only", Integer.valueOf(this.rows.size()), Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3));
    }

    private void showInfo(Map<String, Object> map) {
        String str;
        put("dongle", Json.str(map, "dongle", "-"));
        put("serial", Json.str(map, "serial", "-"));
        put("version", Json.str(map, "version", "-"));
        String str2 = Json.str(map, "commit", "");
        put("commit", str2.isEmpty() ? "-" : str2 + " on " + Json.str(map, "branch", ""));
        put("state", Json.str(map, "state", "-"));
        put("car", Json.str(map, "car", "-"));
        String str3 = Json.str(map, "net", "-");
        String str4 = Json.str(map, "ssid", "");
        String str5 = Json.str(map, "ip", "");
        if (!str4.isEmpty()) {
            str3 = str3 + " " + str4;
        }
        if (!str5.isEmpty()) {
            str3 = str3 + " " + str5;
        }
        if (Json.bool(map, "metered", false)) {
            str = str3 + " (metered)";
        } else {
            str = str3 + " (unmetered)";
        }
        put("network", str);
        double dNum = Json.num(map, "free_gb", -1.0d);
        put("storage", dNum >= 0.0d ? String.format("%.1f GB free", Double.valueOf(dNum)) : "-");
        double dNum2 = Json.num(map, "uptime_s", 0.0d);
        put("uptime", String.format("%dh %02dm", Integer.valueOf((int) (dNum2 / 3600.0d)), Integer.valueOf((int) ((dNum2 % 3600.0d) / 60.0d))));
        Map<String, Object> mapObj = Json.obj(map, "pose");
        if (mapObj != null && !Json.bool(mapObj, "ok", false)) {
            put("model", Json.str(mapObj, "why", "no model"));
        } else if (mapObj != null) {
            put("model", "lane data live");
        }
    }

    private void put(String str, String str2) {
        TextView textView = this.infoValues.get(str);
        if (textView != null) {
            textView.setText(str2);
        }
    }

    private Button tabLogs;
    private String pendingRoute;          // non-null while a single drive's detail is being fetched
    private ScrollView logsScroll;
    private LinearLayout logsList;
    private boolean logsRequested;
    /** Names the archive that answered the last request: the NAS on the LAN, or the host archive. */
    private TextView logsSource;
    /**
     */
    private int logsHeaderCount;

    private LinearLayout buildTabBar() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setBackgroundColor(this.TAB_BAR);
        linearLayout.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(8.0f));
        this.tabSettings = tabButton("Settings");
        this.tabLane = tabButton("Lane view");
        this.tabLogs = tabButton("Logs");
        this.tabSettings.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.showTab(0);
            }
        });
        this.tabLane.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.showTab(1);
            }
        });
        this.tabLogs.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.showTab(2);
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.setMargins(dp(3.0f), 0, dp(3.0f), 0);
        linearLayout.addView(this.tabSettings, layoutParams);
        linearLayout.addView(this.tabLane, layoutParams);
        linearLayout.addView(this.tabLogs, layoutParams);
        return linearLayout;
    }

    private Button tabButton(String str) {
        Button button = new Button(this);
        button.setText(str);
        button.setAllCaps(false);
        button.setTextSize(14.0f);
        button.setPadding(dp(6.0f), dp(10.0f), dp(6.0f), dp(10.0f));
        return button;
    }

    private String savedScreenOn() {
        return getSharedPreferences(PREFS, 0).getString(PREF_SCREEN_ON, ScreenOn.AUTODRIVE);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void chooseScreenOn() {
        String next = ScreenOn.next(savedScreenOn());
        getSharedPreferences(PREFS, 0).edit().putString(PREF_SCREEN_ON, next).apply();
        this.screenButton.setText("Screen: " + ScreenOn.label(next));
        applyScreenOn();
    }

    private void applyScreenOn() {
        boolean zKeep = ScreenOn.keep(savedScreenOn(), this.connectedNow, this.carKnown && this.carEngaged);
        if (zKeep == this.screenOnApplied) {
            return;
        }
        this.screenOnApplied = zKeep;
        if (zKeep) {
            getWindow().addFlags(128);
        } else {
            getWindow().clearFlags(128);
        }
    }

    private String savedTheme() {
        return getSharedPreferences(PREFS, 0).getString(PREF_THEME, "dark");
    }

    private String themeChoiceLabel() {
        String strSavedTheme = savedTheme();
        if ("dark".equals(strSavedTheme)) {
            return "Dark";
        }
        return "light".equals(strSavedTheme) ? "Light" : "System";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void chooseTheme() {
        String[] strArr = {"Dark", "Light", "System"};
        final String[] strArr2 = {"dark", "light", "system"};
        String strSavedTheme = savedTheme();
        int i = 0;
        for (int i2 = 0; i2 < 3; i2++) {
            if (strArr2[i2].equals(strSavedTheme)) {
                i = i2;
            }
        }
        new AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(strArr, i, new DialogInterface.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.16
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i3) {
                SettingsActivity.this.getSharedPreferences(SettingsActivity.PREFS, 0).edit().putString(SettingsActivity.PREF_THEME, strArr2[i3]).apply();
                dialogInterface.dismiss();
                SettingsActivity.this.recreate();
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void cycleLens() {
        getSharedPreferences(PREFS, 0).edit().putString(PREF_LENS_CHOICE, Lens.next(savedLensChoice())).apply();
        this.appliedFocal = 0.0d;
        applyLens(this.lastSpeedMps);
    }

    private String savedLensChoice() {
        String str;
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS, 0);
        String string = sharedPreferences.getString(PREF_LENS_CHOICE, null);
        if (string != null) {
            return string;
        }
        String string2 = sharedPreferences.getString(PREF_LENS, null);
        if (string2 == null) {
            str = Lens.AUTO;
        } else if (string2.startsWith("1.5")) {
            str = Lens.FAR;
        } else {
            str = string2.startsWith("0.75") ? Lens.WIDE : Lens.BALANCED;
        }
        sharedPreferences.edit().putString(PREF_LENS_CHOICE, str).apply();
        return str;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String savedHeightChoice() {
        return getSharedPreferences(PREFS, 0).getString(PREF_HEIGHT, Lens.HEIGHT_HIGHER);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyPanel(boolean z) {
        if (this.sideWrap == null) {
            return;
        }
        boolean z2 = getSharedPreferences(PREFS, 0).getBoolean(PREF_PANEL, true);
        float fOffset = Panel.offset(z2, dp(300.0f));
        if (z) {
            this.sideWrap.animate().translationX(fOffset).setDuration(180L).start();
        } else {
            this.sideWrap.setTranslationX(fOffset);
        }
        this.handleButton.setText(Panel.handle(z2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyHeight() {
        LaneView laneView = this.laneView;
        if (laneView != null) {
            laneView.setCameraHeight(Lens.cameraHeight(savedHeightChoice()));
        }
    }

    private void applyLens(double d) {
        double dFocal = Lens.focal(savedLensChoice(), d, this.appliedBaseFocal);
        double dDrawnFocal = Lens.drawnFocal(savedLensChoice(), d, this.appliedBaseFocal, this.lastPathReach);
        this.appliedBaseFocal = dFocal;
        if (dDrawnFocal != this.appliedFocal) {
            this.appliedFocal = dDrawnFocal;
            LaneView laneView = this.laneView;
            if (laneView != null) {
                laneView.setFocalFraction(dDrawnFocal);
            }
        }
        Button button = this.lensButton;
        if (button != null) {
            button.setText(Lens.label(savedLensChoice(), dFocal));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTab(int which) {
        ScrollView scrollView = this.settingsScroll;
        if (scrollView == null || this.lanePage == null) {
            return;
        }
        if (which == 2 && this.logsScroll == null) {
            buildLogsPage();
        }
        scrollView.setVisibility(which == 0 ? 0 : 8);
        this.lanePage.setVisibility(which == 1 ? 0 : 8);
        if (this.logsScroll != null) {
            this.logsScroll.setVisibility(which == 2 ? 0 : 8);
        }
        styleTab(this.tabSettings, which == 0);
        styleTab(this.tabLane, which == 1);
        if (which == 2) {
            this.logsRequested = true;
            requestDrives(null);      // host archive first, box over Bluetooth as the fallback
        }
        styleTab(this.tabLogs, which == 2);
    }

    /** The log page: built on first use, and attached beside the pages it shares a parent with. */
    private void buildLogsPage() {
        this.logsScroll = new ScrollView(this);
        this.logsScroll.setBackgroundColor(this.BG);
        this.logsList = new LinearLayout(this);
        this.logsList.setOrientation(1);
        this.logsList.setPadding(dp(14.0f), dp(14.0f), dp(14.0f), dp(14.0f));
        TextView textView = new TextView(this);
        textView.setText("Recent drives come from the host archive - the whole history, whether or not "
                + "the car is on - and from the box over Bluetooth when it is connected. Only drives that moved "
            + "are listed - the box logs continuously whenever it is powered, and a parked car reads zero.");
        // A drive's position comes from the box's own GPS: openpilot logs gpsLocationExternal
        // into every route, the host extracts it, and the map matches it to the drive by time.
        // Nothing to record here any more - the phone is out of the loop.
        textView.setTextColor(this.MUTED);
        textView.setTextSize(13.0f);
        this.logsList.addView(textView);

        // Which machine answered. At home that is the NAS itself (one hop, no Cloudflare); anywhere
        // else the published host. Shown rather than implied: "where did this page come from" is
        // exactly the question a slow or empty list raises.
        this.logsSource = new TextView(this);
        this.logsSource.setText("Log source: not asked yet.");
        this.logsSource.setTextColor(this.MUTED);
        this.logsSource.setTextSize(12.0f);
        this.logsSource.setPadding(0, dp(2.0f), 0, dp(6.0f));
        this.logsList.addView(this.logsSource);

        this.logsHeaderCount = this.logsList.getChildCount();      // everything above is header
        this.logsScroll.addView(this.logsList);
        android.view.ViewGroup viewGroup = (android.view.ViewGroup) this.settingsScroll.getParent();
        if (viewGroup instanceof FrameLayout) {
            viewGroup.addView(this.logsScroll, new FrameLayout.LayoutParams(-1, -1));
        } else {
            viewGroup.addView(this.logsScroll, new LinearLayout.LayoutParams(-1, -1));
        }
        this.logsScroll.setVisibility(8);
    }

    private void showDriveMap(final String route, final String source) {
        final android.widget.ImageView image = new android.widget.ImageView(this);
        image.setAdjustViewBounds(true);
        final TextView caption = new TextView(this);
        caption.setTextColor(this.MUTED);
        caption.setTextSize(12.0f);
        caption.setText("Loading map...");

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(1);
        column.setPadding(dp(12.0f), dp(12.0f), dp(12.0f), dp(6.0f));
        column.addView(caption);
        column.addView(image);
        final LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(0);
        // No "phone" choice: the app no longer records a track. The host still falls back to any
        // track recorded before this version under "auto", so old drives keep their maps.
        final String[] sources = {"auto", "box"};
        for (final String choice : sources) {
            Button button = new Button(this);
            button.setText(choice);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showDriveMap(route, choice);
                }
            });
            buttons.addView(button);
        }
        column.addView(buttons);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(route.replace("--", "  ") + "  ·  " + source)
            .setView(column)
            .setPositiveButton("Close", null)
            .create();
        dialog.show();

        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] png = Remote.fetchBytesPath(Remote.candidates(Remote.DEFAULT_BASE),
                        Remote.mapPath(route, source), Remote.DEFAULT_TOKEN, 45000);
                    final android.graphics.Bitmap bitmap =
                        android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (bitmap == null) {
                                caption.setText("The host sent an image this phone could not read.");
                            } else {
                                image.setImageBitmap(bitmap);
                                caption.setText(png.length / 1024 + " kB from the " + source + " track");
                            }
                        }
                    });
                } catch (final Exception exc) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // the host explains why there is no track; show its reason rather than a guess
                            caption.setText(exc.getMessage() == null ? "Could not load the map."
                                : exc.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Ask for the drives: the host archive first (it has the whole history and answers with the car
     * off), then the box over Bluetooth if the host cannot. The host's reply is wrapped into the box's
     * own line shape, so showDrives() renders both without knowing which answered.
     */
    private void requestDrives(final String route) {
        final String base = Remote.DEFAULT_BASE;
        final String token = Remote.DEFAULT_TOKEN;
        this.pendingRoute = route;
        if (!Remote.configured(base, token)) {
            if (this.bt != null) {
                this.bt.send(route == null ? "DRIVES" : "DRIVES " + route);
            }
            return;
        }
        new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                String body = null;
                String why = null;
                try {
                    // The NAS first (it holds the logs, and at home that is one hop), then the
                    // published host - which is also what answers when the car and the NAS are off.
                    body = Remote.fetchPath(Remote.candidates(base),
                        route == null ? Remote.listPath() : Remote.summaryPath(route), token);
                } catch (Exception exc) {
                    why = exc.getMessage();
                }
                final String got = body;
                final String failed = why;
                runOnUiThread(new Runnable() {
                    @Override // java.lang.Runnable
                    public void run() {
                        if (got != null) {
                            if (SettingsActivity.this.logsSource != null) {
                                String from = Remote.lastSource;
                                SettingsActivity.this.logsSource.setText("Log source: "
                                    + (from == null || from.isEmpty() ? "the host archive" : from));
                            }
                            showDrives(Remote.toReply(got));
                            return;
                        }
                        appendLog("host drives unavailable (" + failed + ") - asking the box");
                        if (SettingsActivity.this.logsSource != null) {
                            SettingsActivity.this.logsSource.setText("Log source: the box over Bluetooth");
                        }
                        if (SettingsActivity.this.bt != null) {
                            SettingsActivity.this.bt.send(route == null ? "DRIVES" : "DRIVES " + route);
                        }
                    }
                });
            }
        }).start();
    }

    /** One drive, opened: its readings, then its own minutes one row each. */
    private void showDriveDetail(String route, String replyLine) {
        java.util.List<String[]> pairs = Drives.summary(replyLine);
        StringBuilder body = new StringBuilder();
        for (String[] pair : pairs) {
            body.append(pair[0]).append(":  ").append(pair[1]).append('\n');
        }
        java.util.List<String[]> minutes = Drives.segments(replyLine);
        if (!minutes.isEmpty()) {
            body.append('\n').append("Every minute (").append(minutes.size()).append("):").append('\n');
            for (String[] row : minutes) {
                body.append("   ").append(row[0]).append("   ").append(row[1]).append('\n');
            }
        }
        if (body.length() == 0) {
            body.append("No readings for this drive.");
        }
        // The readings, then a way to see them laid over a map: the button opens the map for this drive
        // with the source already selected, and the map dialog lets it be changed from there.
        final String routeForMap = route;
        final AlertDialog detail = new AlertDialog.Builder(this)
            .setTitle(route.replace("--", "  "))
            .setPositiveButton("Close", null)
            .create();

        TextView readings = new TextView(this);
        readings.setText(body.toString().trim());
        readings.setTextSize(13.0f);
        readings.setPadding(dp(16.0f), dp(10.0f), dp(16.0f), dp(4.0f));

        Button showMap = new Button(this);
        showMap.setText("Show on map");
        showMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                detail.dismiss();
                showDriveMap(routeForMap, "auto");
            }
        });

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(1);
        column.addView(readings);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-2, -2);
        mapParams.leftMargin = dp(16.0f);
        mapParams.bottomMargin = dp(12.0f);
        column.addView(showMap, mapParams);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(column);
        detail.setView(scroller);
        detail.show();
    }

    /** Whatever the box said, as rows a driver can read. */
    private void showDrives(String str) {
        if (this.logsList == null) {
            return;
        }
        if (this.pendingRoute != null && !Drives.isError(str)) {
            String route = this.pendingRoute;
            this.pendingRoute = null;
            showDriveDetail(route, str);
            return;
        }
        int header = this.logsHeaderCount > 0 ? this.logsHeaderCount : this.logsList.getChildCount();
        while (this.logsList.getChildCount() > header) {
            this.logsList.removeViewAt(header);
        }
        if (Drives.isError(str)) {
            TextView textView = new TextView(this);
            textView.setText("The box could not list the drives: " + Drives.errorText(str));
            textView.setTextColor(this.MUTED);
            textView.setTextSize(14.0f);
            this.logsList.addView(textView);
            return;
        }
        java.util.List<String[]> rows = Drives.list(str);
        if (rows.isEmpty()) {
            TextView textView = new TextView(this);
            textView.setText("No drives to show yet.");
            textView.setTextColor(this.MUTED);
            textView.setTextSize(14.0f);
            this.logsList.addView(textView);
            return;
        }
        for (final String[] row : rows) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(1);
            card.setBackground(roundRect(this.SCRIM, 10));
            card.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(10.0f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(8.0f), 0, 0);
            TextView when = new TextView(this);
            when.setText(row[1].length() == 0 ? row[0] : row[1]);
            when.setTextColor(this.TEXT);
            when.setTextSize(17.0f);
            when.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(when);
            TextView stats = new TextView(this);
            stats.setText(row[2] + "   ·   " + row[3]);
            stats.setTextColor(this.MUTED);
            stats.setTextSize(14.0f);
            card.addView(stats);
            card.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    SettingsActivity.this.requestDrives(row[0]);
                }
            });
            this.logsList.addView(card, lp);
        }
    }

    private void styleTab(Button button, boolean z) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(dp(18.0f));
        gradientDrawable.setColor(z ? this.ACCENT : this.CHIP_SELECTED);
        button.setBackground(gradientDrawable);
        button.setTextColor(z ? this.BG : this.MUTED);
        button.setTypeface(null, z ? 1 : 0);
    }

    private FrameLayout buildLanePage() {
        int i;
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(Color.parseColor("#0B1018"));
        this.lanePage = frameLayout;
        LaneView laneView = new LaneView(this);
        this.laneView = laneView;
        laneView.setBackgroundColor(Color.parseColor("#0B1018"));
        frameLayout.addView(this.laneView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(12.0f));
        LinearLayout linearLayout2 = new LinearLayout(this);
        int i2 = 0;
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setBackground(roundRect(this.SCRIM, 10));
        linearLayout2.setPadding(dp(10.0f), dp(6.0f), dp(10.0f), dp(6.0f));
        this.laneDot = new View(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(10.0f), dp(10.0f));
        layoutParams.setMargins(0, 0, dp(10.0f), 0);
        this.laneDot.setLayoutParams(layoutParams);
        setLaneDot(this.MUTED);
        TextView textView = new TextView(this);
        this.laneStatusText = textView;
        textView.setText("not connected");
        this.laneStatusText.setTextColor(this.TEXT);
        this.laneStatusText.setTextSize(14.0f);
        linearLayout2.addView(this.laneDot);
        linearLayout2.addView(this.laneStatusText);
        linearLayout.addView(linearLayout2);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        linearLayout3.setGravity(1);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(16);
        char c = 0;
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams3.setMargins(0, dp(10.0f), 0, 0);
        LinearLayout linearLayout5 = new LinearLayout(this);
        linearLayout5.setOrientation(1);
        linearLayout5.setGravity(1);
        TextView textView2 = new TextView(this);
        this.speedValue = textView2;
        textView2.setText("-");
        this.speedValue.setTextColor(this.TEXT);
        this.speedValue.setTextSize(52.0f);
        this.speedValue.setTypeface(Typeface.DEFAULT_BOLD);
        this.speedValue.setShadowLayer(dp(4.0f), 0.0f, dp(1.0f), Color.argb(190, 0, 0, 0));
        TextView textView3 = new TextView(this);
        this.speedUnit = textView3;
        textView3.setText("km/h");
        this.speedUnit.setTextColor(this.MUTED);
        this.speedUnit.setTextSize(15.0f);
        this.speedUnit.setPadding(0, 0, 0, 0);
        linearLayout5.addView(this.speedValue);
        linearLayout5.addView(this.speedUnit);
        linearLayout3.addView(linearLayout5);
        TextView textView4 = new TextView(this);
        this.setSpeedText = textView4;
        textView4.setText("cruise not set");
        this.setSpeedText.setTextColor(this.MUTED);
        this.setSpeedText.setTextSize(15.0f);
        this.setSpeedText.setShadowLayer(dp(3.0f), 0.0f, dp(1.0f), Color.argb(190, 0, 0, 0));
        linearLayout3.addView(this.setSpeedText);
        linearLayout4.addView(linearLayout3, layoutParams2);
        Button button = new Button(this);
        this.lensButton = button;
        button.setAllCaps(false);
        this.lensButton.setTextSize(20.0f);
        this.lensButton.setTextColor(this.TEXT);
        this.lensButton.setBackground(roundRect(this.SCRIM, 10));
        this.lensButton.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        this.lensButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SettingsActivity.this.cycleLens();
            }
        });
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams4.setMargins(0, dp(8.0f), 0, 0);
        layoutParams4.setMargins(dp(8.0f), 0, 0, 0);
        LinearLayout linearLayout6 = new LinearLayout(this);
        this.sidePanel = linearLayout6;
        linearLayout6.setOrientation(1);
        this.sidePanel.setBackground(roundRect(this.SCRIM, 12));
        this.sidePanel.setPadding(dp(10.0f), dp(10.0f), dp(10.0f), dp(10.0f));
        LinearLayout linearLayout7 = new LinearLayout(this);
        linearLayout7.setOrientation(1);
        this.sidePanel.addView(linearLayout7);
        linearLayout7.addView(this.lensButton);
        Button button2 = new Button(this);
        this.heightButton = button2;
        button2.setAllCaps(false);
        this.heightButton.setTextSize(20.0f);
        this.heightButton.setTextColor(this.TEXT);
        this.heightButton.setBackground(roundRect(this.SCRIM, 10));
        this.heightButton.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        this.heightButton.setText(Lens.heightLabel(savedHeightChoice()));
        this.heightButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                String strNextHeight = Lens.nextHeight(SettingsActivity.this.savedHeightChoice());
                SettingsActivity.this.getSharedPreferences(SettingsActivity.PREFS, 0).edit().putString(SettingsActivity.PREF_HEIGHT, strNextHeight).apply();
                SettingsActivity.this.heightButton.setText(Lens.heightLabel(strNextHeight));
                SettingsActivity.this.applyHeight();
            }
        });
        new LinearLayout.LayoutParams(-2, -2).setMargins(dp(6.0f), 0, 0, 0);
        linearLayout7.addView(this.heightButton);
        linearLayout.addView(linearLayout4, layoutParams3);
        linearLayout.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1.0f));
        TextView textView5 = new TextView(this);
        this.laneCaption = textView5;
        textView5.setText("Waiting for the box. On the bench the model is not running, so the box reports no lane rather than drawing one.");
        this.laneCaption.setTextColor(this.MUTED);
        this.laneCaption.setTextSize(12.0f);
        this.laneCaption.setBackground(roundRect(this.SCRIM, 10));
        this.laneCaption.setPadding(dp(10.0f), dp(6.0f), dp(10.0f), dp(6.0f));
        linearLayout.addView(this.laneCaption);
        LinearLayout linearLayout8 = new LinearLayout(this);
        linearLayout8.setOrientation(1);
        linearLayout8.setBackground(roundRect(this.SCRIM, 10));
        linearLayout8.setPadding(dp(10.0f), dp(8.0f), dp(10.0f), dp(8.0f));
        LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams5.setMargins(0, dp(6.0f), 0, 0);
        this.sidePanel.addView(linearLayout8, layoutParams5);
        String[][] strArr = {new String[]{"width", "Lane width"}, new String[]{"speed", "Speed"}, new String[]{"plan", "Model plan"}, new String[]{"lines", "Lane confidence"}, new String[]{"horizon", "Measured at"}, new String[]{"engaged", "Lane centring"}, new String[]{"age", "Pose age"}, new String[]{"lead", "Lead vehicle"}, new String[]{"lead2", "Lead 2"}, new String[]{"lanes", "Lanes shown"}, new String[]{"route", "Predicted route"}, new String[]{"drift", "Centring drift"}, new String[]{"acc", "Your car's ACC"}, new String[]{"acc_cmd", "ACC requesting"}, new String[]{"gps", "GPS"}, new String[]{"gps_pos", "GPS position"}};
        int i3 = 0;
        while (true) {
            float f = 2.0f;
            if (i3 < 16) {
                LinearLayout linearLayout9 = new LinearLayout(this);
                linearLayout9.setOrientation(i2);
                linearLayout8.addView(linearLayout9);
                int i4 = i2;
                while (i4 < 1) {
                    int i5 = i3 + i4;
                    LinearLayout linearLayout10 = new LinearLayout(this);
                    linearLayout10.setOrientation(i2);
                    linearLayout10.setGravity(16);
                    linearLayout10.setPadding(dp(4.0f), dp(f), dp(4.0f), dp(f));
                    if (i5 < 16) {
                        // The GPS rows carry a coordinate and a count, not a single word, so they are set
                        // smaller than the rest: at the panel's width the alternative is a clipped row.
                        boolean zGpsRow = strArr[i5][0].startsWith("gps");
                        float fRow = zGpsRow ? 14.0f : 20.0f;
                        TextView textView6 = new TextView(this);
                        textView6.setText(strArr[i5][1]);
                        textView6.setTextColor(this.MUTED);
                        textView6.setTextSize(fRow);
                        textView6.setSingleLine(true);
                        linearLayout10.addView(textView6, new LinearLayout.LayoutParams(0, -2, 1.0f));
                        TextView textView7 = new TextView(this);
                        textView7.setText("-");
                        textView7.setTextColor(this.TEXT);
                        textView7.setTextSize(fRow);
                        textView7.setTypeface(Typeface.DEFAULT_BOLD);
                        textView7.setSingleLine(true);
                        linearLayout10.addView(textView7);
                        Map<String, TextView> map = this.laneStats;
                        String[] strArr2 = strArr[i5];
                        i = 0;
                        map.put(strArr2[0], textView7);
                    } else {
                        i = 0;
                    }
                    linearLayout9.addView(linearLayout10, new LinearLayout.LayoutParams(i, -2, 1.0f));
                    i4++;
                    c = 0;
                    f = 2.0f;
                    i2 = 0;
                }
                i3++;
                i2 = 0;
            } else {
                frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(-1, -1));
                Button button3 = new Button(this);
                this.handleButton = button3;
                button3.setAllCaps(false);
                this.handleButton.setTextColor(this.TEXT);
                this.handleButton.setTextSize(16.0f);
                this.handleButton.setPadding(dp(2.0f), dp(12.0f), dp(2.0f), dp(12.0f));
                this.handleButton.setBackground(roundRect(this.SCRIM, 8));
                this.handleButton.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.19
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        SettingsActivity.this.getSharedPreferences(SettingsActivity.PREFS, 0).edit().putBoolean(SettingsActivity.PREF_PANEL, Panel.next(SettingsActivity.this.getSharedPreferences(SettingsActivity.PREFS, 0).getBoolean(SettingsActivity.PREF_PANEL, true))).apply();
                        SettingsActivity.this.applyPanel(true);
                    }
                });
                LinearLayout linearLayout11 = new LinearLayout(this);
                this.sideWrap = linearLayout11;
                linearLayout11.setOrientation(0);
                this.sideWrap.setGravity(16);
                this.sideWrap.addView(this.sidePanel, new LinearLayout.LayoutParams(dp(300.0f), -2));
                this.sideWrap.addView(this.handleButton, new LinearLayout.LayoutParams(dp(20.0f), -2));
                FrameLayout.LayoutParams layoutParams6 = new FrameLayout.LayoutParams(-2, -2);
                layoutParams6.gravity = 8388627;
                frameLayout.addView(this.sideWrap, layoutParams6);
                applyPanel(false);
                return frameLayout;
            }
        }
    }

    private String lensName() {
        double d = this.appliedFocal;
        if (d == 1.5d) {
            return Lens.FAR;
        }
        return d == 1.1d ? Lens.BALANCED : Lens.WIDE;
    }

    private double laneViewHalfWidthAt10m() {
        LaneView laneView = this.laneView;
        if (laneView == null || laneView.getWidth() <= 0) {
            return 0.0d;
        }
        return new LaneGeometry.Camera(this.laneView.getWidth(), this.laneView.getHeight(), this.appliedFocal, Lens.cameraHeight(savedHeightChoice())).halfWidthAt(10.0d);
    }

    private String leadText(LaneGeometry.Lead lead, double d) {
        if (lead == null || !lead.present) {
            return "none";
        }
        double dGapSeconds = lead.gapSeconds(d);
        return dGapSeconds >= 0.0d ? String.format("%.0f m  %.1f s", Double.valueOf(lead.distance), Double.valueOf(dGapSeconds)) : String.format("%.0f m", Double.valueOf(lead.distance));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setLaneStat(String str, String str2) {
        TextView textView = this.laneStats.get(str);
        if (textView != null) {
            textView.setText(str2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showGps(Map<String, Object> map) {
        // The panel is refreshed straight away rather than waiting for the next pose: the GPS row is the
        // one thing on this page that answers "is the box seeing satellites at all", and a second of lag
        // on a question like that is a second of guessing.
        this.gps = LaneGeometry.gpsFromJson(map, System.currentTimeMillis() / 1000.0d);
        LaneGeometry.Pose pose = this.lastPose;
        if (pose != null) {
            Readouts.fill(pose, this.gps, this.poseCache, lensName(), laneViewHalfWidthAt10m(), this.laneSink);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPose(Map<String, Object> map) {
        LaneGeometry.Pose poseFromJson = LaneGeometry.fromJson(map, System.currentTimeMillis() / 1000.0d);
        this.lastPose = poseFromJson;
        this.lastSpeedMps = poseFromJson.displayedSpeed();
        this.lastPathReach = poseFromJson.pathReach;
        applyLens(poseFromJson.displayedSpeed());
        this.laneView.setPose(poseFromJson);
        Readouts.fill(poseFromJson, this.gps, this.poseCache, lensName(), laneViewHalfWidthAt10m(), this.laneSink);
        String str = (poseFromJson.ok ? "ok " : "none ") + poseFromJson.summary();
        if (!str.equals(this.lastPoseVerdict)) {
            this.lastPoseVerdict = str;
            Log.i(TAG, "pose: " + str);
        }
    }

    private void upsertRow(Map<String, Object> map) {
        String str;
        String str2 = Json.str(map, "k", "");
        if (str2.isEmpty()) {
            return;
        }
        Row row = this.rows.get(str2);
        if (row == null) {
            row = new Row();
            row.key = str2;
            this.rows.put(str2, row);
            buildRow(row, map);
            Log.i(TAG, "row " + str2 + " type=" + Json.str(map, "t", "?") + " mode=" + Json.str(map, "mode", "?") + " value=" + Json.str(map, "v", ""));
        }
        row.type = Json.str(map, "t", row.type);
        row.mode = Json.str(map, "mode", row.mode);
        String str3 = Json.str(map, "v", "");
        if (row.stepper != null) {
            row.stepper.reset(str3);
            bindValue(row, row.stepper.value());
        } else {
            bindValue(row, str3);
        }
        boolean zWritable = Json.writable(row.mode);
        if (row.toggle != null) {
            row.toggle.setEnabled(zWritable);
        }
        if (row.field != null) {
            row.field.setEnabled(zWritable);
        }
        if (row.picker != null) {
            row.picker.setEnabled(zWritable);
        }
        if (row.action != null) {
            row.action.setEnabled(zWritable);
        }
        Iterator<Button> it = row.optionButtons.iterator();
        while (it.hasNext()) {
            it.next().setEnabled(zWritable);
        }
        String str4 = Json.str(map, "warn", "");
        if (row.description != null) {
            TextView textView = row.description;
            if (str4.isEmpty()) {
                str = row.desc;
            } else {
                str = row.desc + "  " + str4;
            }
            textView.setText(str);
        }
        if (!str4.isEmpty() && !str4.equals(row.lastWarn)) {
            row.lastWarn = str4;
            Log.w(TAG, "row " + str2 + ": " + str4);
            appendLog("warning: " + str2 + " - " + str4);
        }
    }

    private void bindValue(Row row, String str) {
        row.value = str;
        if (row.toggle != null) {
            row.suppressCallback = true;
            row.toggle.setChecked(Json.looseInt(str, 0) != 0 || "true".equalsIgnoreCase(str));
            row.suppressCallback = false;
        }
        if (row.valueText != null && row.stepper == null) {
            row.valueText.setText(str.isEmpty() ? "-" : str);
        }
        if (row.field != null && !row.field.hasFocus()) {
            row.field.setText(str);
        }
        if (row.picker != null) {
            row.picker.setText(str.isEmpty() ? "choose" : str);
        }
        if (row.minus != null && row.stepper != null) {
            boolean zWritable = Json.writable(row.mode);
            boolean z = row.stepper.atLowerLimit() || row.stepper.atUpperLimit();
            row.valueText.setText(row.stepper.value());
            row.valueText.setTextColor(z ? this.MUTED : this.TEXT);
            row.minus.setEnabled(zWritable && row.stepper.canMove(-1));
            row.plus.setEnabled(zWritable && row.stepper.canMove(1));
        }
        if (!row.optionButtons.isEmpty()) {
            int iLooseInt = Json.looseInt(str, -1);
            int i = 0;
            while (i < row.optionButtons.size()) {
                Button button = row.optionButtons.get(i);
                boolean z2 = i == iLooseInt;
                button.setTextColor(z2 ? -1 : this.TEXT);
                button.setBackground(roundRect(z2 ? this.ACCENT : this.CHIP, 8));
                i++;
            }
        }
    }

    private GradientDrawable roundRect(int i, int i2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(dp(i2));
        return gradientDrawable;
    }

    private void buildRow(final Row row, final Map<String, Object> map) {
        String str;
        int i;
        int i2;
        LinearLayout linearLayout;
        final String str2 = Json.str(map, "title", row.key);
        String str3 = Json.str(map, "desc", "");
        String str4 = Json.str(map, "sec", "sw");
        String str5 = Json.str(map, "mode", "ro");
        boolean zWritable = Json.writable(str5);
        if (map.containsKey("min") && map.containsKey("max")) {
            str3 = str3 + String.format("  (%s to %s%s)", trim(Json.num(map, "min", 0.0d)), trim(Json.num(map, "max", 0.0d)), map.containsKey("step") ? String.format(" in steps of %s", trim(Json.num(map, "step", 0.0d))) : "");
        }
        if (!"nextstart".equals(str5)) {
            str = str3;
        } else {
            str = str3 + "  Applies at the next start, not immediately.";
        }
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setPadding(0, dp(8.0f), 0, dp(8.0f));
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(1);
        linearLayout4.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView = new TextView(this);
        textView.setText(str2);
        textView.setTextColor(this.TEXT);
        textView.setTextSize(15.0f);
        linearLayout4.addView(textView);
        if (!str.isEmpty()) {
            TextView textView2 = new TextView(this);
            textView2.setText(str);
            textView2.setTextColor(this.MUTED);
            textView2.setTextSize(12.0f);
            textView2.setPadding(0, dp(2.0f), dp(8.0f), 0);
            linearLayout4.addView(textView2);
            row.description = textView2;
            row.desc = str;
        }
        linearLayout3.addView(linearLayout4);
        String str6 = Json.str(map, "t", "str");
        if (str6.equals("bool")) {
            Switch r0 = new Switch(this);
            r0.setChecked(Json.bool(map, "v", false));
            r0.setEnabled(zWritable);
            r0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.hermes.ka2settings.SettingsActivity.21
                @Override // android.widget.CompoundButton.OnCheckedChangeListener
                public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                    if (row.suppressCallback) {
                        return;
                    }
                    SettingsActivity.this.bt.send("SET " + row.key + " " + (z ? "1" : "0"));
                }
            });
            row.toggle = r0;
            linearLayout3.addView(r0);
            i = 0;
        } else if (str6.equals("enum") && Json.list(map, "opts") != null) {
            row.options = new LinearLayout(this);
            row.options.setOrientation(0);
            List<Object> list = Json.list(map, "opts");
            for (int i3 = 0; i3 < list.size(); i3++) {
                Button button = new Button(this);
                button.setText(String.valueOf(list.get(i3)));
                button.setTextSize(11.0f);
                button.setAllCaps(false);
                button.setEnabled(zWritable);
                button.setPadding(dp(8.0f), dp(2.0f), dp(8.0f), dp(2.0f));
                button.setMinimumWidth(0);
                button.setMinimumHeight(0);
                button.setBackground(roundRect(this.CHIP, 8));
                final int optionIndex = i3;             // the listener captures this, not the loop variable
                button.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.22
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        SettingsActivity.this.bt.send("SET " + row.key + " " + optionIndex);
                    }
                });
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
                layoutParams.setMargins(dp(4.0f), 0, 0, 0);
                button.setLayoutParams(layoutParams);
                row.optionButtons.add(button);
                row.options.addView(button);
            }
            linearLayout3.addView(row.options);
            i = 0;
        } else if (str6.equals("pick") && Json.list(map, "opts") != null) {
            final List<Object> list2 = Json.list(map, "opts");
            Button button2 = new Button(this);
            button2.setTextSize(12.0f);
            button2.setAllCaps(false);
            button2.setTextColor(this.TEXT);
            button2.setBackground(roundRect(this.CHIP, 8));
            button2.setPadding(dp(10.0f), dp(2.0f), dp(10.0f), dp(2.0f));
            button2.setEnabled(zWritable);
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.23
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    final String[] strArr = new String[list2.size()];
                    for (int i4 = 0; i4 < list2.size(); i4++) {
                        strArr[i4] = String.valueOf(list2.get(i4));
                    }
                    new AlertDialog.Builder(SettingsActivity.this).setTitle(str2).setItems(strArr, new DialogInterface.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.23.1
                        @Override // android.content.DialogInterface.OnClickListener
                        public void onClick(DialogInterface dialogInterface, int i5) {
                            SettingsActivity.this.bt.send("SET " + row.key + " " + strArr[i5]);
                        }
                    }).show();
                }
            });
            row.picker = button2;
            linearLayout3.addView(button2);
            i = 0;
        } else if (str6.equals("action")) {
            Button button3 = new Button(this);
            button3.setText(actionLabel(map));
            button3.setTextSize(12.0f);
            button3.setAllCaps(false);
            button3.setTextColor(this.DANGER);
            button3.setBackground(roundRect(this.DANGER_WASH, 8));
            button3.setPadding(dp(10.0f), dp(2.0f), dp(10.0f), dp(2.0f));
            button3.setEnabled(zWritable);
            i = 0;
            final String str7 = str;
            button3.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.24
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    SettingsActivity.this.confirmAndRun(row.key, str2, str7, Json.str(map, "gate", ""));
                }
            });
            row.action = button3;
            linearLayout3.addView(button3);
        } else {
            i = 0;
            if (str6.equals("num") && map.containsKey("min") && map.containsKey("max") && Json.writable(str5)) {
                row.min = Json.num(map, "min", 0.0d);
                row.max = Json.num(map, "max", 0.0d);
                row.step = Json.num(map, "step", 0.05d);
                row.stepper = new Stepper(row.step, row.min, row.max, Json.str(map, "v", ""));
                LinearLayout linearLayout5 = new LinearLayout(this);
                linearLayout5.setOrientation(0);
                linearLayout5.setGravity(16);
                Button buttonStepButton = stepButton("-");
                TextView textView3 = new TextView(this);
                textView3.setTextSize(15.0f);
                textView3.setTextColor(this.TEXT);
                textView3.setGravity(17);
                textView3.setMinimumWidth(dp(58.0f));
                Button buttonStepButton2 = stepButton("+");
                buttonStepButton.setOnTouchListener(stepperTouch(row, -1));
                buttonStepButton2.setOnTouchListener(stepperTouch(row, 1));
                linearLayout5.addView(buttonStepButton);
                linearLayout5.addView(textView3);
                linearLayout5.addView(buttonStepButton2);
                row.minus = buttonStepButton;
                row.plus = buttonStepButton2;
                row.valueText = textView3;
                linearLayout3.addView(linearLayout5);
            } else if (Json.writable(Json.str(map, "mode", "ro"))) {
                LinearLayout linearLayout6 = new LinearLayout(this);
                linearLayout6.setOrientation(0);
                linearLayout6.setGravity(16);
                EditText editText = new EditText(this);
                editText.setText(Json.str(map, "v", ""));
                editText.setTextSize(14.0f);
                editText.setGravity(8388613);
                editText.setSingleLine(true);
                editText.setPadding(dp(8.0f), dp(6.0f), dp(8.0f), dp(6.0f));
                editText.setBackground(roundRect(this.FIELD, 8));
                if (str6.equals("num")) {
                    i2 = 12290;
                } else {
                    i2 = 1;
                }
                editText.setInputType(i2);
                editText.setLayoutParams(new LinearLayout.LayoutParams(dp(96.0f), -2));
                Button button4 = new Button(this);
                button4.setText("Set");
                button4.setAllCaps(false);
                button4.setTextSize(12.0f);
                button4.setTextColor(this.ACCENT);
                button4.setBackground(null);
                button4.setMinWidth(0);
                button4.setMinimumWidth(0);
                button4.setPadding(dp(8.0f), 0, 0, 0);
                button4.setOnClickListener(new View.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.25
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        SettingsActivity.this.bt.send("SET " + row.key + " " + row.field.getText().toString().trim());
                    }
                });
                linearLayout6.addView(editText);
                linearLayout6.addView(button4);
                row.field = editText;
                linearLayout3.addView(linearLayout6);
            } else {
                TextView textView4 = new TextView(this);
                textView4.setText(Json.str(map, "v", "-"));
                textView4.setTextColor(this.MUTED);
                textView4.setTextSize(14.0f);
                textView4.setGravity(8388613);
                textView4.setMaxWidth(dp(140.0f));
                textView4.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
                row.valueText = textView4;
                linearLayout3.addView(textView4);
            }
        }
        linearLayout2.addView(linearLayout3);
        if ("ACT_RESET_TUNING".equals(row.key)) {
            this.overridesSection.setVisibility(0);
            linearLayout = this.overridesCard;
        } else if ("bend".equals(str4) || ("tune".equals(str4) && row.key != null && row.key.startsWith("VIS_TURN_ACC"))) {
            this.bendSection.setVisibility(0);
            linearLayout = this.bendCard;
        } else if ("lead".equals(str4) || ("tune".equals(str4) && row.key != null && row.key.startsWith("VIS_LEAD_ACC"))) {
            this.leadSection.setVisibility(0);
            linearLayout = this.leadCard;
        } else if ("lane".equals(str4) || "tune".equals(str4)) {
            this.tuningSection.setVisibility(0);
            linearLayout = this.tuningCard;
        } else if ("dev".equals(str4)) {
            linearLayout = this.deviceCard;
        } else {
            linearLayout = this.softwareCard;
        }
        linearLayout.addView(linearLayout2);
        linearLayout.addView(divider());
    }

    private String[] missingBluetoothPermissions() {
        ArrayList arrayList = new ArrayList();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != 0) {
                arrayList.add("android.permission.BLUETOOTH_CONNECT");
            }
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != 0) {
                arrayList.add("android.permission.BLUETOOTH_SCAN");
            }
        } else if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0) {
            arrayList.add("android.permission.ACCESS_FINE_LOCATION");
        }
        return (String[]) arrayList.toArray(new String[0]);
    }

    private void autoConnectSoon() {
        String[] strArrMissingBluetoothPermissions = missingBluetoothPermissions();
        if (strArrMissingBluetoothPermissions.length > 0) {
            requestPermissions(strArrMissingBluetoothPermissions, 1);
            this.statusText.setText("allow nearby-devices access and the app will find the box itself");
        } else {
            this.bt.autoConnect(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doConnect() {
        autoConnectSoon();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void editBoxAddress() {
        final EditText editText = new EditText(this);
        editText.setInputType(524289);
        editText.setText(BtSpp.configuredMac(this));
        editText.setTextColor(this.TEXT);
        editText.setTypeface(Typeface.MONOSPACE);
        editText.setSelection(editText.getText().length());
        new AlertDialog.Builder(this).setTitle("Box Bluetooth address").setMessage("Six hex pairs, like 9C:B8:B4:5F:0A:37. Shown by bluetoothctl on the box as Controller <address>.").setView(editText).setPositiveButton("Save", new DialogInterface.OnClickListener() { // from class: com.hermes.ka2settings.SettingsActivity.26
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                String string = editText.getText().toString();
                if (!BoxName.isMac(string)) {
                    SettingsActivity.this.statusText.setText("that is not a Bluetooth address - nothing changed");
                    return;
                }
                BtSpp.rememberMac(SettingsActivity.this, string);
                SettingsActivity.this.macValue.setText(BtSpp.configuredMac(SettingsActivity.this));
                SettingsActivity.this.bt.stop();
                SettingsActivity.this.bt.autoConnect(SettingsActivity.this);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == 1) {
            boolean z = iArr.length > 0;
            for (int i2 : iArr) {
                z = z && i2 == 0;
            }
            if (z) {
                this.bt.autoConnect(this);
            } else {
                this.statusText.setText("Bluetooth denied - enable Nearby devices for KA2 Settings in Android settings, then reopen the app");
            }
        }
    }

    @Override // com.hermes.ka2settings.BtSpp.Listener
    public void onState(final String str, final String str2) {
        runOnUiThread(new Runnable() { // from class: com.hermes.ka2settings.SettingsActivity.27
            @Override // java.lang.Runnable
            public void run() {
                String str3;
                Log.i(SettingsActivity.TAG, "state: " + str + " " + str2);
                if (ScreenOn.CONNECTED.equals(str)) {
                    SettingsActivity.this.connectedNow = true;
                    SettingsActivity.this.statusText.setText("connected - reading the car's state");
                    SettingsActivity.this.laneStatusText.setText(ScreenOn.CONNECTED);
                    SettingsActivity settingsActivity = SettingsActivity.this;
                    settingsActivity.setDot(settingsActivity.WAIT);
                    SettingsActivity.this.bt.send("STATE");
                    SettingsActivity.this.bt.send("WIFI STATUS");
                    SettingsActivity settingsActivity2 = SettingsActivity.this;
                    settingsActivity2.setLaneDot(settingsActivity2.ACCENT);
                    SettingsActivity.this.connectButton.setText("Disconnect");
                    return;
                }
                if ("connecting".equals(str)) {
                    SettingsActivity.this.statusText.setText(str2);
                    SettingsActivity.this.laneStatusText.setText(str2);
                    SettingsActivity settingsActivity3 = SettingsActivity.this;
                    settingsActivity3.setDot(settingsActivity3.WAIT);
                    SettingsActivity settingsActivity4 = SettingsActivity.this;
                    settingsActivity4.setLaneDot(settingsActivity4.WAIT);
                    return;
                }
                String str4 = str2;
                if (str4 == null || str4.isEmpty()) {
                    str3 = "";
                } else {
                    str3 = " (" + str2 + ")";
                }
                String str5 = "disconnected" + str3;
                SettingsActivity.this.statusText.setText(str5);
                SettingsActivity.this.laneStatusText.setText(str5);
                SettingsActivity settingsActivity5 = SettingsActivity.this;
                settingsActivity5.setDot(settingsActivity5.MUTED);
                SettingsActivity settingsActivity6 = SettingsActivity.this;
                settingsActivity6.setLaneDot(settingsActivity6.MUTED);
                SettingsActivity.this.connectButton.setText("Connect to box");
                SettingsActivity.this.laneView.setPose(null);
                SettingsActivity.this.laneCaption.setText("No lane data while disconnected.");
                SettingsActivity.this.connectedNow = false;
                SettingsActivity.this.carKnown = false;
                SettingsActivity.this.renderState(false);
                Iterator it = SettingsActivity.this.laneStats.keySet().iterator();
                while (it.hasNext()) {
                    SettingsActivity.this.setLaneStat((String) it.next(), "-");
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void appendLog(String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        CharSequence text = this.logText.getText();
        if (text != null && text.length() != 0) {
            str = String.valueOf(text) + "\n" + str;
        }
        String[] strArrSplit = str.split("\n");
        if (strArrSplit.length > 60) {
            StringBuilder sb = new StringBuilder();
            for (int length = strArrSplit.length - 60; length < strArrSplit.length; length++) {
                sb.append(strArrSplit[length]).append('\n');
            }
            str = sb.toString();
        }
        this.logText.setText(str);
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        BtSpp btSpp = this.bt;
        if (btSpp != null) {
            btSpp.close();
        }
    }

    /**
     * The update card: which version is installed, whether GitHub has something newer, and a button
     * that checks - then turns into "Install vX.Y" so the same tap does the update. The check runs as
     * soon as the settings screen opens, so a new release is noticed without anyone going to look.
     */
    private LinearLayout buildUpdateCard() {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(0, dp(4.0f), 0, dp(4.0f));
        TextView label = new TextView(this);
        label.setText("KA2 Settings");
        label.setTextColor(this.TEXT);
        label.setTextSize(15.0f);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText("Check for update");
        button.setTextColor(this.TEXT);
        button.setBackground(roundRect(this.CHIP, 8));
        button.setPadding(dp(14.0f), dp(6.0f), dp(14.0f), dp(6.0f));
        row.addView(button);
        card.addView(row);
        TextView status = new TextView(this);
        status.setTextColor(this.MUTED);
        status.setTextSize(12.0f);
        status.setPadding(0, dp(6.0f), 0, 0);
        card.addView(status);
        Update.attach(this, status, button);
        return card;
    }
}
