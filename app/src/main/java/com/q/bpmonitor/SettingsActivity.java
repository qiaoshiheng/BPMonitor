package com.q.bpmonitor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 设置页：检查更新 / 导出备份 / 导入恢复。
 * 从主界面拆分而来；手动更新也在此发起（Updater.install 的安装结果
 * PendingIntent 会路由回本页，launchMode=singleTop + onNewIntent 处理）。
 */
public class SettingsActivity extends android.app.Activity {

    private static final int REQ_EXPORT = 2001;
    private static final int REQ_IMPORT = 2002;
    private static final String DB_FILE = "bp_monitor.db";
    private static final String PREFS_NAME = "bp_prefs";

    private DbHelper db;
    private final Updater updater = new Updater();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        setContentView(R.layout.activity_settings);

        ((TextView) findViewById(R.id.tvVersion)).setText("当前版本 v" + versionName());
        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> checkUpdate(true));
        findViewById(R.id.btnBackup).setOnClickListener(v -> startExport());
        findViewById(R.id.btnRestore).setOnClickListener(v -> confirmImport());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updater.handleInstallResult(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 安装结果若通过已存在的实例路由而来，getIntent() 不会更新，必须在此处理
        setIntent(intent);
        updater.handleInstallResult(this, intent);
    }

    // ---------- 检查更新 ----------

    private void checkUpdate(final boolean manual) {
        updater.check(this, new Updater.CheckListener() {
            @Override public void onUpToDate() {
                if (manual) Toast.makeText(SettingsActivity.this,
                        "当前已是最新版本 v" + versionName(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onAvailable(int code, String name, String notes, String url, boolean cached) {
                showUpdateDialog(name, notes, url, cached);
            }
            @Override public void onFail(String reason) {
                android.util.Log.d("BpUpdate", "check fail: " + reason);
                if (manual) {
                    Toast.makeText(SettingsActivity.this,
                            reason != null && reason.contains("cached")
                                    ? "已是最新版本（网络不可达，依据缓存信息判断）"
                                    : "检查更新失败，请检查网络",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "?"; }
    }

    private void showUpdateDialog(String ver, String notes, String url, boolean cached) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_confirm);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        dialog.getWindow().setAttributes(lp);

        TextView title = dialog.findViewById(R.id.tvConfirmTitle);
        TextView msg = dialog.findViewById(R.id.tvConfirmMsg);
        TextView btnNo = dialog.findViewById(R.id.btnCancel);
        TextView btnYes = dialog.findViewById(R.id.btnDelete);
        title.setText("发现新版本 v" + ver);
        msg.setText(notes + "\n\n当前版本 v" + versionName() + "，更新不会丢失任何数据"
                + (cached ? "\n\n（当前网络无法访问更新源，以上为缓存的版本信息）" : ""));
        btnNo.setText("以后再说");
        btnYes.setText("立即更新");
        btnNo.setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            downloadUpdate(url);
        });
        dialog.show();
    }

    private void downloadUpdate(String url) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(18));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("正在下载更新");
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF1F2430);
        card.addView(title);

        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFE5484D));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(16), 0, 0);
        bar.setLayoutParams(blp);
        card.addView(bar);

        TextView pct = new TextView(this);
        pct.setText("0%");
        pct.setTextSize(13);
        pct.setTextColor(0xFF8A93A2);
        pct.setGravity(android.view.Gravity.CENTER);
        pct.setPadding(0, dp(10), 0, 0);
        card.addView(pct);

        dialog.setContentView(card);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        dialog.getWindow().setAttributes(lp);
        dialog.show();

        updater.download(this, url, new Updater.DownloadListener() {
            @Override public void onProgress(int p) {
                bar.setProgress(p);
                pct.setText(p + "%");
            }
            @Override public void onDone(java.io.File apk) {
                dialog.dismiss();
                Toast.makeText(SettingsActivity.this, "下载完成，正在请求安装…", Toast.LENGTH_SHORT).show();
                updater.install(SettingsActivity.this, apk);
            }
            @Override public void onFail(String reason) {
                dialog.dismiss();
                android.util.Log.d("BpUpdate", "download fail: " + reason);
                Toast.makeText(SettingsActivity.this,
                        "下载失败，请检查网络后重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---------- 数据备份 / 恢复 ----------

    /** 导出备份：系统"保存文件"对话框，用户选择位置（无需存储权限） */
    private void startExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, "BPMonitor备份_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date())
                + ".bpbackup");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开保存对话框", Toast.LENGTH_SHORT).show();
        }
    }

    /** 导入恢复：先确认覆盖风险，再选择备份文件 */
    private void confirmImport() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_confirm);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        dialog.getWindow().setAttributes(lp);

        TextView title = dialog.findViewById(R.id.tvConfirmTitle);
        TextView msg = dialog.findViewById(R.id.tvConfirmMsg);
        TextView btnNo = dialog.findViewById(R.id.btnCancel);
        TextView btnYes = dialog.findViewById(R.id.btnDelete);
        title.setText("导入备份");
        msg.setText("导入将覆盖当前全部血压记录与用户档案，且不可撤销。\n\n"
                + "建议先「导出备份」保存当前数据，再选择备份文件导入。");
        btnNo.setText("取消");
        btnYes.setText("选择文件并导入");
        btnNo.setOnClickListener(v -> dialog.dismiss());
        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try {
                startActivityForResult(i, REQ_IMPORT);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_EXPORT) {
            doExport(data.getData());
        } else if (requestCode == REQ_IMPORT) {
            doImport(data.getData());
        }
    }

    /** 导出：zip 打包 {数据库文件 + 偏好设置(当前用户/更新检查时间)} */
    private void doExport(Uri uri) {
        try {
            // 回写 WAL，保证拷出的是完整数据
            android.database.Cursor c = db.getReadableDatabase()
                    .rawQuery("PRAGMA wal_checkpoint(FULL)", null);
            c.moveToFirst(); c.close();
            db.close(); // 关闭连接，防止拷贝到写一半的文件

            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    getContentResolver().openOutputStream(uri));
            byte[] buf = new byte[16384]; int n;

            zos.putNextEntry(new java.util.zip.ZipEntry("db/" + DB_FILE));
            java.io.FileInputStream fis = new java.io.FileInputStream(getDatabasePath(DB_FILE));
            while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
            fis.close();
            zos.closeEntry();

            zos.putNextEntry(new java.util.zip.ZipEntry("prefs.json"));
            org.json.JSONObject prefs = new org.json.JSONObject();
            for (java.util.Map.Entry<String, ?> e :
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Long || v instanceof Integer || v instanceof Boolean
                        || v instanceof Float || v instanceof String) {
                    prefs.put(e.getKey(), v);
                }
            }
            zos.write(prefs.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.close();
            Toast.makeText(this, "备份已导出 ✓", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 导入：校验备份文件 → 覆盖数据库与偏好设置 → 刷新 */
    private void doImport(Uri uri) {
        try {
            java.io.File tmpDb = new java.io.File(getCacheDir(), "restore_db");
            java.io.File tmpPrefs = new java.io.File(getCacheDir(), "restore_prefs.json");
            boolean hasDb = false, hasPrefs = false;

            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    getContentResolver().openInputStream(uri));
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (("db/" + DB_FILE).equals(e.getName())) {
                    copyStream(zis, new java.io.FileOutputStream(tmpDb));
                    hasDb = true;
                } else if ("prefs.json".equals(e.getName())) {
                    copyStream(zis, new java.io.FileOutputStream(tmpPrefs));
                    hasPrefs = true;
                }
                zis.closeEntry();
            }
            zis.close();
            if (!hasDb) {
                Toast.makeText(this, "无效的备份文件（缺少数据库）", Toast.LENGTH_LONG).show();
                return;
            }

            // 校验 SQLite 文件头，防止选错文件把数据库覆盖坏
            java.io.FileInputStream chk = new java.io.FileInputStream(tmpDb);
            byte[] magic = new byte[16];
            int rn = chk.read(magic);
            chk.close();
            String head = new String(magic, 0, Math.max(rn, 0), java.nio.charset.StandardCharsets.UTF_8);
            if (!head.startsWith("SQLite format 3")) {
                Toast.makeText(this, "备份文件校验失败，已取消导入", Toast.LENGTH_LONG).show();
                return;
            }

            // 覆盖数据库
            db.close();
            java.io.File dst = getDatabasePath(DB_FILE);
            copyFile(tmpDb, dst);
            new java.io.File(dst.getPath() + "-wal").delete();
            new java.io.File(dst.getPath() + "-shm").delete();
            new java.io.File(dst.getPath() + "-journal").delete();

            // 恢复偏好设置（当前用户等）
            android.content.SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (hasPrefs) {
                org.json.JSONObject o = new org.json.JSONObject(readText(tmpPrefs));
                android.content.SharedPreferences.Editor ed = sp.edit();
                ed.clear();
                java.util.Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    Object v = o.get(k);
                    // JSON 的整数不区分 int/long，org.json 小数值解析为 Integer；
                    // SharedPreferences 里这些键都是 long，必须统一 putLong，否则 getLong 会崩
                    if (v instanceof Integer || v instanceof Long) ed.putLong(k, ((Number) v).longValue());
                    else if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                    else if (v instanceof Double) ed.putFloat(k, (float) (double) (Double) v);
                    else if (v instanceof String) ed.putString(k, (String) v);
                }
                ed.apply();
            }

            tmpDb.delete(); tmpPrefs.delete();

            // 恢复出的"当前用户"必须在数据库中存在
            Object restoredUid = sp.getAll().get("uid");
            long uid = (restoredUid instanceof Number)
                    ? ((Number) restoredUid).longValue() : DbHelper.DEFAULT_USER_ID;
            if (db.user(uid) == null) {
                uid = DbHelper.DEFAULT_USER_ID;
                sp.edit().putLong("uid", uid).apply();
            }
            Toast.makeText(this, "恢复完成 ✓ 记录已还原", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "恢复失败：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyStream(java.io.InputStream in, java.io.FileOutputStream out) throws Exception {
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
        out.close();
    }

    private void copyFile(java.io.File src, java.io.File dst) throws Exception {
        copyStream(new java.io.FileInputStream(src), new java.io.FileOutputStream(dst));
    }

    private String readText(java.io.File f) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] buf = new byte[16384]; int n;
        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
        fis.close();
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
