package com.example.raymond.musicplayer10;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener{

    // declaration for FileChooser
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 6384; // onActivityResult request

    // declaration for folderName
    File filePath;
    File dir;
    String musicFolderName;
    public static String musicFolderFullName;

    // copy from MusicPlayer9
    // declaration for intent
    Intent serviceIntent;

    // declaration for UIs
    private Button buttonPlayStop;

    // declaration for UIstate
    private boolean boolMusicPlaying = false;

    // declaration for song properties
    //String strAudioLink = "10.mp3";
    String strAudioLink = "/mnt/shared/AndroidSDCard/music/HelloVietnam.mp3";

    // declaration for connectivity
    private boolean isOnline;

    // declaration for ProgressDialog and receiver variable
    boolean mBufferBroadcastIsRegistered;
    private ProgressDialog pdBuff = null;

    // declaration for seekBar
    private SeekBar seekBar;
    private int seekMax;
    private static int songEnded = 0;
    boolean mBroadcastIsRegistered;

    public static final String BROADCAST_SEEKBAR = "com.glowingpigs.tutorialstreamaudiopart1b.sendseekbar";
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("MP3 player");
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // initialize name of music folder
        musicFolderName = "/music";

        // Create initialized folder and load a song into it
        makeMusicFolder();

        // copy from MusicPlayer9
        try {
            serviceIntent = new Intent(this, myPlayService.class);

            initViews();
            setListeners();

            // --- set up seekbar intent for broadcasting new position to service ---
            intent = new Intent(BROADCAST_SEEKBAR);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                    e.getClass().getName() + " " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Toast.makeText(getApplicationContext(), "You press settings", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_browsing) {
            Toast.makeText(getApplicationContext(), "You press browsing", Toast.LENGTH_SHORT).show();
            // Display the file chooser dialog
            showChooser();
        }

        return super.onOptionsItemSelected(item);
    }

    // methods for FileChooser intent
    private void showChooser() {
        // Use the GET_CONTENT intent from the utility class
        Intent target = FileUtils.createGetContentIntent();
        // Create the chooser Intent
        Intent intent = Intent.createChooser(
                target, getString(R.string.chooser_title));
        try {
            startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            // The reason for the existence of aFileChooser
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE:
                // If the file selection was successful
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        final Uri uri = data.getData();
                        Log.i(TAG, "Uri = " + uri.toString());
                        try {
                            // Get the file path from the URI
                            final String path = FileUtils.getPath(this, uri);
                            Toast.makeText(MainActivity.this,
                                    "File Selected: " + path, Toast.LENGTH_LONG).show();

                            // Get songName and start service
                            strAudioLink = path;
                            buttonPlayStop.setBackgroundResource(R.drawable.pausebuttonsm);
                            playAudio();
                            boolMusicPlaying = true;

                        } catch (Exception e) {
                            Log.e("FileSelectorTestAct", "File select error", e);
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // methods for making Music Folder
    private void makeMusicFolder() {
        int testMode = 1;
        switch (testMode){
            case 0: //[0] will save in shared Folder (testMode)
                dir = new File("/mnt/shared/AndroidSDCard" + musicFolderName);
                break;
            case 1: //[1] will save in internal memory sdcard/DCIM
                //filePath = Environment.getExternalStorageDirectory();
                filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                dir = new File(filePath.getAbsolutePath() + musicFolderName);
                break;
            case 2: //[2] will save in removable disk /Removable/MicroSD (for Asus test only)
                dir = new File("/Removable/MicroSD" + musicFolderName);
                break;
            default:
                break;
        }
        // Create imgFolderFullName for RecordDatabase class use
        musicFolderFullName = dir.toString();
        if(dir.exists() == false) {
            dir.mkdirs();
            Toast.makeText(getApplicationContext(),
                    musicFolderFullName + " is created",
                    Toast.LENGTH_LONG).show();


        } else {
            Toast.makeText(getApplicationContext(),
                    musicFolderFullName + " is existed",
                    Toast.LENGTH_LONG).show();
        }

        // Write the mp3 file to dir
        AssetManager assetManager = getApplicationContext().getAssets();

        InputStream in = null;
        OutputStream out = null;
        try
        {
            String songName = "hello.mp3";
            in = assetManager.open(songName);
            String newFileName = musicFolderFullName + "/" + songName;
            strAudioLink = newFileName;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            //Utility.printLog("tag", e.getMessage());
        }finally{
            if(in!=null){
                try {
                    in.close();
                } catch (IOException e) {
                    //printLog(TAG, "Exception while closing input stream",e);
                }
            }
            if(out!=null){
                try {
                    out.close();
                } catch (IOException e) {
                    //printLog(TAG, "Exception while closing output stream",e);
                }
            }
        }
    }

    // copy from MusicPlayer9
    // --- Set up initial screen ---
    private void initViews() {
        buttonPlayStop = (Button) findViewById(R.id.ButtonPlayStop);
        buttonPlayStop.setBackgroundResource(R.drawable.playbuttonsm);

        seekBar = (SeekBar) findViewById(R.id.SeekBar01);
    }
    // --- Set up listeners ---
    private void setListeners() {
        buttonPlayStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonPlayStopClick();
            }
        });

        seekBar.setOnSeekBarChangeListener(this);
    }

    private void buttonPlayStopClick() {
        if (!boolMusicPlaying) {
            buttonPlayStop.setBackgroundResource(R.drawable.pausebuttonsm);
            playAudio();
            boolMusicPlaying = true;
        } else {
            if (boolMusicPlaying) {
                buttonPlayStop.setBackgroundResource(R.drawable.playbuttonsm);
                stopMyPlayService();
                boolMusicPlaying = false;
            }
        }
    }

    // methods for service
    private void stopMyPlayService() {
        // --Unregister broadcastReceiver for seekbar
        if (mBroadcastIsRegistered) {
            try {
                unregisterReceiver(broadcastReceiver);
                mBroadcastIsRegistered = false;
            } catch (Exception e) {
                // Log.e(TAG, "Error in Activity", e);
                // TODO Auto-generated catch block
                e.printStackTrace();
                Toast.makeText(getApplicationContext(),
                        e.getClass().getName() + " " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        // stop MediaPlayer service
        try {
            stopService(serviceIntent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                    e.getClass().getName() + " " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        boolMusicPlaying = false;
    }
    private void playAudio() {
        checkConnectivity();
        if (isOnline) {
            stopMyPlayService();
            serviceIntent.putExtra("sentAudioLink", strAudioLink);
            try {
                startService(serviceIntent);
            } catch (Exception e) {

                e.printStackTrace();
                Toast.makeText(
                        getApplicationContext(),
                        e.getClass().getName() + " " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            // -- Register receiver for seekbar--
            registerReceiver(broadcastReceiver, new IntentFilter(myPlayService.BROADCAST_ACTION));
            mBroadcastIsRegistered = true;
        } else {
//            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
//            alertDialog.setTitle("Network Not Connected...");
//            alertDialog.setMessage("Please connect to a network and try again");
//
//            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    // here you can add functions
//                }
//            });
//            alertDialog.setIcon(R.drawable.icon);
//
//            alertDialog.show();
            AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
            adBuilder.setTitle("Network Not Connected...");
            adBuilder.setMessage("Please connect to a network and try again");
            adBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });
            adBuilder.setIcon(R.drawable.icon);
            AlertDialog alertDialog = adBuilder.create();
            alertDialog.show();
            buttonPlayStop.setBackgroundResource(R.drawable.playbuttonsm);
        }
    }

    // methods for connectivity
    private void checkConnectivity() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                .isConnectedOrConnecting()
                || cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                .isConnectedOrConnecting())
            isOnline = true;
        else
            isOnline = false;
    }

    // methods for PD
    // Handle progress dialogue for buffering...
    private void showPD(Intent bufferIntent) {
        String bufferValue = bufferIntent.getStringExtra("buffering");
        int bufferIntValue = Integer.parseInt(bufferValue);

        // When the broadcasted "buffering" value is 1, show "Buffering"
        // progress dialogue.
        // When the broadcasted "buffering" value is 0, dismiss the progress
        // dialogue.

        switch (bufferIntValue) {
            case 0:
                // Log.v(TAG, "BufferIntValue=0 RemoveBufferDialogue");
                // txtBuffer.setText("");
                if (pdBuff != null) {
                    pdBuff.dismiss();
                }
                break;

            case 1:
                BufferDialogue();
                break;

            // Listen for "2" to reset the button to a play button
            case 2:
                buttonPlayStop.setBackgroundResource(R.drawable.playbuttonsm);
                break;

        }
    }
    // Progress dialogue...
    private void BufferDialogue() {
        pdBuff = ProgressDialog.show(MainActivity.this, "Buffering...",
                "Acquiring song...", true);
    }
    // Set up broadcast receiver
    private BroadcastReceiver broadcastBufferReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent bufferIntent) {
            showPD(bufferIntent);
        }
    };

    // methods of MainActivity
    @Override
    protected void onPause() {
        // Unregister broadcast receiver
        if (mBufferBroadcastIsRegistered) {
            unregisterReceiver(broadcastBufferReceiver);
            mBufferBroadcastIsRegistered = false;
        }
        // no need unregister seekBar receiver
        super.onPause();
    }
    @Override
    protected void onResume() {
        // Register broadcast receiver
        if (!mBufferBroadcastIsRegistered) {
            registerReceiver(broadcastBufferReceiver, new IntentFilter(
                    myPlayService.BROADCAST_BUFFER));
            mBufferBroadcastIsRegistered = true;
        }
        // Register seekBar receiver
        if (!mBroadcastIsRegistered) {
            registerReceiver(broadcastReceiver, new IntentFilter(
                    myPlayService.BROADCAST_ACTION));
            mBroadcastIsRegistered = true;
        }
        super.onResume();
    }

    // methods for seekBar
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent serviceIntent) {
            updateUI(serviceIntent);
        }
    };
    private void updateUI(Intent serviceIntent) {
        String counter = serviceIntent.getStringExtra("counter");
        String mediamax = serviceIntent.getStringExtra("mediamax");
        String strSongEnded = serviceIntent.getStringExtra("song_ended");
        int seekProgress = Integer.parseInt(counter);
        seekMax = Integer.parseInt(mediamax);
        songEnded = Integer.parseInt(strSongEnded);
        seekBar.setMax(seekMax);
        seekBar.setProgress(seekProgress);
        if (songEnded == 1) {
            buttonPlayStop.setBackgroundResource(R.drawable.playbuttonsm);
        }
    }

    // methods implementing for OnSeekBarChange
    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
        if (fromUser) {
            int seekPos = seekBar.getProgress();
            intent.putExtra("seekpos", seekPos);
            sendBroadcast(intent);
        }
    }
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
