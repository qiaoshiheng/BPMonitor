package com.q.bpmonitor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 应用内自动更新：从 GitHub 仓库 update.json 检查版本，多路镜像回退（国内可达），
 * 下载 APK（直连/镜像自动切换），交给 MainActivity 通过 PackageInstaller 安装。
 */
public class Updater {

    private static final String REPO_RAW =
            "https://raw.githubusercontent.com/qiaoshiheng/BPMonitor/main/update.json";

    /** update.json 的候选地址，按序尝试直到成功 */
    private static final String[] JSON_URLS = {
            REPO_RAW,
            "https://ghfast.top/" + REPO_RAW,
            "https://gh-proxy.com/" + REPO_RAW,
            "https://cdn.jsdelivr.net/gh/qiaoshiheng/BPMonitor@main/update.json"
    };

    /** APK 下载地址的镜像变换 */
    private static final String[] MIRROR_PREFIXES = {
            "",
            "https://ghfast.top/",
            "https://gh-proxy.com/"
    };

    public interface CheckListener {
        void onUpToDate();
        void onAvailable(int versionCode, String versionName, String notes, String url);
        void onFail(String reason);
    }

    public interface DownloadListener {
        void onProgress(int percent);
        void onDone(File apk);
        void onFail(String reason);
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    // ---------- 版本检查 ----------

    public void check(final Context ctx, final CheckListener listener) {
        new Thread(() -> {
            String reason = "unknown";
            for (String u : JSON_URLS) {
                try {
                    String body = httpGet(u, 8000);
                    JSONObject o = new JSONObject(body);
                    final int code = o.getInt("versionCode");
                    final String name = o.optString("versionName", "");
                    final String notes = o.optString("notes", "");
                    final String url = o.getString("url");
                    int installed = installedVersionCode(ctx);
                    if (code > installed) {
                        main.post(() -> listener.onAvailable(code, name, notes, url));
                    } else {
                        main.post(listener::onUpToDate);
                    }
                    return;
                } catch (Exception e) {
                    reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }
            final String fr = reason;
            main.post(() -> listener.onFail(fr));
        }, "bp-update-check").start();
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

    /** PackageInstaller 安装结果回调：处理用户确认对话框 */
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
