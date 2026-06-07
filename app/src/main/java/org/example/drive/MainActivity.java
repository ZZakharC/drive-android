package org.example.drive;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Config.init(this);
        Auth.init(this);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.loading);
        Utils.hideSystemBars(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loading_page), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        work();
    }

    private void work() {
        Server.get("auth/me", new Server.Callback() {
            @Override
            public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                if (statusCode == 200) {
                    Intent intent = new Intent(MainActivity.this, DriveActivity.class);

                    try {
                        intent.putExtra("user", (new JSONObject(responseBody)).getJSONObject("user").toString());
                    } catch (Exception e) {
                        TextView error = findViewById(R.id.error);
                        error.setText(getString(R.string.error_unknown));
                        return;
                    }

                    startActivity(intent);
                } else
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));

                finish();
            }

            @Override
            public void onError(String e) {
                Log.e(Server.TAG, "ERROR: " + e);
                ProgressBar load = findViewById(R.id.load);
                LinearLayout error = findViewById(R.id.error);
                Button change_address = findViewById(R.id.change_address);

                load.setVisibility(GONE);
                error.setVisibility(VISIBLE);

                change_address.setOnClickListener(v -> {
                    EditText input = new EditText(MainActivity.this);
                    input.setHint(Config.SERVER_URL_DEFAULT);

                    // Настройки диалога
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(getString(R.string.change_address_title));
                    builder.setCancelable(false);
                    builder.setView(input); // добавляем EditText в диалог
                    input.setText(Config.SERVER_URL.equals(Config.SERVER_URL_DEFAULT) ? "" : Config.SERVER_URL);

                    builder.setPositiveButton(getString(R.string.change), (dialog, which) -> {
                        String userInput = input.getText().toString().trim();

                        if (userInput.isEmpty())
                            Config.setServerUrl(MainActivity.this, Config.SERVER_URL_DEFAULT);
                        else
                            Config.setServerUrl(MainActivity.this, userInput);

                        work();
                    });

                    builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel());

                    builder.show();
                });
            }
        });
    }
}