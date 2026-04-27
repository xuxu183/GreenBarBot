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
import android.util.Log;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.greenbarbot.vision.GreenBarDetector;
import com.greenbarbot.vision.TouchInjector;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCapture";
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
    private BroadcastReceiver templateReceiver;

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(3, buildNotification());

            int resultCode = intent.getIntExtra("result_code", -1);
            Intent resultData = intent.getParcelableExtra("result_data");
            if (resultCode == -1 || resultData == null) { stopSelf(); return START_NOT_STICKY; }

            DisplayMetrics m = new DisplayMetrics();
            ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
            screenWidth = m.widthPixels; screenHeight = m.heightPixels; screenDensity = m.densityDpi;

            SharedPreferences prefs = getSharedPreferences("GreenBarBot", MODE_PRIVATE);
            intervalMs = Math.max(25, prefs.getInt("scene1_interval", 25));

            detector = new GreenBarDetector(this);
            touchInjector = new TouchInjector();

            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = mgr.getMediaProjection(resultCode, resultData);

            // Android 14: 必须注册回调
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { stopSelf(); }
                }, new Handler());
            }

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
            registerReceivers();

        } catch (Exception e) {
            Log.e(TAG, "启动失败: " + e.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void scheduleCapture() {
        if (!running) return;
        captureHandler.postDelayed(() -> { processFrame(); scheduleCapture(); }, intervalMs);
    }

    private void processFrame() {
        if (imageReader == null) return;
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            int w = image.getWidth(), h = image.getHeight();

            Bitmap bmp = Bitmap.createBitmap(rowStride / pixelStride, h, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
            if (rowStride / pixelStride != w) bmp = Bitmap.createBitmap(bmp, 0, 0, w, h);

            Mat frame = new Mat();
            Utils.bitmapToMat(bmp, frame);
            bmp.recycle();

            GreenBarDetector.DetectionResult result = detector.detect(frame);
            frame.release();

            if (result.detected) handleDetection(result);

        } catch (Exception e) {
            Log.e(TAG, "帧处理异常: " + e.getMessage());
        } finally {
            if (image != null) try { image.close(); } catch (Exception ignored) {}
        }
    }

    private void handleDetection(GreenBarDetector.DetectionResult result) {
        SharedPreferences prefs = getSharedPreferences("GreenBarBot", MODE_PRIVATE);
        int threshold = prefs.getInt("scene1_threshold", 10);
        int delta = result.greenCenterX - result.yellowBarX;
        String state;

        if (delta < -threshold) {
            touchInjector.longPress((int) FloatingWindowService.leftX,  (int) FloatingWindowService.leftY);
            touchInjector.release((int)  FloatingWindowService.rightX, (int) FloatingWindowService.rightY);
            state = "left";
        } else if (delta > threshold) {
            touchInjector.longPress((int) FloatingWindowService.rightX, (int) FloatingWindowService.rightY);
            touchInjector.release((int)  FloatingWindowService.leftX,  (int) FloatingWindowService.leftY);
            state = "right";
        } else {
            touchInjector.release((int) FloatingWindowService.leftX,  (int) FloatingWindowService.leftY);
            touchInjector.release((int) FloatingWindowService.rightX, (int) FloatingWindowService.rightY);
            state = "none";
        }

        Intent i = new Intent("com.greenbarbot.BOT_STATE");
        i.putExtra("state", state);
        sendBroadcast(i);
    }

    private void registerReceivers() {
        configReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (detector != null) detector.updateConfig(
                    intent.getIntExtra("hue_min", 35), intent.getIntExtra("hue_max", 85),
                    intent.getIntExtra("sat_min", 80), intent.getIntExtra("sat_max", 255),
                    intent.getIntExtra("val_min", 80), intent.getIntExtra("val_max", 255),
                    intent.getIntExtra("threshold", 10));
                intervalMs = Math.max(25, intent.getIntExtra("interval", 25));
            }
        };
        templateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (detector != null) detector.captureTemplate();
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver,  new IntentFilter("com.greenbarbot.CONFIG_UPDATE"),   RECEIVER_NOT_EXPORTED);
            registerReceiver(templateReceiver, new IntentFilter("com.greenbarbot.CAPTURE_TEMPLATE"), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(configReceiver,  new IntentFilter("com.greenbarbot.CONFIG_UPDATE"));
            registerReceiver(templateReceiver, new IntentFilter("com.greenbarbot.CAPTURE_TEMPLATE"));
        }
    }

    private Notification buildNotification() {
        String CH = "greenbar_capture";
        NotificationChannel ch = new NotificationChannel(CH, "截图服务", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        return new NotificationCompat.Builder(this, CH)
            .setContentTitle("GreenBarBot 识别中")
            .setContentText("每 " + intervalMs + "ms 识别一次")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Exception ignored) {}
        try { if (captureThread != null) captureThread.quitSafely(); } catch (Exception ignored) {}
        try { unregisterReceiver(configReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(templateReceiver); } catch (Exception ignored) {}
    }
}
