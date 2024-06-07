package com.example.myapplication;

import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    static final String Tag = "MainActivity";
    static final String Url = "https://fishtest.haoyuban.com/h5/half/olympus/index.html?w=68747470733A2F2F66697368746573742E68616F797562616E2E636F6D2F67657475726C2E706870&target=ccc&v=1.0.1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建一个根布局
        FrameLayout rootLayout = new FrameLayout(this);

        // 创建WebView并将其添加到根布局中
        WebView webView = createWebView();
        rootLayout.addView(webView);

        // 将根布局设置为活动内容视图
        setContentView(rootLayout);
    }

    /**
     * AUTO CODE by chatGPT
     */
    private WebView createWebView() {
        // 获取屏幕高度的一半
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int halfScreenHeight = screenHeight / 2;

        // 创建一个WebView实例
        WebView webView = new WebView(this);

        // 设置WebView的高度为屏幕高度的一半
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                halfScreenHeight,
                Gravity.BOTTOM
        );
        webView.setLayoutParams(layoutParams);

        // 启用JavaScript
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // 设置WebViewClient以确保所有链接在WebView内打开
        webView.setWebViewClient(new WebViewClient());

        // 创建JSKit实例，并添加到WebView中
        JSKit jsKit = new JSKit(this);
        webView.addJavascriptInterface(jsKit, "appJS");

        // 加载一个URL
        webView.loadUrl(Url);

        return webView;
    }
}