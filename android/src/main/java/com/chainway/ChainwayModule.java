// ChainwayModule.java

package com.chainway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.loader.content.AsyncTaskLoader;

import com.barcode.BarcodeUtility;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.exception.ConfigurationException;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
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
    private final String TAGS = "TAGS";
    private final String BARCODE = "BARCODE";

    private static RFIDWithUHFUART mReader = null;
    private static final ArrayList<String> cacheTags = new ArrayList<>();
    private static boolean isSingleRead = false;
    private static ChainwayModule instance = null;
    private static boolean loopFlag = false;
    private static boolean isReadBarcode = false;
    private static BarcodeUtility barcodeUtility = null;
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

    private void sendEvent(String eventName, WritableArray array) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, array);
    }

    public void onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 139 || keyCode == 280 || keyCode == 293) {
            if (event.getRepeatCount() == 0) {
                if (isReadBarcode) {
                    barcodeRead();
                } else {
                    read();
                }

                WritableMap map = Arguments.createMap();
                map.putBoolean("status", true);
                sendEvent(TRIGGER_STATUS, map);
            }
        }
    }

    public void onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == 139 || keyCode == 280 || keyCode == 293) {
            if (event.getRepeatCount() == 0) {
                if (isReadBarcode) {
                    barcodeCancel();
                } else {
                    cancel();
                }

                WritableMap map = Arguments.createMap();
                map.putBoolean("status", false);
                sendEvent(TRIGGER_STATUS, map);
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
        try {
            if (mReader != null) {
                promise.resolve(mReader.getConnectStatus() == ConnectionStatus.CONNECTED);
            } else {
                promise.resolve(false);
            }
        } catch (Exception err) {
            promise.reject(err);
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
        cacheTags.clear();
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
                promise.reject(LOG, "Failed to change antenna power");
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
//                mReader.init();
            } else {
                isReadBarcode = true;
//                mReader.free();
            }
        }

        promise.resolve(true);
    }

    @ReactMethod
    public void softReadCancel(boolean enable, Promise promise) {
        try {
            if (mReader != null) {
                if (enable) {
                    read();
                } else {
                    cancel();
                }

                promise.resolve(true);
            } else {
                throw new Exception("Reader is not connected");
            }
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    private void doConnect() {
        try {
            if (mReader != null) {
                doDisconnect();
            }

            //RFID
            if (mReader == null) {
                mReader = RFIDWithUHFUART.getInstance();
            }
            mReader.init();
            setGen2();

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
            barcodeUtility.enablePlayFailureSound(this.reactContext, false);
            barcodeUtility.enablePlaySuccessSound(this.reactContext, false);
            barcodeUtility.enableEnter(this.reactContext, false);
            barcodeUtility.setBarcodeEncodingFormat(this.reactContext, 1);

            if (barcodeDataReceiver == null) {
                barcodeDataReceiver = new BarcodeDataReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("com.scanner.broadcast");
                this.reactContext.registerReceiver(barcodeDataReceiver, intentFilter);
            }

            WritableMap map = Arguments.createMap();
            map.putBoolean("status", mReader.getConnectStatus() == ConnectionStatus.CONNECTED);
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
            mReader = null;
        }

        if (barcodeUtility != null) {
            barcodeUtility.close(this.reactContext, BarcodeUtility.ModuleType.BARCODE_2D);
            barcodeUtility = null;
        }

        if (barcodeDataReceiver != null) {
            this.reactContext.unregisterReceiver(barcodeDataReceiver);
            barcodeDataReceiver = null;
        }

        WritableMap map = Arguments.createMap();
        map.putBoolean("status", false);
        map.putString("error", null);
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
                            String strResult;
                            UHFTAGInfo res = null;

                            ArrayList<String> temp_tags = new ArrayList<>();

                            while (loopFlag) {
                                res = mReader.readTagFromBuffer();

                                if (res != null) {
                                    String strTid = res.getTid();
                                    String EPC = res.getEPC();
                                    int rssi = Integer.parseInt(res.getRssi());

                                    if (strTid.length() != 0 && !strTid.equals("0000000" +
                                            "000000000") && !strTid.equals("000000000000000000000000")) {
                                        strResult = "TID:" + strTid + "\n";
                                    } else {
                                        strResult = "";
                                    }

                                    Log.i("data", "EPC:" + res.getEPC() + "|" + strResult);

                                    if (addTagToList(EPC)) {
                                        temp_tags.add(EPC);
//                                        sendEvent(TAG, res.getEPC());
                                    }
                                } else {
                                    if (temp_tags.size() > 0) {
                                        sendEvent(TAGS, Arguments.fromList(temp_tags));

                                        temp_tags.clear();
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

    private void setGen2() {
        if (mReader != null) {
            char[] p = mReader.getGen2();
            if (p != null && p.length >= 14) {
                int target = p[0];
                int action = p[1];
                int t = p[2];
                int q = p[3];
                int startQ = p[4];
                int minQ = p[5];
                int maxQ = p[6];
                int dr = p[7];
                int coding = p[8];
                int p1 = p[9];
                int Sel = p[10];
                int Session = p[11];
                int g = p[12];
                int linkFrequency = p[13];
                StringBuilder sb = new StringBuilder();
                sb.append("target=");
                sb.append(target);
                sb.append(" ,action=");
                sb.append(action);
                sb.append(" ,t=");
                sb.append(t);
                sb.append(" ,q=");
                sb.append(q);
                sb.append(" startQ=");
                sb.append(startQ);
                sb.append(" minQ=");
                sb.append(minQ);
                sb.append(" maxQ=");
                sb.append(maxQ);
                sb.append(" dr=");
                sb.append(dr);
                sb.append(" coding=");
                sb.append(coding);
                sb.append(" p=");
                sb.append(p1);
                sb.append(" Sel=");
                sb.append(Sel);
                sb.append(" Session=");
                sb.append(Session);
                sb.append(" g=");
                sb.append(g);
                sb.append(" linkFrequency=");
                sb.append(linkFrequency);
                sb.append("seesionid=");
                sb.append(1);
                sb.append(" inventoried=");
                sb.append(0);
                Log.i(TAG, sb.toString());

                boolean result = mReader.setGen2(target, action, t, q, startQ, minQ, maxQ, dr, coding, p1, Sel, 1, 0, linkFrequency);

                Log.d(LOG, "Set Gen2: " + result);
            }
        }
    }
}
