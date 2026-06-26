package com.chimera.touch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this);
        tv.setText("Chimera Touch Service\n\nClick button to enable accessibility");
        tv.setPadding(50, 100, 50, 50);
        
        Button btn = new Button(this);
        btn.setText("Enable Accessibility");
        btn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
        });
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(tv);
        layout.addView(btn);
        setContentView(layout);
    }
}
