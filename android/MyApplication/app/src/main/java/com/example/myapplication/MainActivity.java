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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // 版本标记文件名，记录解压目录对应的版本
    private static final String VERSION_MARK_FILE = ".version";

    WebView webView;
    EditText zipE, urlE, uidE, gameIdE, tokenE, channelE, appIdE, ratioE, goldE;
    View topup;

    static String url = "";
    static String zip = "";

    private DownloadManager downloadManager;
    private BroadcastReceiver downloadReceiver;
    private Handler progressHandler;
    private Runnable progressRunnable;

    // 按 downloadId 索引的下载任务表，支持并发下载
    private final ConcurrentHashMap<Long, DownloadTask> downloadTasks = new ConcurrentHashMap<>();

    /**
     * 一次下载任务的上下文，按 downloadId 索引
     */
    private static class DownloadTask {
        String zipFileName;   // 本地 zip 文件名，如 olympus-2.0.zip
        File assetsDir;       // 解压目标目录
        String gameName;      // 游戏名
        String version;       // 版本号
        long downloadId;      // DownloadManager 返回的 id
    }

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
        // Android 14+ 必须显式指定 flag；系统发出的广播需 RECEIVER_EXPORTED
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                DownloadTask task = downloadTasks.get(id);
                if (task == null) return; // 不是我们发起的任务

                // 无论成败，先移除任务
                downloadTasks.remove(id);

                File tempFile = new File(getExternalFilesDir(null), task.zipFileName + ".temp");
                File targetFile = new File(getExternalFilesDir(null), task.zipFileName);

                // 查询真实状态，避免下载失败也走重命名
                boolean success = false;
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(id);
                try (Cursor c = downloadManager.query(q)) {
                    if (c != null && c.moveToFirst()) {
                        @SuppressLint("Range") int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        success = (status == DownloadManager.STATUS_SUCCESSFUL);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!success) {
                    if (tempFile.exists()) tempFile.delete();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载失败: " + task.gameName, Toast.LENGTH_SHORT).show());
                    return;
                }

                if (tempFile.exists()) {
                    boolean renamed = tempFile.renameTo(targetFile);
                    if (renamed) {
                        Toast.makeText(MainActivity.this, "下载完成，开始解压: " + task.gameName, Toast.LENGTH_SHORT).show();
                        // 下载期间可能又出现别的版本，重命名成功后再清理一次旧版本
                        cleanOldVersions(task.gameName, task.zipFileName);
                        new Thread(() -> unzipFile(targetFile.getAbsolutePath(), task.assetsDir, task.version)).start();
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "文件重命名失败", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载文件不存在", Toast.LENGTH_SHORT).show());
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
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
                String expectedZipFileName = extractFileNameFromUrl(zip);
                if (TextUtils.isEmpty(expectedZipFileName)) {
                    Toast.makeText(this, "无法解析压缩包文件名", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] parsed = parseGameNameAndVersion(expectedZipFileName);
                if (parsed == null || TextUtils.isEmpty(parsed[0])) {
                    Toast.makeText(this, "无法解析游戏名", Toast.LENGTH_SHORT).show();
                    return;
                }
                String gameName = parsed[0];
                String version = parsed[1];
                Log.i(Tag, "gameName=" + gameName + ", version=" + version);

                // 每个游戏独立目录
                File localZipFile = new File(getExternalFilesDir(null), expectedZipFileName);
                File gameAssetsDir = new File(getExternalFilesDir(null), "game_assets/" + gameName);

                // 先清理该游戏的其他版本 zip / temp，只保留当前版本
                cleanOldVersions(gameName, expectedZipFileName);

                if (localZipFile.exists() && localZipFile.length() > 0) {
                    // 判断解压内容是否为当前版本（而非仅判断 index.html 是否存在）
                    if (isAssetsUpToDate(gameAssetsDir, version)) {
                        Toast.makeText(this, "使用已下载的资源包: " + gameName + " " + version, Toast.LENGTH_SHORT).show();
                        loadLocalHtml(gameAssetsDir);
                        webView.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, "解压内容版本不匹配，重新解压", Toast.LENGTH_SHORT).show();
                        webView.loadDataWithBaseURL(null, "<html><body>正在解压资源包...</body></html>", "text/html", "UTF-8", null);
                        webView.setVisibility(View.VISIBLE);
                        new Thread(() -> unzipFile(localZipFile.getAbsolutePath(), gameAssetsDir, version)).start();
                    }
                } else {
                    // 本地没有当前版本 zip，走下载
                    webView.loadDataWithBaseURL(null, "<html><body>正在下载资源包...</body></html>", "text/html", "UTF-8", null);
                    webView.setVisibility(View.VISIBLE);

                    downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                    // 若同一游戏已有下载任务，先取消，避免重复
                    cancelExistingDownloadForGame(gameName);

                    DownloadTask task = new DownloadTask();
                    task.zipFileName = expectedZipFileName;
                    task.assetsDir = gameAssetsDir;
                    task.gameName = gameName;
                    task.version = version;
                    task.downloadId = enqueueDownload(zip, expectedZipFileName);

                    downloadTasks.put(task.downloadId, task);
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

    // ==================== 文件名 / 版本解析 ====================

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
     * olympus-1.0.zip    -> ["olympus", "1.0"]
     * my-game-2.3.4.zip  -> ["my-game", "2.3.4"]
     * nohyphen.zip       -> ["nohyphen", ""]
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
        gameName = gameName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new String[]{gameName, version};
    }

    // ==================== 版本标记 ====================

    private void writeVersionMark(File assetsDir, String version) {
        try {
            if (!assetsDir.exists()) assetsDir.mkdirs();
            File markFile = new File(assetsDir, VERSION_MARK_FILE);
            FileOutputStream fos = new FileOutputStream(markFile, false);
            fos.write(version.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            Log.i(Tag, "写入版本标记: " + version + " -> " + markFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readVersionMark(File assetsDir) {
        try {
            File markFile = new File(assetsDir, VERSION_MARK_FILE);
            if (!markFile.exists()) return null;
            FileInputStream fis = new FileInputStream(markFile);
            byte[] buf = new byte[(int) markFile.length()];
            int len = fis.read(buf);
            fis.close();
            if (len <= 0) return null;
            return new String(buf, 0, len, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 判断当前解压目录是否已经是对应版本的完整解压内容
     */
    private boolean isAssetsUpToDate(File assetsDir, String version) {
        File indexFile = new File(assetsDir, "web-mobile/index.html");
        if (!indexFile.exists()) return false;
        String marked = readVersionMark(assetsDir);
        return version != null && version.equals(marked);
    }

    // ==================== 清理旧版本 ====================

    /**
     * 清理同一游戏的旧版本 zip 包与 .temp 残留，只保留 keepZipFileName 对应的文件。
     * 解压目录由 unzipFile 内部先删再建，因此这里无需处理目录。
     */
    private void cleanOldVersions(String gameName, String keepZipFileName) {
        File filesDir = getExternalFilesDir(null);
        if (filesDir == null || !filesDir.exists()) return;
        File[] files = filesDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory()) continue;

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

    // ==================== 下载 ====================

    /**
     * 入队下载，返回 downloadId
     */
    private long enqueueDownload(String url, String localFileName) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("正在下载游戏资源包");
        request.setDescription("下载完成后将自动解压");
        request.setDestinationInExternalFilesDir(this, null, localFileName + ".temp");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        return downloadManager.enqueue(request);
    }

    /**
     * 若同一游戏已有下载任务，取消之，避免重复下载
     */
    private void cancelExistingDownloadForGame(String gameName) {
        if (downloadManager == null) return;
        Iterator<Map.Entry<Long, DownloadTask>> it = downloadTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, DownloadTask> e = it.next();
            if (gameName.equals(e.getValue().gameName)) {
                downloadManager.remove(e.getKey());
                it.remove();
                Log.i(Tag, "取消同游戏旧下载任务: " + gameName + " id=" + e.getKey());
            }
        }
    }

    // ==================== 解压（临时目录 + 原子替换） ====================

    /**
     * 解压到指定目录，成功后写入版本标记。
     * 为保证原子性：先解压到 <outputDir>.tmp，成功后再替换正式目录。
     * 失败时正式目录保持不变（或保持为空），绝不会出现"半成品被当成有效缓存"。
     */
    private void unzipFile(String zipFilePath, File outputDir, String version) {
        File tmpDir = new File(outputDir.getAbsolutePath() + ".tmp");
        try {
            if (tmpDir.exists()) deleteDirectory(tmpDir);
            tmpDir.mkdirs();

            ZipFile zipFile = new ZipFile(zipFilePath);
            zipFile.extractAll(tmpDir.getAbsolutePath());

            // 校验关键文件存在
            File tmpIndex = new File(tmpDir, "web-mobile/index.html");
            if (!tmpIndex.exists()) {
                Toast.makeText(MainActivity.this, "解压失败: 解压后未找到 web-mobile/index.html", Toast.LENGTH_SHORT).show();
                return;
            }

            // 写入版本标记到临时目录
            writeVersionMark(tmpDir, version);

            // 原子替换
            if (outputDir.exists()) deleteDirectory(outputDir);
            if (!tmpDir.renameTo(outputDir)) {
                copyDirectory(tmpDir, outputDir);
                deleteDirectory(tmpDir);
            }

            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "解压完成", Toast.LENGTH_LONG).show();
                loadLocalHtml(outputDir);
            });
        } catch (Exception e) {
            e.printStackTrace();
            if (tmpDir.exists()) deleteDirectory(tmpDir);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "解压失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void copyDirectory(File src, File dst) throws IOException {
        if (!dst.exists()) dst.mkdirs();
        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target);
            } else {
                FileInputStream in = new FileInputStream(child);
                FileOutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                out.close();
            }
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

    // ==================== 下载进度追踪（多任务） ====================

    private void startProgressTracking() {
        if (progressHandler == null) {
            progressHandler = new Handler();
        }
        if (progressRunnable != null) {
            // 已在运行
            return;
        }
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateAllDownloadsProgress();
                if (progressHandler != null && !downloadTasks.isEmpty()) {
                    progressHandler.postDelayed(this, 500);
                } else {
                    progressRunnable = null;
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void updateAllDownloadsProgress() {
        if (downloadTasks.isEmpty()) return;
        for (DownloadTask task : downloadTasks.values()) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(task.downloadId);
            try (Cursor cursor = downloadManager.query(query)) {
                if (cursor != null && cursor.moveToFirst()) {
                    @SuppressLint("Range") int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_RUNNING) {
                        @SuppressLint("Range") long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        @SuppressLint("Range") long totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        if (totalBytes > 0) {
                            int percent = (int) (bytesDownloaded * 100 / totalBytes);
                            String progressHtml = "<html><body>正在下载 " + task.gameName + " " + task.version + "... " + percent + "%</body></html>";
                            runOnUiThread(() -> webView.loadDataWithBaseURL(null, progressHtml, "text/html", "UTF-8", null));
                        }
                    }
                    // SUCCESSFUL / FAILED 由广播统一处理
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopProgressTracking() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
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

    // ==================== 充值 / 送礼请求 ====================

    public void sendPrespinRequest(long gold) {
        OkHttpClient client = new OkHttpClient();

        FormBody formBody = new FormBody.Builder().add("uid", String.valueOf(JSKit.Uid)).add("token", "").add("gold", String.valueOf(gold)).build();

        Request request = new Request.Builder().url("https://test2.fanyula.com/buddysrv/gold").post(formBody).build();

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