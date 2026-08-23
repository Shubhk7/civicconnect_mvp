package com.civicconnect.android;

import android.net.Uri;

import java.util.Locale;

/**
 * Which URLs may stay inside the WebView. The site talks to the API with
 * fetch() (so those requests are not navigations); this only gates what
 * the WebView itself loads as a page.
 */
final class UrlPolicy {

    private UrlPolicy() {}

    static boolean isAllowedNavigation(String url, String configuredBaseUrl) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.US);
        if ("about".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme)) {
            return true;
        }
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        if (isCivicHost(host)) {
            return true;
        }
        String configuredHost = Uri.parse(configuredBaseUrl).getHost();
        return configuredHost != null && host.equals(configuredHost.toLowerCase(Locale.US));
    }

    static boolean isCivicHost(String host) {
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        return host.equals("kashnet.online")
                || host.endsWith(".kashnet.online")
                || host.equals("civicconnect-mvp.pages.dev");
    }

    static boolean shouldOpenExternally(String url, String configuredBaseUrl) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.US);
        if ("tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme)
                || "geo".equals(scheme) || "intent".equals(scheme) || "market".equals(scheme)) {
            return true;
        }
        return ("http".equals(scheme) || "https".equals(scheme))
                && !isAllowedNavigation(url, configuredBaseUrl);
    }
}
