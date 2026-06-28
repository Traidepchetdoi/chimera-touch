package com.chimera.touch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

public class TouchService extends AccessibilityService {
    private static final String TAG = "ChimeraOmega";
    
    // 🧠 MODULE MẮT (VISION - GỬI VỀ 8082)
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private FaceDetector mFaceDetector;
    private MyWebSocketClient mWsClient; // Client gửi về Termux
    private Handler mHandler = new Handler();

    // 🖐️ MODULE TAY (TOUCH - NHẬN TỪ 8083)
    private WsServer mWsServer;

    @Override
    public void onServiceConnected() {
        Log.i(TAG, "✅ Accessibility Service Connected (The Hand is Ready)");
        startTouchServer(); // Khởi động cổng nhận lệnh vuốt (8083)
        initFaceDetector(); // Khởi tạo não bộ ML Kit
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "START_VISION".equals(intent.getAction())) {
            int code = intent.getIntExtra("CODE", -1);
            Intent data = intent.getParcelableExtra("DATA");
            if (code != -1 && data != null) {
                startVision(code, data); // Khởi động mắt (8082)
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void initFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        mFaceDetector = FaceDetection.getClient(options);
        Log.i(TAG, "🧠 ML Kit Face Detector Initialized");
    }

    private void startVision(int code, Intent data) {
        Log.i(TAG, "👁️ Starting Vision (X-Ray Eye)...");
        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mMediaProjection = mgr.getMediaProjection(code, data);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        int mWidth = metrics.widthPixels;
        int mHeight = metrics.heightPixels;
        int mDensity = metrics.densityDpi;

        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ChimeraVision",
                mWidth, mHeight, mDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);

        mImageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                processImage(image);
                image.close();
            }
        }, mHandler);

        // Kết nối WebSocket Client về Termux (8082)
        try {
            mWsClient = new MyWebSocketClient(new URI("ws://127.0.0.1:8082"));
            mWsClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "WS Client Error: " + e.getMessage());
        }
    }

    private void processImage(@NonNull Image image) {
        InputImage inputImage = InputImage.fromMediaImage(image, 0); // Rotation 0 for virtual display usually
        
        Task<List<Face>> result = mFaceDetector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    for (Face face : faces) {
                        if (mWsClient != null && mWsClient.isOpen()) {
                            float x = face.getBoundingBox().exactCenterX();
                            float y = face.getBoundingBox().exactCenterY();
                            float ho = face.getBoundingBox().height();
                            float pitch = face.getHeadEulerAngleX(); // 🧠 X-RAY DATA

                            // JSON OMEGA PROTOCOL
                            String json = String.format(
                                "{\"TARGET\":true, \"x\":%.2f, \"y\":%.2f, \"headOffset\":%.2f, \"pitch\":%.2f}",
                                x, y, ho, pitch
                            );
                            mWsClient.send(json);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.d(TAG, "Face detect fail: " + e.getMessage()));
    }

    private void startTouchServer() {
        mWsServer = new WsServer(new InetSocketAddress(8083));
        mWsServer.start();
        Log.i(TAG, "🖐️ Touch Server Started on 8083");
    }

    // 🖐️ LOGIC VUỐT (GIỮ NGUYÊN)
    public void dispatchSwipe(int x, int y, int duration) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(duration, 8));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        if (mMediaProjection != null) mMediaProjection.stop();
        if (mWsServer != null) try { mWsServer.stop(); } catch (Exception e) {}
        if (mWsClient != null) mWsClient.close();
    }

    // 🧠 WEBSOCKET CLIENT (GỬI TỌA ĐỘ VỀ TERMUX 8082)
    private class MyWebSocketClient extends WebSocketClient {
        public MyWebSocketClient(URI serverUri) { super(serverUri); }
        @Override public void onOpen(ServerHandshake h) { Log.i(TAG, "✅ Connected to Termux (8082)"); }
        @Override public void onMessage(String msg) {}
        @Override public void onClose(int c, String r, boolean b) { Log.w(TAG, "Disconnected from Termux"); }
        @Override public void onError(Exception ex) { Log.e(TAG, "WS Client Err: " + ex.getMessage()); }
    }

    // 🖐️ WEBSOCKET SERVER (NHẬN LỆNH VUỐT TỪ TERMUX 8083)
    private class WsServer extends WebSocketServer {
        public WsServer(InetSocketAddress addr) { super(addr); }
        @Override public void onOpen(WebSocket conn, ClientHandshake h) {}
        @Override public void onMessage(WebSocket conn, String msg) {
            try {
                // Parse lệnh GESTURE:x1,y1,x2,y2
                String[] p = msg.split(",");
                if (p.length == 4 && msg.startsWith("GESTURE")) {
                    int tx = (int) Float.parseFloat(p[2]);
                    int ty = (int) Float.parseFloat(p[3]);
                    dispatchSwipe(tx, ty, 8);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        @Override public void onClose(WebSocket conn, int c, String r, boolean b) {}
        @Override public void onError(WebSocket conn, Exception e) {}
        @Override public void onStart() {}
    }
}   
