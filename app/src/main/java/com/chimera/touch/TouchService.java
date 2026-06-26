package com.chimera.touch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class TouchService extends AccessibilityService {
    private static final String TAG = "ChimeraTouch";
    private WsServer wsServer;

    @Override
    public void onServiceConnected() {
        Log.i(TAG, "✅ Accessibility Service connected");
        startWsServer();
    }

    private void startWsServer() {
        wsServer = new WsServer(new InetSocketAddress(8083));
        wsServer.start();
        Log.i(TAG, "✅ WebSocket server on port 8083");
    }

    public void dispatchSwipe(int x, int y, int duration) {
        if (!isServiceConnected()) {
            Log.e(TAG, "Service not connected");
            return;
        }
        
        Path path = new Path();
        path.moveTo(x, y);
        
        GestureDescription.StrokeDescription stroke = 
            new GestureDescription.StrokeDescription(path, 0, Math.max(duration, 8));
        
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();
        
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                Log.d(TAG, "✅ Swipe completed at (" + x + "," + y + ")");
            }
            @Override
            public void onCancelled(GestureDescription g) {
                Log.w(TAG, "❌ Swipe cancelled");
            }
        }, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wsServer != null) {
            try { wsServer.stop(); } catch (Exception e) {}
        }
    }

    private class WsServer extends WebSocketServer {
        public WsServer(InetSocketAddress addr) { super(addr); }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake h) {
            Log.i(TAG, "Client connected");
        }

        @Override
        public void onMessage(WebSocket conn, String msg) {
            try {
                // Format: "prevX,prevY,targetX,targetY"
                String[] p = msg.split(",");
                if (p.length == 4) {
                    int tx = (int) Float.parseFloat(p[2]);
                    int ty = (int) Float.parseFloat(p[3]);
                    dispatchSwipe(tx, ty, 8);
                }
            } catch (Exception e) {
                Log.e(TAG, "Parse error: " + e.getMessage());
            }
        }

        @Override public void onClose(WebSocket conn, int c, String r, boolean b) {}
        @Override public void onError(WebSocket conn, Exception e) {
            Log.e(TAG, "WS error: " + e.getMessage());
        }
        @Override public void onStart() {}
    }
}
