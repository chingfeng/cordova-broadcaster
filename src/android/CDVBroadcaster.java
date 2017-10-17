package org.bsc.cordova;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Set;

/**
 * This class echoes a string called from JavaScript.
 */
public class CDVBroadcaster extends CordovaPlugin {

    public static final String USERDATA = "userdata";
    private static String TAG =  CDVBroadcaster.class.getSimpleName();

    public static final String EVENTNAME_ERROR = "event name null or empty.";

    java.util.Map<String,BroadcastReceiver> receiverMap =
                    new java.util.HashMap<String,BroadcastReceiver>(10);

    /**
     *
     * @param eventName
     * @param jsonUserData
     * @throws JSONException
     */
    protected void fireEvent( final String eventName, final Object jsonUserData) throws JSONException {

        final String method ;
        if( jsonUserData != null ) {
            final String data = String.valueOf(jsonUserData);
            if (!(jsonUserData instanceof JSONObject)) {
                final JSONObject json = new JSONObject(data); // CHECK IF VALID
            }
            method = String.format("javascript:window.broadcaster.fireEvent( '%s', %s );", eventName, data);
        }
        else {
            method = String.format("javascript:window.broadcaster.fireEvent( '%s', {} );", eventName);
        }

        cordova.getActivity().runOnUiThread( new Runnable() {

            @Override
            public void run() {
                CDVBroadcaster.this.webView.loadUrl(method);
            }
        });
    }

    protected void registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
        System.out.println("CDVBroadcaster: registerReceiver");
        super.webView.getContext().registerReceiver(receiver, filter);
    }

    protected void unregisterReceiver(android.content.BroadcastReceiver receiver) {
        System.out.println("CDVBroadcaster: unregisterReceiver");
        super.webView.getContext().unregisterReceiver(receiver);
    }

    protected void sendBroadcast(android.content.Intent intent) {
        System.out.println("CDVBroadcaster: sendBroadcast");
        super.webView.getContext().sendBroadcast(intent);
    }

    @Override
    public Object onMessage(String id, Object data) {
        System.out.println("CDVBroadcaster: onMessage: id = " + id);

        if( receiverMap.containsKey(id) ) {
            try {
                fireEvent( id, data );
            } catch (JSONException e) {
                Log.e(TAG, String.format("userdata [%s] for event [%s] is not a valid json object!", data, id));
            }
        }
        return super.onMessage( id, data );
    }

    private void fireNativeEvent( final String eventName, JSONObject userData ) {
        System.out.println("CDVBroadcaster: fireNativeEvent: eventName = " + eventName);
        if( eventName == null ) {
            throw new IllegalArgumentException("eventName parameter is null!");
        }

        final Intent intent = new Intent(eventName);

        if( userData != null ) {
            Bundle bundle = new Bundle();
            String key;
            String value;
            JSONObject jsonObject;
            JSONArray jsonArray;
            Iterator iterator = userData.keys();
            while(iterator.hasNext()){
                key = (String)iterator.next();
                value = userData.optString(key);
                jsonArray = userData.optJSONArray(key);
                jsonObject = userData.optJSONObject(key);
                if (jsonArray != null) value = jsonArray.toString();
                else if (jsonObject != null) value = jsonObject.toString();
                if (value != null) bundle.putString(key, value);
            }
            intent.putExtras(bundle);
        }

        sendBroadcast( intent );
    }

    /**
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        System.out.println("CDVBroadcaster: execute: action = " + action);
        if( action.equals("fireNativeEvent")) {

            final String eventName = args.getString(0);
            if( eventName==null || eventName.isEmpty() ) {
                callbackContext.error(EVENTNAME_ERROR);

            }
            final JSONObject userData = args.getJSONObject(1);


            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    fireNativeEvent(eventName, userData);
                }
            });

            callbackContext.success();
            return true;
        }
        else if (action.equals("addEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }
            if (!receiverMap.containsKey(eventName)) {

                final BroadcastReceiver r = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, final Intent intent) {
                        final String action = intent.getAction();
                        final Bundle b = intent.getExtras();
                        JSONObject payloadJsonObj = new JSONObject();
                        String key, val;
                        JSONObject jsonObject;
                        JSONArray jsonArray;

                        if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                            return;
                        }

                        if (action.equals("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")) {
                            int CONNECTION_STATE = b.getInt("android.bluetooth.adapter.extra.CONNECTION_STATE");
                            int PREVIOUS_CONNECTION_STATE = b.getInt("android.bluetooth.adapter.extra.PREVIOUS_CONNECTION_STATE");
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            Log.v(TAG, "CDVBroadcaster: CONNECTION_STATE_CHANGED: " + PREVIOUS_CONNECTION_STATE + " => " + CONNECTION_STATE);
                            Log.v(TAG, "CDVBroadcaster: DEVICE: " + device.getName() + ": addr = " + device.getAddress() + ", id = " + device.getUuids());
                            // BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            // System.out.println("CDVBroadcaster: onReceive: device.getName() = " + device.getName() + ", device.getAddress() = " + device.getAddress());
                            // if (val instanceof BluetoothDevice) {
                            //     return mAddress.equals(((BluetoothDevice) o).getAddress());
                            // }
                            return;
                        }

                        if (b != null) {
                            Set<String> keys = b.keySet();
                            Iterator<String> iterator = keys.iterator();
                            while (iterator.hasNext()) {
                                key = iterator.next();
                                val = b.getString(key + "");
                                if (val == null) continue;
                                jsonObject = isValidJsonObj(val);
                                jsonArray = isValidJsonArr(val);
                                try {
                                    if (jsonObject != null) {
                                        payloadJsonObj.put(key, jsonObject);
                                    } else if (jsonArray != null) {
                                        payloadJsonObj.put(key, jsonArray);
                                    } else {
                                        payloadJsonObj.put(key, b.get(key));
                                    }
                                } catch (JSONException e) {
                                    Log.v(TAG, "CDVBroadcaster: sendIntent: Error occur while convert extra to json. key = " + intent.getAction());
                                }
                            }
                        } else {
                            Log.v(TAG, "No extra information in intent bundle");
                        }

                        try {
                            fireEvent(eventName, payloadJsonObj.toString());
                        } catch (JSONException e) {
                            Log.e(TAG, "'userdata' is not a valid json object!");
                        }
                    }
                };

                registerReceiver(r, new IntentFilter(eventName));

                receiverMap.put(eventName, r);
            }
            callbackContext.success();

            return true;
        } else if (action.equals("removeEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            final BroadcastReceiver r = receiverMap.remove(eventName);

            if (r != null) {
                unregisterReceiver(r);
            }
            callbackContext.success();
            return true;
        }
        return false;
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        System.out.println("CDVBroadcaster: onDestroy");
        // deregister receiver
        for( BroadcastReceiver r : receiverMap.values() ) {
                    unregisterReceiver(r);
        }

        receiverMap.clear();

        super.onDestroy();

    }

    public JSONObject isValidJsonObj(String str) {
        try {
            return new JSONObject(str);
        } catch (JSONException ex) {
            return null;
        }
    }

    public JSONArray isValidJsonArr(String str) {
        try {
            return new JSONArray(str);
        } catch (JSONException ex) {
            return null;
        }
    }
}
