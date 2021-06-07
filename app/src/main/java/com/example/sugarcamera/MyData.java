package com.example.sugarcamera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public class MyData {
    private Context context;
    private static SharedPreferences mPreferences;
    private static SharedPreferences.Editor mEditor;
    private static MyData mSharedPreferencesUtil;

//预设存数据的工具类
    @SuppressLint("CommitPrefEdits")
    public MyData(Context context) {
        this.context = context;
        mPreferences = this.context.getSharedPreferences("MY_DATA", Context.MODE_PRIVATE);
        mEditor = mPreferences.edit();
    }

    public static MyData getInstance(Context context) {
        if (mSharedPreferencesUtil == null) {
            mSharedPreferencesUtil = new MyData(context);
        }
        return mSharedPreferencesUtil;
    }
    public void save_AF(float af){
        mEditor.putFloat("AF",af);
        mEditor.commit();
    }



    public float load_AF() {
        return mPreferences.getFloat("AF", 10);
    }

}