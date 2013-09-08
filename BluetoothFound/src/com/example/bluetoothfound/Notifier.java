package com.example.bluetoothfound;

import java.util.Random;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** 
 * This class is to notify the user of messages with NotificationManager.
 *
 * @author Sehwan Noh (devnoh@gmail.com)
 */
public class Notifier {
    private static final Random random = new Random(System.currentTimeMillis());
    private Context context;
    private NotificationManager notificationManager;

    public Notifier(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void notify(boolean isDiscovery, String title, String message) {
            // Show the toast
//            if (isNotificationToastEnabled()) {
//                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
//            }
            // Notification
            Notification notification = new Notification();
            notification.icon = R.drawable.ic_launcher;
            notification.defaults = Notification.DEFAULT_LIGHTS;
//            if (isNotificationSoundEnabled()) {
//                notification.defaults |= Notification.DEFAULT_SOUND;
//            }
//            if (isNotificationVibrateEnabled()) {
//                notification.defaults |= Notification.DEFAULT_VIBRATE;
//            }
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notification.when = System.currentTimeMillis();
            notification.tickerText = message;

            Intent intent = new Intent(context,
                    BluetoothFoundActivity.class);
            //intent.putExtra("title", title);
            intent.putExtra("isDiscovery", isDiscovery);
            //intent.putExtra(Constants.NOTIFICATION_URI, uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            notification.setLatestEventInfo(context, title, message,
                    contentIntent);
            notificationManager.notify(random.nextInt(), notification);
       
    }

}
