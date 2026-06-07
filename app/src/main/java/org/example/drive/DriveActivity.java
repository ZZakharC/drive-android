package org.example.drive;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import android.widget.PopupMenu;
import android.widget.Toast;

public class DriveActivity extends AppCompatActivity {
    private JSONObject user; // Пользователь
    private String path = "/";
    public static final String TAG = "DriveActivity";
    private SwipeRefreshLayout swipeRefreshLayout;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private boolean imageZoom = false;

    private static class FilesAdapter extends RecyclerView.Adapter<DriveActivity.FilesAdapter.ViewHolder> {
        private final ArrayList<JSONObject> dir;
        private final DriveActivity activity;

        public FilesAdapter(ArrayList<JSONObject> dir, DriveActivity activity) {
            this.dir = dir;
            this.activity = activity;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView size;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);

                icon = itemView.findViewById(R.id.icon);
                name = itemView.findViewById(R.id.name);
                size = itemView.findViewById(R.id.size);
            }
        }

        @NonNull
        @Override
        public DriveActivity.FilesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.item_file, parent, false);

            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull DriveActivity.FilesAdapter.ViewHolder holder, int position) {
            try {
                JSONObject file = dir.get(position);

                String name = file.getString("name");
                String type = file.getString("type");
                String url = file.getString("url");

                if (type.equals("dir")) {
                    holder.icon.setImageResource(Config.ICONS_MAP.get("folder"));
                    holder.name.setText(name + "/");

                    holder.itemView.setOnClickListener(v -> activity.clickFolder(v, url));
                } else {
                    int size = file.getInt("size") ;

                    String[] parts = name.split("\\.");
                    String ext = parts.length > 1 ? parts[parts.length - 1].toLowerCase() : "default";
                    int icon = Config.ICONS_MAP.getOrDefault(ext, Config.ICONS_MAP.get("default"));

                    holder.icon.setImageResource(icon);
                    holder.name.setText(name);
                    holder.size.setText(sizeString(size));

                    holder.itemView.setOnClickListener(v -> activity.clickFile(v, file, url, ext, icon));
                }
            } catch (Exception ignored) {}
        }

        @SuppressLint("DefaultLocale")
        private static String sizeString(int size) {
            String[] units = { "B", "KB", "MB", "GB", "TB" };
            double value = size;
            int i = 0;

            while (value >= 1024 && i < units.length - 1) {
                value /= 1024;
                i++;
            }

            if (value == (long)value)
                return String.format("%d %s", (long)value, units[i]);

            return String.format("%.2f %s", value, units[i]);
        }

        @Override
        public int getItemCount() {
            return dir.size();
        }
    }

    // Клик по папке
    protected void clickFolder(View v, String url) {
        // Создание меню
        PopupMenu folderMenu = new PopupMenu(DriveActivity.this, v);

        // Загрука меню из XML
        folderMenu.getMenuInflater().inflate(R.menu.folder, folderMenu.getMenu());

        // Обрабатка нажатие на пункты
        folderMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.open_folder) {
                findViewById(R.id.up_dir).setVisibility(VISIBLE);
                ((TextView)findViewById(R.id.path)).setText(url);
                path = url + "/";
                renderFiles();
                return true;
            } else if (id == R.id.delete_folder) {
                deleteDriveFile(url);
                return true;
            }

            return false;
        });

        folderMenu.show();
    }

    // Клик по файлу
    protected void clickFile(View v, JSONObject file, String url, String ext, int iconRes) {
        // Создание меню
        PopupMenu fileMenu = new PopupMenu(DriveActivity.this, v);

        // Загрука меню из XML
        fileMenu.getMenuInflater().inflate(R.menu.file, fileMenu.getMenu());

        // Обрабатка нажатие на пункты
        fileMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.open_file) {
                renderPreviewFile(file, url, ext, iconRes);
                return true;
            } else if (id == R.id.download_file) {
                downloadDriveFile(url);
                return true;
            } else if (id == R.id.delete_file) {
                deleteDriveFile(url);
                return true;
            }

            return false;
        });

        fileMenu.show();
    }

    protected void downloadDriveFile(String url) {
        if (Utils.hasNotificationPermission(DriveActivity.this))
            Server.downloadFile(DriveActivity.this, url);
        else
            Utils.alertError(DriveActivity.this, TAG, getString(R.string.error_no_notification_perm));
    }

    protected void deleteDriveFile(String url) {
        new AlertDialog.Builder(DriveActivity.this)
            .setTitle(getString(R.string.file_delete_title))
            .setMessage(getString(R.string.file_delete, url))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.no), (dialog, which) -> dialog.dismiss())
            .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                try {
                    if ((user.getInt("rules") & Config.Rule.CHANGE) == 0) {
                        new AlertDialog.Builder(DriveActivity.this)
                                .setTitle("Error")
                                .setMessage(getString(R.string.no_rules))
                                .show();
                    } else {
                        Server.delete("drive" + url, new Server.Callback() {
                            @Override
                            public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                                if (statusCode == 200) {
                                    Toast.makeText(DriveActivity.this, getString(R.string.file_been_delete, url), Toast.LENGTH_LONG).show();
                                    renderFiles();
                                } else
                                    Utils.alertError(DriveActivity.this, TAG, "Error: " + statusCode);
                            }

                            @Override
                            public void onError(String e) { Utils.alertError(DriveActivity.this, Server.TAG, e); }
                        });
                    }
                } catch (JSONException e) {
                    Utils.alertError(DriveActivity.this, TAG, e.toString());
                }
            })
            .show();
    }

    @SuppressLint("SetTextI18n")
    protected void renderPreviewFile(JSONObject file, String url, String ext, int iconRes) {
        try {
            View preview = findViewById(R.id.preview_overlay);

            TextView name = findViewById(R.id.preview_name);
            TextView path = findViewById(R.id.preview_path);
            TextView size = findViewById(R.id.preview_size);
            TextView date = findViewById(R.id.preview_date);
            ImageView icon = findViewById(R.id.preview_icon);
            Button download = findViewById(R.id.preview_download);
            Button delete = findViewById(R.id.preview_delete);
            String fileName = file.getString("name");

            name.setText(fileName);

            ProgressBar load = findViewById(R.id.load_icon);

            if (Arrays.asList(Config.IMAGES_RENDER).contains(ext) && file.getInt("size") < Config.MAX_IMAGE_RENDER_SIZE) {
                load.setVisibility(VISIBLE);
                icon.setVisibility(GONE);
                Server.loadImage(icon, load, url);

                imageZoom = false;
                icon.setOnClickListener(v -> {
                    if (imageZoom) {
                        icon.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                    } else {
                        icon.animate()
                            .scaleX(2.5f)
                            .scaleY(2.5f)
                            .setDuration(200)
                            .start();
                    }

                    imageZoom = !imageZoom;
                });
            } else {
                load.setVisibility(GONE);
                icon.setVisibility(VISIBLE);
                icon.setImageResource(iconRes);
            }

            path.setText(file.optString("url", "/"));
            size.setText(FilesAdapter.sizeString(file.optInt("size", 0)));
            date.setText(file.optString("date", "-"));
            preview.setVisibility(View.VISIBLE);

            download.setOnClickListener(v -> {
                downloadDriveFile(url);
                preview.setVisibility(GONE);
            });

            delete.setOnClickListener(v -> {
                deleteDriveFile(url);
                preview.setVisibility(GONE);
            });

            preview.setOnClickListener(v -> preview.setVisibility(GONE));
        } catch (Exception e) {
            Utils.alertError(this, TAG, e.toString());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.drive);
        Utils.hideSystemBars(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drive_page), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Utils.requestNotificationPermission(DriveActivity.this);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        uploadSelectedFile(uri);

                        // Сохраняем доступ
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }
        );

        swipeRefreshLayout = findViewById(R.id.drive_refresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            renderFiles();
            swipeRefreshLayout.setRefreshing(false);
        });

        findViewById(R.id.up_dir).setOnClickListener(v -> upDir());

        try {
            String resBody = getIntent().getStringExtra("user");

            // Пользователь передан используем
            if (resBody != null) {
                user = new JSONObject(resBody);
                work();
            }
            // Нет пользователя запрашивам у сервера
            else {
                Server.get("auth/me", new Server.Callback() {
                    @Override
                    public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                        if (statusCode == 200) {
                            try {
                                user = (new JSONObject(responseBody)).getJSONObject("user");
                                work();
                                return; // Чтобы не выводит ошибку
                            } catch (JSONException ignored) {}
                        }

                        Utils.alertError(DriveActivity.this, TAG, "Error: " + statusCode);
                    }

                    @Override
                    public void onError(String e) { Utils.alertError(DriveActivity.this, Server.TAG, e); }
                });
            }
        } catch (Exception e) {
            Utils.alertError(DriveActivity.this, TAG, e.toString());
        }
    }

    // Основная логика
    private void work() throws JSONException {
        int rules = user.getInt("rules");

        // Отображаем ифнормацию о пользователе
        renderUserInfo();

        if ((rules & Config.Rule.READ) == 0)
            Utils.alertError(DriveActivity.this, TAG, "Error: Forbidden 403");
        else
            renderFiles();
    }

    // Поднатся верх на дирикторию
    private void upDir() {
        if (path.equals("/"))
            return;

        // убираем последний сегмент
        String[] parts = path.split("/");
        ArrayList<String> filtered = new ArrayList<>();

        for (String part : parts)
            if (!part.isEmpty())
                filtered.add(part);

        if (!filtered.isEmpty())
            filtered.remove(filtered.size() - 1);

        path = "/" + String.join("/", filtered);
        ((TextView)findViewById(R.id.path)).setText(path);

        // Выключаем кнопку если мы в корне /
        if (path.equals("/"))
            findViewById(R.id.up_dir).setVisibility(INVISIBLE);

        // перерисовка
        renderFiles();
    }

    // Выбор файла
    private void openFilePicker() {
        filePickerLauncher.launch(new String[]{"*/*"});
    }

    // Создание задачи на отправку
    private void uploadSelectedFile(Uri uri) {
        new Thread(() -> {
            try {
                String fileName = getFileName(uri);

                Server.uploadFile(DriveActivity.this, uri, path, fileName);

                runOnUiThread(() -> {
                    Toast.makeText(DriveActivity.this, getString(R.string.uploaded, fileName), Toast.LENGTH_SHORT).show();
                    renderFiles();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Utils.alertError(DriveActivity.this, Server.TAG, e.toString())
                );
            }
        }).start();
    }

    // Получения имени файла из uri
    private String getFileName(Uri uri) {
        String result = "file";

        if (Objects.equals(uri.getScheme(), "content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {

                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0)
                        result = cursor.getString(index);
                }
            }
        } else if (uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }

        return result;
    }

    // Отобразить файлы
    private void renderFiles() {
        Server.get("drive" + path, new Server.Callback() {
            @Override
            public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                if (statusCode == 200) {
                    try {
                        JSONArray array = (new JSONObject(responseBody)).getJSONArray("files");
                        ArrayList<JSONObject> files = new ArrayList<>();

                        for (int i = 0; i < array.length(); i++)
                            files.add(array.getJSONObject(i));

                        // ------------------ СОРТИРОВКА ------------------
                        files.sort((a, b) -> {
                            try {
                                String typeA = a.getString("type");
                                String typeB = b.getString("type");

                                // Папки выше файлов
                                if (typeA.equals("dir") && !typeB.equals("dir")) return -1;
                                if (!typeA.equals("dir") && typeB.equals("dir")) return 1;

                                // Внутри группы по алфавиту
                                String nameA = a.getString("name");
                                String nameB = b.getString("name");
                                return nameA.compareTo(nameB);
                            } catch (JSONException e) {
                                return 0;
                            }
                        });

                        RecyclerView recycler = findViewById(R.id.files_list);

                        int itemWidthDp = 120;

                        float density = getResources().getDisplayMetrics().density;
                        int screenWidthPx = getResources().getDisplayMetrics().widthPixels;

                        int itemWidthPx = (int)(itemWidthDp * density);

                        int spanCount = Math.max(1, screenWidthPx / itemWidthPx);

                        recycler.setLayoutManager(
                                new GridLayoutManager(
                                        DriveActivity.this,
                                        spanCount
                                )
                        );
                        recycler.setAdapter(new FilesAdapter(files, DriveActivity.this));
                    } catch (JSONException e) {
                        Utils.alertError(DriveActivity.this, TAG, e.toString());
                    }
                } else
                    Utils.alertError(DriveActivity.this, TAG, String.valueOf(statusCode));
            }

            @Override
            public void onError(String e) { Utils.alertError(DriveActivity.this, Server.TAG, e); }
        });
    }

    // Отображени информации о пользователе
    @SuppressLint("SetTextI18n")
    private void renderUserInfo() throws JSONException {
        if (user == null) return;

        Button adminPanel = findViewById(R.id.admin_panel);
        TextView rules = findViewById(R.id.rules);
        TextView name = findViewById(R.id.name);
        Button create = findViewById(R.id.create);

        int r = user.getInt("rules");
        if ((r & Config.Rule.READ) != 0)
            rules.setText("R");
        if ((r & Config.Rule.CHANGE) != 0) {
            rules.setText(rules.getText() + "C");

            // Кнопка создать
            create.setEnabled(true);
            create.setTextColor(getColor(R.color.act));
            create.setOnClickListener(this::clickCreate);

            // Кнопка удаления с предпросмотра файла
            Button filePreviewDelete = findViewById(R.id.preview_delete);
            filePreviewDelete.setEnabled(true);
            filePreviewDelete.setTextColor(getColor(R.color.red));
        } if ((r & Config.Rule.ADMIN) != 0) {
            rules.setText(rules.getText() + "A");

            adminPanel.setVisibility(VISIBLE);
            adminPanel.setOnClickListener(v -> {
                Intent intent = new Intent(DriveActivity.this, AdminActivity.class);
                intent.putExtra("user", user.toString());
                startActivity(intent);
                finish();
            });
        }

        name.setText(user.optString("name", "None"));
        name.setOnClickListener(v -> this.clickUserName());
    }

    // Клик по кнопке создать
    private void clickCreate(View v) {
        // Создание меню
        PopupMenu createMenu = new PopupMenu(DriveActivity.this, v);

        // Загрука меню из XML
        createMenu.getMenuInflater().inflate(R.menu.create, createMenu.getMenu());

        // Обрабатка нажатие на пункты
        createMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.upload_file) {
                openFilePicker();
                return true;
            } else if (id == R.id.create_folder) {
                // Создаём поле ввода
                EditText input = new EditText(DriveActivity.this);
                input.setHint(getString(R.string.folder_name));

                // Настройки диалога
                AlertDialog.Builder builder = new AlertDialog.Builder(DriveActivity.this);
                builder.setTitle(getString(R.string.create_folder_title));
                builder.setCancelable(false);
                builder.setView(input);  // добавляем EditText в диалог

                builder.setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    String userInput = input.getText().toString();
                    Server.put("drive" + path + userInput, new Server.Callback() {
                        @Override
                        public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                            if (statusCode == 200)
                                renderFiles();
                            else
                                Utils.alertError(DriveActivity.this, TAG,  "Error: "+ statusCode);
                        }

                        @Override
                        public void onError(String e) { Utils.alertError(DriveActivity.this, Server.TAG, e); }
                    });
                });

                builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            }

            return false;
        });

        createMenu.show();
    }

    // Клик по имени пользователя
    private void clickUserName() {
        String userName = "Account";
        try {
            userName = user.getString("name");
        } catch (JSONException e) {
            Log.e(TAG, "ERROR: " + e);
        }

        new AlertDialog.Builder(DriveActivity.this)
            .setTitle(getString(R.string.exit_account_title))
            .setMessage(getString(R.string.exit_account, userName))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.no), (dialog, which) -> dialog.dismiss())
            .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                Server.logout();
                Auth.clearSession();

                startActivity(new Intent(DriveActivity.this, LoginActivity.class));

                finishAffinity();
                finish();
            })
            .show();
    }
}