package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.health.ServiceHealthStats;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    static final String Tag = "MainActivity";

    static final String EUrl = "EUrl";
    static final String EUid = "EUid";
    static final String EToken = "EToken";
    static final String EChannel = "EChannel";
    static final String EGameId = "EGameId";
    static final String ERatio = "ERatio";

    WebView webView;
    EditText urlE;
    EditText uidE;
    EditText gameIdE;
    EditText tokenE;
    EditText channelE;
    EditText ratioE;

    static String url = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        webView = this.findViewById(R.id.web);
        urlE = this.findViewById(R.id.url);
        uidE = this.findViewById(R.id.uid);
        gameIdE = this.findViewById(R.id.gameid);
        tokenE = this.findViewById(R.id.token);
        channelE = this.findViewById(R.id.channel);
        ratioE = this.findViewById(R.id.ratio);

        SharedPreferences sp = getSharedPreferences(Tag, Context.MODE_PRIVATE);
        urlE.setText(sp.getString(MainActivity.EUrl, ""));
        uidE.setText(sp.getLong(MainActivity.EUid, 0) + "");
        gameIdE.setText(sp.getLong(MainActivity.EGameId, 0) + "");
        tokenE.setText(sp.getString(MainActivity.EToken, ""));
        channelE.setText(sp.getString(MainActivity.EChannel, ""));
        ratioE.setText(sp.getString(MainActivity.ERatio, ""));

        this.findViewById(R.id.btn).setOnClickListener(v -> {
            if (this.webView.getVisibility() == View.VISIBLE) {
                Toast.makeText(this, R.string.opening, Toast.LENGTH_SHORT).show();
                return;
            }

            url = urlE.getText().toString();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, R.string.urlempty, Toast.LENGTH_SHORT).show();
                return;
            }

            JSKit.Token = tokenE.getText().toString();
            JSKit.Channel = channelE.getText().toString();
            try {
                JSKit.Uid = Long.parseLong(uidE.getText().toString());
                JSKit.GameId = Integer.parseInt(gameIdE.getText().toString());
            } catch (Exception e) {
                JSKit.Uid = 0;
                JSKit.GameId = 0;
            }
            String ratio = ratioE.getText().toString();

            SharedPreferences.Editor editor = sp.edit();
            editor.putString(MainActivity.EUrl, url);
            editor.putLong(MainActivity.EUid, JSKit.Uid);
            editor.putInt(MainActivity.EGameId, JSKit.GameId);
            editor.putString(MainActivity.EChannel, JSKit.Channel);
            editor.putString(MainActivity.EToken, JSKit.Token);
            editor.putString(MainActivity.ERatio, ratio);
            editor.commit();

            Log.i(Tag, "url:" + sp.getString(EUrl, ""));
            Log.i(Tag, "EUid:" + sp.getLong(EUid, 0));
            Log.i(Tag, "EToken:" + sp.getString(EToken, ""));

            double ratioD = 1.0;
            try {
                ratioD = Double.parseDouble(ratio);
            } catch (Exception e) {
                e.printStackTrace();
                ratioD = 1.0;
            }

            updateWebView(ratioD);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        });

        initWebView();
    }

    @Override
    public void onBackPressed() {
        Log.i(Tag, "back");
        if (webView != null) webView.setVisibility(View.INVISIBLE);
    }

    void updateWebView(double ratio) {
        // 获取屏幕高度的一半
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int halfScreenHeight = getResources().getDisplayMetrics().heightPixels;
        if (ratio > 0) {
            double floor = Math.floor(screenWidth / ratio);
            halfScreenHeight = (int) floor;
        }

        // 创建一个WebView实例
//        WebView webView = new WebView(this);

        // 设置WebView的高度为屏幕高度的一半
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, halfScreenHeight, Gravity.BOTTOM);
        webView.setLayoutParams(layoutParams);
    }

    /**
     * AUTO CODE by chatGPT
     */
    private void initWebView() {
        // 启用JavaScript
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // 设置WebViewClient以确保所有链接在WebView内打开
        webView.setWebViewClient(new WebViewClient());

        // 创建JSKit实例，并添加到WebView中
        JSKit jsKit = new JSKit(this);
        //默认原生接口
        webView.addJavascriptInterface(jsKit, "appJS");
        //兼容haya
        webView.addJavascriptInterface(jsKit, "Application");
    }
}