package com.chillimuffin.survey;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener
{

    private static final String TAG = "MainActivity";
    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;

    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;


    private WebView mWebView;
    private View mView;
    private Questions questions;
    private int currentQuestion = 0;

    private Camera2VideoFragment cameraFragment;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    Timer presenceTimer;
    boolean presenceTimerRunning = false;
    ArrayList<String> presenceData;

    Timer questionnaireTimer;
    TimerTask questionnaireTimerTask;
    boolean questionnaireTimerRunning = false;

    boolean questionnaireRunning = false;

    String[] demoData;
    int demoDataIndex;

    ArrayList<String> videosToUpload;
    int videoUploadIndex = 0;

    boolean BTfound = false;

    static final int ENABLE_BLUETOOTH_REQUEST = 1;
    static final int WRITE_EXTERNAL_STORAGE_PERMISSION = 2;

    public void showErrorAlert(String msg){
        new RemoteLogCat().i(TAG, "showErrorAlert(" + msg + ")");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle("Chyba")
                .setPositiveButton("Ukončiť", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void init() {
        new RemoteLogCat().i(TAG, "init()");
        try {
            questions = new Questions();
        } catch (IOException e) {
            new RemoteLogCat().i(TAG, e.getMessage());
            showErrorAlert("Načítanie otázok zlyhalo");
        }

        // Enable Javascript
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        mWebView.addJavascriptInterface(new JsObject(), "app");

        mWebView.loadUrl("file:///android_asset/html/index.html");
        mWebView.setOnTouchListener(clickHandler);

        presenceTimer = new Timer();
        presenceData = new ArrayList<>();

        videosToUpload = new ArrayList<>();
        
        questionnaireTimer = new Timer();

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new RemoteLogCat().i(TAG, "onCreate()");

        setContentView(R.layout.activity_main);

        cameraFragment = Camera2VideoFragment.newInstance();

        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, cameraFragment)
                    .commit();
        }

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        mWebView = (WebView) findViewById(R.id.activity_main_webview);
        WebView.setWebContentsDebuggingEnabled(true);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_EXTERNAL_STORAGE_PERMISSION);
        } else {
           init();
        }
    }

    void findBT() {
        new RemoteLogCat().i(TAG, "findBT()");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            new RemoteLogCat().i(TAG, "No bluetooth adapter available");
            showErrorAlert("Bluetooth nie je k dipozícií");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, ENABLE_BLUETOOTH_REQUEST);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("SAMOeurope"))
                {
                    mmDevice = device;
                    new RemoteLogCat().i(TAG, "Bluetooth Device Found");
                    BTfound = true;
                    break;
                }
            }
        }
    }

    void openBT() throws IOException {
        new RemoteLogCat().i(TAG, "openBT()");
        UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID

        try {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(SERIAL_UUID);
        } catch (Exception e) {
            System .out.println("Error creating socket");
            showErrorAlert("Pripojenie Bluetooth zlyhalo");
        }

        try {
            mmSocket.connect();
            new RemoteLogCat().i(TAG, "Connected");
        } catch (IOException e) {
            new RemoteLogCat().e("",e.getMessage());
            try {
                new RemoteLogCat().i(TAG, "trying fallback...");

                mmSocket =(BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                mmSocket.connect();

                new RemoteLogCat().i(TAG, "Connected");
            }
            catch (Exception e2) {
                new RemoteLogCat().i(TAG, "Couldn't establish Bluetooth connection!");
                showErrorAlert("Pripojenie Bluetooth zlyhalo");
            }
        }

        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        new RemoteLogCat().i(TAG, "Bluetooth Opened");
    }

    void evaluateInput(String value1, String value2) {
        new RemoteLogCat().i(TAG, "evaluateInput(" + value1 + "," + value2 + ")");
        String a = value1 + " " + value2;
        if(!questionnaireRunning) {
            if (!presenceTimerRunning) {
                if (value1.equals("1")) {
                    presenceTimerRunning = true;
                    presenceData.add(value1);
                    presenceTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            new RemoteLogCat().i(TAG, presenceData.toString());
                            if (computePresence(presenceData)) {
                                startQuestionnaire();
                            }
                            presenceData.clear();
                            presenceTimerRunning = false;
                        }
                    }, 3000);
                } else if (value1.equals("0")) {
                    new RemoteLogCat().i(TAG, "do nothing");
                }
            } else {
                presenceData.add(value1);
            }
        } else {
            new RemoteLogCat().i(TAG, "do nothing");
        }
    }

    boolean computePresence(ArrayList<String> list) {
        new RemoteLogCat().i(TAG, "computePresence(" + list + ")");
        int sum = 0;
        Iterator<String> iter = list.iterator();
        while (iter.hasNext()) {
            String pData = iter.next();
            sum += Integer.parseInt(pData);
        }
        double average = sum/list.size();
        if (average > 0.5) {
           return true;
        }
        return false;
    }

    void stopQuestionnaire() {
        questionnaireRunning = false;
        questionnaireTimerRunning = false;
        questionnaireTimerTask.cancel();
        new RemoteLogCat().i(TAG, "stopQuestionnaire()");
        currentQuestion = -1;
        mWebView.post(new Runnable() {
            public void run() {
                mWebView.evaluateJavascript("hideQuestion()", null);
            }
        });
        if(cameraFragment.ismIsRecordingVideo()) {
            String filename = cameraFragment.stopRecordingVideo();
            videosToUpload.add(filename);
            saveFileToGoogleDrive();
        }
    }

    void startQuestionnaire() {
        questionnaireRunning = true;
        startQuestionnaireTimer();
        if(!cameraFragment.ismIsRecordingVideo()) {
            cameraFragment.startRecordingVideo();
        }
        new RemoteLogCat().i(TAG, "startQuestionnaire()");
        currentQuestion = -1;
        showNextQuestion();
        mWebView.post(new Runnable() {
            public void run() {
                mWebView.evaluateJavascript("vid.pause()", null);
            }
        });
    }

    void showNextQuestion(){
        new RemoteLogCat().i(TAG, "showNextQuestion()");
        if (currentQuestion >= questions.getLength()-1) {
            mWebView.post(new Runnable() {
                public void run() {
                    mWebView.evaluateJavascript("vid.play()", null);
                }
            });
            return;
            /*cakaj*/
        } else {
            mWebView.post(new Runnable() {
                public void run() {
                    currentQuestion++;
                    mWebView.evaluateJavascript("showQuestion('" + questions.getQuestion(currentQuestion) + "')", null);
                }
            });
        }
    }

    void connectToDeviceAndListenData() {
        new RemoteLogCat().i(TAG, "connectToDeviceAndListenData()");
        try {
            findBT();
            if(BTfound) {
                openBT();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Neboli nájdené žiadne spárované zariadenia")
                        .setPositiveButton("Skúsiť znova", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                try {
                                    openBT();
                                } catch (Exception e) {
                                    new RemoteLogCat().i(TAG, e.getMessage());
                                    showErrorAlert("Pripojenie Bluetooth zlyhalo");
                                }
                            }
                        })
                        .setNegativeButton("Zrušiť", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        } catch (IOException ex) {
            new RemoteLogCat().i(TAG, ex.getMessage());
            showErrorAlert("Pripojenie Bluetooth zlyhalo");
        }

       beginListenForData();
    }

    void connectToMockDeviceAndListenData() {
        new RemoteLogCat().i(TAG, "connectToMockDeviceAndListenData()");
        beginListenForMockData();
    }

    void beginListenForMockData() {
        new RemoteLogCat().i(TAG, "beginListenForMockData()");
        final Handler handler = new Handler();
        demoData = new String[64];
        demoData[0] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[1] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[2] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[3] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[4] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[5] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[6] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[7] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[8] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[9] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[10] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[11] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[12] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[13] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[14] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[15] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[16] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[17] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[18] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[19] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[20] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[21] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[22] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[23] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[24] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[25] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[26] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[27] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[28] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[29] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[30] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[31] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[32] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[33] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[34] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[35] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[36] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[37] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[38] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[39] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[40] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[41] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[42] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[43] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[44] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[45] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[46] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[47] = "$XXX,1,0,0,0,0,0,0,10";
        demoData[48] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[49] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[50] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[51] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[52] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[53] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[54] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[55] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[56] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[57] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[58] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[59] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[60] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[61] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[62] = "$XXX,0,0,0,0,0,0,0,10";
        demoData[63] = "$XXX,0,0,0,0,0,0,0,10";
        demoDataIndex = 0;

        stopWorker = false;
        new Thread(new Runnable() {
            public void run() {
                while(!Thread.currentThread().isInterrupted() && !stopWorker) {
                    handler.post(new Runnable() {
                        public void run() {
                            demoDataIndex++;
                            if(demoDataIndex >= demoData.length){
                                demoDataIndex = 0;
                            }
                            String data = demoData[demoDataIndex];
                            new RemoteLogCat().i(TAG, "Data recieved: " + data);
                            evaluateInput(data.split(",")[1],  data.split(",")[2]);
                        }
                    });
                    try {
                        Thread.sleep(990);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    void beginListenForData() {
        new RemoteLogCat().i(TAG, "beginListenForData()");
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        new Thread(new Runnable() {
            public void run() {
                while(!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++) {
                                byte b = packetBytes[i];
                                if(b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    new RemoteLogCat().i(TAG, "Data recieved: " + data);
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run() {
                                            evaluateInput(data.split(",")[1],  data.split(",")[2]);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        } else {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        }).start();
    }

    void sendData(String msg) throws IOException {
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        new RemoteLogCat().i(TAG, "Data Sent");
    }

    void closeBT() {
        new RemoteLogCat().i(TAG, "closeBT()");
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            new RemoteLogCat().i(TAG, "Bluetooth Closed");
        } catch (IOException e){
            new RemoteLogCat().i(TAG, "Bluetooth failed to close " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check which request we're responding to
        if (requestCode == ENABLE_BLUETOOTH_REQUEST) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                findBT();
                try {
                    openBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    init();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }

    View.OnTouchListener clickHandler = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v.getId() == R.id.activity_main_webview && event.getAction() == MotionEvent.ACTION_DOWN){
                new RemoteLogCat().i(TAG, "CLICKED");
            }
            return false;
        }
    };

    /**
     * Called when activity gets invisible. Connection to Drive service needs to
     * be disconnected as soon as an activity is invisible.
     */
    @Override
    protected void onPause() {
        new RemoteLogCat().i(TAG, "onPause()");
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        stopWorker = true;
        //cameraFragment.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        new RemoteLogCat().i(TAG, "onResume()");
        super.onResume();
        //cameraFragment.onResume();
        //connectToMockDeviceAndListenData();
        //connectToDeviceAndListenData();
        mWebView.evaluateJavascript("showVideo()", null);
    }

    @Override
    protected void onDestroy() {
        new RemoteLogCat().i(TAG, "onDestroy()");
        super.onDestroy();
        closeBT();
    }

    protected void saveFileToGoogleDrive() {
        new RemoteLogCat().i(TAG, "saveFileToGoogleDrive()");
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    //.addScope(Drive.SCOPE_APPFOLDER) // required for App Folder sample
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        if(!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        } else {
            String path =
                    Environment.getExternalStorageDirectory().toString() + File.separator + "survey-app" + File.separator + "recorded";
            // Create the folder.
            File folder = new File(path);
            // Create the file.
            String lastFileName = cameraFragment.getmNextVideoFileName();
            File file = new File(folder, lastFileName);
            createGoogleDriveFile(lastFileName, "video/mp4", file);
        }
    }

    // JavaScript interface object
    public class JsObject {

        @JavascriptInterface
        public String getVideos() {
            new RemoteLogCat().i("JavascriptInterface", "getVideos()");
            String path = Environment.getExternalStorageDirectory().toString() + File.separator + "survey-app" + File.separator + "videos";
            File folder = new File(path);

            File[] listOfFiles = folder.listFiles();
            String videos = "[";

            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    videos = videos.concat("\"")
                            .concat(path)
                            .concat("/")
                            .concat(listOfFiles[i].getName())
                            .concat("\",");
                    new RemoteLogCat().i("JavascriptInterface", "File " + listOfFiles[i].getName());
                } else if (listOfFiles[i].isDirectory()) {
                    new RemoteLogCat().i("JavascriptInterface", "Directory " + listOfFiles[i].getName());
                }
            }
            videos = videos.substring(0, videos.length() - 1);
            videos = videos.concat("]");
            new RemoteLogCat().i("JavascriptInterface", videos);
            return videos;
        }

        @JavascriptInterface
        public void saveAnswer(String value) {
            new RemoteLogCat().i("JavascriptInterface", "saveAnswer()");
            saveQuestionnaireAnswer(value);
        }

        @JavascriptInterface
        public void loaded() {
            new RemoteLogCat().i("JavascriptInterface", "loaded()");
            connectToDeviceAndListenData();
            //connectToMockDeviceAndListenData();
        }

        @JavascriptInterface
        public void log(String msg) {
            new RemoteLogCat().i("JavascriptInterface", msg);
        }
    }

    private void startQuestionnaireTimer() {
        new RemoteLogCat().i(TAG, "startQuestionnaireTimer()");
        questionnaireTimerRunning = true;
        questionnaireTimerTask = new TimerTask() {
            @Override
            public void run() {
                stopQuestionnaire();
                mWebView.post(new Runnable() {
                    public void run() {
                        mWebView.evaluateJavascript("vid.play()", null);
                    }
                });
            }
        };
        questionnaireTimer.schedule(questionnaireTimerTask, 10 * 1000);
    }

    private void saveQuestionnaireAnswer(String value) {
        new RemoteLogCat().i(TAG, "saveQuestionnaireAnswer( " + value + "), currentQuestion: " + currentQuestion);
        questionnaireTimerTask.cancel();
        questionnaireTimerRunning = false;
        startQuestionnaireTimer();
        final String[] data = {(new Timestamp(System.currentTimeMillis())).toString(), Integer.toString(currentQuestion), questions.getQuestion(currentQuestion), value, cameraFragment.getmNextVideoFileName()};
        new Thread() {
            @Override
            public void run() {
                try {
                    questions.saveAnswer(data);
                } catch (IOException e) {
                    e.printStackTrace();
                    showErrorAlert("Uloženie odpovede zlyhalo");
                }
            }
        }.start();
        showNextQuestion();
    }

    void createGoogleDriveFile(final String titl, final String mime, final File file) {
        new RemoteLogCat().i(TAG, "createGoogleDriveFile(" + titl + "," + mime + "," + file.getName() + ")");
        DriveId dId = null;
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected() && titl != null && mime != null && file != null) try {

            Drive.DriveApi.newDriveContents(getGoogleApiClient()).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    final DriveContents driveContents = result.getDriveContents();

                    new Thread() {
                        @Override
                        public void run() {
                    OutputStream outputStream = driveContents.getOutputStream();
                    FileInputStream fileInputStream = null;
                    try {
                        fileInputStream = new FileInputStream(file);

                        // Copy the contents of the file to the output stream
                        byte[] buffer = new byte[1024];
                        int count = 0;

                        while ((count = fileInputStream.read(buffer)) >= 0) {
                            outputStream.write(buffer, 0, count);
                        }
                        outputStream.close();
                        file.delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                            .setTitle(titl)
                            .setMimeType(mime)
                            .setStarred(true).build();

                    // create a file on root folder
                    Drive.DriveApi.getRootFolder(getGoogleApiClient())
                            .createFile(getGoogleApiClient(), changeSet, driveContents)
                            .setResultCallback(fileCallback);

                        }
                    }.start();
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    final private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback = new
            ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create new file contents");
                        return;
                    }
                    final DriveContents driveContents = result.getDriveContents();
                    String path =
                            Environment.getExternalStorageDirectory().toString() + File.separator + "survey-app" + File.separator + "recorded";
                    // Create the folder.
                    File folder = new File(path);
                    // Create the file.
                    String lastFileName = cameraFragment.getmNextVideoFileName();
                    File file = new File(folder, lastFileName);
                    createGoogleDriveFile(lastFileName, "video/mp4", file);
                }
            };

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new
            ResultCallback<DriveFolder.DriveFileResult>() {
                @Override
                public void onResult(DriveFolder.DriveFileResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create the file");
                        return;
                    }
                    //showMessage("Created a file with content: " + result.getDriveFile().getDriveId());
                    new RemoteLogCat().i(TAG, "Created a file with content: " + result.getDriveFile().getDriveId());
                }
            };

    /**
     * Called when {@code mGoogleApiClient} is connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        new RemoteLogCat().i(TAG, "GoogleApiClient connected");
        // create new contents resource
        Drive.DriveApi.newDriveContents(getGoogleApiClient())
                .setResultCallback(driveContentsCallback);
    }

    /**
     * Called when {@code mGoogleApiClient} is disconnected.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        new RemoteLogCat().i(TAG, "GoogleApiClient connection suspended");
    }

    /**
     * Called when {@code mGoogleApiClient} is trying to connect but failed.
     * Handle {@code result.getResolution()} if there is a resolution is
     * available.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        new RemoteLogCat().i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            new RemoteLogCat().e(TAG, "Exception while starting resolution activity " + e.getMessage());
        }
    }

    /**
     * Shows a toast message.
     */
    public void showMessage(String message) {
        new RemoteLogCat().i(TAG, "showMessage(" + message + ")");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Getter for the {@code GoogleApiClient}.
     */
    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

}