package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    static final String Tag = "MainActivity";

    static final String EZip = "EZip";
    static final String EUrl = "EUrl";
    static final String EUid = "EUid";
    static final String EToken = "EToken";
    static final String EChannel = "EChannel";
    static final String EAppId = "EAppId";
    static final String EGameId = "EGameId";
    static final String ERatio = "ERatio";

    WebView webView;
    EditText zipE, urlE, uidE, gameIdE, tokenE, channelE, appIdE, ratioE;

    static String url = "";
    static String zip = "";

    private DownloadManager downloadManager;
    private long downloadId;
    private BroadcastReceiver downloadReceiver;
    private android.os.Handler progressHandler;
    private Runnable progressRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        webView = findViewById(R.id.web);
        zipE = findViewById(R.id.zip);
        urlE = findViewById(R.id.url);
        uidE = findViewById(R.id.uid);
        gameIdE = findViewById(R.id.gameid);
        tokenE = findViewById(R.id.token);
        channelE = findViewById(R.id.channel);
        appIdE = findViewById(R.id.appId);
        ratioE = findViewById(R.id.ratio);

        SharedPreferences sp = getSharedPreferences(Tag, Context.MODE_PRIVATE);
        zipE.setText(sp.getString(EZip, ""));
        urlE.setText(sp.getString(EUrl, ""));
        uidE.setText(sp.getLong(EUid, 0) + "");
        gameIdE.setText(sp.getInt(EGameId, 0) + "");
        tokenE.setText(sp.getString(EToken, ""));
        channelE.setText(sp.getString(EChannel, ""));
        appIdE.setText(sp.getString(EAppId, ""));
        ratioE.setText(sp.getString(ERatio, ""));

        // 注册下载完成广播
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    Toast.makeText(MainActivity.this, "下载完成，开始解压", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        File downloadedFile = new File(getExternalFilesDir(null), "temp_game_package.zip");
                        if (downloadedFile.exists()) {
                            unzipFile(downloadedFile.getAbsolutePath());
                        } else {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载文件不存在", Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                }
            }
        };
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        findViewById(R.id.btn).setOnClickListener(v -> {
            if (webView.getVisibility() == View.VISIBLE) {
                Toast.makeText(this, R.string.opening, Toast.LENGTH_SHORT).show();
                return;
            }

            zip = zipE.getText().toString();
            url = urlE.getText().toString();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, R.string.urlempty, Toast.LENGTH_SHORT).show();
                return;
            }

            JSKit.Token = tokenE.getText().toString();
            JSKit.Channel = channelE.getText().toString();
            JSKit.AppId = appIdE.getText().toString();
            try {
                JSKit.Uid = Long.parseLong(uidE.getText().toString());
                JSKit.GameId = Integer.parseInt(gameIdE.getText().toString());
            } catch (Exception e) {
                JSKit.Uid = 0;
                JSKit.GameId = 0;
            }
            String ratio = ratioE.getText().toString();

            SharedPreferences.Editor editor = sp.edit();
            editor.putString(EZip, zip);
            editor.putString(EUrl, url);
            editor.putLong(EUid, JSKit.Uid);
            editor.putInt(EGameId, JSKit.GameId);
            editor.putString(EChannel, JSKit.Channel);
            editor.putString(EAppId, JSKit.AppId);
            editor.putString(EToken, JSKit.Token);
            editor.putString(ERatio, ratio);
            editor.apply();

            Log.i(Tag, "url:" + sp.getString(EUrl, ""));
            Log.i(Tag, "EUid:" + sp.getLong(EUid, 0));
            Log.i(Tag, "EToken:" + sp.getString(EToken, ""));

            double ratioD = 1.0;
            try {
                ratioD = Double.parseDouble(ratio);
            } catch (Exception e) {
                e.printStackTrace();
            }
            updateWebView(ratioD);
            webView.setVisibility(View.VISIBLE);

            if (zip.endsWith(".zip")) {
                webView.loadDataWithBaseURL(null, "<html><body>正在下载资源包... 0%</body></html>", "text/html", "UTF-8", null);
                downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                startDownload(zip);
                startProgressTracking();
            } else {
                webView.loadUrl(url);
            }
        });

        initWebView();
    }

    private void startDownload(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("正在下载游戏资源包");
        request.setDescription("下载完成后将自动解压");
        request.setDestinationInExternalFilesDir(this, null, "temp_game_package.zip");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        downloadId = downloadManager.enqueue(request);
    }

    private void unzipFile(String zipFilePath) {
        try {
            ZipFile zipFile = new ZipFile(zipFilePath);
            File outputDir = new File(getExternalFilesDir(null), "game_assets");
            if (outputDir.exists()) {
                deleteDirectory(outputDir);
            }
            outputDir.mkdirs();
            zipFile.extractAll(outputDir.getAbsolutePath());
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "解压完成", Toast.LENGTH_LONG).show();
                loadLocalHtml(outputDir);
            });
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "解压失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void loadLocalHtml(File webRootDir) {
        File indexFile = new File(webRootDir, "web-mobile/index.html");
        if (!indexFile.exists()) {
            Toast.makeText(this, "未找到 index.html", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String htmlContent = readFileAsString(indexFile);
            String baseFilePath = "file://" + webRootDir.getAbsolutePath() + "/web-mobile/index.html";
            String query = "";
            if (url != null && url.contains("?")) {
                query = url.substring(url.indexOf("?"));
            }
            String baseUrlWithQuery = baseFilePath + query;
            webView.loadDataWithBaseURL(baseUrlWithQuery, htmlContent, "text/html", "UTF-8", null);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "读取 HTML 文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 进度追踪相关
    private void startProgressTracking() {
        if (progressHandler == null) {
            progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateDownloadProgress();
                if (progressHandler != null) {
                    progressHandler.postDelayed(this, 500);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void updateDownloadProgress() {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_RUNNING) {
                    long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (totalBytes > 0) {
                        int percent = (int) (bytesDownloaded * 100 / totalBytes);
                        String progressHtml = "<html><body>正在下载资源包... " + percent + "%</body></html>";
                        webView.loadDataWithBaseURL(null, progressHtml, "text/html", "UTF-8", null);
                    }
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    stopProgressTracking();
                } else if (status == DownloadManager.STATUS_FAILED) {
                    stopProgressTracking();
                    Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopProgressTracking() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    private boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        return dir.delete();
    }

    private String readFileAsString(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        fis.read(bytes);
        fis.close();
        return new String(bytes, "UTF-8");
    }

    @Override
    public void onBackPressed() {
        Log.i(Tag, "back");
        if (webView != null) webView.setVisibility(View.INVISIBLE);
    }

    void updateWebView(double ratio) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int halfScreenHeight = getResources().getDisplayMetrics().heightPixels;
        if (ratio > 0) {
            double floor = Math.floor(screenWidth / ratio);
            halfScreenHeight = (int) floor;
        }
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, halfScreenHeight, Gravity.BOTTOM);
        webView.setLayoutParams(layoutParams);
    }

    private void initWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        JSKit jsKit = new JSKit(this);
        webView.addJavascriptInterface(jsKit, "appJS");
        webView.addJavascriptInterface(jsKit, "Application");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) unregisterReceiver(downloadReceiver);
        stopProgressTracking();
    }
}