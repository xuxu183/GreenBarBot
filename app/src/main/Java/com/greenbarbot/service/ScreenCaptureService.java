package com.greenbarbot.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.greenbarbot.vision.GreenBarDetector;
import com.greenbarbot.vision.TouchInjector;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler captureHandler;
    private HandlerThread captureThread;
    private GreenBarDetector detector;
    private TouchInjector touchInjector;
    private int screenWidth, screenHeight, screenDensity;
    private int intervalMs = 25;
    private volatile boolean running = false;

    private BroadcastReceiver configReceiver;

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(3, buildNotification());

        int resultCode = intent.getIntExtra("result_code", -1);
        Intent resultData = intent.getParcelableExtra("result_data");

        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        SharedPreferences prefs = getSharedPreferences("GreenBarBot", MODE_PRIVATE);
        intervalMs = Math.max(25, prefs.getInt("scene1_interval", 25));

        detector = new GreenBarDetector(this);
        touchInjector = new TouchInjector();

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mgr.getMediaProjection(resultCode, resultData);

        captureThread = new HandlerThread("CaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("GreenBarCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, captureHandler);

        running = true;
        scheduleCapture();
        registerConfigReceiver();

        return START_NOT_STICKY;
    }

    private void scheduleCapture() {
        if (!running) return;
        captureHandler.postDelayed(() -> {
            processFrame();
            scheduleCapture();
        }, intervalMs);
    }

    private void processFrame() {
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;

        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            int width = image.getWidth();
            int height = image.getHeight();

            Bitmap bitmap = Bitmap.createBitmap(rowStride / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            if (rowStride / pixelStride != width) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            }

            Mat frame = new Mat();
            Utils.bitmapToMat(bitmap, frame);
            bitmap.recycle();

            GreenBarDetector.DetectionResult result = detector.detect(frame);
            frame.release();

            if (result.detected) {
                handleDetection(result);
                broadcastFrame(result);
            }
        } finally {
            image.close();
        }
    }

    private void handleDetection(GreenBarDetector.DetectionResult result) {
        // greenCenterX relative to screenWidth
        int threshold = getSharedPreferences("GreenBarBot", MODE_PRIVATE).getInt("scene1_threshold", 10);
        int delta = result.greenCenterX - result.yellowBarX;

        String state;
        if (delta < -threshold) {
            // Green is LEFT of yellow bar → press LEFT, release RIGHT
            touchInjector.longPress((int) FloatingWindowService.leftX, (int) FloatingWindowService.leftY);
            touchInjector.release((int) FloatingWindowService.rightX, (int) FloatingWindowService.rightY);
            state = "left";
        } else if (delta > threshold) {
            // Green is RIGHT of yellow bar → press RIGHT, release LEFT
            touchInjector.longPress((int) FloatingWindowService.rightX, (int) FloatingWindowService.rightY);
            touchInjector.release((int) FloatingWindowService.leftX, (int) FloatingWindowService.leftY);
            state = "right";
        } else {
            touchInjector.release((int) FloatingWindowService.leftX, (int) FloatingWindowService.leftY);
            touchInjector.release((int) FloatingWindowService.rightX, (int) FloatingWindowService.rightY);
            state = "none";
        }

        Intent stateIntent = new Intent("com.greenbarbot.BOT_STATE");
        stateIntent.putExtra("state", state);
        sendBroadcast(stateIntent);
    }

    private void broadcastFrame(GreenBarDetector.DetectionResult result) {
        // Throttle frame broadcast to ~10fps for calibration preview
        Intent i = new Intent("com.greenbarbot.FRAME");
        if (result.previewJpeg != null) i.putExtra("frame", result.previewJpeg);
        sendBroadcast(i);
    }

    private void registerConfigReceiver() {
        configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                detector.updateConfig(
                    intent.getIntExtra("hue_min", 35),
                    intent.getIntExtra("hue_max", 85),
                    intent.getIntExtra("sat_min", 80),
                    intent.getIntExtra("sat_max", 255),
                    intent.getIntExtra("val_min", 80),
                    intent.getIntExtra("val_max", 255),
                    intent.getIntExtra("threshold", 10)
                );
                intervalMs = Math.max(25, intent.getIntExtra("interval", 25));
            }
        };
        registerReceiver(configReceiver, new IntentFilter("com.greenbarbot.CONFIG_UPDATE"));

        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                detector.captureTemplate();
            }
        }, new IntentFilter("com.greenbarbot.CAPTURE_TEMPLATE"));
    }

    private Notification buildNotification() {
        String CHANNEL = "greenbar_capture";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "截图服务", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GreenBarBot 识别中")
            .setContentText("每 " + intervalMs + "ms 截图识别一次")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (captureThread != null) captureThread.quitSafely();
        try { unregisterReceiver(configReceiver); } catch (Exception ignored) {}
    }
}
