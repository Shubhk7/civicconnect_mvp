package com.civicconnect.android;

import android.net.Uri;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * File inputs and browser geolocation for the existing report form.
 * No JavascriptInterface is registered.
 */
final class CivicWebChromeClient extends WebChromeClient {

    interface Host {
        void onProgress(int percent);
        boolean onFileChooser(ValueCallback<Uri[]> filePathCallback, FileChooserParams params);
        void onGeolocationPrompt(String origin, GeolocationPermissions.Callback callback);
    }

    private final Host host;

    CivicWebChromeClient(Host host) {
        this.host = host;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        host.onProgress(newProgress);
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        return host.onFileChooser(filePathCallback, fileChooserParams);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
                                                   GeolocationPermissions.Callback callback) {
        host.onGeolocationPrompt(origin, callback);
    }
}
