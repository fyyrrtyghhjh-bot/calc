package com.example.calc;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class ClickService extends AccessibilityService {
  public static ClickService inst;
  public static Runnable onDone;
  private final Handler h = new Handler(Looper.getMainLooper());
  private boolean running = false;
  private boolean finite = false;
  private int left = 0;
  private long interval = 200;
  private float cx, cy;

  private final Runnable tick = new Runnable() {
    public void run() {
      if (!running) { return; }
      tap();
      if (finite) {
        left--;
        if (left <= 0) {
          running = false;
          if (onDone != null) { h.post(onDone); }
          return;
        }
      }
      h.postDelayed(this, interval);
    }
  };

  private void tap() {
    if (Build.VERSION.SDK_INT < 24) { return; }
    Path p = new Path();
    p.moveTo(cx, cy);
    GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 30);
    dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
  }

  public void startClicks(float x, float y, long ms, int count) {
    stopClicks();
    cx = x;
    cy = y;
    interval = Math.max(80, ms);
    finite = count > 0;
    left = count;
    running = true;
    h.post(tick);
  }

  public void stopClicks() {
    running = false;
    h.removeCallbacks(tick);
  }

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    inst = this;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) { }

  @Override
  public void onInterrupt() { }

  @Override
  public boolean onUnbind(Intent intent) {
    stopClicks();
    inst = null;
    return super.onUnbind(intent);
  }
}
