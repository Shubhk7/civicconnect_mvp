# Android client — architecture notes

## Why a WebView and not a native UI

CivicConnect’s product is the static site in `civicconnect-frontend/`.
Auth (`localStorage` JWT), report photos (`<input type="file">` +
`POST /api/uploads/photo`), GPS (`navigator.geolocation`), Leaflet maps,
citizen and officer dashboards are already there. Duplicating them in
Android would fork the product.

The app is another **client**:

```
Android WebView  →  https://kashnet.online  →  https://api.kashnet.online
                                              →  civicconnect-ai (via backend)
```

## Session

`civic_token` / `civic_user` in `localStorage`. DOM storage is enabled.
Cookies are accepted first-party only. There is no `JavascriptInterface`
and no native token store.

The website now uses `viewport-fit=cover`, a bottom tab bar, and 16px
inputs so Android Chrome / WebView do not zoom form fields or hide nav
behind the gesture bar. `setTextZoom(100)` keeps system font scaling from
breaking layouts.

## File upload

`WebChromeClient.onShowFileChooser` + `FileProvider` camera extra.
The website already posts the file to the API; Android only supplies
the `Uri` to the `<input>`.

## Geolocation

`onGeolocationPermissionsShowPrompt` + runtime `ACCESS_FINE_LOCATION` /
`ACCESS_COARSE_LOCATION`. Prompt runs when the page calls the Geolocation
API (the **Use my location** button), not at process start.

Limitations: HTTPS required; accuracy is OS-dependent; denial surfaces as
the existing JS error path.

## Allow-list

In-WebView navigation is limited to CivicConnect hosts. Other http(s)
and `tel` / `mailto` / `geo` / `intent` leave the app. Subresource loads
(Leaflet on `unpkg.com`, OSM tiles) are not navigations and are not
blocked by `shouldOverrideUrlLoading`.

## Notifications

No FCM in this version. Adding it later does not require changing the
website contract.
