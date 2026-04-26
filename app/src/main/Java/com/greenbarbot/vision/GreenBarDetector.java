package com.greenbarbot.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GreenBarDetector {

    private static final String TAG = "GreenBarDetector";

    // HSV thresholds for green bar detection
    private int hueMin = 35, hueMax = 85;
    private int satMin = 80, satMax = 255;
    private int valMin = 80, valMax = 255;
    private int threshold = 10;

    // HSV thresholds for yellow bar detection  
    private int yHueMin = 20, yHueMax = 35;
    private int ySatMin = 100, ySatMax = 255;
    private int yValMin = 100, yValMax = 255;

    private Mat greenTemplate = null;
    private Mat yellowTemplate = null;
    private Context context;
    private String templateDir;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV init failed");
        }
    }

    public GreenBarDetector(Context context) {
        this.context = context;
        this.templateDir = context.getFilesDir().getAbsolutePath() + "/templates/";
        new File(templateDir).mkdirs();
        loadTemplates();
    }

    public void updateConfig(int hMin, int hMax, int sMin, int sMax, int vMin, int vMax, int thresh) {
        this.hueMin = hMin; this.hueMax = hMax;
        this.satMin = sMin; this.satMax = sMax;
        this.valMin = vMin; this.valMax = vMax;
        this.threshold = thresh;
    }

    public static class DetectionResult {
        public boolean detected = false;
        public int greenCenterX = 0;
        public int yellowBarX = 0;
        public Mat greenMask = new Mat();
        public byte[] previewJpeg = null;
    }

    public DetectionResult detect(Mat frame) {
        DetectionResult result = new DetectionResult();

        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        // Adaptive: scale frame to reference 1080p width for threshold invariance
        double scale = 1080.0 / frame.cols();

        // ── Green bar detection ──────────────────────────────────────────────
        Mat greenMask = new Mat();
        Core.inRange(hsv,
            new Scalar(hueMin, satMin, valMin),
            new Scalar(hueMax, satMax, valMax),
            greenMask);

        // Morphological cleanup
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Imgproc.morphologyEx(greenMask, greenMask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(greenMask, greenMask, Imgproc.MORPH_OPEN, kernel);

        // Find largest green contour
        List<MatOfPoint> greenContours = new ArrayList<>();
        Mat hier = new Mat();
        Imgproc.findContours(greenMask, greenContours, hier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxGreenArea = 0;
        Rect bestGreenRect = null;
        for (MatOfPoint c : greenContours) {
            double area = Imgproc.contourArea(c);
            if (area > maxGreenArea) {
                maxGreenArea = area;
                bestGreenRect = Imgproc.boundingRect(c);
            }
        }

        // ── Yellow bar detection ─────────────────────────────────────────────
        Mat yellowMask = new Mat();
        Core.inRange(hsv,
            new Scalar(yHueMin, ySatMin, yValMin),
            new Scalar(yHueMax, ySatMax, yValMax),
            yellowMask);
        Imgproc.morphologyEx(yellowMask, yellowMask, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> yellowContours = new ArrayList<>();
        Imgproc.findContours(yellowMask, yellowContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxYellowArea = 0;
        Rect bestYellowRect = null;
        for (MatOfPoint c : yellowContours) {
            double area = Imgproc.contourArea(c);
            if (area > maxYellowArea) {
                maxYellowArea = area;
                bestYellowRect = Imgproc.boundingRect(c);
            }
        }

        // ── Template Matching fallback ───────────────────────────────────────
        if (bestGreenRect == null && greenTemplate != null) {
            bestGreenRect = templateMatch(frame, greenTemplate);
        }
        if (bestYellowRect == null && yellowTemplate != null) {
            bestYellowRect = templateMatch(frame, yellowTemplate);
        }

        // ── Assemble result ──────────────────────────────────────────────────
        result.greenMask = greenMask;

        if (bestGreenRect != null && bestYellowRect != null) {
            result.detected = true;
            result.greenCenterX = bestGreenRect.x + bestGreenRect.width / 2;
            result.yellowBarX   = bestYellowRect.x + bestYellowRect.width / 2;

            // Draw visualization
            Mat viz = frame.clone();
            Imgproc.rectangle(viz, bestGreenRect, new Scalar(0, 255, 0), 3);
            Imgproc.rectangle(viz, bestYellowRect, new Scalar(0, 220, 255), 3);
            Imgproc.line(viz,
                new Point(result.greenCenterX, 0),
                new Point(result.greenCenterX, frame.rows()),
                new Scalar(0, 255, 0), 2);
            Imgproc.line(viz,
                new Point(result.yellowBarX, 0),
                new Point(result.yellowBarX, frame.rows()),
                new Scalar(0, 200, 255), 2);

            // Encode preview jpeg (throttled by caller)
            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".jpg", viz, buf, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 60));
            result.previewJpeg = buf.toArray();
            viz.release();
        }

        hsv.release();
        kernel.release();
        yellowMask.release();
        hier.release();

        return result;
    }

    private Rect templateMatch(Mat frame, Mat tmpl) {
        if (tmpl.cols() > frame.cols() || tmpl.rows() > frame.rows()) return null;
        Mat result = new Mat();
        Imgproc.matchTemplate(frame, tmpl, result, Imgproc.TM_CCOEFF_NORMED);
        Core.MinMaxLocResult mm = Core.minMaxLoc(result);
        result.release();
        if (mm.maxVal > 0.65) {
            return new Rect((int) mm.maxLoc.x, (int) mm.maxLoc.y, tmpl.cols(), tmpl.rows());
        }
        return null;
    }

    public void captureTemplate() {
        // Will be filled on next frame by ScreenCaptureService calling saveTemplate()
    }

    public void saveTemplate(Mat greenRegion, Mat yellowRegion) {
        greenTemplate = greenRegion.clone();
        yellowTemplate = yellowRegion.clone();
        Imgcodecs.imwrite(templateDir + "green_template.png", greenTemplate);
        Imgcodecs.imwrite(templateDir + "yellow_template.png", yellowTemplate);
        Log.d(TAG, "Templates saved");
    }

    private void loadTemplates() {
        File gf = new File(templateDir + "green_template.png");
        File yf = new File(templateDir + "yellow_template.png");
        if (gf.exists()) greenTemplate = Imgcodecs.imread(gf.getAbsolutePath());
        if (yf.exists()) yellowTemplate = Imgcodecs.imread(yf.getAbsolutePath());
    }
}
