// ChainwayModule.java

package com.chainway;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

public class ChainwayModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

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
}
