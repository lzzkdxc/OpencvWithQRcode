package com.grt.york.opencvwithqrcode.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.List;

/**
 * Created by york on 2017/9/7.
 */

public class WifiSearch {

    private final static String TAG = WifiSearch.class.getSimpleName();
    private final static int DBM = -60;
    private WifiManager mWifiManager = null;
    private List<ScanResult> mWifiScanResultList;
    private List<WifiConfiguration> mWifiConfigurationList;
    private WifiInfo mWifiInfo;
    private String SSID = "";//(Wi-Fi名稱)
    private String IP = "";//(Wi-Fi IP位置)
    private int NETWORKID = 0;//(Wi-Fi連線ID)
    private int LEVEL = 0;//(Wi-Fi訊號強弱)
    private int IPADRRESS = 0;//(Wi-Fi連線位置)

    public WifiSearch(Context context) {
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean detectWifi(String ssid) {
        boolean isDetect = false;
        //先判斷是否有開啟Wi-Fi，有開啟則回傳true沒有則回傳false
        if(mWifiManager.isWifiEnabled())
        {
            //重新掃描Wi-Fi資訊
            mWifiManager.startScan();
            //偵測周圍的Wi-Fi環境(因為會有很多組Wi-Fi，所以型態為List)
            mWifiScanResultList = mWifiManager.getScanResults();
            //手機內已存的Wi-Fi資訊(因為會有很多組Wi-Fi，所以型態為List)
            mWifiConfigurationList = mWifiManager.getConfiguredNetworks();
            //目前已連線的Wi-Fi資訊
            mWifiInfo = mWifiManager.getConnectionInfo();

            for(int i = 0 ; i < mWifiScanResultList.size() ; i++ )
            {
                //手機目前周圍的Wi-Fi環境
                SSID  = mWifiScanResultList.get(i).SSID;
                LEVEL  = mWifiScanResultList.get(i).level;
                //Log.i(TAG, "SSID: " + SSID + " Level: " + Integer.toString(LEVEL));
                if (SSID.equals(ssid)) {
                    int dbm = mWifiInfo.getRssi();
                    Log.i(TAG, "Wifi dbm : " + Integer.toString(dbm));
                    if (dbm >= DBM) {
                        isDetect = true;
                    }
                }
            }

//            for(int i = 0 ; i < mWifiConfigurationList.size() ; i++ )
//            {
//                //手機內已儲存(已連線過)的Wi-Fi資訊
//                SSID  = mWifiConfigurationList.get(i).SSID ;
//                NETWORKID  = mWifiConfigurationList.get(i).networkId ;
//            }

            //目前手機已連線(現在連線)的Wi-Fi資訊
//            SSID = mWifiInfo.getSSID() ;
//            NETWORKID = mWifiInfo.getNetworkId() ;
//            IPADRRESS = mWifiInfo.getIpAddress() ;
//            IP = String.format("%d.%d.%d.%d", (IPADRRESS & 0xff), (IPADRRESS >> 8 & 0xff), (IPADRRESS >> 16 & 0xff),( IPADRRESS >> 24 & 0xff)) ;
        }
        else
        {
            //把Wi-Fi開啟
            mWifiManager.setWifiEnabled(true);
            //Log.i(TAG, "Wifi enable.");
        }
        return isDetect;
    }

    public void connectWifi() {
//        int NETWORKID = 已連線過的Wi-Fi ID ;
//        mWifiManager.enableNetwork(NETWORKID,true);
    }

    public void disconnectWifi() {
        mWifiManager.disconnect();
    }

}
