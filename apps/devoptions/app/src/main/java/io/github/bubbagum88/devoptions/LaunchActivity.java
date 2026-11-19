package io.github.bubbagum88.devoptions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** Opens Android's own Developer options page by its exact name.
 *  The generic "developer settings" request is sent to Meta's settings on the
 *  Quest, so the page has to be named directly. */
public class LaunchActivity extends Activity {
    private static final String TAG = "DevOptions";
    private static final String SETTINGS = "com.android.settings";
    private static final String PAGE =
            "com.android.settings.Settings$DevelopmentSettingsDashboardActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SETTINGS, PAGE);
        // Always a new task: reusing an old Settings task whose panel is in the
        // background starts the page there, where the shell never shows it.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(intent);
            Log.i(TAG, "Opened " + SETTINGS + "/" + PAGE);
        } catch (Exception e) {
            Log.e(TAG, "Could not open " + SETTINGS + "/" + PAGE, e);
            Toast.makeText(this, "Could not open Developer options: " + e,
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
