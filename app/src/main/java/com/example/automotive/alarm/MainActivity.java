package com.example.automotive.alarm;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int    REQUEST_SELECT_DEVICE = 1;
    private static final int    REQUEST_ENABLE_BT = 2;
    public static final String  TAG = "AUTOMOTIVE ALARM";
    private static final int    UART_PROFILE_CONNECTED = 20;
    private static final int    UART_PROFILE_DISCONNECTED = 21;
    private int                 mState = UART_PROFILE_DISCONNECTED;
    
    public static BluetoothGatt mBluetoothGatt_Pre;       // Previously sending gatt information
    public static BLEService    mService = null;
    private BluetoothDevice     mDevice = null;
    public static boolean       isDeviceConnected = false;
    private BluetoothAdapter    mBtAdapter = null;
    public static boolean       mbSendTime = false;       // If time sending
    
    // Layout parameters
    private LinearLayout        imagelayout, setuplayout;
    private ImageView           imgPlay;
    private RecyclerView        recyclerView;
    private TextView            txt_time;
    private Button              btnConnectDisconnect;
    private CustomVideoView     myVideoView;
    private MediaController     mediaControls;
    private boolean             running = true;
    
    private Spinner mSpinner_ldws_Alarm, mSpinner_fcws_Alarm, mSpinner_bsd_Alarm;
    private Spinner mSpinner_ldws_Strength, mSpinner_fcws_Strength, mSpinner_bsd_Strength;
    private byte[]  sendMsg_ldws, sendMsg_fcws, sendMsg_bsd;

    private String videoPath;
    private int position = 0;

    boolean isSetupLayoutShow = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        if (mediaControls == null) {
            mediaControls = new MediaController(MainActivity.this)
            {
                @Override
                public void hide()
                {
                    mediaControls.show();
                }
            };
        }

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
		setuplayout = (LinearLayout)findViewById(R.id.ln_setup);
        imgPlay = (ImageView)findViewById(R.id.img_play);
        txt_time = (TextView)findViewById(R.id.txt_time);
        btnConnectDisconnect=(Button) findViewById(R.id.btnConnect);
        myVideoView = (CustomVideoView) findViewById(R.id.video_view);
        myVideoView.setBackgroundResource(R.drawable.car_background);
//        recyclerView = (RecyclerView)findViewById(R.id.recyclerViewDevices);
//        recyclerView.setHasFixedSize(true);

        imagelayout = (LinearLayout) findViewById(R.id.image_layout);
        //imagelayout.setEnabled(false);
        imagelayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSetupLayoutShow == true){
					setuplayout.setVisibility(View.INVISIBLE);
					imgPlay.setVisibility(View.INVISIBLE);
					isSetupLayoutShow = false;  // Set setup layout is disappeared
                    mBluetoothGatt_Pre = null;

                    init_playVideo();
					startVideo();
				}
            }
        });

        service_init();
        btnConnectDisconnect.setOnClickListener(new ProcessMyEvent());

        ldws_addItemForSpinner();
        fcws_addItemForSpinner();
        bsd_addItemForSpinner();
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(mediaControls != null) mediaControls.show(0);
        return super.onTouchEvent(event);
    }
    public void setUiEnabled(boolean bool)
    {
        if(bool){
            btnConnectDisconnect.setText("Disconnect");
            imgPlay.setImageResource(R.drawable.press_icon);
        }
        else{
            btnConnectDisconnect.setText("Connect");
            imgPlay.setImageResource(R.drawable.blur_icon);
        }
        Log.d(TAG, "bool " + bool);
        //imagelayout.setEnabled(bool);
    }
    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((BLEService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.d(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            String crrentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            Log.d(TAG,"["+crrentDateTimeString+"] onReceive");
            //*********************//
            if (action.equals(BLEService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
                        setUiEnabled(true);
                        //Log.d(TAG, "["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(BLEService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        setUiEnabled(false);
                        stopVideo();
                        //Log.d(TAG,"["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();;
                    }
                });
            }

            //*********************//
            if (action.equals(BLEService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(BLEService.ACTION_DATA_AVAILABLE)) {
                String crentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                Log.d(TAG,"["+crentDateTimeString+"]ACTION_DATA_AVAILABLE");
                final byte[] txValue = intent.getByteArrayExtra(BLEService.EXTRA_DATA);
				String address = intent.getStringExtra("Address");

				if (BLEService.mBluetoothGatt != null && BLEService.mBluetoothGatt.getDevice().getAddress().equals(address)) {
					mBluetoothGatt_Pre = BLEService.mBluetoothGatt;
				}

                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String text = new String(txValue, "UTF-8");
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            //Log.d(TAG,"["+currentDateTimeString+"]ACTION_DATA_AVAILABLE");
                            Log.d(TAG,"["+currentDateTimeString+"] RX: "+text);
                            //showMessage("["+currentDateTimeString+"] RX: "+text);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });

                mService.writeRXCharacteristic(txValue);
            }
            //*********************//
            if (action.equals(BLEService.DEVICE_DOES_NOT_SUPPORT_UART)){

                showMessage("Device is not supported. Disconnecting");
                mService.disconnect();
            }
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BLEService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BLEService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        stopVideo();
    }

    @Override
    protected void onPause() {
        position = myVideoView.getCurrentPosition();
        myVideoView.pause();
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        if (position != 0) {
            myVideoView.seekTo(position);
            myVideoView.setBackgroundResource(R.color.transparent);
            myVideoView.start();
        } else {
            setuplayout.setVisibility(View.VISIBLE);
            imgPlay.setVisibility(View.VISIBLE);
            myVideoView.requestLayout();
            isSetupLayoutShow = true;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("App is running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }

    public void init_playVideo() {
        videoPath = "android.resource://" + getPackageName() + "/" + R.raw.automotive_video;
        Uri uri = Uri.parse(videoPath);
        myVideoView.suspend();

        mediaControls = new MediaController(this, false);
        mediaControls.setVisibility(View.INVISIBLE);
        mediaControls.setAnchorView(myVideoView);
        myVideoView.setMediaController(mediaControls);
        myVideoView.setBackgroundResource(R.color.transparent);
        myVideoView.setVideoURI(uri);
        myVideoView.requestFocus();

        myVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        stopVideo();
                        setuplayout.setVisibility(View.VISIBLE);
                        imgPlay.setVisibility(View.VISIBLE);
                        myVideoView.requestLayout();
                        isSetupLayoutShow = true;
                        Log.d(TAG,"onCompletion");
                        running = false;
                        txt_time.setText("0");
                    }
                });
                final int duration = myVideoView.getDuration();
                final int[] time = {0};
                final boolean[] send_ldws_left_flag = {true},  send_ldws_right_flag = {true};
                final boolean[] send_fcws_flag ={true};
                final boolean[] send_bsd_left_flag ={true}, send_bsd_right_flag ={true};

                sendMsg_ldws[0] = 0x43; sendMsg_fcws[0] = 0x43; sendMsg_bsd[0] = 0x43;  // C
                sendMsg_ldws[1] = 0x4B;  sendMsg_fcws[1] = 0x4B;  sendMsg_bsd[1] = 0x4B;  //  K
                for(int i = 5; i < 11; i++) { sendMsg_ldws[i] = 0x00; }  // Reserved
                for(int i = 5; i < 11; i++) { sendMsg_fcws[i] = 0x00; }  // Reserved
                for(int i = 5; i < 11; i++) { sendMsg_bsd[i] = 0x00; }  // Reserved
                sendMsg_ldws[11] = 0x0A; sendMsg_fcws[11] = 0x0A; sendMsg_bsd[11] = 0x0A; // Finish

                new Thread(new Runnable() {
                    public void run() {
                        do{
                            txt_time.post(new Runnable() {
                                public void run() {
                                    time[0] = (duration - myVideoView.getCurrentPosition())/1000;
                                    txt_time.setText(time[0] +"");
                                }
                            });
                            //time[0] = (duration - myVideoView.getCurrentPosition())/1000;
                            switch (time[0]){
                                case 38:
                                    sendMsg_ldws[3] = 0x01;
                                    if (send_ldws_left_flag[0]){
                                        mService.writeRXCharacteristic(sendMsg_ldws);
                                        send_ldws_left_flag[0] = false;
                                        Log.d("HJ", "sendMsg ldws left " + "\n");
//                                        for(int i = 0; i < sendMsg_ldws.length; i++){
//                                            Log.d("HJ", "sendMsg_ldws1["+i+"] = " + sendMsg_ldws[i] + "\n");
//                                        }
                                    }
                                    break;
                                case 29:
                                    sendMsg_ldws[3] = 0x02;
                                    if (send_ldws_right_flag[0]){
                                        mService.writeRXCharacteristic(sendMsg_ldws);
                                        send_ldws_right_flag[0] = false;
//                                        for(int i = 0; i < sendMsg_ldws.length; i++){
//                                            Log.d("HJ", "sendMsg_ldws2["+i+"] = " + sendMsg_ldws[i] + "\n");
//                                        }
                                        Log.d("HJ", "sendMsg ldws right " + "\n");
                                    }
                                    break;
                                case 21:
                                    sendMsg_fcws[3] = 0x01;
                                    if (send_fcws_flag[0]){
                                        mService.writeRXCharacteristic(sendMsg_fcws);
                                        send_fcws_flag[0] = false;
//                                        for(int i = 0; i < sendMsg_fcws.length; i++){
//                                            Log.d("HJ", "sendMsg_fcws["+i+"] = " + sendMsg_fcws[i] + "\n");
//                                        }
                                        Log.d("HJ", "sendMsg fcws " + "\n");
                                    }
                                    break;
                                case 13:
                                    sendMsg_bsd[3] = 0x01;
                                    if (send_bsd_left_flag[0]) {
                                        mService.writeRXCharacteristic(sendMsg_bsd);
                                        send_bsd_left_flag[0] = false;
//                                        for(int i = 0; i < sendMsg_bsd.length; i++){
//                                            Log.d("HJ", "sendMsg_bsd1["+i+"] = " + sendMsg_bsd[i] + "\n");
//                                        }
                                        Log.d("HJ", "sendMsg bsd left " + "\n");
                                    }
                                    break;
                                case 4:
                                    sendMsg_bsd[3] = 0x02;
                                    if (send_bsd_right_flag[0]) {
                                        mService.writeRXCharacteristic(sendMsg_bsd);
                                        send_bsd_right_flag[0] = false;
//                                        for(int i = 0; i < sendMsg_bsd.length; i++){
//                                            Log.d("HJ", "sendMsg_bsd2["+i+"] = " + sendMsg_bsd[i] + "\n");
//                                        }
                                        Log.d("HJ", "sendMsg bsd right " + "\n");
                                    }
                                    break;
                            }

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if(!running) break;
                        }
                        while(myVideoView.getCurrentPosition()<duration);
                    }
                }).start();
            }
        });
    }

    public void startVideo(){
        running = true;
        myVideoView.start();
    }
    public void stopVideo(){

        if (videoPath != null) {
            init_playVideo();
            myVideoView.invalidate();
        }

        if (myVideoView !=null){
            myVideoView.stopPlayback();
            myVideoView.resume();
            myVideoView.setBackgroundResource(R.drawable.car_background);
            myVideoView.refreshDrawableState();
        }

        txt_time.setText("0");
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        //savedInstanceState.putInt("Position", myVideoView.getCurrentPosition());
        //myVideoView.pause();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        position = savedInstanceState.getInt("Position");
        myVideoView.seekTo(position);
    }
    private class ProcessMyEvent implements View.OnClickListener {
        @Override
        public void onClick(View arg0) {
            if (arg0.getId() == R.id.btnConnect){
                Log.i(TAG, "Connect button onClick" );
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (btnConnectDisconnect.getText().equals("Connect")){
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice!=null || isDeviceConnected)
                        {
                            stopVideo();
                            mService.disconnect();
                            isDeviceConnected = false;
                        }
                    }
                }
            }
        }
    }

    private void ldws_addItemForSpinner() {
        sendMsg_ldws = new byte[12];
        mSpinner_ldws_Alarm = (Spinner) findViewById(R.id.mSpinner_ldws_Alarm);
        mSpinner_ldws_Strength = (Spinner) findViewById(R.id.mSpinner_ldws_Strength);

        // Strength
        final List<Byte> list_strength = new ArrayList<Byte>();
        final List<String> list_name_strength = new ArrayList<String>();

        list_strength.add((byte) 0x01);
        list_name_strength.add("Level 1");
        list_strength.add((byte) 0x02);
        list_name_strength.add("Level 2");
        list_strength.add((byte) 0x03);
        list_name_strength.add("Level 3");
        list_strength.add((byte) 0x04);
        list_name_strength.add("Level 4");
        list_strength.add((byte) 0x05);
        list_name_strength.add("Level 5");
        list_strength.add((byte) 0x06);
        list_name_strength.add("Level 6");
        list_strength.add((byte) 0x07);
        list_name_strength.add("Level 7");
        list_strength.add((byte) 0x08);
        list_name_strength.add("Level 8");
        list_strength.add((byte) 0x09);
        list_name_strength.add("Level 9");
        list_strength.add((byte) 0x0A);
        list_name_strength.add("Level 10");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list_name_strength);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner_ldws_Strength.setAdapter(dataAdapter);

        mSpinner_ldws_Strength.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                sendMsg_ldws[4] = list_strength.get(pos);
//                Log.d("HJ", "strength " + sendMsg_ldws[4]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Pattern type
        final List<Byte> list_pattern = new ArrayList<Byte>();
        final List<String> list_name_pattern = new ArrayList<String>();

        list_pattern.add((byte) 0x11);
        list_name_pattern.add("LDWS 1");
        list_pattern.add((byte) 0x12);
        list_name_pattern.add("LDWS 2");

        ArrayAdapter<String> dataAdapter_pattern = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list_name_pattern);
        dataAdapter_pattern.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner_ldws_Alarm.setAdapter(dataAdapter_pattern);

        mSpinner_ldws_Alarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                sendMsg_ldws[2] = list_pattern.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void fcws_addItemForSpinner() {
        sendMsg_fcws = new byte[12];
        mSpinner_fcws_Alarm = (Spinner) findViewById(R.id.mSpinner_fcws_Alarm);
        mSpinner_fcws_Strength = (Spinner) findViewById(R.id.mSpinner_fcws_Strength);

        // Strength
        final List<Byte> list_strength = new ArrayList<Byte>();
        final List<String> list_name_strength = new ArrayList<String>();

        list_strength.add((byte) 0x01);
        list_name_strength.add("Level 1");
        list_strength.add((byte) 0x02);
        list_name_strength.add("Level 2");
        list_strength.add((byte) 0x03);
        list_name_strength.add("Level 3");
        list_strength.add((byte) 0x04);
        list_name_strength.add("Level 4");
        list_strength.add((byte) 0x05);
        list_name_strength.add("Level 5");
		list_strength.add((byte) 0x06);
		list_name_strength.add("Level 6");
		list_strength.add((byte) 0x07);
		list_name_strength.add("Level 7");
		list_strength.add((byte) 0x08);
		list_name_strength.add("Level 8");
		list_strength.add((byte) 0x09);
		list_name_strength.add("Level 9");
		list_strength.add((byte) 0x0A);
		list_name_strength.add("Level 10");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list_name_strength);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner_fcws_Strength.setAdapter(dataAdapter);

        mSpinner_fcws_Strength.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                sendMsg_fcws[4] = list_strength.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Pattern type
        final List<Byte> list_pattern = new ArrayList<Byte>();
        final List<String> list_name_pattern = new ArrayList<String>();

        list_pattern.add((byte) 0x13);
        list_name_pattern.add("FCWS 1");
        list_pattern.add((byte) 0x14);
        list_name_pattern.add("FCWS 2");

        ArrayAdapter<String> dataAdapter_pattern = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list_name_pattern);
        dataAdapter_pattern.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner_fcws_Alarm.setAdapter(dataAdapter_pattern);

        mSpinner_fcws_Alarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                sendMsg_fcws[2] = list_pattern.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void bsd_addItemForSpinner() {
        sendMsg_bsd = new byte[12];
        mSpinner_bsd_Alarm = (Spinner) findViewById(R.id.mSpinner_bsd_Alarm);
        mSpinner_bsd_Strength = (Spinner) findViewById(R.id.mSpinner_bsd_Strength);

        // Strength
        final List<Byte> list_strength = new ArrayList<Byte>();
        final List<String> list_name_strength = new ArrayList<String>();

        list_strength.add((byte) 0x01);
        list_name_strength.add("Level 1");
        list_strength.add((byte) 0x02);
        list_name_strength.add("Level 2");
        list_strength.add((byte) 0x03);
        list_name_strength.add("Level 3");
        list_strength.add((byte) 0x04);
        list_name_strength.add("Level 4");
        list_strength.add((byte) 0x05);
        list_name_strength.add("Level 5");
		list_strength.add((byte) 0x06);
		list_name_strength.add("Level 6");
		list_strength.add((byte) 0x07);
		list_name_strength.add("Level 7");
		list_strength.add((byte) 0x08);
		list_name_strength.add("Level 8");
		list_strength.add((byte) 0x09);
		list_name_strength.add("Level 9");
		list_strength.add((byte) 0x0A);
		list_name_strength.add("Level 10");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list_name_strength);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner_bsd_Strength.setAdapter(dataAdapter);

        mSpinner_bsd_Strength.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                sendMsg_bsd[4] = list_strength.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Pattern type
        final List<Byte> list_pattern = new ArrayList<Byte>();
        final List<String> list_name_pattern = new ArrayList<String>();

        list_pattern.add((byte) 0x15);
        list_name_pattern.add("BSD 1");
        list_pattern.add((byte) 0x16);
        list_name_pattern.add("BSD 2");

        ArrayAdapter<String> dataAdapter_pattern = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list_name_pattern);
        dataAdapter_pattern.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner_bsd_Alarm.setAdapter(dataAdapter_pattern);

        mSpinner_bsd_Alarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                sendMsg_bsd[2] = list_pattern.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }
}