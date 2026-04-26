package com.greenbarbot.vision;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;

/**
 * TouchInjector — uses AccessibilityService.dispatchGesture for long-press/release.
 * The service instance is provided via static singleton set in GreenBarAccessibilityService.
 */
public class TouchInjector {

    private static final String TAG = "TouchInjector";
    private static final long HOLD_DURATION_MS = 60000; // virtually infinite hold

    // Active stroke IDs for left and right
    private static volatile boolean leftPressed = false;
    private static volatile boolean rightPressed = false;

    public void longPress(int x, int y) {
        AccessibilityService svc = GreenBarAccessibilityService.instance;
        if (svc == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Accessibility service not available");
            return;
        }
        // Start a gesture that is a long "swipe to same point" (simulates hold)
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, HOLD_DURATION_MS, false);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        svc.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { Log.d(TAG, "LongPress completed"); }
            @Override public void onCancelled(GestureDescription g) { Log.d(TAG, "LongPress cancelled"); }
        }, null);
    }

    public void release(int x, int y) {
        // Release by dispatching a zero-duration lift
        AccessibilityService svc = GreenBarAccessibilityService.instance;
        if (svc == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 1, false);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        svc.dispatchGesture(gesture, null, null);
    }
}
