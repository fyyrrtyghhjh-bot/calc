package com.example.calc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Launch")
public class LaunchPlugin extends Plugin {
  private View overlay;
  private WindowManager wm;
  private final Handler h = new Handler(Looper.getMainLooper());
  private long endAt = 0;
  private String tPkg = null;

  private final Runnable tick = new Runnable() {
    public void run() {
      if (endAt == 0) { return; }
      long left = endAt - System.currentTimeMillis();
      if (left <= 0) { finishTimer(); return; }
      if (overlay instanceof TextView) { ((TextView) overlay).setText(label(left)); }
      h.postDelayed(this, 1000);
    }
  };

  private String label(long ms) {
    long s = (ms + 999) / 1000;
    String t = s >= 3600
      ? String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
      : String.format("%d:%02d", s / 60, s % 60);
    return "\u2190 " + t;
  }

  private void stopTimer() {
    h.removeCallbacks(tick);
    endAt = 0;
    tPkg = null;
  }

  private void finishTimer() {
    final String pkg = tPkg;
    stopTimer();
    removeOverlay();
    Context c = getContext().getApplicationContext();
    Intent i = new Intent(c, MainActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    try { c.startActivity(i); } catch (Exception e) { }
    if (pkg != null) {
      h.postDelayed(new Runnable() {
        public void run() {
          try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(pkg);
          } catch (Exception e) { }
        }
      }, 1200);
    }
  }

  @PluginMethod
  public void open(PluginCall call) {
    String pkg = call.getString("pkg");
    boolean hide = Boolean.TRUE.equals(call.getBoolean("hide", false));
    if (pkg == null) { call.reject("no package"); return; }
    Intent i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
    if (i == null) { call.reject("App not found: " + pkg); return; }
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (hide) { i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS); }
    try { getContext().startActivity(i); call.resolve(); }
    catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
  }

  @PluginMethod
  public void hideSelf(PluginCall call) {
    boolean hide = Boolean.TRUE.equals(call.getBoolean("hide", false));
    try {
      ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
      for (ActivityManager.AppTask t : am.getAppTasks()) { t.setExcludeFromRecents(hide); }
      call.resolve();
    } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
  }

  @PluginMethod
  public void canOverlay(PluginCall call) {
    JSObject r = new JSObject();
    r.put("ok", Settings.canDrawOverlays(getContext()));
    call.resolve(r);
  }

  @PluginMethod
  public void askOverlay(PluginCall call) {
    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(i);
    call.resolve();
  }

  @PluginMethod
  public void startTimer(PluginCall call) {
    int sec = call.getInt("sec", 0);
    String pkg = call.getString("pkg");
    stopTimer();
    if (sec > 0) {
      endAt = System.currentTimeMillis() + sec * 1000L;
      tPkg = pkg;
      h.post(tick);
    }
    call.resolve();
  }

  @PluginMethod
  public void cancelTimer(PluginCall call) {
    stopTimer();
    call.resolve();
  }

  @PluginMethod
  public void showBack(final PluginCall call) {
    getActivity().runOnUiThread(new Runnable() {
      public void run() {
        try {
          if (overlay != null) { call.resolve(); return; }
          final Context c = getContext().getApplicationContext();
          wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
          TextView v = new TextView(c);
          v.setText("\u2190");
          v.setTextSize(18);
          v.setTextColor(0xFFFFFFFF);
          v.setGravity(Gravity.CENTER);
          GradientDrawable g = new GradientDrawable();
          g.setShape(GradientDrawable.RECTANGLE);
          g.setCornerRadius(200);
          g.setColor(0xDDFF9F0A);
          v.setBackground(g);
          float d = c.getResources().getDisplayMetrics().density;
          v.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
          WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
          p.gravity = Gravity.TOP | Gravity.END;
          p.x = 16;
          p.y = 220;
          v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
              stopTimer();
              removeOverlay();
              Intent i = new Intent(c, MainActivity.class);
              i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
              c.startActivity(i);
            }
          });
          wm.addView(v, p);
          overlay = v;
          call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
      }
    });
  }

  @PluginMethod
  public void hideBack(final PluginCall call) {
    getActivity().runOnUiThread(new Runnable() {
      public void run() { stopTimer(); removeOverlay(); call.resolve(); }
    });
  }

  private void removeOverlay() {
    try { if (overlay != null && wm != null) { wm.removeView(overlay); } } catch (Exception e) { }
    overlay = null;
  }
}
