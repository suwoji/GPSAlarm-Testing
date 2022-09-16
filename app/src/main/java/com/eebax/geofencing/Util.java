package com.eebax.geofencing;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class Util {
    public static void log(String string){
        Log.d(TAG, string);
    }
    public static void toastLong(String string, Context context){
        Toast.makeText(context, string, Toast.LENGTH_LONG).show();
        log("Toast show");
    }
    public static void toastShort(String string, Context context){
        Toast.makeText(context, string, Toast.LENGTH_SHORT).show();
        log("Toast show");
    }
}
