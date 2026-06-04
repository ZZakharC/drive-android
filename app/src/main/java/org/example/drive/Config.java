package org.example.drive;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

public class Config {
    public static final String SERVER_URL_DEFAULT = "https://192.168.0.159:3000/"; // Адрес сервера по умолчанию
    public static String SERVER_URL = "https://192.168.0.159:3000/"; // Адрес сервера по умолчанию
    public static final int MIN_LOGIN_LENGTH = 4; // Мин. длина логина
    public static final int MIN_PASSWORD_LENGTH = 6; // Мин. длина пароля
    private static final String PREFS = "server_config";
    private static final String KEY_BASE_URL = "base_url";

    public static void init(Context context) {
        SERVER_URL = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, SERVER_URL_DEFAULT);
    }

    public static void setServerUrl(Context context, String url) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, url)
                .apply();
        SERVER_URL = url;
    }

    // Права доступа (битовая маска)
    public static class Rule {
        public static final int READ = 1; // Чтение
        public static final int CHANGE = 2; // Изменения
        public static final int ADMIN = 4; // Доступ к админ панели
    }

    public static final Map<String, Integer> ICONS_MAP = new HashMap<>();

    static {
        ICONS_MAP.put("default", R.drawable.file);
        ICONS_MAP.put("folder", R.drawable.folder);
        ICONS_MAP.put("exe", R.drawable.exe);
        ICONS_MAP.put("jpg", R.drawable.jpg);
        ICONS_MAP.put("jpeg", R.drawable.jpg);
        ICONS_MAP.put("html", R.drawable.html);
        ICONS_MAP.put("htm", R.drawable.html);
        ICONS_MAP.put("iso", R.drawable.iso);
        ICONS_MAP.put("txt", R.drawable.txt);
        ICONS_MAP.put("mp4", R.drawable.mp4);
        ICONS_MAP.put("pdf", R.drawable.pdf);
        ICONS_MAP.put("zip", R.drawable.zip);
        ICONS_MAP.put("svg", R.drawable.svg);
        ICONS_MAP.put("rar", R.drawable.rar);
    }
}