package com.example.myapplication;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

public class JSKit {

    static String Tag = "JSKit";

    public static String Token = "0175f1f8b0e5e70fb5e3fc6daec250ad";
    public static long Uid = 60000159;

//            'X-Authorization': '0175f1f8b0e5e70fb5e3fc6daec250ad',
//                    'X-Uid': '60000159',

    private Context mContext;

    public JSKit(Context context) {
        this.mContext = context;
    }

    @android.webkit.JavascriptInterface
    public void hideCloseIcon() {
        Log.i(Tag, "hideCloseIcon");
    }

    @android.webkit.JavascriptInterface
    public String getGameNeedInfo() {
        try {
            JSONObject json = new JSONObject();
            json.put("userId", JSKit.Uid);
            json.put("token", JSKit.Token);
            return json.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }

    public static void eval(WebView webView) {
        webView.evaluateJavascript("walletUpdate()", null);
    }
}
