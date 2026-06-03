package org.example.drive;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {
    private JSONObject user; // Пользователь
    private final Map<Integer, Integer> originalRules = new HashMap<>(); // Пользователи
    private final Map<Integer, Integer> changes = new HashMap<>(); // Изменённые пользователи
    private Button applyBtn;
    public static final String TAG = "";

    private class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {
        private final ArrayList<JSONObject> users;

        public UsersAdapter(ArrayList<JSONObject> users) {
            this.users = users;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            CheckBox r;
            CheckBox c;
            CheckBox a;
            AppCompatButton delete;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);

                name = itemView.findViewById(R.id.name);
                r = itemView.findViewById(R.id.rule_r);
                c = itemView.findViewById(R.id.rule_c);
                a = itemView.findViewById(R.id.rule_a);
                delete = itemView.findViewById(R.id.delete);
            }
        }

        private void updateLocal(int id, int bit, boolean enabled) {
            int base = originalRules.getOrDefault(id, 0);
            int current = changes.getOrDefault(id, base);

            if (enabled)
                current |= bit;
            else
                current &= ~bit;

            // если совпадает с оригиналом, то убираем из diff
            if (current == base)
                changes.remove(id);
            else
                changes.put(id, current);

            applyBtn.setVisibility(VISIBLE);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false);

            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            try {
                JSONObject user = users.get(position);

                int id = user.getInt("id");
                String name = user.getString("login");
                int rules = user.getInt("rules");

                holder.name.setText(id + ":" + name);

                holder.r.setChecked((rules & 1) != 0);
                holder.r.setOnCheckedChangeListener((b, checked) -> updateLocal(id, Config.Rule.READ, checked));
                holder.c.setChecked((rules & 2) != 0);
                holder.c.setOnCheckedChangeListener((b, checked) -> updateLocal(id, Config.Rule.CHANGE, checked));
                holder.a.setChecked((rules & 4) != 0);
                holder.a.setOnCheckedChangeListener((b, checked) -> updateLocal(id, Config.Rule.ADMIN, checked));

                if (id == 0) {
                    holder.name.setTextColor(getColor(R.color.white_dark));
                    holder.r.setEnabled(false);
                    holder.c.setEnabled(false);
                    holder.a.setEnabled(false);
                    holder.delete.setEnabled(false);
                    holder.delete.setTextColor(getColor(R.color.white_dark));
                } else
                    holder.delete.setOnClickListener(v -> deleteUser(id, name) );

            } catch (Exception ignored) {}
        }

        @Override
        public int getItemCount() {
            return users.size();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.admin);
        Utils.hideSystemBars(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_page), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        try {
            String resBody = getIntent().getStringExtra("user");

            // Пользователь передан используем
            if (resBody != null) {
                user = new JSONObject(resBody);
                if ((user.getInt("rules") & Config.Rule.ADMIN) != 0)
                    renderUserInfo();
                else
                    Utils.alertError(AdminActivity.this, TAG, "Error: Forbidden 403");
            }
            // Нет пользователя запрашивам у сервера
            else {
                Server.get("auth/me", new Server.Callback() {
                    @Override
                    public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                        if (statusCode == 200) {
                            try {
                                user = (new JSONObject(responseBody)).getJSONObject("user");
                                if ((user.getInt("rules") & Config.Rule.ADMIN) != 0) {
                                    renderUserInfo();
                                    return; // Чтобы не перенапрявить пользователя
                                }
                            } catch (JSONException ignored) {}
                        }

                        Utils.alertError(AdminActivity.this, TAG, "Error: " + statusCode);
                    }

                    @Override
                    public void onError(String e) {
                        Utils.alertError(AdminActivity.this, TAG, e);
                    }
                });
            }
        } catch (Exception e) {
            Utils.alertError(AdminActivity.this, TAG, e.toString());
        }

        // Клик по имени
        TextView userName = findViewById(R.id.name);
        userName.setOnClickListener(v -> this.clickUserName());

        // Клик по диску
        Button drive = findViewById(R.id.drive);
        drive.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, DriveActivity.class);
            intent.putExtra("user", user.toString());
            startActivity(intent);
            finish();
        });

        // Клик по Применить
        applyBtn = findViewById(R.id.apply);
        applyBtn.setOnClickListener(v -> clickApply() );

        renderUsers();
    }

    // Удаления пользователя
    public void deleteUser(int id, String name) {
        new AlertDialog.Builder(AdminActivity.this)
                .setTitle(getString(R.string.delete_account_title))
                .setMessage(getString(R.string.delete_account, name))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.no), (dialog, which) -> dialog.dismiss())
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                    Server.delete("admin/users/" + id, new Server.Callback() {
                        @Override
                        public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                            finishAffinity();

                            if (statusCode != 200)
                                Utils.alertError(AdminActivity.this, Server.TAG, "Error code: " + statusCode);
                            else
                                renderUsers();
                        }

                        @Override
                        public void onError(String e) {
                            finishAffinity();
                            Utils.alertError(AdminActivity.this, Server.TAG, e);
                        }
                    });
                })
                .show();
    }

    private void renderUsers() {
        Server.get("admin/users",  new Server.Callback() {
            @Override
            public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                if (statusCode == 200) {
                    try {
                        JSONArray array = (new JSONObject(responseBody)).getJSONArray("users");
                        ArrayList<JSONObject> users = new ArrayList<>();
                        originalRules.clear();

                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);

                            int id = obj.getInt("id");
                            int rules = obj.getInt("rules");

                            originalRules.put(id, rules);
                            users.add(obj);
                        }

                        RecyclerView recycler = findViewById(R.id.users_list);
                        recycler.setLayoutManager(new LinearLayoutManager(AdminActivity.this));
                        recycler.setAdapter(new UsersAdapter(users));
                    } catch (JSONException e) {
                        Utils.alertError(AdminActivity.this, TAG, e.toString());
                    }
                } else
                    Utils.alertError(AdminActivity.this, TAG, String.valueOf(statusCode));
            }

            @Override
            public void onError(String e) { Utils.alertError(AdminActivity.this, Server.TAG, e); }
        });
    }

    // Клик по кнопке Приминить
    private void clickApply() {
        try {
            JSONArray arr = new JSONArray();

            for (Map.Entry<Integer, Integer> e : changes.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("id", e.getKey());
                obj.put("rules", e.getValue());
                arr.put(obj);
            }

            JSONObject body = new JSONObject();
            body.put("users", arr);

            Server.post("admin/users", body, new Server.Callback() {
                @Override
                public void onResponse(int statusCode, String body, Map<String,String> headers) {
                    if (statusCode == 200) {
                        changes.clear();
                        applyBtn.setVisibility(INVISIBLE); // Делаем кнопку Применить невидимой
                        renderUsers();
                    } else
                        Utils.alertError(AdminActivity.this, TAG, "Code: " + statusCode);
                }

                @Override
                public void onError(String e) {
                    Utils.alertError(AdminActivity.this, Server.TAG, e);
                }
            });
        } catch (Exception e) {
            Utils.alertError(AdminActivity.this, TAG, e.toString());
        }
    }

    // Отображени информации о пользователе
    @SuppressLint("SetTextI18n")
    private void renderUserInfo() throws JSONException {
        if (user == null) return;
        TextView name = findViewById(R.id.name);
        name.setText(user.optString("name", "None"));
    }

    // Выход из аккаунт с алертом
    private void clickUserName() {
        String userName = "Account";
        try {
            userName = user.getString("name");
        } catch (JSONException e) {
            Utils.alertError(AdminActivity.this, TAG, e.toString());
        }

        new AlertDialog.Builder(AdminActivity.this)
                .setTitle(getString(R.string.exit_account_title))
                .setMessage(getString(R.string.exit_account, userName))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.no), (dialog, which) -> dialog.dismiss())
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                    Server.logout();
                    Auth.clearSession();

                    startActivity(new Intent(AdminActivity.this, LoginActivity.class));

                    finishAffinity();
                    finish();
                })
                .show();
    }
}