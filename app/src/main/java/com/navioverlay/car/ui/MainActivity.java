package com.navioverlay.car.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.navioverlay.car.R;
import com.navioverlay.car.core.AppInfo;
import com.navioverlay.car.core.Constants;
import com.navioverlay.car.core.OverlayFonts;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;
import com.navioverlay.car.engine.AppStateDetector;
import com.navioverlay.car.engine.MusicStateDetector;
import com.navioverlay.car.engine.TrackSnapshot;
import com.navioverlay.car.helpers.LocaleHelper;
import com.navioverlay.car.overlay.TrackOverlayManager;
import com.navioverlay.car.services.ForegroundDetector;
import com.navioverlay.car.services.ForegroundState;
import com.navioverlay.car.services.MonitorService;
import com.navioverlay.car.services.NavigatorAccessibilityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = 0xFF030712;
    private static final int BG_2 = 0xFF071021;
    private static final int CARD = 0xFF101827;
    private static final int CARD_TOP = 0xFF18233A;
    private static final int CARD_2 = 0xFF1A2538;
    private static final int FIELD = 0xFF0B1220;
    private static final int STROKE = 0x4460708A;
    private static final int STROKE_SOFT = 0x223E4C61;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFC4CBD7;
    private static final int MUTED_2 = 0xFF8EA0B8;
    private static final int GREEN = 0xFF22C55E;
    private static final int WARN = 0xFFFFB020;

    private Prefs prefs;
    private LinearLayout root;
    private TextView statusView;
    private Switch enableSwitch;
    private Switch onlyTriggerSwitch;
    private Switch englishUiSwitch;


    @Override protected void attachBaseContext(Context base) {
        prefs = new Prefs(base);
        super.attachBaseContext(LocaleHelper.wrap(base, prefs.englishUi()));
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestPostNotificationsRuntimeOnly();
        buildUi();
        refreshStatus();
        if (prefs.enabled()) MonitorService.start(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs.enabled()) MonitorService.poke(this);
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackground(gradient(BG, BG_2, 0));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 20, 24, 20, 30);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView title = tv("Navi Overlay", 36, TEXT, Typeface.BOLD, Gravity.CENTER);
        title.setLetterSpacing(0.01f);
        root.addView(title, matchWrap());
        TextView subtitle = tv(tr(R.string.app_subtitle), 15, MUTED, Typeface.NORMAL, Gravity.CENTER);
        subtitle.setLineSpacing(Ui.dp(this, 2), 1.0f);
        root.addView(subtitle, matchWrap());
        space(root, 18);

        addStatusCard();
        addMainCard();
        addAppsCard();
        addSettingsCard();
        addPermissionsCard();
        addDiagnosticsCard();
    }

    private void addStatusCard() {
        LinearLayout c = card(tr(R.string.StatutCard_Title), tr(R.string.StatutCard_Description));
        statusView = tv("", 16, TEXT, Typeface.BOLD, Gravity.START);
        statusView.setLineSpacing(Ui.dp(this, 4), 1.0f);
        statusView.setBackground(Ui.stroke(FIELD, 1, STROKE_SOFT, 18, this));
        Ui.pad(statusView, 16, 14, 16, 14);
        c.addView(statusView, smallGapLp());
    }

    private void addMainCard() {
        LinearLayout c = card(tr(R.string.MainCard_Title), tr(R.string.MainCard_Description));
        enableSwitch = switchRow(c, tr(R.string.MainCard_EnableSwitch), prefs.enabled(), on -> {
            prefs.setEnabled(on);
            if (on) MonitorService.start(this); else { MonitorService.stop(this); TrackOverlayManager.hideNowForce(); }
            refreshStatus();
        });
        onlyTriggerSwitch = switchRow(c, tr(R.string.MainCard_OnlyTriggerSwitch), prefs.showOnlyWithTrigger(), on -> {
            prefs.setShowOnlyWithTrigger(on);
            TrackOverlayManager.hideNowForce();
        });
        englishUiSwitch = switchRow(c, tr(R.string.MainCard_EnglishUI), prefs.englishUi(), on -> {
            prefs.setEnglishUi(on);
            recreate();
        });
    }

    private void addAppsCard() {
        LinearLayout c = card(tr(R.string.AppsCard_Title), tr(R.string.AppsCard_Description));
        c.addView(menuButton(tr(R.string.AppsCard_NavigationButton_Title), tr(R.string.AppsCard_NavigationButton_Title), v -> showAppDialog(true)), smallGapLp());
        c.addView(menuButton(tr(R.string.AppsCard_MusicButton_Title), tr(R.string.AppsCard_MusicButton_Description), v -> showAppDialog(false)), smallGapLp());
    }

    private void addSettingsCard() {
        LinearLayout c = card(tr(R.string.SettingsCard_Title), tr(R.string.SettingsCard_Description));
        c.addView(menuButton(tr(R.string.SettingsCard_TextButton_Title), tr(R.string.SettingsCard_TextButton_Description), v -> showTextSettingsDialog()), smallGapLp());
        c.addView(menuButton(tr(R.string.SettingsCard_WindowButton_Title), tr(R.string.SettingsCard_WindowButton_Description), v -> showWindowSettingsDialog()), smallGapLp());
        c.addView(menuButton(tr(R.string.SettingsCard_ColorButton_Title), tr(R.string.SettingsCard_ColorButton_Description), v -> showColorSettingsDialog()), smallGapLp());
        c.addView(menuButton(tr(R.string.SettingsCard_ExtraButton_Title), tr(R.string.SettingsCard_ExtraButton_Description), v -> showAdditionalFeaturesDialog()), smallGapLp());
    }

    private void addPermissionsCard() {
        LinearLayout c = card(tr(R.string.PermissionsCard_Title), tr(R.string.PermissionsCard_Description));
        c.addView(menuButton(tr(R.string.PermissionsCard_Overlay_Title), tr(R.string.PermissionsCard_Overlay_Description), v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))), smallGapLp());
        c.addView(menuButton(tr(R.string.PermissionsCard_Notification_Title), tr(R.string.PermissionsCard_Notification_Description), v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), smallGapLp());
        c.addView(menuButton(tr(R.string.PermissionsCard_AppNotif_Title), tr(R.string.PermissionsCard_AppNotif_Description), v -> openPostNotificationsFlow()), smallGapLp());
        c.addView(menuButton(tr(R.string.PermissionsCard_Accessibility_Title), tr(R.string.PermissionsCard_Accessibility_Description), v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), smallGapLp());
        c.addView(menuButton(tr(R.string.PermissionsCard_Usage_Title), tr(R.string.PermissionsCard_Usage_Description), v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))), smallGapLp());
    }

    private void addDiagnosticsCard() {
        LinearLayout c = card(tr(R.string.DiagnosticsCard_Title), tr(R.string.DiagnosticsCard_Description));
        c.addView(menuButton(tr(R.string.DiagnosticsCard_CheckApp_Title), tr(R.string.DiagnosticsCard_CheckApp_Description), v -> {
            refreshStatus();
            Toast.makeText(this, ForegroundDetector.debugCurrent(this), Toast.LENGTH_LONG).show();
        }), smallGapLp());
        c.addView(menuButton(tr(R.string.DiagnosticsCard_Recover_Title), tr(R.string.DiagnosticsCard_Recover_Description), v -> checkAndRecover()), smallGapLp());
        c.addView(menuButton(tr(R.string.DiagnosticsCard_CopyReport_Title), tr(R.string.DiagnosticsCard_CopyReport_Description), v -> copyDiagnosticsReport()), smallGapLp());
    }

    private interface BoolCb { void set(boolean b); }
    private Switch switchRow(LinearLayout parent, String label, boolean checked, BoolCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.stroke(CARD_2, 1, STROKE_SOFT, 18, this));
        Ui.pad(row, 14, 12, 12, 12);

        TextView name = tv(label, 16, TEXT, Typeface.BOLD, Gravity.START);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.set(isChecked));
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 56), -2));
        parent.addView(row, smallGapLp());
        return sw;
    }

    private void showAppDialog(boolean triggers) {
        final Dialog dialog = fullDialog();
        LinearLayout box = dialogRoot();
        dialog.setContentView(box);

        box.addView(tv(tr(triggers ? R.string.AppDialog_Title_Nav : R.string.AppDialog_Title_Audio), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(tr(triggers ? R.string.AppDialog_Subtitle_Nav : R.string.AppDialog_Subtitle_Audio), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(box, 14);

        EditText search = new EditText(this);
        search.setHint(tr(R.string.AppDialog_SearchHint));
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(0xFF7F8EA3);
        search.setTextSize(16);
        search.setBackground(Ui.stroke(FIELD, 1, STROKE, 18, this));
        Ui.pad(search, 14, 12, 14, 12);
        box.addView(search, matchWrap());

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        quick.setClipChildren(false);
        quick.setClipToPadding(false);
        quick.setPadding(0, 0, 0, Ui.dp(this, 10));
        quick.addView(secondaryButton(tr(triggers ? R.string.AppDialog_Popular_Nav : R.string.AppDialog_Popular_Audio), null), quickButtonLp(1));
        quick.addView(secondaryButton(tr(R.string.AppDialog_Clear), null), quickButtonLp(1));
        Button popular = (Button) quick.getChildAt(0);
        Button clear = (Button) quick.getChildAt(1);
        popular.setTextSize(triggers ? 14 : 13);
        popular.setGravity(Gravity.CENTER);
        popular.setMaxLines(2);
        popular.setSingleLine(false);
        popular.setEllipsize(null);
        clear.setGravity(Gravity.CENTER);
        clear.setMaxLines(1);
        clear.setSingleLine(true);
        LinearLayout.LayoutParams quickLp = smallGapLp();
        quickLp.bottomMargin = Ui.dp(this, 14);
        box.addView(quick, quickLp);

        LinearLayout manualRow = new LinearLayout(this);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText manualPackage = new EditText(this);
        manualPackage.setHint(tr(R.string.AppDialog_ManualHint));
        manualPackage.setSingleLine(true);
        manualPackage.setTextColor(TEXT);
        manualPackage.setHintTextColor(0xFF7F8EA3);
        manualPackage.setTextSize(14);
        manualPackage.setBackground(Ui.stroke(FIELD, 1, STROKE, 16, this));
        Ui.pad(manualPackage, 12, 10, 12, 10);
        manualRow.addView(manualPackage, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button addManual = secondaryButton(tr(R.string.AppDialog_Add), null);
        manualRow.addView(addManual, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 48)));
        box.addView(manualRow, smallGapLp());

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        Button done = primaryButton(tr(R.string.AppDialog_Done), v -> dialog.dismiss());
        box.addView(done, btnLp());

        final Runnable[] render = new Runnable[1];
        render[0] = () -> renderApps(list, search.getText().toString(), triggers);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { render[0].run(); }
            public void afterTextChanged(Editable s) {}
        });
        popular.setOnClickListener(v -> {
            Set<String> s = triggers ? prefs.triggers() : prefs.players();
            if (triggers) Collections.addAll(s, Constants.DEFAULT_TRIGGER_PACKAGES);
            else Collections.addAll(s, Constants.DEFAULT_PLAYER_PACKAGES);
            if (triggers) prefs.setTriggers(s); else prefs.setPlayers(s);
            render[0].run();
        });
        clear.setOnClickListener(v -> { if (triggers) prefs.setTriggers(new HashSet<String>()); else prefs.setPlayers(new HashSet<String>()); render[0].run(); });
        addManual.setOnClickListener(v -> {
            String pkg = manualPackage.getText().toString().trim();
            if (pkg.length() < 3 || !pkg.contains(".")) {
                Toast.makeText(this, tr(R.string.AppDialog_InvalidPackage), Toast.LENGTH_SHORT).show();
                return;
            }
            Set<String> s = triggers ? prefs.triggers() : prefs.players();
            s.add(pkg);
            if (triggers) prefs.setTriggers(s); else prefs.setPlayers(s);
            manualPackage.setText("");
            render[0].run();
        });
        render[0].run();
        dialog.show();
        fitDialog(dialog, true);
    }

    private void renderApps(LinearLayout list, String q, boolean triggers) {
        list.removeAllViews();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Set<String> selected = triggers ? prefs.triggers() : prefs.players();
        ArrayList<AppInfo> apps = loadApps(selected);
        int count = 0;
        for (AppInfo a : apps) {
            if (!query.isEmpty()
                    && !a.label.toLowerCase(Locale.ROOT).contains(query)
                    && !a.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(a.label + "\n" + a.packageName);
            cb.setTextColor(TEXT);
            cb.setTextSize(15);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            cb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, this));
            cb.setChecked(selected.contains(a.packageName));
            Ui.pad(cb, 12, 10, 12, 10);
            cb.setOnCheckedChangeListener((v, on) -> {
                Set<String> s = triggers ? prefs.triggers() : prefs.players();
                if (on) s.add(a.packageName); else s.remove(a.packageName);
                if (triggers) prefs.setTriggers(s); else prefs.setPlayers(s);
            });
            list.addView(cb, smallGapLp());
            count++;
        }
        if (count == 0) list.addView(tv(tr(R.string.RenderApps_NothingFound), 15, MUTED, Typeface.NORMAL, Gravity.CENTER), btnLp());
    }

    private ArrayList<AppInfo> loadApps(Set<String> selected) {
        ArrayList<AppInfo> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(i, 0);
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo r : infos) {
            String p = r.activityInfo.packageName;
            String l = String.valueOf(r.loadLabel(pm));
            out.add(new AppInfo(l, p, selected.contains(p)));
            seen.add(p);
        }
        for (String p : selected) {
            if (p != null && !p.isEmpty() && !seen.contains(p)) out.add(new AppInfo(tr(R.string.LoadApps_AddedManually), p, true));
        }
        Collections.sort(out, (a, b) -> {
            if (a.selected != b.selected) return a.selected ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private void showTextSettingsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr(R.string.TextDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.TextDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addSeek(box, tr(R.string.TextDialog_TextSize), 1, 100, prefs.textSize(), v -> { prefs.setTextSize(v); TrackOverlayManager.refresh(this); });
        switchRow(box, tr(R.string.TextDialog_Bold), prefs.textBold(), on -> { prefs.setTextBold(on); TrackOverlayManager.refresh(this); });
        switchRow(box, tr(R.string.TextDialog_Shadow), prefs.textShadow(), on -> { prefs.setTextShadow(on); TrackOverlayManager.refresh(this); });
        box.addView(menuButton(tr(R.string.TextDialog_Font_Title), tr(R.string.TextDialog_Font_Description), v -> showFontDialog()), smallGapLp());
        box.addView(previewButton(tr(R.string.TextDialog_Preview), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr(R.string.TextDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showWindowSettingsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr(R.string.WindowDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.WindowDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addSeek(box, tr(R.string.WindowDialog_Alpha), 15, 100, prefs.windowAlpha(), v -> { prefs.setWindowAlpha(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr(R.string.WindowDialog_Corner), 0, 40, prefs.corner(), v -> { prefs.setCorner(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr(R.string.WindowDialog_Border), 0, 12, prefs.borderWidth(), v -> { prefs.setBorderWidth(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr(R.string.WindowDialog_PaddingX), 8, 48, prefs.paddingX(), v -> { prefs.setPaddingX(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr(R.string.WindowDialog_PaddingY), 6, 100, prefs.paddingY(), v -> { prefs.setPaddingY(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr(R.string.WindowDialog_DisplayTime), 0, 10, prefs.displayMs() / 1000, v -> { prefs.setDisplayMs(v * 1000); TrackOverlayManager.refresh(this); });
        box.addView(menuButton(tr(R.string.WindowDialog_Position_Title), tr(R.string.WindowDialog_Position_Description), v -> showPositionDialog()), smallGapLp());
        box.addView(menuButton(tr(R.string.WindowDialog_Preset_Title), tr(R.string.WindowDialog_Preset_Description), v -> showDesignPresetDialog()), smallGapLp());
        box.addView(previewButton(tr(R.string.WindowDialog_Preview), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr(R.string.WindowDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showColorSettingsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr(R.string.ColorDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.ColorDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(menuButton(tr(R.string.ColorDialog_Window_Title), tr(R.string.ColorDialog_Window_Description), v -> showColorDialog(0)), smallGapLp());
        box.addView(menuButton(tr(R.string.ColorDialog_Border_Title), tr(R.string.ColorDialog_Border_Description), v -> showBorderColorDialog(false)), smallGapLp());
        box.addView(menuButton(tr(R.string.ColorDialog_Controls_Title), tr(R.string.ColorDialog_Controls_Description), v -> showBorderColorDialog(true)), smallGapLp());
        box.addView(menuButton(tr(R.string.ColorDialog_Artist_Title), tr(R.string.ColorDialog_Artist_Description), v -> showColorDialog(1)), smallGapLp());
        box.addView(menuButton(tr(R.string.ColorDialog_Track_Title), tr(R.string.ColorDialog_Track_Description), v -> showColorDialog(2)), smallGapLp());
        box.addView(previewButton(tr(R.string.ColorDialog_Preview), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr(R.string.ColorDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showAdditionalFeaturesDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr(R.string.ExtraDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.ExtraDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        switchRow(box, tr(R.string.ExtraDialog_Controls), prefs.featureControls(), on -> { prefs.setFeatureControls(on); TrackOverlayManager.refresh(this); });
        switchRow(box, tr(R.string.ExtraDialog_SwipeTracks), prefs.featureSwipeTracks(), on -> prefs.setFeatureSwipeTracks(on));
        switchRow(box, tr(R.string.ExtraDialog_Snap), prefs.featureSnap(), on -> prefs.setFeatureSnap(on));
        switchRow(box, tr(R.string.ExtraDialog_VolumeDim), prefs.featureVolumeDim(), on -> prefs.setFeatureVolumeDim(on));
        switchRow(box, tr(R.string.ExtraDialog_Floating), prefs.featureFloating(), on -> prefs.setFeatureFloating(on));
        switchRow(box, tr(R.string.ExtraDialog_AlbumArt), prefs.featureAlbumArt(), on -> { prefs.setFeatureAlbumArt(on); TrackOverlayManager.refresh(this); });
        switchRow(box, tr(R.string.ExtraDialog_HideWithNav), prefs.featureHideWithNavigation(), on -> prefs.setFeatureHideWithNavigation(on));
        switchRow(box, tr(R.string.ExtraDialog_FixedWindow), prefs.featureFixedWindow(), on -> {
            prefs.setFeatureFixedWindow(on);
            TrackOverlayManager.refresh(this);
        });
        TextView fixedHint = tv(tr(R.string.ExtraDialog_FixedWindow_Hint), 12, MUTED_2, Typeface.NORMAL, Gravity.START);
        LinearLayout.LayoutParams fixedHintLp = matchWrap();
        fixedHintLp.setMargins(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), 0);
        box.addView(fixedHint, fixedHintLp);
        switchRow(box, tr(R.string.ExtraDialog_AlwaysShow), prefs.displayWhilePlaying(), on -> {
            prefs.setDisplayWhilePlaying(on);
            TrackOverlayManager.refresh(this);
        });
        box.addView(previewButton(tr(R.string.ExtraDialog_Preview), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr(R.string.ExtraDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }
    private interface IntCb { void set(int v); }
    private void addSeek(LinearLayout box, String label, int min, int max, int value, IntCb cb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, this));
        Ui.pad(wrap, 14, 10, 14, 10);
        TextView t = tv(label + ": " + value, 15, TEXT, Typeface.BOLD, Gravity.START);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { int v = min + progress; t.setText(label + ": " + v); cb.set(v); }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(t, matchWrap());
        wrap.addView(sb, matchWrap());
        box.addView(wrap, smallGapLp());
    }

    private void showColorDialog(int target) {
        final Dialog dialog = fullDialog();
        LinearLayout box = dialogRoot();
        dialog.setContentView(box);
        box.addView(tv(colorTargetTitle(target), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(tr(R.string.ColorPickerDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(box, 12);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        int[] colors = target == 0 ? windowPalette() : textPalette();
        for (int col : colors) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView swatch = new TextView(this);
            swatch.setText(" ");
            swatch.setBackground(Ui.stroke(col, 2, 0x88FFFFFF, 16, this));
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54));
            cell.addView(swatch, swLp);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = -2;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6));
            grid.addView(cell, lp);
            cell.setOnClickListener(v -> {
                if (target == 0) prefs.setBgColor(col);
                else if (target == 1) prefs.setArtistColor(col);
                else prefs.setTitleColor(col);
                TrackOverlayManager.test(this);
            });
        }
        box.addView(grid, matchWrap());
        box.addView(primaryButton(tr(R.string.ColorPickerDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, false);
    }

    private String colorTargetTitle(int target) {
        if (target == 1) return tr(R.string.ColorTargetTitle_Artist);
        if (target == 2) return tr(R.string.ColorTargetTitle_Title);
        return tr(R.string.ColorTargetTitle_Window);
    }

    private int[] textPalette() {
        return new int[]{0xFFFFFFFF, 0xFFE5E7EB, 0xFFCBD5E1, 0xFF94A3B8,
                0xFF00D5FF, 0xFF38BDF8, 0xFF60A5FA, 0xFF818CF8,
                0xFF22C55E, 0xFF84CC16, 0xFFFFD166, 0xFFFFA94D,
                0xFFFF6B6B, 0xFFFF4D9D, 0xFFC084FC, 0xFF2DD4BF};
    }

    private int[] windowPalette() {
        return new int[]{0xFF020617, 0xFF0B1220, 0xFF111827, 0xFF1F2937,
                0xFF0F172A, 0xFF172554, 0xFF082F49, 0xFF042F2E,
                0xFF052E16, 0xFF3F2A05, 0xFF431407, 0xFF450A0A,
                0xFF312E81, 0xFF4A044E, 0xFF1E1B4B, 0xFF18181B};
    }

    private void showBorderColorDialog(boolean controls) {
        final Dialog dialog = fullDialog();
        LinearLayout box = dialogRoot();
        dialog.setContentView(box);
        box.addView(tv(controls ? tr(R.string.BorderColorDialog_Controls_Title) : tr(R.string.BorderColorDialog_Window_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(controls ? tr(R.string.BorderColorDialog_Controls_Description) : tr(R.string.BorderColorDialog_Window_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(box, 12);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (int col : borderPalette()) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView swatch = new TextView(this);
            swatch.setText(" ");
            swatch.setBackground(Ui.stroke(col, 2, 0xAAFFFFFF, 16, this));
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54));
            cell.addView(swatch, swLp);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = -2;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6));
            grid.addView(cell, lp);
            cell.setOnClickListener(v -> {
                if (controls) prefs.setControlsBorderColor(col); else prefs.setBorderColor(col);
                TrackOverlayManager.test(this);
            });
        }
        box.addView(grid, matchWrap());
        box.addView(primaryButton(tr(R.string.BorderColorDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, false);
    }

    private int[] borderPalette() {
        return new int[]{
                0x00FFFFFF, 0x33FFFFFF, 0x66FFFFFF, 0xAAFFFFFF,
                0xFF00D5FF, 0xFF38BDF8, 0xFF60A5FA, 0xFF818CF8,
                0xFF22C55E, 0xFF84CC16, 0xFFFFD166, 0xFFFFA726,
                0xFFFF6B6B, 0xFFFF4D9D, 0xFFC084FC, 0xFF2DD4BF
        };
    }

    private void showFontDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr(R.string.FontDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.FontDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        ArrayList<LinearLayout> rows = new ArrayList<>();
        ArrayList<TextView> names = new ArrayList<>();
        ArrayList<TextView> samples = new ArrayList<>();
        ArrayList<TextView> hints = new ArrayList<>();

        for (int i = 0; i < OverlayFonts.count(); i++) {
            final int fontIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.stroke(CARD_2, 1, fontIndex == prefs.textFont() ? prefs.accentColor() : 0x223A4658, 16, this));
            Ui.pad(row, 16, 14, 16, 14);

            TextView name = tv(OverlayFonts.nameAt(fontIndex), 16, fontIndex == prefs.textFont() ? prefs.accentColor() : TEXT, Typeface.BOLD, Gravity.START);
            TextView sample = tv(tr(R.string.FontDialog_Sample), 18, TEXT, Typeface.NORMAL, Gravity.START);
            sample.setTypeface(OverlayFonts.resolve(this, fontIndex, prefs.textBold()));
            sample.getPaint().setFakeBoldText(prefs.textBold());
            TextView hint = tv(fontIndex == 0 ? tr(R.string.FontDialog_Hint_System) : tr(R.string.FontDialog_Hint_Sample), 12, MUTED_2, Typeface.NORMAL, Gravity.START);

            row.addView(name, matchWrap());
            row.addView(sample, matchWrap());
            row.addView(hint, matchWrap());
            box.addView(row, smallGapLp());
            rows.add(row);
            names.add(name);
            samples.add(sample);
            hints.add(hint);

            row.setOnClickListener(v -> {
                prefs.setTextFont(fontIndex);
                TrackOverlayManager.refresh(this);
                for (int j = 0; j < rows.size(); j++) {
                    boolean selected = j == prefs.textFont();
                    rows.get(j).setBackground(Ui.stroke(CARD_2, 1, selected ? prefs.accentColor() : 0x223A4658, 16, this));
                    names.get(j).setTextColor(selected ? prefs.accentColor() : TEXT);
                    samples.get(j).setTypeface(OverlayFonts.resolve(this, j, prefs.textBold()));
                    samples.get(j).getPaint().setFakeBoldText(prefs.textBold());
                    hints.get(j).setText(selected
                            ? tr(R.string.FontDialog_Hint_Selected)
                            : (j == 0 ? tr(R.string.FontDialog_Hint_System) : tr(R.string.FontDialog_Hint_Sample)));
                }
            });
        }

        outer.addView(primaryButton(tr(R.string.FontDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showPositionDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr(R.string.PositionDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.PositionDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{
                tr(R.string.PositionDialog_TopLeft),
                tr(R.string.PositionDialog_TopCenter),
                tr(R.string.PositionDialog_TopRight),
                tr(R.string.PositionDialog_Center),
                tr(R.string.PositionDialog_BottomLeft),
                tr(R.string.PositionDialog_BottomCenter),
                tr(R.string.PositionDialog_BottomRight)
        };
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.position();

        for (int i = 0; i < labels.length; i++) {
            final int positionIndex = i;
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(positionIndex == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setPosition(positionIndex);
                TrackOverlayManager.refresh(this);
                for (int j = 0; j < radios.size(); j++) radios.get(j).setChecked(j == positionIndex);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }

        box.addView(previewButton(tr(R.string.PositionDialog_Preview), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr(R.string.PositionDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showDesignPresetDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr(R.string.DesignPresetDialog_Title), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr(R.string.DesignPresetDialog_Description), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] names = new String[]{
                tr(R.string.DesignPresetDialog_Classic),
                tr(R.string.DesignPresetDialog_Minimal),
                tr(R.string.DesignPresetDialog_Glass),
                tr(R.string.DesignPresetDialog_CarUI),
                tr(R.string.DesignPresetDialog_Soft),
                tr(R.string.DesignPresetDialog_Contrast),
                tr(R.string.DesignPresetDialog_Capsule),
                tr(R.string.DesignPresetDialog_Premium),
                tr(R.string.DesignPresetDialog_Spikes),
                tr(R.string.DesignPresetDialog_Orbit)
        };
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.designPreset();

        for (int i = 0; i < names.length; i++) {
            final int presetIndex = i;
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(presetIndex == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setDesignPreset(presetIndex);
                TrackOverlayManager.refresh(this);
                for (int j = 0; j < radios.size(); j++) radios.get(j).setChecked(j == presetIndex);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }

        box.addView(previewButton(tr(R.string.DesignPresetDialog_Preview), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr(R.string.DesignPresetDialog_Done), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void copyDiagnosticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Navi Overlay diagnostics\n");
        sb.append("enabled=").append(prefs.enabled()).append("\n");
        sb.append("overlay=").append(TrackOverlayManager.canDraw(this)).append("\n");
        sb.append("notificationListener=").append(notificationAccessEnabled(this)).append("\n");
        sb.append("postNotifications=").append(hasPostNotificationsPermission()).append("\n");
        sb.append("accessibility=").append(ForegroundDetector.isAccessibilityEnabled(this)).append("\n");
        sb.append("accessibilityServiceConnected=").append(NavigatorAccessibilityService.isServiceConnected()).append("\n");
        sb.append("accessibilityConnectedAgeSec=").append(ageSeconds(NavigatorAccessibilityService.connectedAt())).append("\n");
        sb.append("accessibilityLastEventAgeSec=").append(ageSeconds(NavigatorAccessibilityService.lastEventAt())).append("\n");
        sb.append("accessibilityLastRootReadAgeSec=").append(ageSeconds(NavigatorAccessibilityService.lastRootReadAt())).append("\n");
        sb.append("usageAccess=").append(ForegroundDetector.hasUsageAccess(this)).append("\n");
        sb.append("persistedLastTrigger=").append(prefs.lastTriggerPackage()).append("\n");
        sb.append("persistedLastTriggerAgeSec=").append(ageSeconds(prefs.lastTriggerAt())).append("\n");
        sb.append("accessibilityPackage=").append(ForegroundState.get()).append("\n");
        sb.append("usagePackage=").append(ForegroundDetector.debugCurrent(this)).append("\n");
        try {
            AppStateDetector.Result appState = new AppStateDetector(this).read();
            sb.append("navigatorVisible=").append(appState.navigatorVisible).append("\n");
            sb.append("foregroundPackage=").append(appState.foregroundPackage).append("\n");
            sb.append("navReason=").append(appState.reason).append("\n");
        } catch (Throwable t) { sb.append("appStateError=").append(t.getClass().getSimpleName()).append("\n"); }
        try {
            TrackSnapshot track = new MusicStateDetector(this).read();
            sb.append("musicPlaying=").append(track.playing).append("\n");
            sb.append("trackSourceType=").append(track.sourceType).append("\n");
            sb.append("trackPackage=").append(track.sourcePackage).append("\n");
            sb.append("artist=").append(track.artist).append("\n");
            sb.append("title=").append(track.title).append("\n");
            sb.append("featureControls=").append(prefs.featureControls()).append("\n");
            sb.append("featureSwipeTracks=").append(prefs.featureSwipeTracks()).append("\n");
            sb.append("featureSnap=").append(prefs.featureSnap()).append("\n");
            sb.append("featureVolumeDim=").append(prefs.featureVolumeDim()).append("\n");
            sb.append("featureFloating=").append(prefs.featureFloating()).append("\n");
            sb.append("designPreset=").append(prefs.designPreset()).append("\n");
            sb.append("textFont=").append(OverlayFonts.nameAt(prefs.textFont())).append("\n");
            sb.append("borderColor=#").append(Integer.toHexString(prefs.borderColor())).append("\n");
            sb.append("borderWidth=").append(prefs.borderWidth()).append("\n");
            sb.append("controlsBorderColor=#").append(Integer.toHexString(prefs.controlsBorderColor())).append("\n");
        } catch (Throwable t) { sb.append("musicStateError=").append(t.getClass().getSimpleName()).append("\n"); }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Navi Overlay diagnostics", sb.toString()));
        Toast.makeText(this, tr(R.string.Diagnostics_Copied), Toast.LENGTH_SHORT).show();
    }

    private long ageSeconds(long ts) {
        if (ts <= 0L) return -1L;
        long delta = System.currentTimeMillis() - ts;
        return Math.max(0L, delta / 1000L);
    }

    private void refreshStatus() {
        if (statusView == null) return;
        boolean overlay = TrackOverlayManager.canDraw(this);
        boolean usage = ForegroundDetector.hasUsageAccess(this);
        boolean acc = ForegroundDetector.isAccessibilityEnabled(this);
        boolean accConnected = NavigatorAccessibilityService.isServiceConnected();
        boolean notif = notificationAccessEnabled(this);
        // "Уведомления приложения" не должны ломать общий статус разрешений:
        // для Android 12 и ниже их физически нет, а для Android 13+ это отдельная
        // дополнительная опция, не критичная для базовой работы overlay-движка.
        boolean allPerms = overlay && usage && acc && notif;

        AppStateDetector.Result appState;
        try { appState = new AppStateDetector(this).read(); } catch (Throwable t) { appState = null; }
        TrackSnapshot track;
        try { track = new MusicStateDetector(this).read(); } catch (Throwable t) { track = TrackSnapshot.empty(); }

        boolean enabled = prefs.enabled();
        boolean navOk = appState != null && appState.navigatorVisible;
        boolean musicOk = track != null && track.playing && track.hasText();

        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendStatusLine(sb, tr(R.string.Status_Label_Status), enabled ? tr(R.string.Status_Enabled) : tr(R.string.Status_Disabled), enabled);
        appendStatusLine(sb, tr(R.string.Status_Label_Permissions), allPerms ? tr(R.string.Status_Permissions_Granted) : tr(R.string.Status_Permissions_NotAll), allPerms);
        appendStatusLine(sb, tr(R.string.Status_Label_Navigator), navOk ? tr(R.string.Status_Running) : tr(R.string.Status_No), navOk);
        appendStatusLine(sb, tr(R.string.Status_Label_Music), musicOk ? tr(R.string.Status_Running) : tr(R.string.Status_No), musicOk);
        if (enabled && acc && !accConnected) {
            appendStatusLine(sb, tr(R.string.Status_Label_AccessibilityService), tr(R.string.Status_NoConnection), false);
        }
        statusView.setText(sb);
        statusView.setTextColor(TEXT);
        if (enableSwitch != null && enableSwitch.isChecked() != prefs.enabled()) enableSwitch.setChecked(prefs.enabled());
        if (onlyTriggerSwitch != null && onlyTriggerSwitch.isChecked() != prefs.showOnlyWithTrigger()) onlyTriggerSwitch.setChecked(prefs.showOnlyWithTrigger());
        if (englishUiSwitch != null && englishUiSwitch.isChecked() != prefs.englishUi()) englishUiSwitch.setChecked(prefs.englishUi());
    }

    private void checkAndRecover() {
        if (!prefs.enabled()) {
            Toast.makeText(this, tr(R.string.Recover_EnableFirst), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TrackOverlayManager.canDraw(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!notificationAccessEnabled(this)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            return;
        }
        if (!ForegroundDetector.isAccessibilityEnabled(this)) {
            Toast.makeText(this, tr(R.string.Recover_AccessibilityOff), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (prefs.showOnlyWithTrigger() && !ForegroundDetector.hasUsageAccess(this)) {
            Toast.makeText(this, tr(R.string.Recover_UsageAccessRequired), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        if (!NavigatorAccessibilityService.isServiceConnected()) {
            Toast.makeText(this, tr(R.string.Recover_AccessibilityNotResponding), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        MonitorService.poke(this);
        refreshStatus();
        Toast.makeText(this, tr(R.string.Recover_NoProblems), Toast.LENGTH_SHORT).show();
    }

    private void appendStatusLine(SpannableStringBuilder sb, String label, String value, boolean ok) {
        int start = sb.length();
        sb.append(label).append(value).append("\n");
        int valueStart = start + label.length();
        int valueEnd = valueStart + value.length();
        sb.setSpan(new ForegroundColorSpan(ok ? GREEN : 0xFFFF4D4D), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void requestPostNotificationsRuntimeOnly() {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3301);
            }
        } catch (Throwable ignored) {}
    }

    private void openPostNotificationsFlow() {
        try {
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(this, tr(R.string.PostNotif_NotRequired), Toast.LENGTH_LONG).show();
                return;
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3301);
                return;
            }
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {
                Toast.makeText(this, tr(R.string.PostNotif_OpenFailed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasPostNotificationsPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationAccessEnabled(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            return s != null && s.toLowerCase(Locale.ROOT).contains(c.getPackageName().toLowerCase(Locale.ROOT));
        } catch (Throwable t) { return false; }
    }

    private LinearLayout card(String title, String desc) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(cardBg(24));
        Ui.pad(c, 18, 17, 18, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, Ui.dp(this, 15));
        root.addView(c, lp);

        TextView h = tv(title, 22, TEXT, Typeface.BOLD, Gravity.CENTER);
        h.setLetterSpacing(0.01f);
        c.addView(h, matchWrap());
        if (!TextUtils.isEmpty(desc)) {
            TextView d = tv(desc, 14, MUTED, Typeface.NORMAL, Gravity.CENTER);
            d.setLineSpacing(Ui.dp(this, 2), 1.0f);
            LinearLayout.LayoutParams dlp = matchWrap();
            dlp.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 6));
            c.addView(d, dlp);
        }
        return c;
    }

    private View menuButton(String title, String desc, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(menuBg());
        Ui.pad(row, 14, 12, 12, 12);

        TextView accent = new TextView(this);
        accent.setBackground(Ui.round(prefs.accentColor(), 6, this));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(this, 4), Ui.dp(this, 38));
        alp.setMargins(0, 0, Ui.dp(this, 12), 0);
        row.addView(accent, alp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = tv(title, 16, TEXT, Typeface.BOLD, Gravity.START);
        titleView.setSingleLine(false);
        texts.addView(titleView, matchWrap());
        if (!TextUtils.isEmpty(desc)) {
            TextView descView = tv(desc, 13, MUTED_2, Typeface.NORMAL, Gravity.START);
            descView.setLineSpacing(Ui.dp(this, 1), 1.0f);
            texts.addView(descView, matchWrap());
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = tv("›", 32, prefs.accentColor(), Typeface.BOLD, Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(this, 30), -2));
        row.setOnClickListener(listener);
        return row;
    }

    private Button primaryButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(gradientButton(prefs.accentColor(), lighten(prefs.accentColor(), 30), 18));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private Button previewButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(gradientButton(0xFF2DDFFF, 0xFFC85BFF, 18));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private Button secondaryButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setIncludeFontPadding(false);
        b.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        b.setBackground(Ui.stroke(CARD_2, 1, STROKE, 16, this));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private TextView tv(String s, int sp, int color, int style, int gravity) {
        TextView t = Ui.tv(this, s, sp, color, style);
        t.setGravity(gravity);
        return t;
    }

    private GradientDrawable gradient(int top, int bottom, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(Ui.dp(this, radius));
        return g;
    }

    private GradientDrawable gradientButton(int left, int right, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
        g.setCornerRadius(Ui.dp(this, radius));
        return g;
    }

    private GradientDrawable cardBg(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CARD_TOP, CARD});
        g.setCornerRadius(Ui.dp(this, radius));
        g.setStroke(Ui.dp(this, 1), STROKE_SOFT);
        return g;
    }

    private GradientDrawable menuBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF1A263B, 0xFF131E30});
        g.setCornerRadius(Ui.dp(this, 18));
        g.setStroke(Ui.dp(this, 1), STROKE_SOFT);
        return g;
    }

    private int lighten(int color, int amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, Color.red(color) + amount);
        int g = Math.min(255, Color.green(color) + amount);
        int b = Math.min(255, Color.blue(color) + amount);
        return Color.argb(a, r, g, b);
    }

    private Dialog fullDialog() {
        Dialog d = new Dialog(this);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private LinearLayout dialogRoot() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(gradient(BG, BG_2, 0));
        Ui.pad(box, 18, 20, 18, 16);
        return box;
    }

    private void fitDialog(Dialog d, boolean fullHeight) {
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, fullHeight ? -1 : -2);
        }
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams btnLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)); lp.setMargins(0, Ui.dp(this, 12), 0, 0); return lp; }
    private LinearLayout.LayoutParams smallGapLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, Ui.dp(this, 9), 0, 0); return lp; }
    private LinearLayout.LayoutParams weightLp(float weight) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 58), weight); lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), 0); return lp; }
    private LinearLayout.LayoutParams quickButtonLp(float weight) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), weight); lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 10)); return lp; }
    private void space(LinearLayout parent, int dp) { Space s = new Space(this); parent.addView(s, new LinearLayout.LayoutParams(1, Ui.dp(this, dp))); }

    private String tr(int traductionKey) {return getString(traductionKey);}
}
