package com.android.privacyview.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.privacyview.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        // check if this is the first launch
        SharedPreferences preferences = getSharedPreferences("AppPreferences",MODE_PRIVATE);
        boolean isFirstLaunch =preferences.getBoolean("FirstLaunch",true);


        if (!isFirstLaunch){

            // if not first launch ,go to home screen
            Intent intent_home= new Intent(MainActivity.this, RegisterConfirmation.class);
            startActivity(intent_home);
            finish();

        } else {

            // show EULU( End user License Agreement for first time launch
            setContentView(R.layout.activity_main);
            TextView licenseTextView = findViewById(R.id.license_text);
            String licenseText = getString(R.string.privacy_policy_text);
            //noinspection deprecation
            licenseTextView.setText(Html.fromHtml(licenseText));

            TextView userTestingConsentView = findViewById(R.id.acceptance_text);
            String userTestingConsent=getString(R.string.user_testing_consent);
            //noinspection deprecation
            userTestingConsentView.setText(Html.fromHtml(userTestingConsent));

            // Find the ImageButton by its ID
            ImageButton continueImageButton = findViewById(R.id.continue_button);
            TextView continueTextView = findViewById(R.id.text_continue);
            CheckBox checkBox_agreement = findViewById(R.id.checkBox);

            continueImageButton.setEnabled(false);
            continueTextView.setEnabled(false);
            checkBox_agreement.setOnCheckedChangeListener((buttonView, isChecked) -> {

                if (isChecked){
                    continueImageButton.setBackgroundResource(R.drawable.rec);
                    continueImageButton.setEnabled(true);
                    continueTextView.setEnabled(true);
                }else {
                    continueImageButton.setEnabled(false);
                    continueTextView.setEnabled(false);
                    continueImageButton.setBackgroundResource(R.drawable.rec_low_color);
                }
            });

            continueImageButton.setOnClickListener(v -> openAssetEntryActivity(preferences));
            continueTextView.setOnClickListener(v ->openAssetEntryActivity(preferences));
        }

    }
    private void openAssetEntryActivity(SharedPreferences preferences) {
        //save users agreement
        SharedPreferences.Editor FirstLaunchEditor=preferences.edit();
        FirstLaunchEditor.putBoolean("FirstLaunch",false);
        FirstLaunchEditor.apply();

        // intent to open the SecurePrivacy activity
        Intent openAssetEntryIntent = new Intent(MainActivity.this, RegisterConfirmation.class);
        startActivity(openAssetEntryIntent);
    }
}