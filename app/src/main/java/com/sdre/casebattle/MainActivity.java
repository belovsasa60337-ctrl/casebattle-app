package com.sdre.casebattle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final String HOME = "file:///android_asset/index.html";
    static final String RETURN = "https://belovsasa60337-ctrl.github.io/casebattle-app/steam-return";
    static final String REALM = "https://belovsasa60337-ctrl.github.io/";
    private WebView web, steam;
    private LinearLayout overlay;
    private ProgressBar bar;
    private TextView err;
    private boolean steamLoaded = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ValueCallback<Uri[]> fileCb;
    private String pendingJs = null;
    private static final int PICK = 1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0a0c13"));
        getWindow().setNavigationBarColor(Color.parseColor("#0a0c13"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0a0c13"));
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(buildOverlay(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return route(r.getUrl().toString());
            }
            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                if (url.startsWith(RETURN)) { v.stopLoading(); handleReturn(url); }
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (url.startsWith(HOME) && pendingJs != null) {
                    v.evaluateJavascript(pendingJs, null);
                    pendingJs = null;
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try { startActivityForResult(Intent.createChooser(i, "Аватар"), PICK); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });

        if (b != null) web.restoreState(b);
        else web.loadUrl(HOME);
    }

    /** true = мы сами обработали ссылку */
    private boolean route(String url) {
        if (url.startsWith(RETURN)) { handleReturn(url); return true; }
        Uri u = Uri.parse(url);
        String scheme = u.getScheme(), host = u.getHost() == null ? "" : u.getHost();
        if ("file".equals(scheme)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
        return true;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private View buildOverlay() {
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.parseColor("#0a0c13"));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(10), dp(8), dp(10));
        top.setBackgroundColor(Color.parseColor("#1b2838"));
        TextView t = new TextView(this);
        t.setText("Вход через Steam");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(this);
        close.setText("Закрыть");
        close.setOnClickListener(v -> closeSteam(true));
        top.addView(close);
        overlay.addView(top, new LinearLayout.LayoutParams(-1, -2));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        overlay.addView(bar, new LinearLayout.LayoutParams(-1, dp(4)));

        err = new TextView(this);
        err.setTextColor(Color.parseColor("#c7d5e0"));
        err.setTextSize(15);
        err.setPadding(dp(20), dp(16), dp(20), dp(16));
        err.setBackgroundColor(Color.parseColor("#2a1a1f"));
        err.setVisibility(View.GONE);
        err.setOnClickListener(v -> startSteam());
        overlay.addView(err, new LinearLayout.LayoutParams(-1, -2));

        steam = new WebView(this);
        steam.setBackgroundColor(Color.parseColor("#171a21"));
        WebSettings s = steam.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(steam, true);
        steam.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith(RETURN)) { handleReturn(u); return true; }
                return false;
            }
            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                if (url.startsWith(RETURN)) { v.stopLoading(); handleReturn(url); }
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (url.contains("steamcommunity.com") || url.contains("steampowered.com")) {
                    steamLoaded = true; bar.setVisibility(View.GONE); err.setVisibility(View.GONE);
                }
            }
            @Override
            public void onReceivedError(WebView v, android.webkit.WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) showErr("Не удалось открыть Steam (" + e.getDescription() + ").");
            }
        });
        steam.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (p > 5) { bar.setIndeterminate(false); bar.setProgress(p); }
            }
        });
        overlay.addView(steam, new LinearLayout.LayoutParams(-1, 0, 1));
        return overlay;
    }

    private void showErr(String why) {
        bar.setVisibility(View.GONE);
        err.setText(why + "\n\nСайт steamcommunity.com у некоторых провайдеров в России открывается очень медленно или не открывается. Попробуй переключиться между Wi‑Fi и мобильным интернетом или включить VPN.\n\nНажми сюда, чтобы попробовать снова.");
        err.setVisibility(View.VISIBLE);
    }

    private void startSteam() {
        steamLoaded = false;
        err.setVisibility(View.GONE);
        bar.setVisibility(View.VISIBLE); bar.setIndeterminate(true);
        overlay.setVisibility(View.VISIBLE);
        Uri.Builder q = Uri.parse("https://steamcommunity.com/openid/login").buildUpon()
            .appendQueryParameter("openid.ns", "http://specs.openid.net/auth/2.0")
            .appendQueryParameter("openid.mode", "checkid_setup")
            .appendQueryParameter("openid.return_to", RETURN)
            .appendQueryParameter("openid.realm", REALM)
            .appendQueryParameter("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
            .appendQueryParameter("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
        steam.stopLoading();
        steam.loadUrl(q.build().toString());
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(() -> { if (!steamLoaded && overlay.getVisibility() == View.VISIBLE) showErr("Steam не отвечает уже 20 секунд."); }, 20000);
    }

    private void closeSteam(boolean cancelled) {
        ui.removeCallbacksAndMessages(null);
        overlay.setVisibility(View.GONE);
        steam.stopLoading();
        steam.loadUrl("about:blank");
        if (cancelled) web.evaluateJavascript("window.steamFail&&steamFail('cancel')", null);
    }

    private void handleReturn(final String url) {
        closeSteam(false);
        web.evaluateJavascript("window.steamWait&&steamWait()", null);
        new Thread(() -> {
            String js;
            try {
                Uri u = Uri.parse(url);
                if (!"id_res".equals(u.getQueryParameter("openid.mode"))) throw new Exception("cancel");
                String claimed = u.getQueryParameter("openid.claimed_id");
                Matcher m = Pattern.compile("^https://steamcommunity\\.com/openid/id/(\\d{17})$").matcher(claimed == null ? "" : claimed);
                if (!m.find()) throw new Exception("bad id");
                String id = m.group(1);

                // Проверка подписи у самого Steam
                StringBuilder body = new StringBuilder();
                for (String k : u.getQueryParameterNames()) {
                    if (!k.startsWith("openid.")) continue;
                    String val = k.equals("openid.mode") ? "check_authentication" : u.getQueryParameter(k);
                    if (body.length() > 0) body.append('&');
                    body.append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(val, "UTF-8"));
                }
                String res = http("https://steamcommunity.com/openid/login", body.toString());
                if (!res.contains("is_valid:true")) throw new Exception("invalid");

                // Ник и аватар из публичного профиля
                String name = "Steam_" + id.substring(12), avatar = "";
                try {
                    String xml = http("https://steamcommunity.com/profiles/" + id + "/?xml=1", null);
                    Matcher n = Pattern.compile("<steamID><!\\[CDATA\\[(.*?)\\]\\]></steamID>", Pattern.DOTALL).matcher(xml);
                    if (n.find()) name = n.group(1);
                    Matcher a = Pattern.compile("<avatarFull><!\\[CDATA\\[(.*?)\\]\\]></avatarFull>", Pattern.DOTALL).matcher(xml);
                    if (a.find()) avatar = a.group(1);
                } catch (Exception ignored) {}

                JSONObject o = new JSONObject();
                o.put("id", id); o.put("name", name); o.put("avatar", avatar);
                js = "window.steamDone&&steamDone(" + o.toString() + ")";
            } catch (Exception e) {
                js = "window.steamFail&&steamFail(" + JSONObject.quote(String.valueOf(e.getMessage())) + ")";
            }
            final String fjs = js;
            runOnUiThread(() -> {
                web.evaluateJavascript(fjs, null);
            });
        }).start();
    }

    private static String http(String url, String post) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CaseBattleApp");
        if (post != null) {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = c.getOutputStream()) { os.write(post.getBytes("UTF-8")); }
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line; while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    class Bridge {
        @JavascriptInterface
        public void steamLogin() { runOnUiThread(() -> startSteam()); }
        @JavascriptInterface
        public boolean isApp() { return true; }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK && fileCb != null) {
            Uri[] r = null;
            if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{ data.getData() };
            fileCb.onReceiveValue(r);
            fileCb = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            if (steam.canGoBack()) steam.goBack(); else closeSteam(true);
            return;
        }
        String u = web.getUrl();
        if (u != null && !u.startsWith(HOME)) { web.loadUrl(HOME); return; }
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
