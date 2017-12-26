package com.grt.york.opencvwithqrcode;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.grt.york.opencvwithqrcode.ble.BluetoothLeService;
import com.grt.york.opencvwithqrcode.ble.DeviceControlActivity;
import com.grt.york.opencvwithqrcode.ble.SampleGattAttributes;
import com.grt.york.opencvwithqrcode.wifi.WifiSearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.Manifest.permission.CAMERA;

public class MainActivity extends AppCompatActivity {

    private String mWifiSSID = "NewYorkCity";//"HJBIKE";//Alex
    private String mDeviceAddress = "E8:EB:11:0A:CB:1D";//Alex
    private String mCmd = "a";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private final static String TAG = MainActivity.class.getSimpleName();
    private final static int REQUEST_WRITE_STORAGE = 0;
    private static final int REQUEST_OPENCV_RESULT_CODE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_LOCATION = 3;
    private static final long SCAN_PERIOD = 5000;
    private Button btnC;
    private ImageView imgWifi;
    private ProgressBar pgBar;
    private BluetoothAdapter mBluetoothAdapter;
    private Button mScanBtn, mRentBackBtn;
    private List<String> mBLEs;
    private boolean mScanning;
    private Handler mHandler;
    private WifiSearch mWifiSearch;
    private Thread mCheckWifiRegionThread;
    private boolean inWifiRange = false;
    private boolean isBindService = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        imgWifi = (ImageView) findViewById(R.id.img_wifi);
        pgBar = (ProgressBar) findViewById(R.id.progressBar);
        pgBar.setVisibility(View.INVISIBLE);

        mScanBtn  = (Button) findViewById(R.id.button_rent_bicycle);
        mScanBtn.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                //disconnectBleDevice();
                new IntentIntegrator(MainActivity.this)
                        .setOrientationLocked(false)
                        .setCaptureActivity(QRCodeActivity.class)
                        .initiateScan();
            }
        });

        mRentBackBtn = (Button) findViewById(R.id.button_rent_back);
        mRentBackBtn.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                //disconnectBleDevice();
                if (inWifiRange) {
                    Intent intent = new Intent(MainActivity.this, OpenCVActivity.class);
                    startActivityForResult(intent, REQUEST_OPENCV_RESULT_CODE);
                } else {
                    showMessage(R.string.return_not_in_wifi_range);
                }
            }
        });

        btnC = (Button) findViewById(R.id.button_tuning);
        btnC.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
//                connectBleDevice();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendDataToBLE("c");
                    }
                },1000);
            }
        });

        checkBleDeive();
        mWifiSearch = new WifiSearch(MainActivity.this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int permission = ActivityCompat.checkSelfPermission(this, CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_WRITE_STORAGE);
        } else {

        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mCheckWifiRegionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (mWifiSearch.detectWifi(mWifiSSID)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imgWifi.setImageResource(R.drawable.wifi_on);
                                inWifiRange = true;
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imgWifi.setImageResource(R.drawable.wifi_off);
                                inWifiRange = false;
                            }
                        });
                    }
                    try {
                        Thread.sleep(300);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (!mConnected) {
                        connectBleDevice();
                    }
                }
            }
        });
        mCheckWifiRegionThread.start();
        initialBleService();

    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        if (mBLEs != null) {
            mBLEs.clear();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCheckWifiRegionThread.interrupt();
        mCheckWifiRegionThread = null;
        disconectBleService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Now user should be able to use camera
            } else if (requestCode == REQUEST_LOCATION) {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {

                }
                return;
            } else {

            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //feedback from zxing QR code
        IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(scanningResult!=null){
            String scanContent=scanningResult.getContents();
            if (!scanContent.equals("") && scanContent != null) {
                showMessage(R.string.rent_ok);
                sendDataToBLE(mCmd);
            }
        }

        switch(requestCode) {
            case (REQUEST_OPENCV_RESULT_CODE) :
                if (resultCode == Activity.RESULT_OK) {
//                    Toast.makeText(getApplicationContext(),data.getStringExtra("qr_string"),Toast.LENGTH_SHORT).show();
//                    connectBleDevice();//藍芽連線
                     sendDataToBLE(mCmd);
                }
                break;
            case (REQUEST_ENABLE_BT):
                if (resultCode == MainActivity.RESULT_CANCELED) {
                    finish();
                    return;
                }
                break;
        }

    }

    private boolean checkBleDeive(){

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_LOCATION);
            }
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            pgBar.setVisibility(View.VISIBLE);
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    pgBar.setVisibility(View.INVISIBLE);
                    showListViewDialog(mBLEs);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            pgBar.setVisibility(View.INVISIBLE);
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!mBLEs.contains(device.getAddress())) {
                                mBLEs.add(device.getAddress());
                            }
                        }
                    });
                }
            };

    private void showListViewDialog(final List<String> bles) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getResources().getString(R.string.title_devices))
                .setItems(bles.toArray(new CharSequence[bles.size()]), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String bleMac = bles.get(which);
                        if (mScanning) {
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                            mScanning = false;
                        }
                        final Intent intent = new Intent(MainActivity.this, DeviceControlActivity.class);
                        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, "HC-08");
                        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, bleMac);
                        startActivity(intent);

                    }
                }).show();
    }

    private void showMessage(int stringId) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getResources().getString(R.string.dialog_title))
                .setMessage(getResources().getString(stringId))
                .setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).show();
    }


    //Connect BLE device
    //==============================================================
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private void initialBleService() {
        isBindService = true;
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private void connectBleDevice(){
        if (mBluetoothLeService != null) {
            Log.i(TAG, "try to connect ble device.");
            mBluetoothLeService.connect(mDeviceAddress);
        }
    }

    private void disconnectBleDevice() {
        if (mBluetoothLeService != null) {
            Log.i(TAG, "try to disconnect ble device.");
            mBluetoothLeService.disconnect();
        }
    }

    private void disconectBleService() {
        if (mServiceConnection != null && isBindService) {
            unbindService(mServiceConnection);
            isBindService = false;
            if (mBluetoothLeService != null)
                mBluetoothLeService = null;
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            //mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Log.i(TAG, "connected");
            }
            if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Log.i(TAG, "disconnected");
            }
            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                final String msg = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, msg);
                        if (msg.equals("t")) {
                            showMessage(R.string.return_ok);
                        } else if(msg.equals("p")) {
                            showMessage(R.string.return_paid);
                        } else {
                            showMessage(R.string.return_fail);
                        }
                    }
                });
            }
        }
    };

    private void sendDataToBLE(String str) {
        final byte[] tx = str.getBytes();
        if (mConnected) {
            Log.i(TAG, "Sending result=" + str);
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();


        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));

            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
