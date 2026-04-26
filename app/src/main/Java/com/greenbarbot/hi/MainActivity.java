package com.greenbarbot.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.greenbarbot.R;
import com.greenbarbot.service.FloatingWindowService;
import com.greenbarbot.service.ScreenCaptureService;

public class MainActivity extends AppCompatActivity {

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("GreenBarBot", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        initViews();
        loadConfig();
        checkPermissions();
    }

    private void initViews() {
        swMain = findViewById(R.id.sw_main);
        btnCalibrate = findViewById(R.id.btn_calibrate);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        btnScene1 = findViewById(R.id.btn_scene1);
        btnScene2 = findViewById(R.id.btn_scene2);
        btnScene3 = findViewById(R.id.btn_scene3);
        tvStatus = findViewById(R.id.tv_status);

        sbHueMin = findViewById(R.id.sb_hue_min);
        sbHueMax = findViewById(R.id.sb_hue_max);
        sbSatMin = findViewById(R.id.sb_sat_min);
        sbSatMax = findViewById(R.id.sb_sat_max);
        sbValMin = findViewById(R.id.sb_val_min);
        sbValMax = findViewById(R.id.sb_val_max);
        tvHueMin = findViewById(R.id.tv_hue_min);
        tvHueMax = findViewById(R.id.tv_hue_max);
        tvSatMin = findViewById(R.id.tv_sat_min);
        tvSatMax = findViewById(R.id.tv_sat_max);
        tvValMin = findViewById(R.id.tv_val_min);
        tvValMax = findViewById(R.id.tv_val_max);

        sbInterval = findViewById(R.id.sb_interval);
        sbThreshold = findViewById(R.id.sb_threshold);
        tvInterval = findViewById(R.id.tv_interval);
        tvThreshold = findViewById(R.id.tv_threshold);

        sbHueMin.setMax(180);
        sbHueMax.setMax(180);
        sbSatMin.setMax(255);
        sbSatMax.setMax(255);
        sbValMin.setMax(255);
        sbValMax.setMax(255);
        sbInterval.setMax(200);
        sbThreshold.setMax(100);

        setSeekBarListener(sbHueMin, tvHueMin, "绿色Hue最小值: ");
        setSeekBarListener(sbHueMax, tvHueMax, "绿色Hue最大值: ");
        setSeekBarListener(sbSatMin, tvSatMin, "饱和度最小值: ");
        setSeekBarListener(sbSatMax, tvSatMax, "饱和度最大值: ");
        setSeekBarListener(sbValMin, tvValMin, "亮度最小值: ");
        setSeekBarListener(sbValMax, tvValMax, "亮度最大值: ");
        setSeekBarListener(sbInterval, tvInterval, "识别间隔(ms): ");
        setSeekBarListener(sbThreshold, tvThreshold, "判定阈值(px): ");

        swMain.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) startBot();
            else stopBot();
        });

        btnCalibrate.setOnClickListener(v -> openCalibration());
        btnSaveConfig.setOnClickListener(v -> saveConfig());

        btnScene1.setOnClickListener(v -> loadScene(1));
        btnScene2.setOnClickListener(v -> loadScene(2));
        btnScene3.setOnClickListener(v -> loadScene(3));
    }

    private void setSeekBarListener(SeekBar sb, TextView tv, String label) {
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                tv.setText(label + p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void loadConfig() {
        int scene = prefs.getInt("current_scene", 1);
        loadScene(scene);
    }

    private void loadScene(int scene) {
        String prefix = "scene" + scene + "_";
        sbHueMin.setProgress(prefs.getInt(prefix + "hue_min", 35));
        sbHueMax.setProgress(prefs.getInt(prefix + "hue_max", 85));
        sbSatMin.setProgress(prefs.getInt(prefix + "sat_min", 80));
        sbSatMax.setProgress(prefs.getInt(prefix + "sat_max", 255));
        sbValMin.setProgress(prefs.getInt(prefix + "val_min", 80));
        sbValMax.setProgress(prefs.getInt(prefix + "val_max", 255));
        sbInterval.setProgress(prefs.getInt(prefix + "interval", 25));
        sbThreshold.setProgress(prefs.getInt(prefix + "threshold", 10));
        prefs.edit().putInt("current_scene", scene).apply();
        tvStatus.setText("已加载场景 " + scene);
    }

    private void saveConfig() {
        int scene = prefs.getInt("current_scene", 1);
        String prefix = "scene" + scene + "_";
        prefs.edit()
            .putInt(prefix + "hue_min", sbHueMin.getProgress())
            .putInt(prefix + "hue_max", sbHueMax.getProgress())
            .putInt(prefix + "sat_min", sbSatMin.getProgress())
            .putInt(prefix + "sat_max", sbSatMax.getProgress())
            .putInt(prefix + "val_min", sbValMin.getProgress())
            .putInt(prefix + "val_max", sbValMax.getProgress())
            .putInt(prefix + "interval", sbInterval.getProgress())
            .putInt(prefix + "threshold", sbThreshold.getProgress())
            .apply();
        Toast.makeText(this, "配置已保存到场景 " + scene, Toast.LENGTH_SHORT).show();
        broadcastConfigUpdate();
    }

    private void broadcastConfigUpdate() {
        Intent i = new Intent("com.greenbarbot.CONFIG_UPDATE");
        i.putExtra("hue_min", sbHueMin.getProgress());
        i.putExtra("hue_max", sbHueMax.getProgress());
        i.putExtra("sat_min", sbSatMin.getProgress());
        i.putExtra("sat_max", sbSatMax.getProgress());
        i.putExtra("val_min", sbValMin.getProgress());
        i.putExtra("val_max", sbValMax.getProgress());
        i.putExtra("interval", sbInterval.getProgress());
        i.putExtra("threshold", sbThreshold.getProgress());
        sendBroadcast(i);
    }

    private void checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        }
    }

    private void startBot() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            swMain.setChecked(false);
            checkPermissions();
            return;
        }
        saveConfig();
        startService(new Intent(this, FloatingWindowService.class));
        Intent proj = projectionManager.createScreenCaptureIntent();
        startActivityForResult(proj, REQ_MEDIA_PROJECTION);
        tvStatus.setText("运行中... 请点击左/右悬浮块设置坐标");
    }

    private void stopBot() {
        stopService(new Intent(this, FloatingWindowService.class));
        stopService(new Intent(this, ScreenCaptureService.class));
        tvStatus.setText("已停止");
    }

    private void openCalibration() {
        startActivity(new Intent(this, CalibrationActivity.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK) {
            Intent service = new Intent(this, ScreenCaptureService.class);
            service.putExtra("result_code", resultCode);
            service.putExtra("result_data", data);
            startService(service);
        } else if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                tvStatus.setText("悬浮窗权限已获取");
            }
        }
    }
}
