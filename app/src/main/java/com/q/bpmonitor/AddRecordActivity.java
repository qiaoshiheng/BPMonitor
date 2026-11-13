package com.q.bpmonitor;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddRecordActivity extends Activity {

    private DbHelper db;
    private Calendar selected = Calendar.getInstance();

    private EditText etSys, etDia, etHr, etNote;
    private Switch swIrregular, swAfib;
    private Button btnDate, btnTime;

    private final SimpleDateFormat fmtDate = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);
    private final SimpleDateFormat fmtTime = new SimpleDateFormat("HH:mm", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        setContentView(R.layout.activity_add);

        etSys = findViewById(R.id.etSys);
        etDia = findViewById(R.id.etDia);
        etHr = findViewById(R.id.etHr);
        etNote = findViewById(R.id.etNote);
        swIrregular = findViewById(R.id.swIrregular);
        swAfib = findViewById(R.id.swAfib);
        btnDate = findViewById(R.id.btnPickDate);
        btnTime = findViewById(R.id.btnPickTime);

        android.view.View btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        updateDateTimeButtons();

        btnDate.setOnClickListener(v -> {
            DatePickerDialog dlg = new DatePickerDialog(this, R.style.PickerTheme,
                    (dp, y, m, d) -> {
                        selected.set(Calendar.YEAR, y);
                        selected.set(Calendar.MONTH, m);
                        selected.set(Calendar.DAY_OF_MONTH, d);
                        updateDateTimeButtons();
                    },
                    selected.get(Calendar.YEAR),
                    selected.get(Calendar.MONTH),
                    selected.get(Calendar.DAY_OF_MONTH));
            dlg.setTitle("选择测量日期");
            dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
            dlg.show();
        });

        btnTime.setOnClickListener(v -> new TimePickerDialog(this, R.style.PickerTheme,
                (tp, h, min) -> {
                    selected.set(Calendar.HOUR_OF_DAY, h);
                    selected.set(Calendar.MINUTE, min);
                    updateDateTimeButtons();
                },
                selected.get(Calendar.HOUR_OF_DAY),
                selected.get(Calendar.MINUTE),
                true).show());

        // 输入框获得焦点时隐藏内置提示文字，失焦且未填写时恢复
        bindHintHide(etSys);
        bindHintHide(etDia);
        bindHintHide(etHr);

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> save());
    }

    private void updateDateTimeButtons() {
        btnDate.setText(fmtDate.format(new Date(selected.getTimeInMillis())));
        btnTime.setText(fmtTime.format(new Date(selected.getTimeInMillis())));
    }

    private void bindHintHide(android.widget.EditText et) {
        final CharSequence hint = et.getHint();
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                et.setHint("");
            } else if (et.getText().toString().trim().isEmpty()) {
                et.setHint(hint);
            }
        });
    }

    private void save() {
        String s = etSys.getText().toString().trim();
        String d = etDia.getText().toString().trim();
        String h = etHr.getText().toString().trim();

        if (s.isEmpty() || d.isEmpty() || h.isEmpty()) {
            toast("请填写高压、低压和心率");
            return;
        }
        int sys = Integer.parseInt(s), dia = Integer.parseInt(d), hr = Integer.parseInt(h);

        if (sys < 60 || sys > 260) { toast("高压应在 60~260 之间"); return; }
        if (dia < 30 || dia > 160) { toast("低压应在 30~160 之间"); return; }
        if (hr < 30 || hr > 220)   { toast("心率应在 30~220 之间"); return; }
        if (sys <= dia)            { toast("高压应大于低压"); return; }
        if (selected.getTimeInMillis() > System.currentTimeMillis() + 60_000) {
            toast("测量时间不能是未来");
            return;
        }

        Record r = new Record();
        r.userId = getSharedPreferences("bp_prefs", MODE_PRIVATE)
                .getLong("uid", DbHelper.DEFAULT_USER_ID);
        r.ts = selected.getTimeInMillis();
        r.sys = sys;
        r.dia = dia;
        r.hr = hr;
        r.irregular = swIrregular.isChecked();
        r.afib = swAfib.isChecked();
        r.note = etNote.getText().toString().trim();

        db.insert(r);
        if (r.irregular || r.afib) {
            Toast.makeText(this,
                    r.afib ? "已保存。记录提示疑似房颤，建议尽早就医复查心电图"
                           : "已保存。脉搏不规律，建议复测并关注变化",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
