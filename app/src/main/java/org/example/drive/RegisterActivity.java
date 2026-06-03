package org.example.drive;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    public static final String TAG = "RegisterActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.register);
        Utils.hideSystemBars(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register_page), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Регистрация аккаунта
        TextView loginBtn = findViewById(R.id.register_btn);
        loginBtn.setOnClickListener(v -> this.register());

        // Есть аккаунт
        TextView switchToLogin = findViewById(R.id.login_btn);
        switchToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    // Регистрация пользователя (получает данные с input)
    protected void register() {
        String loginText = ((EditText) findViewById(R.id.login_inp)).getText().toString();
        String passwordText = ((EditText) findViewById(R.id.password_inp)).getText().toString();
        String password2Text = ((EditText) findViewById(R.id.password2_inp)).getText().toString();
        TextView errorText = findViewById(R.id.error);

        String error = Auth.Validator.validateCredentials(this, loginText, passwordText);

        if (loginText.length() < Config.MIN_LOGIN_LENGTH)
            errorText.setText(getString(R.string.error_login_len, Config.MIN_LOGIN_LENGTH));
        else if (passwordText.length() < Config.MIN_PASSWORD_LENGTH)
            errorText.setText(getString(R.string.error_password_len, Config.MIN_PASSWORD_LENGTH));
        else if (!passwordText.equals(password2Text))
            errorText.setText(getString(R.string.error_password_match));
        else if (error != null)
            errorText.setText(error);
        else {
            errorText.setText("");

            try {
                JSONObject body = new JSONObject();
                body.put("login", loginText);
                body.put("password", passwordText);

                Server.post("auth/register", body, new Server.Callback() {
                    @Override
                    public void onResponse(int statusCode, String responseBody, Map<String, String> headers) {
                        if (statusCode == 200) {
                            // Сахроняем токен и CSRF
                            Auth.setSession(headers.get("Set-Cookie"), headers.get("X-CSRF-Token"));
                            startActivity(new Intent(RegisterActivity.this, DriveActivity.class));
                            finish();
                        } else if (statusCode == 409)
                            errorText.setText(getString(R.string.error_user_exists));
                        else if (statusCode == 429)
                            errorText.setText(getString(R.string.error_many_request));
                        else
                            errorText.setText(getString(R.string.error_unknown));
                    }

                    @Override
                    public void onError(String e) {
                        Log.e(TAG, "ERROR: " + e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "ERROR: " + e);
            }
        }
    }
}