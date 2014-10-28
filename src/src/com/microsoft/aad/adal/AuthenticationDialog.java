
package com.microsoft.aad.adal;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;

@SuppressLint({
        "InflateParams", "SetJavaScriptEnabled", "ClickableViewAccessibility"
})
class AuthenticationDialog {
    protected static final String TAG = "AuthenticationDialog";

    private Context mContext;

    private AuthenticationContext mAuthContext;

    private AuthenticationRequest mRequest;

    private Handler mHandlerInView;

    private Dialog mDialog;

    public AuthenticationDialog(Handler handler, Context context, AuthenticationContext authCtx,
            AuthenticationRequest request) {
        mHandlerInView = handler;
        mContext = context;
        mAuthContext = authCtx;
        mRequest = request;
    }

    /**
     * Create dialog using the context. Inflate the layout with inflater
     * service. This will run with the handler.
     */
    public void show() {

        mHandlerInView.post(new Runnable() {

            @Override
            public void run() {
                LayoutInflater inflater = (LayoutInflater)mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

                // using static layout
                View webviewInDialog = inflater.inflate(R.layout.dialog_authentication, null);
                final WebView webview = (WebView)webviewInDialog.findViewById(R.id.com_microsoft_aad_adal_webView1);
                if (webview == null) {
                    Logger.e(
                            TAG,
                            "Expected resource name for webview is com_microsoft_aad_adal_webView1. It is not in your layout file",
                            "", ADALError.DEVELOPER_DIALOG_LAYOUT_INVALID);
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(AuthenticationConstants.Browser.REQUEST_ID,
                            mRequest.getRequestId());
                    mAuthContext.onActivityResult(AuthenticationConstants.UIRequest.BROWSER_FLOW,
                            AuthenticationConstants.UIResponse.BROWSER_CODE_CANCEL, resultIntent);
                    if (mHandlerInView != null) {
                        mHandlerInView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mDialog != null && mDialog.isShowing()) {
                                    mDialog.dismiss();
                                }
                            }
                        });
                    }
                    return;
                }
                webview.getSettings().setJavaScriptEnabled(true);
                webview.requestFocus(View.FOCUS_DOWN);

                // Set focus to the view for touch event
                webview.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent event) {
                        int action = event.getAction();
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                            if (!view.hasFocus()) {
                                view.requestFocus();
                            }
                        }
                        return false;
                    }
                });

                webview.getSettings().setLoadWithOverviewMode(true);
                webview.getSettings().setDomStorageEnabled(true);
                webview.getSettings().setUseWideViewPort(true);
                webview.getSettings().setBuiltInZoomControls(true);

                try {
                    Oauth2 oauth = new Oauth2(mRequest);
                    final String startUrl = oauth.getCodeRequestUrl();

                    final String stopRedirect = mRequest.getRedirectUri();
                    webview.setWebViewClient(new DialogWebViewClient(stopRedirect,
                            AuthenticationConstants.UIRequest.BROWSER_FLOW, mRequest));
                    webview.post(new Runnable() {
                        @Override
                        public void run() {
                            webview.loadUrl("about:blank");
                            webview.loadUrl(startUrl);
                        }
                    });

                } catch (UnsupportedEncodingException e) {
                    Logger.e(TAG, "Encoding error", "", ADALError.ENCODING_IS_NOT_SUPPORTED, e);
                }

                builder.setView(webviewInDialog).setCancelable(true);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(AuthenticationConstants.Browser.REQUEST_ID,
                                mRequest.getRequestId());
                        mAuthContext.onActivityResult(
                                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                                AuthenticationConstants.UIResponse.BROWSER_CODE_CANCEL,
                                resultIntent);
                        if (mHandlerInView != null) {
                            mHandlerInView.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mDialog != null && mDialog.isShowing()) {
                                        mDialog.dismiss();
                                    }
                                }
                            });
                        }
                    }
                });
                mDialog = builder.create();
                mDialog.show();
            }
        });
    }

    class DialogWebViewClient extends BasicWebViewClient {

        public DialogWebViewClient(String stopRedirect, int browserFlow,
                AuthenticationRequest request) {
            super(stopRedirect, browserFlow, request);
        }

        public void showSpinner(final boolean status) {
            if (mHandlerInView != null) {
                mHandlerInView.post(new Runnable() {

                    @Override
                    public void run() {
                        if (mDialog != null && mDialog.isShowing()) {
                            ProgressBar progressBar = (ProgressBar)mDialog
                                    .findViewById(R.id.com_microsoft_aad_adal_progressBar);
                            if (progressBar != null) {
                                int showFlag = status ? View.VISIBLE : View.INVISIBLE;
                                progressBar.setVisibility(showFlag);
                            }
                        }
                    }
                });
            }
        }

        public void sendResponse(int returnCode, Intent responseIntent) {
            mAuthContext.onActivityResult(AuthenticationConstants.UIRequest.BROWSER_FLOW,
                    returnCode, responseIntent);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.toLowerCase(Locale.US).startsWith(mRedirect.toLowerCase(Locale.US))) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(AuthenticationConstants.Browser.RESPONSE_FINAL_URL, url);
                resultIntent.putExtra(AuthenticationConstants.Browser.RESPONSE_REQUEST_INFO,
                        mRequest);
                resultIntent.putExtra(AuthenticationConstants.Browser.REQUEST_ID,
                        mRequest.getRequestId());
                sendResponse(AuthenticationConstants.UIResponse.BROWSER_CODE_COMPLETE, resultIntent);
                view.stopLoading();
                mDialog.dismiss();
                return true;
            } else if (url.startsWith(AuthenticationConstants.Broker.BROWSER_EXT_PREFIX)) {
                Logger.v(TAG, "It is an external website request");
                openLinkInBrowserFromDialog(url);
                view.stopLoading();
                return true;
            }

            return false;
        }

        private void openLinkInBrowserFromDialog(final String url) {
            if (mHandlerInView != null) {
                mHandlerInView.post(new Runnable() {

                    @Override
                    public void run() {
                        String link = url.replace(
                                AuthenticationConstants.Broker.BROWSER_EXT_PREFIX, "https://");
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                        mContext.startActivity(intent);
                    }
                });
            }
        }
    }
}