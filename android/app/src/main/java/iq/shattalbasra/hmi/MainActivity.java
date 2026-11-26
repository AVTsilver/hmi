/* «سجل أعطال HMI» — تصميم وبرمجة م. محمد علي محمد صبيح — محطة شط البصرة الغازية © 2026
   Android shell: the whole web app is bundled inside the APK and runs offline. */
package iq.shattalbasra.hmi;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
  private static final String HOST = "https://appassets.androidplatform.net/assets/www/index.html";
  private static final int PICK = 7;
  private WebView web;
  private ValueCallback<Uri[]> pending;
  private Uri camUri;

  static String mime(String p) {
    String s = p.toLowerCase();
    if (s.endsWith(".html")) return "text/html";
    if (s.endsWith(".js") || s.endsWith(".mjs")) return "text/javascript";
    if (s.endsWith(".wasm")) return "application/wasm";
    if (s.endsWith(".css")) return "text/css";
    if (s.endsWith(".json")) return "application/json";
    if (s.endsWith(".png")) return "image/png";
    if (s.endsWith(".woff2")) return "font/woff2";
    return "text/plain";
  }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(0xff111417);
    web = new WebView(this);
    setContentView(web);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", path -> {
          try {
            InputStream in = getAssets().open(path);
            return new WebResourceResponse(mime(path), "utf-8", in);
          } catch (Exception e) { return null; }
        }).build();
    web.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
        return loader.shouldInterceptRequest(r.getUrl());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        if ("appassets.androidplatform.net".equals(u.getHost())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
        return true;
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (pending != null) pending.onReceiveValue(null);
        pending = cb;
        camUri = null;
        Intent cam = null;
        try {
          File dir = new File(getCacheDir(), "cam"); dir.mkdirs();
          File f = new File(dir, "hmi_" + System.currentTimeMillis() + ".jpg");
          camUri = FileProvider.getUriForFile(MainActivity.this, "iq.shattalbasra.hmi.files", f);
          cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
          cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
          cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) { cam = null; }
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
        Intent go;
        if (p.isCaptureEnabled() && cam != null) go = cam;
        else {
          go = Intent.createChooser(pick, "اختر صورة الشاشة");
          if (cam != null) go.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
        }
        try { startActivityForResult(go, PICK); }
        catch (Exception e) { pending.onReceiveValue(null); pending = null; return false; }
        return true;
      }
    });
    web.addJavascriptInterface(new Bridge(), "HMIAndroid");
    if (b != null) web.restoreState(b); else web.loadUrl(HOST);
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req != PICK || pending == null) return;
    ArrayList<Uri> out = new ArrayList<>();
    if (res == RESULT_OK) {
      if (data != null && data.getClipData() != null)
        for (int i = 0; i < data.getClipData().getItemCount(); i++) out.add(data.getClipData().getItemAt(i).getUri());
      else if (data != null && data.getData() != null) out.add(data.getData());
      else if (camUri != null) out.add(camUri);
    }
    pending.onReceiveValue(out.isEmpty() ? null : out.toArray(new Uri[0]));
    pending = null;
  }

  @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

  @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

  /* the report (PDF / HTML) is handed over from the page and saved in Downloads */
  class Bridge {
    @JavascriptInterface public String save(String name, String b64, String type) {
      try {
        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 29) {
          ContentValues cv = new ContentValues();
          cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
          cv.put(MediaStore.Downloads.MIME_TYPE, type);
          cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HMI");
          uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
          try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
        } else {
          File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS); dir.mkdirs();
          File f = new File(dir, name);
          try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
          uri = FileProvider.getUriForFile(MainActivity.this, "iq.shattalbasra.hmi.files", f);
        }
        final Uri u = uri;
        runOnUiThread(() -> {
          Toast.makeText(MainActivity.this, "حُفظ في التنزيلات/HMI: " + name, Toast.LENGTH_LONG).show();
          try {
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(u, type);
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(v, "فتح الملف"));
          } catch (Exception e) { }
        });
        return "ok";
      } catch (Exception e) { return "error: " + e.getMessage(); }
    }
  }
}
