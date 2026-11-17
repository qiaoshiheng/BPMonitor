package com.q.bpmonitor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用内自动更新：从 GitHub 仓库 update.json 检查版本。
 * 版本检查采用「多源并发竞速」：所有候选地址（含国内镜像与 GitHub API 兜底）
 * 同时发起请求，谁先返回有效数据用谁的；全部失败时回退到本地缓存的
 * 上一次版本信息（cached=true，可能为旧数据）。
 * APK 下载仍按 直连 → 镜像 顺序回退。
 */
public class Updater {

    private static final String RAW_URL =
            "https://raw.githubusercontent.com/qiaoshiheng/BPMonitor/main/update.json";
    /** GitHub Contents API：返回 base64 内容，作为 raw 被墙时的兜底 */
    private static final String API_URL =
            "https://api.github.com/repos/qiaoshiheng/BPMonitor/contents/update.json?ref=main";

    /** update.json 的候选地址（并发竞速，不按顺序依赖） */
    private static final String[] JSON_URLS = {
            "https://ghfast.top/" + RAW_URL,
            "https://gh-proxy.com/" + RAW_URL,
            "https://cdn.jsdelivr.net/gh/qiaoshiheng/BPMonitor@main/update.json",
            "https://ghfast.top/" + API_URL,
            "https://gh-proxy.com/" + API_URL,
            RAW_URL,
            API_URL
    };

    /** APK 下载地址的镜像变换 */
    private static final String[] MIRROR_PREFIXES = {
            "",
            "https://ghfast.top/",
            "https://gh-proxy.com/"
    };

    public interface CheckListener {
        void onUpToDate();
        /** cached=true 表示该版本信息来自本地缓存（所有在线源都不可达时） */
        void onAvailable(int versionCode, String versionName, String notes, String url, boolean cached);
        void onFail(String reason);
    }

    public interface DownloadListener {
        void onProgress(int percent);
        void onDone(File apk);
        void onFail(String reason);
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    // ---------- 版本检查 ----------

    private static final String CACHE_FILE = "update_cache.json";

    public void check(final Context ctx, final CheckListener listener) {
        new Thread(() -> {
            // 多源并发竞速：第一个返回有效 JSON 的源胜出
            final AtomicReference<JSONObject> winner = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(JSON_URLS.length);
            for (final String u : JSON_URLS) {
                new Thread(() -> {
                    try {
                        JSONObject o = fetchVersionJson(u, 6000);
                        if (o != null) winner.compareAndSet(null, o);
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                }, "bp-update-src").start();
            }
            try {
                latch.await(9, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) { }

            JSONObject o = winner.get();
            if (o != null) {
                // 缓存本次成功的版本信息，供断网/全部源失效时兜底
                saveCache(ctx, o.toString());
                notifyVersion(ctx, o, false, listener);
                return;
            }

            // 全部源失败 → 尝试本地缓存
            JSONObject cached = loadCache(ctx);
            if (cached != null) {
                notifyVersion(ctx, cached, true, listener);
                return;
            }
            main.post(() -> listener.onFail("all sources unreachable, no cache"));
        }, "bp-update-check").start();
    }

    /** 解析版本信息并回调（code > installed 才提示更新；缓存模式下 up-to-date 也静默） */
    private void notifyVersion(Context ctx, JSONObject o, final boolean cached, final CheckListener listener) {
        try {
            final int code = o.getInt("versionCode");
            final String name = o.optString("versionName", "");
            final String notes = o.optString("notes", "");
            final String url = o.getString("url");
            final int installed = installedVersionCode(ctx);
            if (code > installed) {
                main.post(() -> listener.onAvailable(code, name, notes, url, cached));
            } else if (!cached) {
                main.post(listener::onUpToDate);
            } else {
                main.post(() -> listener.onFail("cached info up-to-date"));
            }
        } catch (Exception e) {
            main.post(() -> listener.onFail("bad version json: " + e.getMessage()));
        }
    }

    /**
     * 拉取并解析 update.json。
     * raw / jsDelivr / 镜像直接返回 JSON；api.github.com contents 返回
     * {"content":"<base64>"}，需解码后再解析。校验 versionCode/url 字段有效才算成功。
     */
    private JSONObject fetchVersionJson(String urlStr, int timeoutMs) throws Exception {
        String body = httpGet(urlStr, timeoutMs);
        try {
            JSONObject o = new JSONObject(body);
            if (urlStr.contains("api.github.com")) {
                String b64 = o.optString("content", "").replace("\n", "");
                if (b64.isEmpty()) return null;
                o = new JSONObject(new String(
                        Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8));
            }
            o.getInt("versionCode");
            o.getString("url");
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCache(Context ctx, String json) {
        try {
            FileOutputStream fos = ctx.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception ignored) { }
    }

    private JSONObject loadCache(Context ctx) {
        try {
            FileInputStream fis = ctx.openFileInput(CACHE_FILE);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            fis.close();
            JSONObject o = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            o.getInt("versionCode");
            o.getString("url");
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- APK 下载 ----------

    public void download(final Context ctx, final String apkUrl, final DownloadListener listener) {
        new Thread(() -> {
            File out = new File(ctx.getExternalFilesDir("update"), "update.apk");
            Exception lastErr = null;
            for (String prefix : MIRROR_PREFIXES) {
                try {
                    HttpURLConnection conn = open(prefix + apkUrl, 15000);
                    long total = conn.getContentLength();
                    InputStream in = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[16384];
                    long done = 0; int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            final int p = (int) (done * 100 / total);
                            main.post(() -> listener.onProgress(p));
                        }
                    }
                    fos.flush(); fos.close(); in.close();
                    main.post(() -> listener.onDone(out));
                    return;
                } catch (Exception e) {
                    lastErr = e;
                }
            }
            final String msg = lastErr == null ? "unknown" : lastErr.getMessage();
            main.post(() -> listener.onFail(msg));
        }, "bp-update-download").start();
    }

    // ---------- 安装（PackageInstaller，无需 FileProvider） ----------

    public void install(Context activity, File apk) {
        try {
            android.content.pm.PackageInstaller pm =
                    activity.getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams sp =
                    new android.content.pm.PackageInstaller.SessionParams(
                            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setSize(apk.length());
            int id = pm.createSession(sp);
            android.content.pm.PackageInstaller.Session s = pm.openSession(id);
            OutputStream out = s.openWrite("bp_update", 0, apk.length());
            FileInputStream in = new FileInputStream(apk);
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            s.fsync(out);
            out.close(); in.close();

            android.content.Intent confirm = new android.content.Intent(activity, activity.getClass());
            confirm.setAction("com.q.bpmonitor.INSTALL_RESULT");
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(activity,
                    1001, confirm,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_MUTABLE);
            s.commit(pi.getIntentSender());
            s.close();
        } catch (Exception ignored) { }
    }

    /** PackageInstaller 安装结果回调：处理用户确认 / 失败 / 成功 */
    public void handleInstallResult(Context ctx, android.content.Intent intent) {
        if (!"com.q.bpmonitor.INSTALL_RESULT".equals(intent.getAction())) return;
        int status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_FAILURE);
        if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
            android.content.Intent confirm = intent.getParcelableExtra(
                    android.content.Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(confirm); // 弹出系统"安装应用"确认页
            }
        } else if (status == android.content.pm.PackageInstaller.STATUS_FAILURE) {
            String raw = intent.getStringExtra(
                    android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE);
            String friendly;
            if (raw != null && (raw.contains("INCOMPATIBLE") || raw.contains("signatures"))) {
                friendly = "安装失败：与已安装的旧版签名不一致，无法覆盖升级。\n"
                        + "请先卸载旧版再安装新包（卸载会清除本地记录，"
                        + "可先在「AI 咨询文本」里导出数据备份）。";
            } else if (raw != null && raw.contains("INVALID")) {
                friendly = "安装失败：下载的安装包不完整或已损坏，请重试。";
            } else {
                friendly = "安装失败：" + (raw == null ? "未知原因" : raw);
            }
            android.widget.Toast.makeText(ctx, friendly,
                    android.widget.Toast.LENGTH_LONG).show();
        } else if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            android.widget.Toast.makeText(ctx, "更新已安装",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 工具 ----------

    private int installedVersionCode(Context ctx) {
        try {
            return ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    private String httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = open(urlStr, timeoutMs);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        InputStream in = conn.getInputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private HttpURLConnection open(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "BPMonitor");
        return conn;
    }
}
