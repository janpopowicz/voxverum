package com.whatsapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;

/** Atrapa trwającego połączenia: usługa pierwszoplanowa z mikrofonem, jak WhatsApp w trakcie rozmowy. */
public class CallService extends Service {
    private AudioRecord record;
    private volatile boolean running;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (record != null) return START_NOT_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("call", "Call", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "call")
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("Ongoing fake call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        getSystemService(AudioManager.class).setMode(AudioManager.MODE_IN_COMMUNICATION);
        int size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            record.startRecording();
            running = true;
            new Thread(() -> { short[] buf = new short[size / 2]; while (running) record.read(buf, 0, buf.length); }).start();
        } catch (SecurityException e) {
            // sam tryb audio wystarcza do wykrycia rozmowy
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (record != null) { record.stop(); record.release(); record = null; }
        getSystemService(AudioManager.class).setMode(AudioManager.MODE_NORMAL);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
