package com.q.bpmonitor;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends android.app.Activity {

    public static final int PERIOD_DAY = 0, PERIOD_WEEK = 1, PERIOD_MONTH = 2, PERIOD_YEAR = 3;
    private static final String[] PERIOD_NAMES = {"日", "周", "月", "年"};

    private DbHelper db;
    private int period = PERIOD_DAY;
    private long currentUserId = DbHelper.DEFAULT_USER_ID;

    private TextView tvDate, tvLatestSys, tvLatestDia, tvLatestHr, tvLatestTime, tvChartSummary;
    private LinearLayout badgeRow, periodTabs, recordList, adviceList, statsRow;
    private ChartView chart;
    private TextView userChip;
    private final Updater updater = new Updater();

    private final SimpleDateFormat fmtToday = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
    private final SimpleDateFormat fmtDateTime = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA);
    private final SimpleDateFormat fmtShort = new SimpleDateFormat("M/d", Locale.CHINA);
    private final SimpleDateFormat fmtHm = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat fmtFull = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        setContentView(R.layout.activity_main);

        tvDate = findViewById(R.id.tvDate);
        tvLatestSys = findViewById(R.id.tvLatestSys);
        tvLatestDia = findViewById(R.id.tvLatestDia);
        tvLatestHr = findViewById(R.id.tvLatestHr);
        tvLatestTime = findViewById(R.id.tvLatestTime);
        tvChartSummary = findViewById(R.id.tvChartSummary);
        badgeRow = findViewById(R.id.badgeRow);
        periodTabs = findViewById(R.id.periodTabs);
        recordList = findViewById(R.id.recordList);
        chart = findViewById(R.id.chart);
        statsRow = findViewById(R.id.statsRow);
        adviceList = findViewById(R.id.adviceList);
        userChip = findViewById(R.id.userChip);

        // 类型安全读取：兼容旧版导入 bug 曾把 uid 写成 int 的情况（getLong 会抛 ClassCastException）
        Object savedUid = getSharedPreferences("bp_prefs", MODE_PRIVATE).getAll().get("uid");
        currentUserId = (savedUid instanceof Number)
                ? ((Number) savedUid).longValue() : DbHelper.DEFAULT_USER_ID;

        userChip.setOnClickListener(v -> showUserDialog());
        findViewById(R.id.tvAiTextBtn).setOnClickListener(v -> showAiTextDialog());
        findViewById(R.id.tvSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        tvDate.setText(fmtToday.format(new Date()));

        for (int i = 0; i < PERIOD_NAMES.length; i++) addPeriodTab(i);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddRecordActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updater.handleInstallResult(this, getIntent());
        autoCheckUpdate();
        refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // PackageInstaller 结果若通过已存在实例路由而来（如 singleTask/singleTop），
        // getIntent() 不会更新，必须在 onNewIntent 里处理
        setIntent(intent);
        updater.handleInstallResult(this, intent);
    }

    // ---------- 自动更新 ----------

    private void autoCheckUpdate() {
        Object l = getSharedPreferences("bp_prefs", MODE_PRIVATE).getAll().get("last_update_check");
        long last = (l instanceof Number) ? ((Number) l).longValue() : 0;
        if (System.currentTimeMillis() - last < 24f * 3600 * 1000) return;
        getSharedPreferences("bp_prefs", MODE_PRIVATE).edit()
                .putLong("last_update_check", System.currentTimeMillis()).apply();
        checkUpdate(false);
    }

    private void checkUpdate(final boolean manual) {
        updater.check(this, new Updater.CheckListener() {
            @Override public void onUpToDate() {
                if (manual) Toast.makeText(MainActivity.this,
                        "当前已是最新版本 v" + versionName(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onAvailable(int code, String name, String notes, String url, boolean cached) {
                showUpdateDialog(name, notes, url, cached);
            }
            @Override public void onFail(String reason) {
                android.util.Log.d("BpUpdate", "check fail: " + reason);
                if (manual) {
                    Toast.makeText(MainActivity.this,
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

    // ---------- 数据备份 / 恢复：已移至 SettingsActivity ----------

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
        pct.setGravity(Gravity.CENTER);
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
                Toast.makeText(MainActivity.this, "下载完成，正在请求安装…", Toast.LENGTH_SHORT).show();
                updater.install(MainActivity.this, apk);
            }
            @Override public void onFail(String reason) {
                dialog.dismiss();
                android.util.Log.d("BpUpdate", "download fail: " + reason);
                Toast.makeText(MainActivity.this,
                        "下载失败，请检查网络后重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addPeriodTab(final int idx) {
        Button b = new Button(this);
        b.setText(PERIOD_NAMES[idx]);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setStateListAnimator(null);
        b.setIncludeFontPadding(false);
        int padH = dp(14), padV = dp(7);
        b.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (idx > 0) lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> { period = idx; refresh(); });
        periodTabs.addView(b);
    }

    private void styleTabs() {
        for (int i = 0; i < periodTabs.getChildCount(); i++) {
            Button b = (Button) periodTabs.getChildAt(i);
            if (i == period) {
                b.setBackgroundResource(R.drawable.bg_chip_selected);
                b.setTextColor(Color.WHITE);
            } else {
                b.setBackgroundResource(R.drawable.bg_chip);
                b.setTextColor(Color.parseColor("#5A6270"));
            }
        }
    }

    // ---------- 数据刷新 ----------

    private void refresh() {
        styleTabs();
        renderUserChip();
        renderLatest();
        renderChart();
        renderAdvice();
        renderList();
    }

    // ---------- 健康提示（基于文献的建议引擎） ----------
    // 级别：3=紧急(红) 2=警示(琥珀) 1=关注(蓝) 0=良好(绿)

    private static class AdviceItem {
        final int level; final String text;
        AdviceItem(int level, String text) { this.level = level; this.text = text; }
    }

    private void renderAdvice() {
        adviceList.removeAllViews();
        List<AdviceItem> items = new ArrayList<>();

        List<Record> all = db.all(currentUserId);
        if (all.isEmpty()) {
            addItem(new AdviceItem(0, "还没有测量记录。建议每天固定时间（如晨起后）静息 5 分钟，坐位测量，袖带与心脏同高；连续记录才能看出趋势。"));
            return;
        }

        Record latest = all.get(0);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        List<Record> week = db.since(cal.getTimeInMillis(), currentUserId);
        float[] wk = week.isEmpty() ? new float[]{latest.sys, latest.dia, latest.hr} : avgOf(week);
        int afibCnt = 0, irrCnt = 0;
        for (Record r : week) { if (r.afib) afibCnt++; if (r.irregular) irrCnt++; }

        // 1. 3 级高血压：紧急
        if (latest.sys >= 180 || latest.dia >= 110) {
            items.add(new AdviceItem(3, "血压达到 3 级高血压范围（≥180/110 mmHg，《中国高血压防治指南(2024年修订版)》）。请立即安静休息 5 分钟后复测；若复测仍达此水平，或伴剧烈头痛、胸闷、视物模糊，请尽快就医。"));
        }
        // 2. 疑似房颤：紧急提示
        if (afibCnt > 0) {
            items.add(new AdviceItem(3, "近 7 天有 " + afibCnt + " 次疑似房颤记录。脉搏不规律（脉律绝对不齐、强弱不等）是房颤最重要的线索，但触诊/血压计提示不能确诊——房颤的确诊依据是心电图，阵发性房颤需 24 小时动态心电图捕获。房颤显著增加脑卒中风险，建议尽快至心内科就诊，由医生评估抗凝治疗必要性（卒中风险通常按 CHA₂DS₂-VASc 评分判断）。"));
        } else if (irrCnt > 0) {
            items.add(new AdviceItem(2, "近 7 天有 " + irrCnt + " 次脉搏不规律记录。研究显示脉搏触诊发现不规律对房颤有较高提示意义（敏感度约 79%~97%），但单次结果不能确诊，且 irregular pulse 时自动血压计读数可能不准（NICE 建议）。建议增加测量频次、复测时留意节律，必要时做普通心电图确认。"));
        }
        // 3. 血压分级（近7天平均，按家庭自测标准 ≥135/85）
        if (wk[0] >= 135 || wk[1] >= 85) {
            items.add(new AdviceItem(2, String.format(java.util.Locale.CHINA,
                    "近 7 天平均血压 %.0f/%.0f mmHg。指南推荐：家庭自测连续 5~7 天平均 ≥135/85 mmHg 即提示高血压可能（对应诊室 ≥140/90）。建议尽早就医评估，并遵医嘱处理。", wk[0], wk[1])));
        } else if (wk[0] >= 130 || wk[1] >= 80) {
            items.add(new AdviceItem(1, String.format(java.util.Locale.CHINA,
                    "近 7 天平均血压 %.0f/%.0f mmHg，处于正常高值（120~139/80~89 mmHg）。此阶段重点是生活方式干预：每日食盐 <5g、每周 ≥150 分钟中等强度运动、控制体重、戒烟限酒、保证 7~9 小时睡眠。", wk[0], wk[1])));
        } else {
            items.add(new AdviceItem(0, String.format(java.util.Locale.CHINA,
                    "近 7 天平均血压 %.0f/%.0f mmHg，在理想范围内（<120/80）。继续保持规律测量即可。", wk[0], wk[1])));
        }
        // 4. 心率异常
        if (latest.hr > 100) {
            items.add(new AdviceItem(1, "最新静息心率 " + latest.hr + " 次/分（正常成人安静状态 60~100 次/分）。可能与测量前活动、情绪、咖啡因有关，建议静息 5 分钟后复测；持续偏快请就医排查。"));
        } else if (latest.hr < 60) {
            items.add(new AdviceItem(1, "最新静息心率 " + latest.hr + " 次/分，偏慢（正常 60~100 次/分）。长期运动者可偏慢属正常；若伴头晕、乏力、黑矇等症状请就医。"));
        }
        // 5. 高血压是房颤的危险因素提示（有房颤/不规律记录时联动提醒）
        if ((afibCnt > 0 || irrCnt > 0) && wk[0] >= 140) {
            items.add(new AdviceItem(2, "高血压是房颤最重要的危险因素之一，血压控制不佳时房颤风险显著增加。请在控压的同时，更积极地完成心电图检查。"));
        }

        // 6. 结合个人资料（年龄/性别/身高/体重）的针对性提示
        User user = db.user(currentUserId);
        if (user != null) {
            if (user.age <= 0 || user.height <= 0 || user.weight <= 0 || user.sex < 0) {
                items.add(new AdviceItem(1, "个人资料未填写完整：点顶部用户徽章 →「编辑当前用户资料」，填入年龄、性别、身高、体重后，可获得 BMI 评估、65 岁以上房颤筛查提醒和卒中风险评分等更精准的建议。"));
            } else {
                double bmi = user.bmi();
                if (bmi >= 28) {
                    double lo = 18.5 * Math.pow(user.height / 100.0, 2);
                    double hi = 24.0 * Math.pow(user.height / 100.0, 2);
                    items.add(new AdviceItem(2, String.format(Locale.CHINA,
                            "BMI %.1f，属肥胖（中国标准 ≥28）。超重和肥胖均显著增加高血压与房颤风险，减重本身即可降压。建议通过饮食控制＋每周 ≥150 分钟运动，将体重逐步降至 %.0f~%.0f kg（对应 BMI 18.5~24）；腰围宜控制在男性 <90cm、女性 <85cm。",
                            bmi, lo, hi)));
                } else if (bmi >= 24) {
                    double hi = 24.0 * Math.pow(user.height / 100.0, 2);
                    items.add(new AdviceItem(1, String.format(Locale.CHINA,
                            "BMI %.1f，属超重（24~28），是高血压的危险因素之一。建议控制饮食总量、减少精制碳水与油腻食物，保持运动习惯，将体重逐步降至 %.0f kg 以内。",
                            bmi, hi)));
                }
                if (user.age >= 65) {
                    items.add(new AdviceItem(1, "年龄 ≥65 岁：2020 ESC 房颤管理指南推荐对 ≥65 岁人群进行机会性房颤筛查（脉搏触诊或心电图）。建议每年至少做一次常规心电图，测量血压时留意脉搏是否规律。"));
                }
                if (afibCnt > 0) {
                    int pts = 0;
                    StringBuilder why = new StringBuilder();
                    if (user.age >= 75) { pts += 2; why.append("年龄≥75(+2) "); }
                    else if (user.age >= 65) { pts += 1; why.append("年龄65~74(+1) "); }
                    if (user.sex == 0) { pts += 1; why.append("女性(+1) "); }
                    if (wk[0] >= 135 || wk[1] >= 85) { pts += 1; why.append("高血压(+1) "); }
                    if (why.length() == 0) why.append("暂无加分项 ");
                    items.add(new AdviceItem(pts >= 2 ? 3 : 2, String.format(Locale.CHINA,
                            "卒中风险初评：按 CHA₂DS₂-VA 评分，基于你已填写的资料（%s）合计 %d 分。%s注意：糖尿病、心衰、卒中/血栓史等病史项未计入，实际评分请由医生完成。CHA₂DS₂-VA ≥2 分通常提示需要长期抗凝治疗评估，建议尽快心内科就诊。",
                            why.toString().trim(), pts,
                            pts >= 2 ? "已达需评估抗凝治疗的分值门槛。" : "目前分值较低，但仍建议尽早明确房颤诊断。")));
                }
            }
        }

        // 按严重程度排序，最多显示 5 条
        java.util.Collections.sort(items, (a, b) -> b.level - a.level);
        for (int i = 0; i < Math.min(items.size(), 5); i++) addItem(items.get(i));
    }

    private void addItem(AdviceItem item) {
        int color;
        switch (item.level) {
            case 3: color = 0xFFE5484D; break;
            case 2: color = 0xFFF5A524; break;
            case 1: color = 0xFF3E7BFA; break;
            default: color = 0xFF30A46C;
        }
        TextView t = new TextView(this);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("●  ");
        sb.setSpan(new android.text.style.ForegroundColorSpan(color), 0, 1, 0);
        sb.append(item.text);
        t.setText(sb);
        t.setTextSize(13);
        t.setTextColor(0xFF3C4350);
        t.setLineSpacing(dp(3), 1f);
        int p = dp(10);
        t.setPadding(p, p, p, p);
        t.setBackgroundResource(R.drawable.bg_input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        t.setLayoutParams(lp);
        adviceList.addView(t);
    }

    // ---------- 用户切换 / 新增 ----------

    private void renderUserChip() {
        User u = db.user(currentUserId);
        if (u == null) return;
        userChip.setText(u.name);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(u.color);
        bg.setCornerRadius(dp(18));
        userChip.setBackground(bg);
        userChip.setTextColor(0xFFFFFFFF);
    }

    private void showUserDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_users);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        dialog.getWindow().setAttributes(lp);

        LinearLayout rows = dialog.findViewById(R.id.userRows);
        TextView tvAdd = dialog.findViewById(R.id.tvAddUser);

        List<User> users = db.users();
        for (int i = 0; i < users.size(); i++) {
            final User u = users.get(i);
            View row = buildUserRow(u, users.size());
            row.setOnClickListener(v -> {
                currentUserId = u.id;
                saveCurrentUser();
                dialog.dismiss();
                refresh();
            });
            rows.addView(row);
            if (i < users.size() - 1) rows.addView(divider());
        }
        tvAdd.setOnClickListener(v -> {
            dialog.dismiss();
            showAddUserDialog();
        });
        dialog.findViewById(R.id.tvEditProfile).setOnClickListener(v -> {
            dialog.dismiss();
            showProfileDialog();
        });
        dialog.show();
    }

    private View buildUserRow(User u, int total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(12);
        row.setPadding(0, p, 0, p);

        // 头像：用户色圆形 + 首字
        TextView avatar = new TextView(this);
        avatar.setText(u.name.substring(0, 1));
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setTextSize(16);
        avatar.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable av = new android.graphics.drawable.GradientDrawable();
        av.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        av.setColor(u.color);
        avatar.setBackground(av);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        avatar.setLayoutParams(avLp);
        row.addView(avatar);

        // 名称 + 记录数
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLp.setMargins(dp(12), 0, 0, 0);
        col.setLayoutParams(colLp);

        TextView name = new TextView(this);
        name.setText(u.name);
        name.setTextColor(0xFF1F2430);
        name.setTextSize(16);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(name);

        TextView cnt = new TextView(this);
        int n = db.all(u.id).size();
        cnt.setText(n + " 条记录");
        cnt.setTextColor(0xFF8A93A2);
        cnt.setTextSize(12);
        cnt.setPadding(0, dp(3), 0, 0);
        col.addView(cnt);
        row.addView(col);

        // 当前用户 ✓
        if (u.id == currentUserId) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(0xFF30A46C);
            check.setTextSize(18);
            check.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(check);
        }
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFF0F2F5);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private void showAddUserDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_add_user);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        dialog.getWindow().setAttributes(lp);

        android.widget.EditText input = dialog.findViewById(R.id.etUserName);
        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnCreate).setOnClickListener(v -> create(input, dialog));
        input.setOnEditorActionListener((v, actionId, event) -> {
            create(input, dialog);
            return true;
        });
        dialog.show();
    }

    private void create(android.widget.EditText input, android.app.Dialog dialog) {
        String name = input.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.length() > 12) name = name.substring(0, 12);
        long id = db.insertUser(name, db.nextUserColor());
        if (id > 0) {
            currentUserId = id;
            saveCurrentUser();
            Toast.makeText(this, "已创建并切换到「" + name + "」", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refresh();
        }
    }

    private void saveCurrentUser() {
        getSharedPreferences("bp_prefs", MODE_PRIVATE).edit()
                .putLong("uid", currentUserId).apply();
    }

    private void renderLatest() {
        Record r = db.latest(currentUserId);
        badgeRow.removeAllViews();
        if (r == null) {
            tvLatestSys.setText("--");
            tvLatestDia.setText("--");
            tvLatestHr.setText("--");
            tvLatestTime.setText("还没有记录，点右上角 + 添加第一次测量");
            return;
        }
        tvLatestSys.setText(String.valueOf(r.sys));
        tvLatestDia.setText(String.valueOf(r.dia));
        tvLatestHr.setText(String.valueOf(r.hr));
        String level = levelOf(r.sys, r.dia);
        int levelColor;
        switch (level) {
            case "偏高": levelColor = 0xFFE5484D; break;
            case "临界": levelColor = 0xFFF5A524; break;
            case "偏低": levelColor = 0xFF3E7BFA; break;
            default: levelColor = 0xFF30A46C;
        }
        tvLatestTime.setText(fmtDateTime.format(new Date(r.ts)) + " · 血压" + level);
        tvLatestTime.setTextColor(levelColor);

        if (r.irregular)
            badgeRow.addView(badge("脉搏不规律", R.drawable.bg_badge_irregular, 0xFFB47B00));
        if (r.afib)
            badgeRow.addView(badge("疑似房颤 · 建议就医复查", R.drawable.bg_badge_afib, 0xFF7C4DBC));
        if (!r.irregular && !r.afib)
            badgeRow.addView(badge("节律正常", R.drawable.bg_chip, 0xFF30A46C));
        if (r.note != null && !r.note.trim().isEmpty()) {
            TextView note = new TextView(this);
            note.setText("备注：" + r.note);
            note.setTextSize(12);
            note.setTextColor(0xFF8A93A2);
            badgeRow.addView(note);
        }
    }

    private void renderChart() {
        Calendar cal = Calendar.getInstance();
        List<ChartView.Series> out = new ArrayList<>();
        ChartView.Series sysS = new ChartView.Series(0xFFE5484D);
        ChartView.Series diaS = new ChartView.Series(0xFF3E7BFA);
        ChartView.Series hrS  = new ChartView.Series(0xFF30A46C);
        out.add(sysS); out.add(diaS); out.add(hrS);

        int xCount;
        String[] labels;
        String periodLabel;
        List<Record> inRange;

        switch (period) {
            case PERIOD_WEEK: {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DAY_OF_YEAR, -6);
                inRange = db.since(cal.getTimeInMillis(), currentUserId);
                // 按天分桶
                Calendar cur = (Calendar) cal.clone();
                xCount = 7;
                labels = new String[xCount];
                List<List<Record>> buckets = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    long a = cur.getTimeInMillis();
                    cur.add(Calendar.DAY_OF_YEAR, 1);
                    long b = cur.getTimeInMillis();
                    labels[i] = fmtShort.format(new Date(a));
                    buckets.add(filter(inRange, a, b));
                }
                fillSeries(sysS, diaS, hrS, buckets);
                periodLabel = "近 7 天";
                break;
            }
            case PERIOD_MONTH: {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DAY_OF_YEAR, -29);
                inRange = db.since(cal.getTimeInMillis(), currentUserId);
                Calendar cur = (Calendar) cal.clone();
                xCount = 30;
                labels = new String[xCount];
                List<List<Record>> buckets = new ArrayList<>();
                for (int i = 0; i < 30; i++) {
                    long a = cur.getTimeInMillis();
                    cur.add(Calendar.DAY_OF_YEAR, 1);
                    long b = cur.getTimeInMillis();
                    labels[i] = (i % 5 == 0 || i == 29) ? fmtShort.format(new Date(a)) : null;
                    buckets.add(filter(inRange, a, b));
                }
                fillSeries(sysS, diaS, hrS, buckets);
                periodLabel = "近 30 天";
                break;
            }
            case PERIOD_YEAR: {
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.MONTH, -11);
                inRange = db.since(cal.getTimeInMillis(), currentUserId);
                Calendar cur = (Calendar) cal.clone();
                xCount = 12;
                labels = new String[xCount];
                List<List<Record>> buckets = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    long a = cur.getTimeInMillis();
                    cur.add(Calendar.MONTH, 1);
                    long b = cur.getTimeInMillis();
                    labels[i] = (cur.get(Calendar.MONTH) + 1) + "月";
                    buckets.add(filter(inRange, a, b));
                }
                fillSeries(sysS, diaS, hrS, buckets);
                periodLabel = "近 12 个月";
                break;
            }
            default: { // 日：x 轴为 0~24 小时，按真实测量时刻定位
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                inRange = db.since(cal.getTimeInMillis(), currentUserId);
                if (!inRange.isEmpty()) {
                    long start = cal.getTimeInMillis();
                    float dayMs = 24f * 3600 * 1000;
                    for (Record r : inRange) {
                        float x = (r.ts - start) / dayMs * 6f; // 0..6（7 个刻度）
                        sysS.pts.add(new float[]{x, r.sys});
                        diaS.pts.add(new float[]{x, r.dia});
                        hrS.pts.add(new float[]{x, r.hr});
                    }
                }
                xCount = 7;
                labels = new String[]{"00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00"};
                periodLabel = "今天";
                break;
            }
        }

        chart.setData(xCount, labels, out);
        renderStats(inRange, periodLabel);
        tvChartSummary.setText(periodLabel + " · " + countText(inRange));
    }

    private void fillSeries(ChartView.Series sysS, ChartView.Series diaS,
                            ChartView.Series hrS, List<List<Record>> buckets) {
        for (int i = 0; i < buckets.size(); i++) {
            List<Record> b = buckets.get(i);
            if (b.isEmpty()) continue;
            float[] avg = avgOf(b);
            sysS.pts.add(new float[]{i, avg[0]});
            diaS.pts.add(new float[]{i, avg[1]});
            hrS.pts.add(new float[]{i, avg[2]});
        }
    }

    private List<Record> filter(List<Record> src, long from, long to) {
        List<Record> out = new ArrayList<>();
        for (Record r : src) if (r.ts >= from && r.ts < to) out.add(r);
        return out;
    }

    private float[] avgOf(List<Record> l) {
        float s = 0, d = 0, h = 0;
        for (Record r : l) { s += r.sys; d += r.dia; h += r.hr; }
        int n = l.size();
        return new float[]{s / n, d / n, h / n};
    }

    private String countText(List<Record> l) {
        return l.isEmpty() ? "暂无测量数据" : "共 " + l.size() + " 次测量";
    }

    /** 图表下方统计卡片：平均血压 / 平均心率 / 测量次数 / 脉搏不规律 / 疑似房颤 */
    private void renderStats(List<Record> l, String periodLabel) {
        statsRow.removeAllViews();
        if (l.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(periodLabel + "暂无测量数据，切换周期或添加记录后这里会显示平均值与异常统计");
            t.setTextSize(12);
            t.setTextColor(0xFF8A93A2);
            statsRow.addView(t);
            return;
        }
        float[] a = avgOf(l);
        int irr = 0, af = 0;
        for (Record r : l) { if (r.irregular) irr++; if (r.afib) af++; }
        statsRow.addView(statCell("平均血压", String.format(Locale.CHINA, "%.0f/%.0f", a[0], a[1]),
                "mmHg", 0xFF1F2430));
        statsRow.addView(statCell("平均心率", String.format(Locale.CHINA, "%.0f", a[2]),
                "次/分", 0xFF30A46C));
        statsRow.addView(statCell("测量次数", String.valueOf(l.size()), "次", 0xFF3E7BFA));
        statsRow.addView(statCell("脉搏不规律", String.valueOf(irr), "次",
                irr > 0 ? 0xFFB47B00 : 0xFF8A93A2));
        statsRow.addView(statCell("疑似房颤", String.valueOf(af), "次",
                af > 0 ? 0xFF7C4DBC : 0xFF8A93A2));
    }

    private View statCell(String label, String value, String unit, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackgroundResource(R.drawable.bg_input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        c.setLayoutParams(lp);
        int p = dp(2);
        c.setPadding(p, dp(7), p, dp(7));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(10);
        tvLabel.setTextColor(0xFF8A93A2);
        tvLabel.setGravity(Gravity.CENTER);
        c.addView(tvLabel);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextSize(label.equals("平均血压") ? 15 : 17);
        tvVal.setTextColor(color);
        tvVal.setTypeface(null, android.graphics.Typeface.BOLD);
        tvVal.setIncludeFontPadding(false);
        tvVal.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valLp.setMargins(0, dp(4), 0, 0);
        tvVal.setLayoutParams(valLp);
        c.addView(tvVal);

        TextView tvUnit = new TextView(this);
        tvUnit.setText(unit);
        tvUnit.setTextSize(9);
        tvUnit.setTextColor(0xFF8A93A2);
        tvUnit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        unitLp.setMargins(0, dp(3), 0, 0);
        tvUnit.setLayoutParams(unitLp);
        c.addView(tvUnit);
        return c;
    }

    // ---------- 记录列表 ----------

    private void renderList() {
        recordList.removeAllViews();
        List<Record> all = db.all(currentUserId);
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无记录");
            empty.setTextColor(0xFF8A93A2);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            recordList.addView(empty);
            return;
        }
        LayoutInflater inf = LayoutInflater.from(this);
        for (final Record r : all) {
            View v = inf.inflate(R.layout.item_record, recordList, false);
            TextView tvVal = v.findViewById(R.id.tvVal);
            TextView tvHr = v.findViewById(R.id.tvHr);
            TextView tvTime = v.findViewById(R.id.tvTime);
            LinearLayout badges = v.findViewById(R.id.badges);

            tvVal.setText(r.sys + "/" + r.dia + " mmHg");
            tvVal.setTextColor(levelOf(r.sys, r.dia).equals("偏高") ? 0xFFE5484D : 0xFF1F2430);
            tvHr.setText(r.hr + " 次/分");
            tvTime.setText(fmtFull.format(new Date(r.ts))
                    + (r.note != null && !r.note.trim().isEmpty() ? " · " + r.note : ""));

            if (r.irregular) badges.addView(badge("脉搏不规律", R.drawable.bg_badge_irregular, 0xFFB47B00));
            if (r.afib) badges.addView(badge("疑似房颤", R.drawable.bg_badge_afib, 0xFF7C4DBC));

            v.setOnLongClickListener(vv -> {
                android.app.Dialog dialog = new android.app.Dialog(this);
                dialog.setContentView(R.layout.dialog_confirm);
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
                dialog.getWindow().setAttributes(lp);

                TextView msg = dialog.findViewById(R.id.tvConfirmMsg);
                msg.setText(fmtFull.format(new Date(r.ts)) + "\n" + r.sys + "/" + r.dia
                        + " mmHg · " + r.hr + " 次/分\n删除后无法恢复");
                dialog.findViewById(R.id.btnCancel).setOnClickListener(vd -> dialog.dismiss());
                dialog.findViewById(R.id.btnDelete).setOnClickListener(vd -> {
                    dialog.dismiss();
                    db.delete(r.id);
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    refresh();
                });
                dialog.show();
                return true;
            });
            recordList.addView(v);
        }
    }

    // ---------- 个人资料 ----------

    private void showProfileDialog() {
        User u = db.user(currentUserId);
        if (u == null) return;

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_profile);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
        dialog.getWindow().setAttributes(lp);

        android.widget.EditText etName = dialog.findViewById(R.id.etPName);
        android.widget.EditText etAge = dialog.findViewById(R.id.etPAge);
        android.widget.EditText etH = dialog.findViewById(R.id.etPHeight);
        android.widget.EditText etW = dialog.findViewById(R.id.etPWeight);
        TextView chipF = dialog.findViewById(R.id.chipFemale);
        TextView chipM = dialog.findViewById(R.id.chipMale);

        etName.setText(u.name);
        if (u.age > 0) etAge.setText(String.valueOf((int) u.age));
        if (u.height > 0) etH.setText(String.valueOf(u.height));
        if (u.weight > 0) etW.setText(String.valueOf(u.weight));

        final int[] selSex = {u.sex};
        Runnable restyle = () -> {
            chipF.setBackgroundResource(selSex[0] == 0 ? R.drawable.bg_chip_selected : R.drawable.bg_input);
            chipF.setTextColor(selSex[0] == 0 ? 0xFFFFFFFF : 0xFF5A6270);
            chipM.setBackgroundResource(selSex[0] == 1 ? R.drawable.bg_chip_selected : R.drawable.bg_input);
            chipM.setTextColor(selSex[0] == 1 ? 0xFFFFFFFF : 0xFF5A6270);
        };
        restyle.run();
        chipF.setOnClickListener(v -> { selSex[0] = 0; restyle.run(); });
        chipM.setOnClickListener(v -> { selSex[0] = 1; restyle.run(); });

        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "姓名不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            double age = parseNum(etAge), h = parseNum(etH), w = parseNum(etW);
            if (age > 0 && (age < 1 || age > 120)) { Toast.makeText(this, "年龄应在 1~120 之间", Toast.LENGTH_SHORT).show(); return; }
            if (h > 0 && (h < 40 || h > 250))      { Toast.makeText(this, "身高应在 40~250 cm 之间", Toast.LENGTH_SHORT).show(); return; }
            if (w > 0 && (w < 20 || w > 300))      { Toast.makeText(this, "体重应在 20~300 kg 之间", Toast.LENGTH_SHORT).show(); return; }

            db.updateUser(currentUserId, name, selSex[0], age, h, w);
            Toast.makeText(this, "资料已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refresh();
        });
        dialog.show();
    }

    private double parseNum(android.widget.EditText et) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    // ---------- AI 咨询文本 ----------

    private void showAiTextDialog() {
        List<Record> all = db.all(currentUserId);
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有记录，先添加测量数据", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = buildAiText(all);

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_ai_text);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
        dialog.getWindow().setAttributes(lp);

        TextView tv = dialog.findViewById(R.id.tvAiText);
        tv.setText(text);
        dialog.findViewById(R.id.btnCopy).setOnClickListener(v -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("bp", text));
            Toast.makeText(this, "已复制全文，可粘贴给 AI", Toast.LENGTH_SHORT).show();
        });
        dialog.findViewById(R.id.btnShare).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(android.content.Intent.EXTRA_TEXT, text);
            startActivity(android.content.Intent.createChooser(i, "分享血压记录"));
        });
        dialog.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String buildAiText(List<Record> allDesc) {
        User u = db.user(currentUserId);
        SimpleDateFormat fmtDay = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SimpleDateFormat fmtLine = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);

        Record latest = allDesc.get(0);
        Record oldest = allDesc.get(allDesc.size() - 1);

        // 统计
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        List<Record> week = db.since(cal.getTimeInMillis(), currentUserId);
        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30);
        List<Record> month = db.since(cal.getTimeInMillis(), currentUserId);

        StringBuilder sb = new StringBuilder();
        sb.append("【血压记录数据】\n");
        sb.append("用户：").append(u == null ? "我" : u.name).append("\n");
        sb.append("测量方式：家用电子血压计自测\n");
        if (u != null && (u.age > 0 || u.height > 0 || u.weight > 0 || u.sex >= 0)) {
            StringBuilder base = new StringBuilder("基础信息：");
            base.append("年龄 ").append(u.age > 0 ? (int) u.age + " 岁" : "未填写");
            base.append("，性别 ").append(u.sex == 1 ? "男" : u.sex == 0 ? "女" : "未填写");
            base.append("，身高 ").append(u.height > 0 ? (int) u.height + " cm" : "未填写");
            base.append("，体重 ").append(u.weight > 0 ? (int) u.weight + " kg" : "未填写");
            if (u.bmi() > 0) base.append(String.format(Locale.CHINA, "，BMI %.1f", u.bmi()));
            sb.append(base).append("\n");
        }
        sb.append("记录时段：").append(fmtDay.format(new Date(oldest.ts)))
          .append(" 至 ").append(fmtDay.format(new Date(latest.ts)))
          .append("，共 ").append(allDesc.size()).append(" 次\n");

        if (!week.isEmpty()) {
            float[] a = avgOf(week);
            sb.append(String.format(Locale.CHINA, "近7天：%d 次，平均 %.0f/%.0f mmHg，心率 %.0f 次/分\n",
                    week.size(), a[0], a[1], a[2]));
        }
        if (!month.isEmpty()) {
            float[] a = avgOf(month);
            sb.append(String.format(Locale.CHINA, "近30天：%d 次，平均 %.0f/%.0f mmHg，心率 %.0f 次/分\n",
                    month.size(), a[0], a[1], a[2]));
        }
        // 极值与异常标记
        int maxSys = 0, maxDia = 0, afibCnt = 0, irrCnt = 0;
        for (Record r : allDesc) {
            maxSys = Math.max(maxSys, r.sys);
            maxDia = Math.max(maxDia, r.dia);
            if (r.afib) afibCnt++;
            if (r.irregular) irrCnt++;
        }
        float[] aAll = avgOf(allDesc);
        sb.append(String.format(Locale.CHINA, "全部：平均 %.0f/%.0f mmHg，最高 %.0f/%.0f mmHg\n",
                aAll[0], aAll[1], (float) maxSys, (float) maxDia));
        sb.append("异常标记：疑似房颤 ").append(afibCnt).append(" 次，脉搏不规律 ").append(irrCnt).append(" 次\n");

        // 明细（最近 40 条，新→旧）
        sb.append("\n【测量明细】（时间 | 高压/低压 mmHg | 心率 次/分 | 提示 | 备注）\n");
        int n = Math.min(allDesc.size(), 40);
        for (int i = 0; i < n; i++) {
            Record r = allDesc.get(i);
            sb.append(fmtLine.format(new Date(r.ts))).append(" | ")
              .append(r.sys).append("/").append(r.dia).append(" | ")
              .append(r.hr).append(" | ")
              .append(r.afib ? "疑似房颤" : r.irregular ? "脉搏不规律" : "节律正常");
            if (r.note != null && !r.note.trim().isEmpty()) sb.append(" | ").append(r.note);
            sb.append("\n");
        }
        if (allDesc.size() > n)
            sb.append("（仅列最近 ").append(n).append(" 条，更早记录从略）\n");

        // 预置提示词
        sb.append("\n【我的问题】\n");
        sb.append("请作为心血管内科医生，基于以上血压自测记录帮我分析：\n");
        sb.append("1. 血压水平评估与分级（请注明依据的标准，如《中国高血压防治指南(2024年修订版)》的家庭自测标准）；\n");
        sb.append("2. 心率与节律情况：若记录中有脉搏不规律或疑似房颤提示，请说明风险与下一步应做哪些检查（如心电图、24小时动态心电图）；\n");
        sb.append("3. 血压与房颤的关联：我的情况是否需要更加警惕？\n");
        sb.append("4. 具体的生活方式干预建议（饮食、运动、睡眠、情绪等）；\n");
        sb.append("5. 出现哪些警示信号时需要立即就医？\n");
        sb.append("6. 结合我的年龄、性别、身高体重（BMI）等基础信息，给出有针对性的体重管理与生活方式目标。\n");
        sb.append("请用通俗易懂的语言回答，并在结论处标注置信度。以上数据为家用电子血压计自测结果，仅供参考。\n");
        return sb.toString();
    }

    // ---------- 公共 ----------

    private String levelOf(int sys, int dia) {
        if (sys >= 140 || dia >= 90) return "偏高";
        if (sys >= 130 || dia >= 85) return "临界";
        if (sys < 90 || dia < 60) return "偏低";
        return "正常";
    }

    private TextView badge(String text, int bgRes, int fgColor) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(fgColor);
        t.setBackgroundResource(bgRes);
        int p = dp(8);
        t.setPadding(p, dp(3), p, dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
