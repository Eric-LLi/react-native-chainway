// ChainwayModule.java

package com.chainway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import com.barcode.BarcodeUtility;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.exception.ConfigurationException;
import com.rscja.deviceapi.interfaces.IUHF;
import com.zebra.adc.decoder.Barcode2DWithSoft;

import java.util.ArrayList;

public class ChainwayModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

    private final String LOG = "[CHAINWAY]";
    private final String READER_STATUS = "READER_STATUS";
    private final String TRIGGER_STATUS = "TRIGGER_STATUS";
    private final String WRITE_TAG_STATUS = "WRITE_TAG_STATUS";
    private final String TAG = "TAG";
    private final String BARCODE = "BARCODE";

    private static RFIDWithUHFUART mReader = null;
    private static ArrayList<String> cacheTags = new ArrayList<>();
    private static boolean isSingleRead = false;
    private static ChainwayModule instance = null;
    private static boolean loopFlag = false;
    private static boolean isReadBarcode = false;
    private static BarcodeUtility barcodeUtility;
    private static BarcodeDataReceiver barcodeDataReceiver = null;

    public ChainwayModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);
        instance = this;
    }

    public static ChainwayModule getInstance() {
        return instance;
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendEvent(String eventName, String msg) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, msg);
    }

    public void onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 139 || keyCode == 280 || keyCode == 293) {
            if (event.getRepeatCount() == 0) {
                WritableMap map = Arguments.createMap();
                map.putBoolean("status", true);
                sendEvent(TRIGGER_STATUS, map);

                if (isReadBarcode) {
                    barcodeRead();
                } else {
                    read();
                }
            }
        }
    }

    public void onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == 139 || keyCode == 280 || keyCode == 293) {
            if (event.getRepeatCount() == 0) {
                WritableMap map = Arguments.createMap();
                map.putBoolean("status", false);
                sendEvent(TRIGGER_STATUS, map);

                if (isReadBarcode) {
                    barcodeCancel();
                } else {
                    cancel();
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Chainway";
    }

    @Override
    public void onHostResume() {
        //
    }

    @Override
    public void onHostPause() {
        //
    }

    @Override
    public void onHostDestroy() {
        doDisconnect();
    }

    @ReactMethod
    public void isConnected(Promise promise) {
        if (mReader != null) {
            promise.resolve(mReader.getConnectStatus());
        } else {
            promise.resolve(false);
        }
    }

    @ReactMethod
    public void connect(Promise promise) {
        try {
            doConnect();
            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void reconnect(Promise promise) {
        try {
            doConnect();
            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void disconnect(Promise promise) {
        try {
            doDisconnect();
            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void clear() {
        cacheTags = new ArrayList<>();
    }

    @ReactMethod
    public void setSingleRead(boolean enable) {
        isSingleRead = enable;
    }

    @ReactMethod
    public void getDeviceDetails(Promise promise) {
        if (mReader != null) {
            int antennaLevel = mReader.getPower();

            WritableMap map = Arguments.createMap();
            map.putString("name", "ChainWay");
            map.putString("mac", "");
            map.putInt("antennaLevel", antennaLevel);
            promise.resolve(map);
        } else {
            promise.reject(LOG, "Reader is not connected");
        }
    }

    @ReactMethod
    public void setAntennaLevel(int antennaLevel, Promise promise) {
        if (mReader != null) {
            boolean result = mReader.setPower(antennaLevel);

            if (result) {
                promise.resolve(true);
            } else {
                promise.reject(LOG, "Set antenna level fail");
            }
        } else {
            promise.reject(LOG, "Reader is not connected");
        }
    }

    @ReactMethod
    public void programTag(String oldTag, String newTag, Promise promise) {
        if (mReader != null) {
            String strPWD = "00000000";
            int cntStr = 6;
            int filterPtr = 32;
            int strPtr = 2;

            boolean result = mReader.writeData(
                    strPWD,
                    IUHF.Bank_EPC,
                    filterPtr,
                    oldTag.length() * 4,
                    oldTag,
                    IUHF.Bank_EPC,
                    strPtr,
                    cntStr,
                    newTag
            );

            WritableMap map = Arguments.createMap();
            map.putBoolean("status", result);
            map.putString("error", result ? null : "Program tag fail");

            sendEvent(WRITE_TAG_STATUS, map);
        } else {
            promise.reject(LOG, "Reader is not connected");
        }
    }

    @ReactMethod
    public void setEnabled(boolean enable, Promise promise) {
        if (mReader != null) {
            if (enable) {
                isReadBarcode = false;
                mReader.init();
            } else {
                isReadBarcode = true;
                mReader.free();
            }
        }
    }

    @ReactMethod
    public void softReadCancel(boolean enable, Promise promise) {
        if(mReader != null){
            if(enable){
                read();
            } else {
                cancel();
            }
        }
    }

    private void doConnect() {
        try {
            //RFID
            if (mReader == null) {
                mReader = RFIDWithUHFUART.getInstance();
            }
            mReader.init();

            //Barcode
            if (barcodeUtility == null) {
                barcodeUtility = BarcodeUtility.getInstance();
            }

            barcodeUtility.setOutputMode(this.reactContext, 2);// Broadcast receive data
            barcodeUtility.setScanResultBroadcast(this.reactContext, "com.scanner.broadcast", "data"); //Set Broadcast
            barcodeUtility.open(this.reactContext, BarcodeUtility.ModuleType.BARCODE_2D);
            barcodeUtility.setReleaseScan(this.reactContext, false);
            barcodeUtility.setScanFailureBroadcast(this.reactContext, true);
            barcodeUtility.enableContinuousScan(this.reactContext, false);
            barcodeUtility.enablePlayFailureSound(this.reactContext, true);
            barcodeUtility.enablePlaySuccessSound(this.reactContext, true);
            barcodeUtility.enableEnter(this.reactContext, false);
            barcodeUtility.setBarcodeEncodingFormat(this.reactContext, 1);

            if (barcodeDataReceiver == null) {
                barcodeDataReceiver = new BarcodeDataReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("com.scanner.broadcast");
                this.reactContext.registerReceiver(barcodeDataReceiver, intentFilter);
            }

            WritableMap map = Arguments.createMap();
            map.putBoolean("status", true);
            sendEvent(READER_STATUS, map);
        } catch (ConfigurationException e) {
            e.printStackTrace();

            WritableMap map = Arguments.createMap();
            map.putBoolean("status", false);
            map.putString("error", e.getMessage());
            sendEvent(READER_STATUS, map);
        }
    }

    private void doDisconnect() {
        if (mReader != null) {
            mReader.free();
        }

        cacheTags = new ArrayList<>();
        mReader = null;

        if (barcodeUtility != null) {
            barcodeUtility.close(this.reactContext, BarcodeUtility.ModuleType.BARCODE_2D);
        }

        if (barcodeDataReceiver != null) {
            this.reactContext.unregisterReceiver(barcodeDataReceiver);
            barcodeDataReceiver = null;
        }

        WritableMap map = Arguments.createMap();
        map.putBoolean("status", false);
        sendEvent(READER_STATUS, map);
    }

    private void read() {
        if (mReader != null) {
            if (isSingleRead) {
                UHFTAGInfo strUII = mReader.inventorySingleTag();
                if (strUII != null) {
                    String strEPC = strUII.getEPC();
                    String rssi = strUII.getRssi();

                    sendEvent(TAG, strEPC);
                }
            } else {
                if (mReader.startInventoryTag()) {
                    loopFlag = true;

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String strTid;
                            String strResult;
                            UHFTAGInfo res = null;

                            while (loopFlag) {
                                res = mReader.readTagFromBuffer();
                                if (res != null) {
                                    strTid = res.getTid();
                                    if (strTid.length() != 0 && !strTid.equals("0000000" +
                                            "000000000") && !strTid.equals("000000000000000000000000")) {
                                        strResult = "TID:" + strTid + "\n";
                                    } else {
                                        strResult = "";
                                    }

                                    Log.i("data", "EPC:" + res.getEPC() + "|" + strResult);

                                    if (addTagToList(res.getEPC())) {
                                        sendEvent(TAG, res.getEPC());
                                    }
                                }
                            }
                        }
                    }).start();
                }
            }
        }
    }


    private void cancel() {
        if (mReader != null && !isSingleRead) {
            loopFlag = false;
            mReader.stopInventory();
        }
    }

    private void barcodeRead() {
        if (barcodeUtility != null) {
            barcodeUtility.startScan(this.reactContext, BarcodeUtility.ModuleType.BARCODE_2D);
        }
    }

    private void barcodeCancel() {
        if (barcodeUtility != null) {
            barcodeUtility.stopScan(this.reactContext, BarcodeUtility.ModuleType.BARCODE_2D);
        }
    }

    private boolean addTagToList(String strEPC) {
        if (strEPC != null) {
            if (!cacheTags.contains(strEPC)) {
                cacheTags.add(strEPC);
                return true;
            }
        }
        return false;
    }

    class BarcodeDataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String barCode = intent.getStringExtra("data");
            String status = intent.getStringExtra("SCAN_STATE");

            if (status != null && (status.equals("cancel") || status.equals("failuer"))) {
                return;
            } else {
                sendEvent(BARCODE, barCode);
            }
        }
    }
}
