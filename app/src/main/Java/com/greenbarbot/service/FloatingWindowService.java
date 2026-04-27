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
import android.os.IBinder;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {

    private WindowManager windowManager;
    private View floatLeft, floatRight;
    private WindowManager.LayoutParams paramsLeft, paramsRight;

    public static volatile float leftX = 200, leftY = 500;
    public static volatile float rightX = 800, rightY = 500;

    private BroadcastReceiver stateReceiver;

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            startForeground(2, buildNotification());
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            createFloatingButtons();
            registerStateReceiver();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void createFloatingButtons() {
        floatLeft  = createButton("左", Color.parseColor("#CC2196F3"));
        floatRight = createButton("右", Color.parseColor("#CCF44336"));

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        paramsLeft = new WindowManager.LayoutParams(
            180, 180, type, flags, PixelFormat.TRANSLUCENT);
        paramsLeft.gravity = Gravity.TOP | Gravity.LEFT;
        paramsLeft.x = 80; paramsLeft.y = 400;

        paramsRight = new WindowManager.LayoutParams(
            180, 180, type, flags, PixelFormat.TRANSLUCENT);
        paramsRight.gravity = Gravity.TOP | Gravity.LEFT;
        paramsRight.x = 600; paramsRight.y = 400;

        windowManager.addView(floatLeft, paramsLeft);
        windowManager.addView(floatRight, paramsRight);

        setDraggable(floatLeft, paramsLeft, true);
        setDraggable(floatRight, paramsRight, false);

        leftX  = paramsLeft.x  + 90;
        leftY  = paramsLeft.y  + 90;
        rightX = paramsRight.x + 90;
        rightY = paramsRight.y + 90;
    }

    private View createButton(String label, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(color);
        tv.setAlpha(0.85f);
        return tv;
    }

    private void setDraggable(View view, WindowManager.LayoutParams params, boolean isLeft) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY;
            float initTX, initTY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x; initY = params.y;
                        initTX = e.getRawX(); initTY = e.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initX + (int)(e.getRawX() - initTX);
                        params.y = initY + (int)(e.getRawY() - initTY);
                        try { windowManager.updateViewLayout(view, params); } catch (Exception ignored) {}
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
                String state = intent.getStringExtra("state");
                if (floatLeft == null || floatRight == null) return;
                switch (state != null ? state : "none") {
                    case "left":
                        floatLeft.setAlpha(1.0f); floatRight.setAlpha(0.4f); break;
                    case "right":
                        floatLeft.setAlpha(0.4f); floatRight.setAlpha(1.0f); break;
                    default:
                        floatLeft.setAlpha(0.85f); floatRight.setAlpha(0.85f);
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.greenbarbot.BOT_STATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    private Notification buildNotification() {
        String CH = "greenbar_float";
        NotificationChannel ch = new NotificationChannel(CH, "悬浮窗", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        return new NotificationCompat.Builder(this, CH)
            .setContentTitle("GreenBarBot")
            .setContentText("悬浮按钮运行中，可拖动定位")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (floatLeft  != null) windowManager.removeView(floatLeft);  } catch (Exception ignored) {}
        try { if (floatRight != null) windowManager.removeView(floatRight); } catch (Exception ignored) {}
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }
}
