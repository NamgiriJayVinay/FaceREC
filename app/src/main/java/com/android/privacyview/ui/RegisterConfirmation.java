package com.android.privacyview.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.privacyview.R;
import com.android.privacyview.utils.FaceRecognitionHelper;

public class RegisterConfirmation extends AppCompatActivity {
    ImageButton continueImageButton;
    TextView continueTextView;

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_OVERLAY_PERMISSION = 1002;
    private FaceRecognitionHelper faceRecognitionHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

            // show EULU( End user License Agreement for first time launch
            setContentView(R.layout.registration_confirmation);

            // Find the ImageButton by its ID
            continueImageButton = findViewById(R.id.continue_button);
            continueTextView = findViewById(R.id.text_continue);
            continueImageButton.setClickable(true);
            continueTextView.setClickable(true);

            continueImageButton.setOnClickListener(v -> openLiveFaceActivity());
            continueTextView.setOnClickListener(v ->openLiveFaceActivity());


            requestPermissions();
        faceRecognitionHelper = new FaceRecognitionHelper(this);
        faceRecognitionHelper.loadFaceEmbeddings();
        // Add button in layout for recognition
        Button recognizeButton = findViewById(R.id.recognizeButton);
        recognizeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, FaceRecognitionActivity.class);
            startActivity(intent);
        });

    }

    private void openLiveFaceActivity() {

        // intent to open the SecurePrivacy activity
        Intent openLiveIntent = new Intent(RegisterConfirmation.this, FaceDetection.class);
        startActivity(openLiveIntent);
    }

    private void requestPermissions() {
        String[] permissions;

        // Check if the permission is granted
        if (!Settings.canDrawOverlays(this)) {
            // Permission is not granted, request it
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            // Permission is already granted, proceed with the intended functionality
            // ...
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.READ_MEDIA_IMAGES
            };
        } else {
            permissions = new String[]{
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }



        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}