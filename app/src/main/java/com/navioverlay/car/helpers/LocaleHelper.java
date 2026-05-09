package com.navioverlay.car.helpers;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public final class LocaleHelper {

    public static String tr(Context c, int traductionKey) {return c.getString(traductionKey);}

    public static Context wrap(Context base, boolean isEnglish) {
        Locale locale = isEnglish ? Locale.ENGLISH : new Locale("ru");
        Locale.setDefault(locale);

        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);

        return base.createConfigurationContext(cfg);
    }
}
