package org.example.drive;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Server {
    public static final String TAG = "Network";

    // Отключаем проверку SSL сертификатов
    static {
        try {
            @SuppressLint("CustomX509TrustManager") TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @SuppressLint("TrustAllX509TrustManager")
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    @SuppressLint("TrustAllX509TrustManager")
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            Log.w(TAG, "SSL verification DISABLED");
        } catch (Exception e) {
            Log.e(TAG, "SSL context init failed", e);
        }
    }

    // ========== GET запрос ==========
    public static void get(String urlPath, Callback callback) {
        new RequestTask("GET", urlPath, null, callback).execute();
    }

    // ========== PUT запрос ==========
    public static void put(String urlPath, Callback callback) {
        new RequestTask("PUT", urlPath, null, callback).execute();
    }

    // ========== POST запрос ==========
    public static void post(String urlPath, JSONObject body, Callback callback) {
        new RequestTask("POST", urlPath, body, callback).execute();
    }

    // ========== DELETE запрос ==========
    public static void delete(String urlPath, Callback callback) {
        new RequestTask("DELETE", urlPath, null, callback).execute();
    }

    public static void uploadFile(Context context, Uri uri, String path, String fileName) throws Exception {
        String boundary = "----AndroidBoundary" + System.currentTimeMillis();
        String LINE_END = "\r\n";

        URL url = new URL(Config.SERVER_URL + "drive" + path);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setChunkedStreamingMode(0);

        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("ENCTYPE", "multipart/form-data");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Accept", "application/json");

        String sessionToken = Auth.getSessionToken();
        String csrfToken = Auth.getCsrfToken();

        if (sessionToken != null)
            conn.setRequestProperty("Cookie", "token=" + sessionToken);

        if (csrfToken != null)
            conn.setRequestProperty("X-CSRF-Token", csrfToken);

        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null)
            throw new RuntimeException("Cannot open InputStream for URI: " + uri);

        DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());

        try {
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            // ===== FILE PART HEADER =====
            outputStream.writeBytes("--" + boundary + LINE_END);
            outputStream.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename*=UTF-8''" + encodedFileName + LINE_END
            );
            outputStream.writeBytes("Content-Type: application/octet-stream" + LINE_END);
            outputStream.writeBytes(LINE_END);

            // ===== FILE DATA =====
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1)
                outputStream.write(buffer, 0, bytesRead);

            outputStream.writeBytes(LINE_END);

            // ===== END BOUNDARY =====
            outputStream.writeBytes("--" + boundary + "--" + LINE_END);
            outputStream.flush();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Code: " + responseCode);
        } finally {
            inputStream.close();
            outputStream.close();
            conn.disconnect();
        }
    }

    public static void downloadFile(Context context, String urlStr) {
        new Thread(() -> {
            // Проверка разрешений уведомлений
            if (!Utils.hasNotificationPermission(context)) {
                Log.d(TAG, "NO NOTIFICATION PERMISSION");
                return;
            }

            String channelId = "downloads_channel";
            int notificationId = 1001;
            NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(channel);
            }

            NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                    .setContentTitle(context.getString(R.string.download_file))
                    .setContentText(context.getString(R.string.preparation))
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, 0, false);

            nm.notify(notificationId, builder.build());

            HttpURLConnection connection = null;
            InputStream input = null;
            OutputStream output = null;
            Uri fileUri = null;
            String fileName = null;

            try {
                URL url = new URL(Config.SERVER_URL + "download" + urlStr);
                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(Config.TIMEOUT);
                connection.setReadTimeout(Config.TIMEOUT);
                connection.setRequestProperty("Cookie", "token=" + Auth.getSessionToken());

                connection.connect();

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK)
                    throw new RuntimeException("HTTP error: " + code);

                int fileLength = connection.getContentLength();

                fileName = Uri.parse(urlStr).getLastPathSegment();

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, URLConnection.guessContentTypeFromName(fileName));
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                fileUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (fileUri == null)
                    throw new RuntimeException("MediaStore insert failed");

                input = connection.getInputStream();
                output = context.getContentResolver().openOutputStream(fileUri);

                byte[] buffer = new byte[8192];
                int len;
                long downloaded = 0;

                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                    downloaded += len;

                    if (fileLength > 0) {
                        int progress = (int) ((downloaded * 100) / fileLength);

                        builder.setProgress(100, progress, false)
                                .setContentText(progress + "%");

                        nm.notify(notificationId, builder.build());
                    }
                }

                output.flush();

                // Открытие файла по клику
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(fileUri,
                    URLConnection.guessContentTypeFromName(fileName));
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                builder.setContentTitle(context.getString(R.string.download_done))
                    .setContentText(fileName)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setProgress(0, 0, false)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

                nm.notify(notificationId, builder.build());

            } catch (Exception e) {
                builder.setContentTitle(context.getString(R.string.download_error))
                    .setContentText(e.getMessage())
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setProgress(0, 0, false);

                nm.notify(notificationId, builder.build());
                Log.e(TAG, "ERROR", e);
            } finally {
                try {
                    if (input != null) input.close();
                    if (output != null) output.close();
                } catch (Exception ignored) {}

                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    public static void logout() {
        Server.delete("auth/me", new Server.Callback() {
            @Override
            public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {}
            @Override
            public void onError(String errorMessage) {}
        });
    }

    // ========== AsyncTask ==========
    private static class RequestTask extends AsyncTask<Void, Void, NetworkResponse> {
        private final String method;
        private final String urlPath;
        private final JSONObject body;
        private final Callback callback;

        RequestTask(String method, String urlPath, JSONObject body, Callback callback) {
            this.method = method;
            this.urlPath = urlPath;
            this.body = body;
            this.callback = callback;
        }

        @Override
        protected NetworkResponse doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                String fullUrl = Config.SERVER_URL + urlPath;

                URL url = new URL(fullUrl);
                connection = (HttpURLConnection) url.openConnection();

                // Настройки
                connection.setRequestMethod(method);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(Config.TIMEOUT);
                connection.setReadTimeout(Config.TIMEOUT);
                connection.setDoInput(true);

                // Отправляем cookie и CSRF
                String sessionToken = Auth.getSessionToken();
                String csrfToken = Auth.getCsrfToken();
                if (sessionToken != null)
                    connection.setRequestProperty("Cookie", "token=" + sessionToken);

                if (csrfToken != null)
                    connection.setRequestProperty("X-CSRF-Token", csrfToken);

                // Для POST добавляем тело
                if ("POST".equals(method) && body != null) {
                    connection.setDoOutput(true);
                    String jsonBody = body.toString();

                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }

                // Получаем ответ
                int responseCode = connection.getResponseCode();

                // Читаем ответ
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                responseCode >= 200 && responseCode < 300 ?
                                        connection.getInputStream() :
                                        connection.getErrorStream(),
                                StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null)
                        response.append(line.trim());
                }

                String responseBody = response.toString();

                // Собираем все заголовки ответа
                Map<String, String> headers = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                    if (entry.getKey() != null) {
                        // Склеиваем все значения через ", "
                        String values = String.join(", ", entry.getValue());
                        headers.put(entry.getKey(), values);
                    }
                }

                return new NetworkResponse(responseCode, responseBody, headers, null);
            } catch (Exception e) {
                Log.e(TAG, "ERROR: " + method + " " + urlPath, e);
                return new NetworkResponse(-1, null, null, e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(NetworkResponse result) {
            if (callback != null) {
                if (result.error != null)
                    callback.onError(result.error);
                else
                    callback.onResponse(result.code, result.body, result.headers);
            }
        }
    }

    // ========== Модель ответа ==========
    private static class NetworkResponse {
        int code;
        String body;
        Map<String, String> headers;
        String error;

        NetworkResponse(int code, String body, Map<String, String> headers, String error) {
            this.code = code;
            this.body = body;
            this.headers = headers;
            this.error = error;
        }
    }

    // ========== Callback ==========
    public interface Callback {
        void onResponse(int statusCode, String responseBody, Map<String, String> headers);
        void onError(String errorMessage);
    }
}