package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    static final String Tag = "MainActivity";

    // SharedPreferences 键名（只保存用户输入的参数，不保存版本）
    static final String EZip = "EZip";
    static final String EUrl = "EUrl";
    static final String EUid = "EUid";
    static final String EToken = "EToken";
    static final String EChannel = "EChannel";
    static final String EAppId = "EAppId";
    static final String EGameId = "EGameId";
    static final String ERatio = "ERatio";

    WebView webView;
    EditText zipE, urlE, uidE, gameIdE, tokenE, channelE, appIdE, ratioE, goldE;
    View topup;

    static String url = "";
    static String zip = "";

    private DownloadManager downloadManager;
    private long downloadId;
    private BroadcastReceiver downloadReceiver;
    private Handler progressHandler;
    private Runnable progressRunnable;

    // 当前期望的本地 zip 文件名（不含路径），例如 olympus-1.0.zip
    private String expectedZipFileName;
    // 当前下载完成后应解压到的目标目录，例如 .../game_assets/olympus
    private File pendingGameAssetsDir;
    // 当前下载对应的游戏名，用于广播里再次清理旧版本
    private String pendingGameName;

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

        topup = findViewById(R.id.ll_topup);
        goldE = findViewById(R.id.gold);

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
        // 注意：DownloadManager 的广播是系统发出的，必须用 RECEIVER_EXPORTED，
        // 否则 Android 14/15 上收不到。
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    // 下载完成，将 .temp 文件重命名为正式文件名
                    File tempFile = new File(getExternalFilesDir(null), expectedZipFileName + ".temp");
                    File targetFile = new File(getExternalFilesDir(null), expectedZipFileName);
                    if (tempFile.exists()) {
                        boolean renamed = tempFile.renameTo(targetFile);
                        if (renamed) {
                            Toast.makeText(MainActivity.this, "下载完成，开始解压", Toast.LENGTH_SHORT).show();
                            // 下载期间可能又出现别的版本，重命名成功后再清理一次旧版本
                            if (pendingGameName != null) {
                                cleanOldVersions(pendingGameName, expectedZipFileName);
                            }
                            final File assetsDir = pendingGameAssetsDir != null
                                    ? pendingGameAssetsDir
                                    : new File(getExternalFilesDir(null), "game_assets");
                            new Thread(() -> unzipFile(targetFile.getAbsolutePath(), assetsDir)).start();
                        } else {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "文件重命名失败", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载文件不存在", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 必须显式指定；系统广播需 EXPORTED
            registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

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

            // 保存 JSKit 参数
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

            double ratioD = 1.0;
            try {
                ratioD = Double.parseDouble(ratio);
            } catch (Exception e) {
                e.printStackTrace();
            }
            updateWebView(ratioD);

            // 核心逻辑：通过本地文件名比对版本，决定是否需要下载
            if (!TextUtils.isEmpty(zip)) {
                // 从 zip URL 中提取期望的本地文件名
                expectedZipFileName = extractFileNameFromUrl(zip);
                if (TextUtils.isEmpty(expectedZipFileName)) {
                    Toast.makeText(this, "无法解析压缩包文件名", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 从文件名解析游戏名和版本：olympus-1.0.zip -> ["olympus", "1.0"]
                String[] parsed = parseGameNameAndVersion(expectedZipFileName);
                if (parsed == null || TextUtils.isEmpty(parsed[0])) {
                    Toast.makeText(this, "无法解析游戏名", Toast.LENGTH_SHORT).show();
                    return;
                }
                String gameName = parsed[0];
                String version = parsed[1];
                Log.i(Tag, "gameName=" + gameName + ", version=" + version);

                // 每个游戏独立目录：game_assets/<游戏名>/
                File localZipFile = new File(getExternalFilesDir(null), expectedZipFileName);
                File gameAssetsDir = new File(getExternalFilesDir(null), "game_assets/" + gameName);
                File indexFile = new File(gameAssetsDir, "web-mobile/index.html");

                // 无论走哪条分支，先清理该游戏的其他版本 zip / temp，只保留当前版本
                cleanOldVersions(gameName, expectedZipFileName);

                if (localZipFile.exists() && localZipFile.length() > 0) {
                    if (indexFile.exists()) {
                        // 已有完整解压内容，直接加载
                        Toast.makeText(this, "使用已下载的资源包: " + gameName + " " + version, Toast.LENGTH_SHORT).show();
                        loadLocalHtml(gameAssetsDir);
                        webView.setVisibility(View.VISIBLE);
                    } else {
                        // 解压目录缺失，需要解压
                        Toast.makeText(this, "解压目录缺失，重新解压", Toast.LENGTH_SHORT).show();
                        webView.loadDataWithBaseURL(null, "<html><body>正在解压资源包...</body></html>", "text/html", "UTF-8", null);
                        webView.setVisibility(View.VISIBLE);
                        new Thread(() -> unzipFile(localZipFile.getAbsolutePath(), gameAssetsDir)).start();
                    }
                } else {
                    // 本地没有同名 zip 文件，需要下载
                    webView.loadDataWithBaseURL(null, "<html><body>正在下载资源包...</body></html>", "text/html", "UTF-8", null);
                    webView.setVisibility(View.VISIBLE);
                    downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    pendingGameAssetsDir = gameAssetsDir;
                    pendingGameName = gameName;
                    startDownloadWithTemp(zip);
                    startProgressTracking();
                }
            } else {
                // 没有 zip 地址，直接加载网络 URL
                webView.loadUrl(url);
                webView.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.btn_topup_close).setOnClickListener(view -> {
            topup.setVisibility(View.GONE);
        });
        findViewById(R.id.btn_add).setOnClickListener(view -> {
            String s = goldE.getText().toString();
            if (TextUtils.isEmpty(s)) return;
            long gold = Long.parseLong(s);
            if (gold < 0) gold = -gold;
            sendPrespinRequest(gold);
        });
        findViewById(R.id.btn_minus).setOnClickListener(view -> {
            String s = goldE.getText().toString();
            if (TextUtils.isEmpty(s)) return;
            long gold = Long.parseLong(s);
            if (gold > 0) gold = -gold;
            sendPrespinRequest(gold);
        });

        initWebView();
    }

    /**
     * 从 URL 中提取文件名（例如 http://example.com/olympus-1.0.zip?token=xxx -> olympus-1.0.zip）
     */
    private String extractFileNameFromUrl(String url) {
        if (TextUtils.isEmpty(url)) return null;
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            if (path == null) return null;
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            int queryIndex = fileName.indexOf('?');
            if (queryIndex != -1) {
                fileName = fileName.substring(0, queryIndex);
            }
            return fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解析 zip 文件名，提取游戏名和版本号。
     * 规则：以最后一个 '-' 分隔。
     *   olympus-1.0.zip    -> ["olympus", "1.0"]
     *   my-game-2.3.4.zip  -> ["my-game", "2.3.4"]
     *   nohyphen.zip       -> ["nohyphen", ""]
     */
    private String[] parseGameNameAndVersion(String zipFileName) {
        if (TextUtils.isEmpty(zipFileName)) return null;
        String base = zipFileName;
        if (base.toLowerCase().endsWith(".zip")) {
            base = base.substring(0, base.length() - 4);
        }
        int idx = base.lastIndexOf('-');
        String gameName;
        String version = "";
        if (idx > 0) {
            gameName = base.substring(0, idx);
            version = base.substring(idx + 1);
        } else {
            gameName = base;
        }
        // 安全过滤，防止路径穿越
        gameName = gameName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new String[]{gameName, version};
    }

    /**
     * 清理同一游戏的旧版本 zip 包与 .temp 残留，只保留 keepZipFileName 对应的文件。
     * 解压目录由 unzipFile 内部先删再建，因此这里无需处理目录。
     *
     * @param gameName        游戏名
     * @param keepZipFileName 需要保留的 zip 文件名（不含路径）
     */
    private void cleanOldVersions(String gameName, String keepZipFileName) {
        File filesDir = getExternalFilesDir(null);
        if (filesDir == null || !filesDir.exists()) return;
        File[] files = filesDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory()) continue;

            // 保留当前版本
            if (name.equals(keepZipFileName)) continue;
            if (name.equals(keepZipFileName + ".temp")) continue;

            if (name.toLowerCase().endsWith(".zip")) {
                String[] parsed = parseGameNameAndVersion(name);
                if (parsed != null && gameName.equals(parsed[0])) {
                    if (f.delete()) Log.i(Tag, "已删除旧版本 zip: " + name);
                }
            } else if (name.endsWith(".temp")) {
                String base = name.substring(0, name.length() - 5);
                String[] parsed = parseGameNameAndVersion(base);
                if (parsed != null && gameName.equals(parsed[0])) {
                    if (f.delete()) Log.i(Tag, "已删除旧版本 temp: " + name);
                }
            }
        }
    }

    /**
     * 使用临时文件名下载，下载完成后在广播接收器中重命名
     */
    private void startDownloadWithTemp(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("正在下载游戏资源包");
        request.setDescription("下载完成后将自动解压");
        request.setDestinationInExternalFilesDir(this, null, expectedZipFileName + ".temp");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        downloadId = downloadManager.enqueue(request);
    }

    /**
     * 解压到指定目录。若目录已存在，先清空再解压（保证只保留当前版本内容）。
     */
    private void unzipFile(String zipFilePath, File outputDir) {
        try {
            ZipFile zipFile = new ZipFile(zipFilePath);
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
            Toast.makeText(this, "未找到 web-mobile/index.html", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = Uri.fromFile(indexFile);
        String query = "";
        if (url != null && url.contains("?")) {
            query = url.substring(url.indexOf("?"));
        }
        String finalUrl = fileUri.toString() + query;
        webView.loadUrl(finalUrl);
    }

    // 下载进度追踪
    private void startProgressTracking() {
        if (progressHandler == null) {
            progressHandler = new Handler();
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
                @SuppressLint("Range") int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_RUNNING) {
                    @SuppressLint("Range") long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    @SuppressLint("Range") long totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (totalBytes > 0) {
                        int percent = (int) (bytesDownloaded * 100 / totalBytes);
                        String progressHtml = "<html><body>正在下载资源包... " + percent + "%</body></html>";
                        webView.loadDataWithBaseURL(null, progressHtml, "text/html", "UTF-8", null);
                    }
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    stopProgressTracking();
                } else if (status == DownloadManager.STATUS_FAILED) {
                    stopProgressTracking();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show());
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
        webSettings.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= 16) {
            webSettings.setAllowFileAccessFromFileURLs(true);
            webSettings.setAllowUniversalAccessFromFileURLs(true);
        }
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        JSKit jsKit = new JSKit(this);
        webView.addJavascriptInterface(jsKit, "appJS");
        webView.addJavascriptInterface(jsKit, "Application");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
        stopProgressTracking();
    }

    public void sendPrespinRequest(long gold) {
        OkHttpClient client = new OkHttpClient();

        FormBody formBody = new FormBody.Builder()
                .add("uid", String.valueOf(JSKit.Uid))
                .add("token", "")
                .add("gold", String.valueOf(gold))
                .build();

        Request request = new Request.Builder()
                .url("https://test2.fanyula.com/buddysrv/gold")
                .post(formBody)
                .build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    try {
                        Gson gson = new Gson();
                        BaseResponse result = gson.fromJson(jsonResponse, BaseResponse.class);
                        runOnUiThread(() -> handleResponse(result, gold));
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "数据异常:" + jsonResponse, Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "网络请求失败", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleResponse(BaseResponse response, long gold) {
        if (response.getCode() == 0) {
            Toast.makeText(this, "✅ " + response.getMsg(), Toast.LENGTH_SHORT).show();
            // 充值回调，送礼不回调
            if (gold > 0) JSKit.WalletUpdateNoCoin(this.webView);
        } else {
            Toast.makeText(this, "❌ " + response.getMsg(), Toast.LENGTH_SHORT).show();
        }
    }
}