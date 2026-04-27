package com.greenbarbot.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.greenbarbot.R;
import com.greenbarbot.service.FloatingWindowService;
import com.greenbarbot.service.ScreenCaptureService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GreenBarBot";
    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_MEDIA_PROJECTION = 1002;

    private Switch swMain;
    private Button btnCalibrate, btnSaveConfig, btnScene1, btnScene2, btnScene3;
    private TextView tvStatus;
    private SeekBar sbHueMin, sbHueMax, sbSatMin, sbSatMax, sbValMin, sbValMax;
    private TextView tvHueMin, tvHueMax, tvSatMin, tvSatMax, tvValMin, tvValMax;
    private SeekBar sbInterval, sbThreshold;
    private TextView tvInterval, tvThreshold;
    private SharedPreferences prefs;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ── 全局崩溃捕获，闪退前弹窗显示错误 ─────────────────────────────
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String msg = throwable.toString() + "\n" + Log.getStackTraceString(throwable);
            Log.e(TAG, "CRASH: " + msg);
            // 写入SharedPreferences，下次启动时展示
            getSharedPreferences("GreenBarBot", MODE_PRIVATE)
                .edit().putString("last_crash", msg).apply();
        });

        super.onCreate(savedInstanceState);

        // ── 展示上次崩溃信息 ───────────────────────────────────────────────
        SharedPreferences p = getSharedPreferences("GreenBarBot", MODE_PRIVATE);
        String lastCrash = p.getString("last_crash", null);
        if (lastCrash != null) {
            p.edit().remove("last_crash").apply();
            new AlertDialog.Builder(this)
                .setTitle("上次崩溃原因（请截图发给开发者）")
                .setMessage(lastCrash)
                .setPositiveButton("确定", null)
                .show();
        }

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("GreenBarBot", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        try {
            initViews();
            loadConfig();
        } catch (Exception e) {
            showError("initViews 失败", e);
        }
    }

    private void showError(String where, Exception e) {
        String msg = where + ": " + e.getClass().getSimpleName() + "\n" + e.getMessage();
        Log.e(TAG, msg);
        if (tvStatus != null) tvStatus.setText("错误: " + msg);
        try {
            new AlertDialog.Builder(this)
                .setTitle("错误（截图发给开发者）")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show();
        } catch (Exception ignored) {}
    }

    private void initViews() {
        swMain        = findViewById(R.id.sw_main);
        btnCalibrate  = findViewById(R.id.btn_calibrate);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        btnScene1     = findViewById(R.id.btn_scene1);
        btnScene2     = findViewById(R.id.btn_scene2);
        btnScene3     = findViewById(R.id.btn_scene3);
        tvStatus      = findViewById(R.id.tv_status);
        sbHueMin      = findViewById(R.id.sb_hue_min);
        sbHueMax      = findViewById(R.id.sb_hue_max);
        sbSatMin      = findViewById(R.id.sb_sat_min);
        sbSatMax      = findViewById(R.id.sb_sat_max);
        sbValMin      = findViewById(R.id.sb_val_min);
        sbValMax      = findViewById(R.id.sb_val_max);
        tvHueMin      = findViewById(R.id.tv_hue_min);
        tvHueMax      = findViewById(R.id.tv_hue_max);
        tvSatMin      = findViewById(R.id.tv_sat_min);
        tvSatMax      = findViewById(R.id.tv_sat_max);
        tvValMin      = findViewById(R.id.tv_val_min);
        tvValMax      = findViewById(R.id.tv_val_max);
        sbInterval    = findViewById(R.id.sb_interval);
        sbThreshold   = findViewById(R.id.sb_threshold);
        tvInterval    = findViewById(R.id.tv_interval);
        tvThreshold   = findViewById(R.id.tv_threshold);

        sbHueMin.setMax(180);  sbHueMax.setMax(180);
        sbSatMin.setMax(255);  sbSatMax.setMax(255);
        sbValMin.setMax(255);  sbValMax.setMax(255);
        sbInterval.setMax(200); sbThreshold.setMax(100);

        setSeekBarListener(sbHueMin,  tvHueMin,  "绿色Hue最小值: ");
        setSeekBarListener(sbHueMax,  tvHueMax,  "绿色Hue最大值: ");
        setSeekBarListener(sbSatMin,  tvSatMin,  "饱和度最小值: ");
        setSeekBarListener(sbSatMax,  tvSatMax,  "饱和度最大值: ");
        setSeekBarListener(sbValMin,  tvValMin,  "亮度最小值: ");
        setSeekBarListener(sbValMax,  tvValMax,  "亮度最大值: ");
        setSeekBarListener(sbInterval,  tvInterval,  "识别间隔(ms): ");
        setSeekBarListener(sbThreshold, tvThreshold, "判定阈值(px): ");

        swMain.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) startBot(); else stopBot();
        });
        btnCalibrate.setOnClickListener(v -> {
            try { startActivity(new Intent(this, CalibrationActivity.class)); }
            catch (Exception e) { showError("打开校准页失败", e); }
        });
        btnSaveConfig.setOnClickListener(v -> saveConfig());
        btnScene1.setOnClickListener(v -> loadScene(1));
        btnScene2.setOnClickListener(v -> loadScene(2));
        btnScene3.setOnClickListener(v -> loadScene(3));

        tvStatus.setText("就绪 - 请先开启辅助功能服务");
    }

    private void setSeekBarListener(SeekBar sb, TextView tv, String label) {
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { tv.setText(label + p); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void loadConfig() { loadScene(prefs.getInt("current_scene", 1)); }

    private void loadScene(int scene) {
        String px = "scene" + scene + "_";
        sbHueMin.setProgress(prefs.getInt(px+"hue_min", 35));
        sbHueMax.setProgress(prefs.getInt(px+"hue_max", 85));
        sbSatMin.setProgress(prefs.getInt(px+"sat_min", 80));
        sbSatMax.setProgress(prefs.getInt(px+"sat_max", 255));
        sbValMin.setProgress(prefs.getInt(px+"val_min", 80));
        sbValMax.setProgress(prefs.getInt(px+"val_max", 255));
        sbInterval.setProgress(prefs.getInt(px+"interval", 25));
        sbThreshold.setProgress(prefs.getInt(px+"threshold", 10));
        prefs.edit().putInt("current_scene", scene).apply();
        tvStatus.setText("已加载场景 " + scene);
    }

    private void saveConfig() {
        int scene = prefs.getInt("current_scene", 1);
        String px = "scene" + scene + "_";
        prefs.edit()
            .putInt(px+"hue_min", sbHueMin.getProgress())
            .putInt(px+"hue_max", sbHueMax.getProgress())
            .putInt(px+"sat_min", sbSatMin.getProgress())
            .putInt(px+"sat_max", sbSatMax.getProgress())
            .putInt(px+"val_min", sbValMin.getProgress())
            .putInt(px+"val_max", sbValMax.getProgress())
            .putInt(px+"interval", sbInterval.getProgress())
            .putInt(px+"threshold", sbThreshold.getProgress())
            .apply();
        Toast.makeText(this, "已保存场景 " + scene, Toast.LENGTH_SHORT).show();
        Intent i = new Intent("com.greenbarbot.CONFIG_UPDATE");
        i.putExtra("hue_min", sbHueMin.getProgress()); i.putExtra("hue_max", sbHueMax.getProgress());
        i.putExtra("sat_min", sbSatMin.getProgress()); i.putExtra("sat_max", sbSatMax.getProgress());
        i.putExtra("val_min", sbValMin.getProgress()); i.putExtra("val_max", sbValMax.getProgress());
        i.putExtra("interval", sbInterval.getProgress()); i.putExtra("threshold", sbThreshold.getProgress());
        sendBroadcast(i);
    }

    private void startBot() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
            swMain.setChecked(false);
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            return;
        }
        saveConfig();
        try { startService(new Intent(this, FloatingWindowService.class)); }
        catch (Exception e) { showError("启动悬浮窗失败", e); return; }
        try { startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION); }
        catch (Exception e) { showError("请求录屏失败", e); }
        tvStatus.setText("运行中...");
    }

    private void stopBot() {
        try { stopService(new Intent(this, FloatingWindowService.class)); } catch (Exception ignored) {}
        try { stopService(new Intent(this, ScreenCaptureService.class)); } catch (Exception ignored) {}
        tvStatus.setText("已停止");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MEDIA_PROJECTION && res == Activity.RESULT_OK) {
            try {
                Intent svc = new Intent(this, ScreenCaptureService.class);
                svc.putExtra("result_code", res);
                svc.putExtra("result_data", data);
                startService(svc);
            } catch (Exception e) {
                showError("启动截图服务失败", e);
                swMain.setChecked(false);
            }
        } else if (req == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            tvStatus.setText("悬浮窗权限已获取");
        }
    }
}
