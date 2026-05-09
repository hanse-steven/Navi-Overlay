package com.navioverlay.car.engine;

import android.content.Context;
import android.text.TextUtils;
import com.navioverlay.car.R;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.services.ForegroundDetector;
import com.navioverlay.car.services.ForegroundState;

import static com.navioverlay.car.helpers.LocaleHelper.tr;

/**
 * Определяет, действительно ли выбранный навигатор сейчас является основным экраном.
 * Главный источник — AccessibilityService. UsageStats используется только как запасной вариант.
 */
public final class AppStateDetector {
    private final Context app;
    private final Prefs prefs;
    private long lastNavigatorVisibleAt = 0L;

    public AppStateDetector(Context context) {
        app = context.getApplicationContext();
        prefs = new Prefs(app);
    }

    public Result read() {
        if (!prefs.showOnlyWithTrigger()) {
            return new Result(true, "disabled-filter", false, tr(app, R.string.AppStateDetector_FilterDisabled));
        }

        long now = System.currentTimeMillis();
        long graceMs = Math.max(5000L, prefs.navGraceMs());
        boolean accEnabled = ForegroundDetector.isAccessibilityEnabled(app);

        String accessibilityPkg = TrackSnapshot.clean(ForegroundState.get());
        long accessibilityAge = ForegroundState.ageMs();
        boolean accessibilityFresh = !accessibilityPkg.isEmpty() && accessibilityAge < 5000L;

        String usagePkg = TrackSnapshot.clean(ForegroundDetector.currentByUsage(app));
        String self = app.getPackageName();

        if (prefs.isTrigger(accessibilityPkg)) {
            lastNavigatorVisibleAt = now;
            return new Result(true, accessibilityPkg, false, tr(app, R.string.AppStateDetector_NavOnScreen));
        }

        if (prefs.isTrigger(usagePkg)) {
            prefs.setLastTrigger(usagePkg);
            lastNavigatorVisibleAt = now;
            return new Result(true, usagePkg, false, tr(app, accEnabled
                ? R.string.AppStateDetector_NavViaUsage
                : R.string.AppStateDetector_NavViaUsageAccOff
            ));
        }

        String pkg = !accessibilityPkg.isEmpty() ? accessibilityPkg : usagePkg;
        boolean transientWindow = ForegroundState.isTransientSystemWindow()
                || ForegroundState.hasRecentTransientUi()
                || ForegroundState.isTransientPackage(accessibilityPkg)
                || ForegroundState.isTransientPackage(usagePkg);
        boolean selfOnScreen = (self.equals(accessibilityPkg) && accessibilityFresh)
                || self.equals(usagePkg);
        boolean accessibilityOtherApp = accEnabled
                && accessibilityFresh
                && !accessibilityPkg.isEmpty()
                && !ForegroundState.isTransientPackage(accessibilityPkg)
                && !prefs.isTrigger(accessibilityPkg)
                && !self.equals(accessibilityPkg);
        boolean usageOtherApp = !usagePkg.isEmpty()
                && !ForegroundState.isTransientPackage(usagePkg)
                && !prefs.isTrigger(usagePkg)
                && !self.equals(usagePkg);
        boolean canUseLastTriggerFallback = transientWindow
                || (TextUtils.isEmpty(accessibilityPkg) && TextUtils.isEmpty(usagePkg));

        if (transientWindow && now - lastNavigatorVisibleAt < graceMs) {
            return new Result(true, pkg, true, tr(app, R.string.AppStateDetector_TransientWindow));
        }

        if (accessibilityOtherApp) {
            return new Result(false, accessibilityPkg, false, tr(app, R.string.AppStateDetector_OtherAppOpen));
        }

        if (usageOtherApp) {
            return new Result(false, usagePkg, false, tr(app, accEnabled
                ? R.string.AppStateDetector_NavMinimized
                : R.string.AppStateDetector_UsageShowsOther
            ));
        }

        String lastTrigger = TrackSnapshot.clean(ForegroundState.lastTrigger());
        long lastTriggerAgeMs = ForegroundState.lastTriggerAgeMs();
        if (lastTrigger.isEmpty()) {
            lastTrigger = TrackSnapshot.clean(prefs.lastTriggerPackage());
            long persistedAt = prefs.lastTriggerAt();
            lastTriggerAgeMs = persistedAt > 0L ? Math.max(0L, now - persistedAt) : Long.MAX_VALUE;
        }
        if (canUseLastTriggerFallback && !lastTrigger.isEmpty() && lastTriggerAgeMs < graceMs && !selfOnScreen) {
            return new Result(true, lastTrigger, true, tr(app, accEnabled
                ? R.string.AppStateDetector_AccTempNoData
                : R.string.AppStateDetector_AccOffUseLast
            ));
        }

        if (!accEnabled) {
            return new Result(false, usagePkg, false, tr(app, R.string.AppStateDetector_AccDisabled));
        }

        return new Result(false, pkg, false, tr(app, R.string.AppStateDetector_NavNotOnScreen));
    }

    public static final class Result {
        public final boolean navigatorVisible;
        public final String foregroundPackage;
        public final boolean transientOverlay;
        public final String reason;
        public Result(boolean navigatorVisible, String foregroundPackage, boolean transientOverlay, String reason) {
            this.navigatorVisible = navigatorVisible;
            this.foregroundPackage = foregroundPackage == null ? "" : foregroundPackage;
            this.transientOverlay = transientOverlay;
            this.reason = reason == null ? "" : reason;
        }
    }
}
