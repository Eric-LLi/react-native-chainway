// ChainwayModule.java

package com.chainway;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class ChainwayModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

    private final String LOG = "[CHAINWAY]";
    private final String READER_STATUS = "READER_STATUS";
    private final String TRIGGER_STATUS = "TRIGGER_STATUS";
    private final String WRITE_TAG_STATUS = "WRITE_TAG_STATUS";
    private final String BATTERY_STATUS = "BATTERY_STATUS";
    private final String TAG = "TAG";
    private final String LOCATE_TAG = "LOCATE_TAG";

    private static RFIDWithUHF mReader = null;

    public ChainwayModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);
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
        //
    }

    @ReactMethod
    public void isConnected(Promise promise) {
        //
    }

    private void doConnect() {
        //
    }

    private void doDisconnect() {
        if(mReader != null) {
            mReader.free();

            scannedTags = new ArrayList<>();
			mReader = null;
        }
    }
}
