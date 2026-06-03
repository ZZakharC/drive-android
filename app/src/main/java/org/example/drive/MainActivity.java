package org.example.drive;

import static android.view.View.INVISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
                TextView error = findViewById(R.id.error);

                load.setVisibility(INVISIBLE);
                error.setText(getString(R.string.error_network));
            }
        });
    }
}