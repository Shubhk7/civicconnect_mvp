package com.civicconnect.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Native shell only: splash, WebView, progress, and a retryable error
 * pane. Login, dashboards, maps, and reporting stay in the website.
 * Auth remains the site's localStorage JWT — this class never stores tokens.
 */
public class MainActivity extends AppCompatActivity
        implements CivicWebViewClient.Host, CivicWebChromeClient.Host {

    private WebView webView;
    private ProgressBar progressBar;
    private View splash;
    private View errorPanel;
    private TextView errorDetail;

    private ValueCallback<Uri[]> pendingFileCallback;
    private WebChromeClient.FileChooserParams pendingFileParams;
    private Uri cameraImageUri;
    private boolean waitingForCameraPermission;

    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private boolean waitingForLocationPermission;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Uri[] uris = null;
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null && data.getData() != null) {
                                uris = new Uri[]{data.getData()};
                            } else if (data != null && data.getClipData() != null
                                    && data.getClipData().getItemCount() > 0) {
                                int n = data.getClipData().getItemCount();
                                uris = new Uri[n];
                                for (int i = 0; i < n; i++) {
                                    uris[i] = data.getClipData().getItemAt(i).getUri();
                                }
                            } else if (cameraImageUri != null) {
                                uris = new Uri[]{cameraImageUri};
                            }
                        }
                        deliverFileChooserResult(uris);
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        splash = findViewById(R.id.splash);
        errorPanel = findViewById(R.id.error_panel);
        errorDetail = findViewById(R.id.error_detail);
        Button retry = findViewById(R.id.retry_button);
        retry.setOnClickListener(v -> loadHome());

        configureWebView();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            splash.setVisibility(View.GONE);
        } else {
            loadHome();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " CivicConnectAndroid/1.0");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new CivicWebViewClient(this));
        webView.setWebChromeClient(new CivicWebChromeClient(this));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void loadHome() {
        errorPanel.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        webView.loadUrl(BuildConfig.CIVICCONNECT_BASE_URL);
    }

    @Override
    public String configuredBaseUrl() {
        return BuildConfig.CIVICCONNECT_BASE_URL;
    }

    @Override
    public void onPageLoadStarted() {
        progressBar.setVisibility(View.VISIBLE);
        errorPanel.setVisibility(View.GONE);
    }

    @Override
    public void onPageLoadFinished(boolean success) {
        progressBar.setVisibility(View.GONE);
        if (success) {
            splash.setVisibility(View.GONE);
            errorPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMainFrameError(String description) {
        splash.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        errorPanel.setVisibility(View.VISIBLE);
        errorDetail.setText(description);
    }

    @Override
    public void onProgress(int percent) {
        progressBar.setProgress(percent);
        progressBar.setVisibility(percent >= 100 ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        deliverFileChooserResult(null);
        pendingFileCallback = callback;
        pendingFileParams = params;

        if (params != null && params.isCaptureEnabled()
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            waitingForCameraPermission = true;
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
            return true;
        }
        return launchFileChooser(params);
    }

    private boolean launchFileChooser(WebChromeClient.FileChooserParams params) {
        Intent gallery = params != null ? params.createIntent() : new Intent(Intent.ACTION_GET_CONTENT);
        if (gallery.getAction() == null) {
            gallery.setAction(Intent.ACTION_GET_CONTENT);
        }
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        if (gallery.getType() == null) {
            gallery.setType("image/*");
        }

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File photo = createImageFile();
            cameraImageUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (IOException e) {
            cameraImageUri = null;
            camera = null;
        }

        Intent chooser = Intent.createChooser(gallery, getString(R.string.choose_photo));
        if (camera != null && camera.resolveActivity(getPackageManager()) != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
        }
        try {
            fileChooserLauncher.launch(chooser);
            return true;
        } catch (ActivityNotFoundException e) {
            deliverFileChooserResult(null);
            return false;
        }
    }

    private File createImageFile() throws IOException {
        String name = "civic_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            dir = getCacheDir();
        }
        return File.createTempFile(name, ".jpg", dir);
    }

    private void deliverFileChooserResult(@Nullable Uri[] uris) {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(uris);
            pendingFileCallback = null;
        }
        pendingFileParams = null;
        waitingForCameraPermission = false;
    }

    @Override
    public void onGeolocationPrompt(String origin, GeolocationPermissions.Callback callback) {
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
        }
        pendingGeoCallback = callback;
        pendingGeoOrigin = origin;

        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            finishGeolocationPrompt(true);
            return;
        }
        waitingForLocationPermission = true;
        permissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void finishGeolocationPrompt(boolean allow) {
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, allow, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
        waitingForLocationPermission = false;
    }

    private void onPermissionsResult(Map<String, Boolean> grants) {
        if (waitingForLocationPermission) {
            boolean granted = Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_FINE_LOCATION))
                    || Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_COARSE_LOCATION));
            finishGeolocationPrompt(granted);
        }
        if (waitingForCameraPermission) {
            waitingForCameraPermission = false;
            launchFileChooser(pendingFileParams);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        deliverFileChooserResult(null);
        finishGeolocationPrompt(false);
        webView.loadUrl("about:blank");
        webView.stopLoading();
        webView.setWebChromeClient(null);
        webView.setWebViewClient(null);
        webView.destroy();
        super.onDestroy();
    }
}
