package com.example.calc;

import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
  private static final int[] SPEEDS = {1000, 500, 200, 100};
  private static final String[] SPEED_L = {"1/\u0441", "2/\u0441", "5/\u0441", "10/\u0441"};
  private static final int[] COUNTS = {0, 10, 50, 100, 500, 1000};
  private static final String[] COUNT_L = {"\u221e", "10", "50", "100", "500", "1000"};
  private int sp = 2;
  private int ct = 0;
  private boolean clicking = false;
  private WebView petView;
  private LinearLayout panel;
  private TextView cross, bGo, bSp, bCt;
  private WindowManager.LayoutParams panelP, crossP;
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
  public void saveFile(PluginCall call) {
    String name = call.getString("name");
    String data = call.getString("data");
    String mime = call.getString("mime", "application/octet-stream");
    boolean b64 = Boolean.TRUE.equals(call.getBoolean("b64", false));
    if (name == null || data == null) { call.reject("no data"); return; }
    try {
      byte[] bytes = b64 ? Base64.decode(data, Base64.DEFAULT) : data.getBytes("UTF-8");
      String path;
      if (Build.VERSION.SDK_INT >= 29) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) { call.reject("cannot create file"); return; }
        OutputStream os = getContext().getContentResolver().openOutputStream(uri);
        os.write(bytes);
        os.close();
        path = "Download/" + name;
      } else {
        File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File out = new File(dir, name);
        FileOutputStream os = new FileOutputStream(out);
        os.write(bytes);
        os.close();
        path = out.getAbsolutePath();
      }
      JSObject r = new JSObject();
      r.put("path", path);
      call.resolve(r);
    } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
  }

  private WindowManager.LayoutParams lp(int w, int h2) {
    return new WindowManager.LayoutParams(w, h2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT);
  }

  private TextView chip(Context c, String t) {
    TextView v = new TextView(c);
    v.setText(t);
    v.setTextColor(0xFFFFFFFF);
    v.setTextSize(14);
    float d = c.getResources().getDisplayMetrics().density;
    v.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (8 * d));
    return v;
  }

  private void drag(View handle, final View target, final WindowManager.LayoutParams p) {
    handle.setOnTouchListener(new View.OnTouchListener() {
      float dx, dy;
      int sx, sy;
      public boolean onTouch(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
          dx = e.getRawX(); dy = e.getRawY(); sx = p.x; sy = p.y;
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
          p.x = sx + (int) (e.getRawX() - dx);
          p.y = sy + (int) (e.getRawY() - dy);
          try { wm.updateViewLayout(target, p); } catch (Exception ex) { }
        }
        return true;
      }
    });
  }

  private void goHome() {
    stopTimer();
    removeOverlay();
    removeClicker(); removePet();
    Context c = getContext().getApplicationContext();
    Intent i = new Intent(c, MainActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    c.startActivity(i);
  }

  private void toggleClick() {
    if (clicking) { stopClick(); return; }
    ClickService s = ClickService.inst;
    if (s == null) { bGo.setText("!"); return; }
    float cx = crossP.x + crossP.width / 2f;
    float cy = crossP.y + crossP.height / 2f;
    clicking = true;
    bGo.setText("\u25a0");
    crossP.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    try { wm.updateViewLayout(cross, crossP); } catch (Exception e) { }
    ClickService.onDone = new Runnable() { public void run() { stopClick(); } };
    s.startClicks(cx, cy, SPEEDS[sp], COUNTS[ct]);
  }

  private void stopClick() {
    clicking = false;
    if (ClickService.inst != null) { ClickService.inst.stopClicks(); }
    ClickService.onDone = null;
    if (bGo != null) { bGo.setText("\u25b6"); }
    if (cross != null && crossP != null && wm != null) {
      crossP.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
      try { wm.updateViewLayout(cross, crossP); } catch (Exception e) { }
    }
  }

  private void removeClicker() {
    stopClick();
    try { if (panel != null && wm != null) { wm.removeView(panel); } } catch (Exception e) { }
    try { if (cross != null && wm != null) { wm.removeView(cross); } } catch (Exception e) { }
    panel = null;
    cross = null;
    bGo = null;
    bSp = null;
    bCt = null;
  }

  @PluginMethod
  public void clickStatus(PluginCall call) {
    JSObject r = new JSObject();
    r.put("overlay", Settings.canDrawOverlays(getContext()));
    r.put("access", ClickService.inst != null);
    call.resolve(r);
  }

  @PluginMethod
  public void openAccess(PluginCall call) {
    Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(i);
    call.resolve();
  }

  @PluginMethod
  public void showClicker(final PluginCall call) {
    getActivity().runOnUiThread(new Runnable() {
      public void run() {
        try {
          if (panel != null) { call.resolve(); return; }
          Context c = getContext().getApplicationContext();
          wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
          float d = c.getResources().getDisplayMetrics().density;
          DisplayMetrics dm = c.getResources().getDisplayMetrics();

          cross = new TextView(c);
          cross.setText("\u25ce");
          cross.setTextSize(30);
          cross.setTextColor(0xFFFFFFFF);
          cross.setGravity(Gravity.CENTER);
          GradientDrawable g1 = new GradientDrawable();
          g1.setShape(GradientDrawable.OVAL);
          g1.setColor(0x66FF3B30);
          g1.setStroke((int) (2 * d), 0xFFFFFFFF);
          cross.setBackground(g1);
          int cs = (int) (64 * d);
          crossP = lp(cs, cs);
          crossP.gravity = Gravity.TOP | Gravity.START;
          crossP.x = dm.widthPixels / 2 - cs / 2;
          crossP.y = dm.heightPixels / 2 - cs / 2;
          drag(cross, cross, crossP);
          wm.addView(cross, crossP);

          panel = new LinearLayout(c);
          panel.setOrientation(LinearLayout.HORIZONTAL);
          GradientDrawable g2 = new GradientDrawable();
          g2.setShape(GradientDrawable.RECTANGLE);
          g2.setCornerRadius(60);
          g2.setColor(0xE61C1C1E);
          panel.setBackground(g2);
          TextView handle = chip(c, "\u2630");
          TextView bBack = chip(c, "\u2190");
          bGo = chip(c, "\u25b6");
          bSp = chip(c, "\u26a1" + SPEED_L[sp]);
          bCt = chip(c, "\u00d7" + COUNT_L[ct]);
          panel.addView(handle);
          panel.addView(bBack);
          panel.addView(bGo);
          panel.addView(bSp);
          panel.addView(bCt);
          bBack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { goHome(); } });
          bGo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleClick(); } });
          bSp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            sp = (sp + 1) % SPEEDS.length;
            bSp.setText("\u26a1" + SPEED_L[sp]);
            if (clicking) { stopClick(); toggleClick(); }
          } });
          bCt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            ct = (ct + 1) % COUNTS.length;
            bCt.setText("\u00d7" + COUNT_L[ct]);
            if (clicking) { stopClick(); toggleClick(); }
          } });
          panelP = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
          panelP.gravity = Gravity.TOP | Gravity.START;
          panelP.x = (int) (12 * d);
          panelP.y = (int) (120 * d);
          drag(handle, panel, panelP);
          wm.addView(panel, panelP);
          call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
      }
    });
  }

  private void removePet() {
    try { if (petView != null && wm != null) { wm.removeView(petView); petView.destroy(); } } catch (Exception e) { }
    petView = null;
  }

  @PluginMethod
  public void showPet(final PluginCall call) {
    final String html = call.getString("html");
    getActivity().runOnUiThread(new Runnable() {
      public void run() {
        try {
          if (petView != null || html == null) { call.resolve(); return; }
          Context c = getContext().getApplicationContext();
          wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
          float d = c.getResources().getDisplayMetrics().density;
          petView = new WebView(c);
          petView.setBackgroundColor(Color.TRANSPARENT);
          petView.getSettings().setJavaScriptEnabled(true);
          petView.setVerticalScrollBarEnabled(false);
          petView.setHorizontalScrollBarEnabled(false);
          WindowManager.LayoutParams pp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (int) (100 * d),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
          pp.gravity = Gravity.BOTTOM | Gravity.START;
          pp.y = (int) (48 * d);
          wm.addView(petView, pp);
          petView.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null);
          call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
      }
    });
  }

  @PluginMethod
  public void hidePet(final PluginCall call) {
    getActivity().runOnUiThread(new Runnable() {
      public void run() { removePet(); call.resolve(); }
    });
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
              removeClicker(); removePet();
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
      public void run() { stopTimer(); removeOverlay(); removeClicker(); removePet(); call.resolve(); }
    });
  }

  private void removeOverlay() {
    try { if (overlay != null && wm != null) { wm.removeView(overlay); } } catch (Exception e) { }
    overlay = null;
  }
}
