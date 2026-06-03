package org.example.drive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.AEADBadTagException;

public class Auth {
    private static final String TAG = "Auth";
    private static final String PREFS_NAME = "drive";
    private static final String MASTER_KEY_ALIAS = "drive_master_key";
    private static final String KEY_SESSION_TOKEN = "token";
    private static final String KEY_CSRF_TOKEN = "csrf_token";
    private static SharedPreferences securePrefs;

    static public class Validator {
        private static final Pattern LOGIN_REGEX = Pattern.compile("^[a-zA-Z0-9._-]{4,50}$");
        private static final Pattern PASSWORD_REGEX = Pattern.compile("^[\\x20-\\x7E]{6,100}$");

        public static String validateCredentials(Context context, String login, String password) {
            Matcher loginMatcher = LOGIN_REGEX.matcher(login);
            if (!loginMatcher.matches())
                return context.getString(R.string.error_login_incorrect);

            Matcher passwordMatcher = PASSWORD_REGEX.matcher(password);
            if (!passwordMatcher.matches())
                return context.getString(R.string.error_password_incorrect);

            return null;
        }
    }

    public static void init(Context context) {
        Context appContext = context.getApplicationContext();
        securePrefs = createEncryptedPreferences(appContext);
        if (securePrefs != null)
            Log.d(TAG, "EncryptedSharedPreferences initialized");
        else
            Log.e(TAG, "Failed to initialize secure storage – using null (all methods will be no-op)");
    }

    private static SharedPreferences createEncryptedPreferences(Context appContext) {
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // ВАЖНО: проверяем, что само исключение — AEADBadTagException
            if (e instanceof AEADBadTagException) {
                Log.w(TAG, "AHEADBadTagException detected – cleaning corrupted storage and retrying");
                cleanCorruptedStorage(appContext);
                try {
                    // Повторная попытка с новым ключом и пустым хранилищем
                    MasterKey newMasterKey = new MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build();
                    return EncryptedSharedPreferences.create(
                            appContext,
                            PREFS_NAME,
                            newMasterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );
                } catch (GeneralSecurityException | IOException ex) {
                    Log.e(TAG, "Retry failed after cleaning", ex);
                    return null;
                }
            } else {
                Log.e(TAG, "Failed to initialize secure storage (non-AEAD error)", e);
                return null;
            }
        }
    }

    private static void cleanCorruptedStorage(Context context) {
        // 1. Удаляем файлы SharedPreferences
        try {
            // Самый надёжный способ через API
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
            // Принудительно удаляем XML-файлы
            File prefsDir = new File(context.getFilesDir().getParent(), "shared_prefs");
            if (prefsDir.exists()) {
                File prefsFile = new File(prefsDir, PREFS_NAME + ".xml");
                if (prefsFile.exists()) prefsFile.delete();
                File prefsBak = new File(prefsDir, PREFS_NAME + ".xml.bak");
                if (prefsBak.exists()) prefsBak.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting prefs files", e);
        }

        // 2. Удаляем ключ из AndroidKeyStore
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(MASTER_KEY_ALIAS);
            Log.d(TAG, "Master key deleted: " + MASTER_KEY_ALIAS);
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete master key", e);
        }
    }

    public static String getSessionToken() {
        return securePrefs == null ? null : securePrefs.getString(KEY_SESSION_TOKEN, null);
    }

    public static String getCsrfToken() {
        return securePrefs == null ? null : securePrefs.getString(KEY_CSRF_TOKEN, null);
    }

    public static void setSession(String setCookieHeader, String csrfToken) {
        if (securePrefs == null) return;
        String tokenCookie = setCookieHeader.split(";")[0].trim();
        securePrefs.edit()
                .putString(KEY_SESSION_TOKEN, tokenCookie.replace("token=", ""))
                .putString(KEY_CSRF_TOKEN, csrfToken)
                .apply();
    }

    public static void clearSession() {
        if (securePrefs == null) return;
        securePrefs.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_CSRF_TOKEN)
                .apply();
        Log.d(TAG, "Session cleared.");
    }
}