package org.efidroid.efidroidmanager.services;

import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.efidroid.efidroidmanager.R;
import org.efidroid.efidroidmanager.RootToolsEx;
import org.efidroid.efidroidmanager.activities.MainActivity;
import org.efidroid.efidroidmanager.activities.NotificationReceiverActivity;
import org.efidroid.efidroidmanager.activities.OSUpdateProgressActivity;
import org.efidroid.efidroidmanager.models.OperatingSystem;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class OperatingSystemUpdateIntentService extends IntentServiceEx {
    // tasks
    private static final String ACTION_UPDATE_OPERATING_SYSTEM = "org.efidroid.efidroidmanager.services.action.update_operating_system";

    // args
    private static final String ARG_OPERATING_SYSTEM = "operatingsystem";

    // button_actions
    private static final String BUTTON_ACTION_STOP = "button_stop";

    // message actions
    public static final String MESSAGE_ACTION_NOTIFICATION_SHOW = "message_action_notification_show";
    public static final String MESSAGE_ACTION_NOTIFICATION_HIDE = "message_action_notification_hide";

    // status
    private OperatingSystem mOperatingSystem = null;
    private boolean mShowNotifications = false;

    // DEBUG
    private static boolean simulateError = false;

    public OperatingSystemUpdateIntentService() {
        super("OperatingSystemUpdateIntentService");
    }

    public static void startActionUpdateOperatingSystem(Context context, OperatingSystem os) {
        Intent intent = new Intent(context, OperatingSystemUpdateIntentService.class);
        intent.setAction(ACTION_UPDATE_OPERATING_SYSTEM);
        intent.putExtra(ARG_OPERATING_SYSTEM, os);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE_OPERATING_SYSTEM.equals(action)) {
                OperatingSystem os = intent.getParcelableExtra(ARG_OPERATING_SYSTEM);
                handleActionUpdateOperatingSystem(os);
            }
        }
    }

    private void showProgressNotification() {
        Intent intent;
        PendingIntent pIntent;

        // get notification manager
        NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // build notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getBaseContext().getResources().getString(R.string.app_name))
                .setOngoing(true)
                .setContentText((mOperatingSystem.isCreationMode() ? "Creating" : "Updating") + " System '" + mOperatingSystem.getName() + "'");

        // create button intent
        intent = new Intent(this, NotificationReceiverActivity.class);
        intent.putExtra(NotificationReceiverActivity.ARG_NOTIFICATION_ID, NotificationReceiverActivity.NOTIFICATION_ID_OP_UPDATE_SERVICE);
        intent.setAction(BUTTON_ACTION_STOP);
        pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);
        mBuilder.addAction(R.drawable.ic_action_delete, "Stop", pIntent);

        // create content intent
        intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);
        mBuilder.setContentIntent(pIntent);

        // show notification
        notifyManager.notify(NotificationReceiverActivity.NOTIFICATION_ID_OP_UPDATE_SERVICE, mBuilder.build());
    }

    private void showResultNotification(boolean success) {
        Intent intent;
        PendingIntent pIntent;

        // get notification manager
        NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // build notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getBaseContext().getResources().getString(R.string.app_name))
                .setAutoCancel(true);

        if (success) {
            mBuilder = mBuilder.setContentText((mOperatingSystem.isCreationMode() ? "Created" : "Updated") + " System '" + mOperatingSystem.getName() + "'");
        } else {
            mBuilder = mBuilder.setContentText("Error " + (mOperatingSystem.isCreationMode() ? "Creating" : "Updating") + " System '" + mOperatingSystem.getName() + "'");
        }

        // create content intent
        intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);
        mBuilder.setContentIntent(pIntent);

        notifyManager.notify(NotificationReceiverActivity.NOTIFICATION_ID_OP_UPDATE_SERVICE, mBuilder.build());
    }

    private void cancelNotification() {
        // get notification manager
        NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notifyManager.cancel(NotificationReceiverActivity.NOTIFICATION_ID_OP_UPDATE_SERVICE);
    }

    private void handleActionUpdateOperatingSystem(OperatingSystem os) {
        mOperatingSystem = os;
        mShowNotifications = false;

        boolean success = false;
        for(int i=0; i<=100; i++) {
            if(shouldStop()) {
                publishProgress(i, "Aborted");
                break;
            }

            publishProgress(i, "Doing "+i+"/"+100);
            try {
                Thread.sleep(50, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // error
            if(i==80 && simulateError) {
                success = false;
                publishProgress(i, "Unknown Error");
                break;
            }

            if(i==100)
                success = true;
        }

        // publish status
        if(success)
            publishProgress(100, "Done");
        publishFinish(success);
        simulateError = !simulateError;

        if(mShowNotifications) {
            // remove progress notification
            cancelNotification();

            // show result if user didn't cancel(the activity will be opened anyway in that case)
            if (!shouldStop()) {
                showResultNotification(success);
            }
        }

        mOperatingSystem = null;
    }

    private void publishProgress(int percent, String text) {
        Intent intent = new Intent();
        intent.setAction(OSUpdateProgressActivity.ACTION_OPUPDATE_PROGRESS);
        intent.putExtra(OSUpdateProgressActivity.ARG_OPUPDATE_PROGRESS, percent);
        intent.putExtra(OSUpdateProgressActivity.ARG_OPUPDATE_TEXT, text);
        sendBroadcast(intent);
    }

    private void publishFinish(boolean success) {
        Intent intent = new Intent();
        intent.setAction(OSUpdateProgressActivity.ACTION_OPUPDATE_FINISH);
        intent.putExtra(OSUpdateProgressActivity.ARG_OPUPDATE_SUCCESS, success);
        sendBroadcast(intent);
    }

    @Override
    protected void onHandleMessage(Intent intent) {
        if(mOperatingSystem==null)
            return;

        // get notification manager
        NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        switch (intent.getAction()) {
            case MESSAGE_ACTION_NOTIFICATION_SHOW:
                if(!mShowNotifications) {
                    showProgressNotification();
                    mShowNotifications = true;
                }
                break;

            case MESSAGE_ACTION_NOTIFICATION_HIDE:
                if(mShowNotifications) {
                    cancelNotification();
                    mShowNotifications = false;
                }
                break;
        }
    }

    public static void handleNotificationIntent(Activity activity) {
        switch(activity.getIntent().getAction()) {
            case BUTTON_ACTION_STOP:
                stopCurrentTask(activity, OperatingSystemUpdateIntentService.class);
                break;
        }
    }

    public static void showNotification(Context context, boolean show) {
        Intent intent = new Intent();
        intent.setAction(show?MESSAGE_ACTION_NOTIFICATION_SHOW:MESSAGE_ACTION_NOTIFICATION_HIDE);
        sendMessage(context, OperatingSystemUpdateIntentService.class, intent);
    }
}
