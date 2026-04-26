package com.greenbarbot.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.greenbarbot.R;
import com.greenbarbot.vision.GreenBarDetector;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class CalibrationActivity extends AppCompatActivity {

    private ImageView ivPreview, ivMask;
    private TextView tvResult;
    private BroadcastReceiver frameReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        ivPreview = findViewById(R.id.iv_preview);
        ivMask = findViewById(R.id.iv_mask);
        tvResult = findViewById(R.id.tv_cal_result);

        frameReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                byte[] jpgBytes = intent.getByteArrayExtra("frame");
                if (jpgBytes != null) updatePreview(jpgBytes);
            }
        };
        registerReceiver(frameReceiver, new IntentFilter("com.greenbarbot.FRAME"));

        Button btnCapture = findViewById(R.id.btn_capture_template);
        btnCapture.setOnClickListener(v -> captureTemplate());
    }

    private void updatePreview(byte[] jpgBytes) {
        Bitmap bmp = BitmapFactory.decodeByteArray(jpgBytes, 0, jpgBytes.length);
        if (bmp == null) return;

        Mat frame = new Mat();
        Utils.bitmapToMat(bmp, frame);

        GreenBarDetector detector = new GreenBarDetector(this);
        GreenBarDetector.DetectionResult result = detector.detect(frame);

        Mat mask = result.greenMask;
        Bitmap maskBmp = Bitmap.createBitmap(mask.cols(), mask.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mask, maskBmp);

        runOnUiThread(() -> {
            ivPreview.setImageBitmap(bmp);
            ivMask.setImageBitmap(maskBmp);
            if (result.detected) {
                tvResult.setText("检测成功！绿条中心 X=" + result.greenCenterX
                    + "  黄杠 X=" + result.yellowBarX
                    + "\n偏移=" + (result.greenCenterX - result.yellowBarX) + "px");
            } else {
                tvResult.setText("未检测到目标，请调整HSV参数");
            }
        });

        frame.release();
    }

    private void captureTemplate() {
        sendBroadcast(new Intent("com.greenbarbot.CAPTURE_TEMPLATE"));
        Toast.makeText(this, "模板已捕获", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(frameReceiver); } catch (Exception ignored) {}
    }
}
