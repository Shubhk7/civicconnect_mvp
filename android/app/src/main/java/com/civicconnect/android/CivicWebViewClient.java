package com.civicconnect.android;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** CivicConnect pages stay in-app; maps/mailto/third-party links leave. */
final class CivicWebViewClient extends WebViewClient {

    interface Host {
        String configuredBaseUrl();
        void onPageLoadStarted();
        void onPageLoadFinished(boolean success);
        void onMainFrameError(String description);
    }

    private final Host host;
    private boolean receivedError;

    CivicWebViewClient(Host host) {
        this.host = host;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) {
            return false;
        }
        return handleUrl(view, request.getUrl().toString());
    }

    @Deprecated
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(view, url);
    }

    private boolean handleUrl(WebView view, String url) {
        String base = host.configuredBaseUrl();
        if (UrlPolicy.isAllowedNavigation(url, base)) {
            return false;
        }
        if (UrlPolicy.shouldOpenExternally(url, base)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                view.getContext().startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
                // No handler (e.g. no email app).
            }
            return true;
        }
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        receivedError = false;
        host.onPageLoadStarted();
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        host.onPageLoadFinished(!receivedError);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request != null && request.isForMainFrame()) {
            receivedError = true;
            CharSequence desc = error != null ? error.getDescription() : "Unknown error";
            host.onMainFrameError(desc != null ? desc.toString() : "Unknown error");
        }
    }

    @Deprecated
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        receivedError = true;
        host.onMainFrameError(description != null ? description : "Unknown error");
    }
}
