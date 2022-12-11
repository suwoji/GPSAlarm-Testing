package com.eebax.geofencing;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Random;

public class NotificationHelper extends ContextWrapper {

    private static final String TAG = "NotificationHelper";

    public NotificationHelper(Context base) {
        super(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannels();
        }
    }

    private String CHANNEL_NAME = "High priority channel";
    private String CHANNEL_ID = "com.example.notifications" + CHANNEL_NAME;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createChannels() {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        notificationChannel.enableVibration(true);
        notificationChannel.setDescription("this is the description of the channel.");
        notificationChannel.setLightColor(Color.RED);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(notificationChannel);
    }

    public void sendHighPriorityNotification(String title, String body, Class activityName) {

        Intent intent = new Intent(this, activityName);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                267,
                intent,
                PendingIntent.FLAG_IMMUTABLE);
        Uri uri = Uri.parse("android.resource://"+getApplicationContext().getPackageName()+"/"+R.raw.geo_alarm);
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle(title)
//                .setContentText(body)
//                .setSmallIcon(R.drawable.ic_launcher_background)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setStyle(new NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body))
//                .setContentIntent(pendingIntent)
//                .setAutoCancel(true)
//                .setSound(uri)
//                .addAction(R.drawable.alarm_icon, String.valueOf(R.string.push_stop_button), pendingIntent)
//                .build();
        long when = System.currentTimeMillis() + 60000L;

//        NotificationManagerCompat.from(this).notify(new Random().nextInt(), notification);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, "CH_ID")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .addAction(R.drawable.alarm_icon, body, pendingIntent)
                .setAutoCancel(true)
                .setSound(uri)
                .setContentIntent(pendingIntent);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            if(uri != null){
                // Changing Default mode of notification
                notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
                // Creating an Audio Attribute
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();

                // Creating Channel
                NotificationChannel notificationChannel = new NotificationChannel("CH_ID","Testing_Audio",NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setSound(uri,audioAttributes);
                mNotificationManager.createNotificationChannel(notificationChannel);
            }
        }
        mNotificationManager.notify(0, notificationBuilder.build());
    }
}
