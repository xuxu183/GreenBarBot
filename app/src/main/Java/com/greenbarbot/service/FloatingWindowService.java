package com.greenbarbot.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.greenbarbot.R;

public class FloatingWindowService extends Service {

    private WindowManager windowManager;
    private View floatLeft, floatRight;
    private WindowManager.LayoutParams paramsLeft, paramsRight;

    // Coordinates used for injection
    public static volatile float leftX, leftY, rightX, rightY;

    private BroadcastReceiver stateReceiver;

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(2, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingButtons();
        registerStateReceiver();
    }

    private void createFloatingButtons() {
        floatLeft = createButton("左", Color.parseColor("#CC2196F3"));
        floatRight = createButton("右", Color.parseColor("#CCF44336"));

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        paramsLeft = new WindowManager.LayoutParams(
            180, 180, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        paramsLeft.gravity = Gravity.TOP | Gravity.LEFT;
        paramsLeft.x = 80; paramsLeft.y = 400;

        paramsRight = new WindowManager.LayoutParams(
            180, 180, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        paramsRight.gravity = Gravity.TOP | Gravity.LEFT;
        paramsRight.x = 600; paramsRight.y = 400;

        windowManager.addView(floatLeft, paramsLeft);
        windowManager.addView(floatRight, paramsRight);

        setDraggable(floatLeft, paramsLeft, true);
        setDraggable(floatRight, paramsRight, false);

        leftX = paramsLeft.x + 90;
        leftY = paramsLeft.y + 90;
        rightX = paramsRight.x + 90;
        rightY = paramsRight.y + 90;
    }

    private View createButton(String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(color);
        tv.setAlpha(0.85f);
        // Rounded via background drawable would be set via XML; keep simple here
        return tv;
    }

    private void setDraggable(View view, WindowManager.LayoutParams params, boolean isLeft) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float initialTouchX, initialTouchY;
            long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int)(event.getRawX() - initialTouchX);
                        params.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(view, params);
                        if (isLeft) { leftX = params.x + 90; leftY = params.y + 90; }
                        else        { rightX = params.x + 90; rightY = params.y + 90; }
                        break;
                }
                return true;
            }
        });
    }

    private void registerStateReceiver() {
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String state = intent.getStringExtra("state"); // "left" | "right" | "none"
                if (floatLeft == null || floatRight == null) return;
                switch (state != null ? state : "none") {
                    case "left":
                        floatLeft.setAlpha(1.0f);
                        floatRight.setAlpha(0.4f);
                        break;
                    case "right":
                        floatLeft.setAlpha(0.4f);
                        floatRight.setAlpha(1.0f);
                        break;
                    default:
                        floatLeft.setAlpha(0.85f);
                        floatRight.setAlpha(0.85f);
                }
            }
        };
        registerReceiver(stateReceiver, new IntentFilter("com.greenbarbot.BOT_STATE"));
    }

    private Notification buildNotification() {
        String CHANNEL = "greenbar_float";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "悬浮窗", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GreenBarBot 运行中")
            .setContentText("悬浮按钮已显示，可拖动定位")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatLeft != null) windowManager.removeView(floatLeft);
        if (floatRight != null) windowManager.removeView(floatRight);
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }
}
