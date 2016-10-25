package com.example.raymond.musicplayer10;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

/**
 * Created by Raymond on 10/21/2016.
 */
public class myPlayService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener {

    // declaration for MediaPlayer
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private String sntAudioLink;

    // declaration for notification
    private static final int NOTIFICATION_ID = 1;

    // declaration for IncomingCall handler
    private static final String TAG = "TELSERVICE";
    private boolean isPausedInCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    // declaration for broadcast identifier and intent
    public static final String BROADCAST_BUFFER = "com.glowingpigs.tutorialstreamaudiopart1b.broadcastbuffer";
    Intent bufferIntent;

    // declaration for headset handler
    private int headsetSwitch = 1;

    // declaration for seekBar
    String sntSeekPos;
    int intSeekPos;
    int mediaPosition;
    int mediaMax;
    private final Handler handler = new Handler();
    private static int songEnded;
    public static final String BROADCAST_ACTION = "com.glowingpigs.tutorialstreamaudiopart1b.seekprogress";
    Intent seekIntent;

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {

    }
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        stopMedia();
        stopSelf();
    }
    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Toast.makeText(this,
                        "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra,
                        Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Toast.makeText(this, "MEDIA ERROR SERVER DIED " + extra,
                        Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Toast.makeText(this, "MEDIA ERROR UNKNOWN " + extra,
                        Toast.LENGTH_SHORT).show();
                break;
        }
        return false;
    }
    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }
    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        // Send a message to activity to end progress dialogue
        sendBufferCompleteBroadcast();

        // Final action
        playMedia();
    }
    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {
        if (!mediaPlayer.isPlaying()){
            playMedia();
            Toast.makeText(this,
                    "SeekComplete", Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        Log.v(TAG, "Creating Service");
        // Instantiate bufferIntent to communicate with Activity for progress dialogue
        bufferIntent = new Intent(BROADCAST_BUFFER);

        // initialize for MediaPlayer
        mediaPlayer.setOnSeekCompleteListener(this);

        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);

        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.reset();

        // initialize for headset receiver
        registerReceiver(headsetReceiver, new IntentFilter(
                Intent.ACTION_HEADSET_PLUG));

        // initialize for seekBar
        seekIntent = new Intent(BROADCAST_ACTION);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        }
        // Cancel the notification
        cancelNotification();

        // Dismiss phoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener,
                    PhoneStateListener.LISTEN_NONE);
        }

        // Unregister headsetReceiver
        unregisterReceiver(headsetReceiver);
        // Service ends, need to tell activity to display "Play" button
        resetButtonPlayStopBroadcast();

        // Stop the seekbar handler from sending updates to UI
        handler.removeCallbacks(sendUpdatesToUI);
        // Unregister seekbar receiver
        unregisterReceiver(broadcastReceiver);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Manage incoming phone calls during playback. Pause mp on incoming,
        // resume on hangup.
        // -----------------------------------------------------------------------------------
        // Get the telephony manager
        Log.v(TAG, "Starting telephony");
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        Log.v(TAG, "Starting listener");
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // String stateString = "N/A";
                Log.v(TAG, "Starting CallStateChange");
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            isPausedInCall = true;
                        }

                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (isPausedInCall) {
                                isPausedInCall = false;
                                playMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);

        // Insert notification start
        initNotification();

        sntAudioLink = intent.getExtras().getString("sentAudioLink");
        mediaPlayer.reset();
        // Set up the MediaPlayer data source using the strAudioLink value
        if (!mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.setDataSource(sntAudioLink);
                // Send message to Activity to display progress dialogue
                sendBufferingBroadcast();
                //
                mediaPlayer.prepareAsync();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
            }
        }
        // --- Set up seekbar handler ---
        setupHandler();
        // ---Set up receiver for seekbar change ---
        registerReceiver(broadcastReceiver, new IntentFilter(
                MainActivity.BROADCAST_SEEKBAR));
        return START_STICKY;
    }
    // methods for MediaPlayer
    public void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }
    // Add for Telephony Manager
    public void pauseMedia() {
        // Log.v(TAG, "Pause Media");
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }
    public void stopMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }
    // methods for notification
    // Create Notification
    private void initNotification() {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

        CharSequence tickerText = "Tutorial: Music In Service";
        long when = System.currentTimeMillis();

        Context context = getApplicationContext();
        CharSequence contentTitle = "Music In Service App Tutorial";
        CharSequence contentText = "Listen To Music While Performing Other Tasks";
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                notificationIntent, 0);
//        Notification notification = new Notification(icon, tickerText, when);
//        notification.flags = Notification.FLAG_ONGOING_EVENT;
//        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        Notification notification = new Notification.Builder(this)
                .setTicker(tickerText)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(contentIntent)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }
    // Cancel Notification
    private void cancelNotification() {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

    // methods for broadcasting
    // Send a message to Activity that audio is being prepared and buffering
    // started.
    private void sendBufferingBroadcast() {
        // Log.v(TAG, "BufferStartedSent");
        bufferIntent.putExtra("buffering", "1");
        sendBroadcast(bufferIntent);
    }
    // Send a message to Activity that audio is prepared and ready to start
    // playing.
    private void sendBufferCompleteBroadcast() {
        // Log.v(TAG, "BufferCompleteSent");
        bufferIntent.putExtra("buffering", "0");
        sendBroadcast(bufferIntent);
    }

    // headset
    // declaration for headset
    // If headset gets unplugged, stop music and service.
    private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        private boolean headsetConnected = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            // Log.v(TAG, "ACTION_HEADSET_PLUG Intent received");
            if (intent.hasExtra("state")) {
                if (headsetConnected && intent.getIntExtra("state", 0) == 0) {
                    headsetConnected = false;
                    headsetSwitch = 0;
                    // Log.v(TAG, "State =  Headset disconnected");
                    // headsetDisconnected();
                } else if (!headsetConnected
                        && intent.getIntExtra("state", 0) == 1) {
                    headsetConnected = true;
                    headsetSwitch = 1;
                    // Log.v(TAG, "State =  Headset connected");
                }
            }
            switch (headsetSwitch) {
                case (0):
                    headsetDisconnected();
                    break;
                case (1):
                    break;
            }
        }
    };
    // methods for headset
    private void headsetDisconnected() {
        stopMedia();
        stopSelf();

    }
    // Send a message to Activity to reset the play button.
    private void resetButtonPlayStopBroadcast() {
        // Log.v(TAG, "BufferCompleteSent");
        bufferIntent.putExtra("buffering", "2");
        sendBroadcast(bufferIntent);
    }

    // methods for seekBar
    private void setupHandler() {
        handler.removeCallbacks(sendUpdatesToUI);
        handler.postDelayed(sendUpdatesToUI, 1000); // 1 second
    }
    private Runnable sendUpdatesToUI = new Runnable() {
        public void run() {
            //Log.d(TAG, "entered sendUpdatesToUI");
            LogMediaPosition();
            handler.postDelayed(this, 1000); // 1 seconds
        }
    };
    private void LogMediaPosition() {
        //Log.d(TAG, "entered LogMediaPosition");
        if (mediaPlayer.isPlaying()) {
            mediaPosition = mediaPlayer.getCurrentPosition();
            mediaMax = mediaPlayer.getDuration();
            //seekIntent.putExtra("time", new Date().toLocaleString());
            try {
                seekIntent.putExtra("counter", String.valueOf(mediaPosition));
                seekIntent.putExtra("mediamax", String.valueOf(mediaMax)); // no need to send every second
                seekIntent.putExtra("song_ended", String.valueOf(songEnded));
                sendBroadcast(seekIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    // --Receive seekbar position if it has been changed by the user in the
    // activity
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSeekPos(intent);
        }
    };
    // Update seek position from Activity
    public void updateSeekPos(Intent intent) {
        int seekPos = intent.getIntExtra("seekpos", 0);
        if (mediaPlayer.isPlaying()) {
            handler.removeCallbacks(sendUpdatesToUI);
            mediaPlayer.seekTo(seekPos);
            setupHandler();
        }
    }
}
