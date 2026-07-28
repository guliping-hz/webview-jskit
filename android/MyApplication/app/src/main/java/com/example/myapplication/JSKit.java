package com.example.myapplication;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

public class JSKit {

    static String Tag = "JSKit";

    public static String Channel = "";
    public static String AppId = "";
    public static String Token = "";
    public static long Uid = 100001;
    public static int GameId = 0;

//            'X-Authorization': '0175f1f8b0e5e70fb5e3fc6daec250ad',
//                    'X-Uid': '60000159',

    private final MainActivity mContext;

    public JSKit(MainActivity context) {
        this.mContext = context;
    }

    @android.webkit.JavascriptInterface
    public void hideCloseIcon() {
        Log.i(Tag, "hideCloseIcon");
        mContext.runOnUiThread(() -> {
            Toast.makeText(mContext, "Close", Toast.LENGTH_SHORT).show();
        });
    }

    @android.webkit.JavascriptInterface
    public void gameLoaded() {
        Log.i(Tag, "gameLoaded");
        mContext.runOnUiThread(() -> {
            Toast.makeText(mContext, "Close", Toast.LENGTH_SHORT).show();
        });
    }

    @android.webkit.JavascriptInterface
    public void playLoaded() {
        this.gameLoaded();
    }

    @android.webkit.JavascriptInterface
    public String getGameNeedInfo() {
        try {
            JSONObject json = new JSONObject();
            json.put("userId", "" + JSKit.Uid);
            json.put("gameId", JSKit.GameId);
            json.put("token", JSKit.Token);
            json.put("a", JSKit.AppId);
            json.put("appId", JSKit.AppId);
            json.put("c", JSKit.Channel);
            json.put("channel", JSKit.Channel);
            Log.i(Tag, "getGameNeedInfo：" + json.toString());
            return json.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }

    @android.webkit.JavascriptInterface
    public String getPlayNeedInfo() {
        return this.getGameNeedInfo();
    }

    @android.webkit.JavascriptInterface
    public void recharge() {
        mContext.runOnUiThread(() -> {
            mContext.topup.setVisibility(View.VISIBLE);
        });
    }

    public static void Eval(WebView webView, String evalStr) {
        webView.post(() -> {
            webView.evaluateJavascript(evalStr, null);
        });
    }

    public static void WalletUpdateNoCoin(WebView webView) {
        JSKit.Eval(webView, "walletUpdate()");
    }

    public static void WalletUpdate(WebView webView, Long coin) {
        //用字符串传递coin，防止coin丢失精度
        JSKit.Eval(webView, "walletUpdate(\"" + coin + "\")");
    }
}
