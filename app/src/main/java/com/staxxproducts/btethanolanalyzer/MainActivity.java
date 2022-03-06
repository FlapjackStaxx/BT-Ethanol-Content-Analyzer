package com.staxxproducts.btethanolanalyzer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    private final String TAG = MainActivity.class.getSimpleName();

    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView ethPct;
    private TextView fuelTemp;
    String ethPctNum = "";
    String tempInNum = "";
    String restartName = "";
    String info = "";
    String ethT = "75";



    private ListView mDevicesListView;

    private BluetoothAdapter mBTAdapter;
    private ArrayAdapter<String> mBTArrayAdapter;

    // Handler receives callback notifications
    private Handler mHandler;

    // ConnectedThread handles BT interactions
    private ConnectedThread mConnectedThread;
    private BluetoothSocket mBTSocket = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_portrait);


/*        if(getResources().getDisplayMetrics().widthPixels>getResources().getDisplayMetrics().
                heightPixels)
        {
            setContentView(R.layout.content_landscape);

            //Toast.makeText(this,"Screen switched to Landscape mode",Toast.LENGTH_SHORT).show();
        }
        else
        {
            setContentView(R.layout.content_portrait);

           // Toast.makeText(this,"Screen switched to Portrait mode",Toast.LENGTH_SHORT).show();
        }*/


        mBluetoothStatus = findViewById(R.id.bluetooth_status);
        ethPct = findViewById(R.id.ethNumTv);
        fuelTemp = findViewById(R.id.fTempNumTV);
        ImageButton mScanBtn = findViewById(R.id.btOnBtn);
        ImageButton mOffBtn = findViewById(R.id.btOffBtn);
        ImageButton mHidePairedBtn = findViewById(R.id.hidePairedBtn);
        ImageButton mListPairedDevicesBtn = findViewById(R.id.btShowPairedBtn);
        ImageButton startButton = findViewById(R.id.startBtn);
        ImageButton stopButton = findViewById(R.id.stopBtn);
        float etohNum = Float.parseFloat(ethT);
        CircularProgressIndicator ethProgressView = findViewById(R.id.ethContentCirc);
        CircularProgressIndicator tempProgressView = findViewById(R.id.ethTempCirc);




        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        mDevicesListView = findViewById(R.id.devices_list_view);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
/*        ethPct.setText( ethT + "%");
        String intemp = "20";
        int calcTemp = (Integer.parseInt(intemp)-5);
        fuelTemp.setText( calcTemp + "°F");
        ethProgressView.setProgress(75,true);
        tempProgressView.setProgress(calcTemp,true);*/

/*        ethPct.setText(ethT + " %");
        fuelTemp.setText("25° F");
        ethProgressView.setProgress(10,true);
        tempProgressView.setProgress(30,true);
        mBluetoothStatus.setText("Connected");*/

        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage;
                    readMessage = new String((byte[]) msg.obj, StandardCharsets.UTF_8);
                    ethPctNum = readMessage.substring(0,2);
                    tempInNum = readMessage.substring(3,5);




                    /*int calcTemp = (Integer.parseInt(tempInNum)-5);
                    int calcPct = (Integer.parseInt(ethPctNum));*/

                    ethPct.setText(ethPctNum + " %");
                    fuelTemp.setText(tempInNum + "°F");
                  /*  ethProgressView.setProgress(calcPct,true);
                    tempProgressView.setProgress(calcTemp,true);*/

                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        mBluetoothStatus.setText("Connected to Device: " + msg.obj);
                    else
                        mBluetoothStatus.setText(R.string.connection_failed);
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText(R.string.bt_status_not_found);
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {
            // Start button checks if the ConnectedThread is created then starts a sync
            startButton.setOnClickListener(v -> {
                if(mConnectedThread != null) {
                    mBluetoothStatus.setText("Sync Started");
                    startConnection(info);
                }
                else
                    Toast.makeText(MainActivity.this,"Select Paired Device To Connect",Toast.LENGTH_SHORT).show();
            });

            // Turns BT On
            mScanBtn.setOnClickListener(v -> bluetoothOn());
            // Turns BT Off
            mOffBtn.setOnClickListener(v -> bluetoothOff());
            // Lists currently paired devices
            mListPairedDevicesBtn.setOnClickListener(v -> listPairedDevices());
            // Hides currently paired devices
            mHidePairedBtn.setOnClickListener(v -> hideDeviceList());
            // Stop button checks if the ConnectedThread is created then cancels and clears ETOH % and Fuel Temp
            stopButton.setOnClickListener(v -> {
                if (mConnectedThread != null) {
                    mConnectedThread.cancel();
                mBluetoothStatus.setText(R.string.connection_end);
                ethPct.setText("");
                fuelTemp.setText("");
            }
                else
                    Toast.makeText(MainActivity.this,"Can't Stop A Connection That Wasn't Connected!",Toast.LENGTH_SHORT).show();
            });
        }


    }





    // Hides the paired devices list
    private void hideDeviceList() {
        mDevicesListView.setVisibility(View.INVISIBLE);
    }

    // Turns BT On after checking if it's enabled or not
    private void bluetoothOn(){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText(R.string.bt_enabled);
            Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        // Check which request we're responding to
        super.onActivityResult(requestCode, resultCode, Data);
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText(R.string.Enabled);
            } else
                mBluetoothStatus.setText(R.string.Disabled);
        }
    }

    // Turns BT Off
    private void bluetoothOff(){
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText(R.string.bt_disabled);
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    // Lists all paired devices after setting the device list(clickable) viewable
    private void listPairedDevices(){
        mDevicesListView.setVisibility(View.VISIBLE);
        mBTArrayAdapter.clear();

        Set<BluetoothDevice> mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // Sets device to adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    // Click listener for the paired devices list
    private final AdapterView.OnItemClickListener mDeviceClickListener = (parent, view, position, id) -> {
        info = ((TextView) view).getText().toString();
        // Starts a new connection
        startConnection(info);
    };

    // Starts a connection based on the information gathered from the selected device
    private void startConnection(String info) {
        if(!mBTAdapter.isEnabled()) {
            Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            return;
        }

        mBluetoothStatus.setText(R.string.bt_connecting);
        // Get the device MAC address, which is the last 17 chars in the View
        final String address = info.substring(info.length() - 17);
        final String name = info.substring(0,info.length() - 17);

        // Saves the name of the last clicked paired device in case of a need to reconnect
        restartName = name;

        // Spawn a new thread to avoid blocking the GUI one
        new Thread()
        {
            @Override
            public void run() {
                boolean fail = false;

                BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                try {
                    mBTSocket = createBluetoothSocket(device);
                } catch (IOException e) {
                    fail = true;
                    Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                }
                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket.connect();
                } catch (IOException e) {
                    try {
                        fail = true;
                        mBTSocket.close();
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget();
                    } catch (IOException e2) {
                        // Insert code to deal with this
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                }
                // If everything goes well, obtain the data from the Arduino app and send it to the target - % and Temp
                if(!fail) {
                    mConnectedThread = new ConnectedThread(mBTSocket, mHandler);
                    mConnectedThread.start();

                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget();
                    hideDeviceList();
                }
            }
        }.start();
    }

    // Creates a new BT socket (or throws an exception
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }


}