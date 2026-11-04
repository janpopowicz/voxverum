package com.whatsapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** Ekran atrapy rozmowy. Połączenie trzyma CallService; koniec rozmowy: am stopservice com.whatsapp/.CallService. */
public class CallActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView v = new TextView(this);
        v.setText("Fake WhatsApp call");
        v.setTextSize(28);
        setContentView(v);
        startForegroundService(new Intent(this, CallService.class));
    }
}
