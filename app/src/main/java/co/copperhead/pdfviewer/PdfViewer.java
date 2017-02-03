package co.copperhead.pdfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

import java.io.InputStream;
import java.io.IOException;

public class PdfViewer extends Activity {
    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 4;
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_LEVEL = "zoomLevel";

    private WebView mWebView;
    private Uri mUri;
    private int mPage;
    private int mNumPages;
    private int mZoomLevel = 2;
    private Channel mChannel;
    private boolean mZoomInState = true;
    private boolean mZoomOutState = true;
    private InputStream mInputStream;

    private class Channel {
        @JavascriptInterface
        public int getPage() {
            return mPage;
        }

        @JavascriptInterface
        public int getZoomLevel() {
            return mZoomLevel;
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            mNumPages = numPages;
        }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview);

        mWebView = (WebView) findViewById(R.id.webview);
        WebSettings settings = mWebView.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(settings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);

        CookieManager.getInstance().setAcceptCookie(false);

        mChannel = new Channel();
        mWebView.addJavascriptInterface(mChannel, "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("GET".equals(request.getMethod()) && "https://localhost/placeholder.pdf".equals(request.getUrl().toString())) {
                    return new WebResourceResponse("application/pdf", null, mInputStream);
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });

        final Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (!"application/pdf".equals(intent.getType())) {
                throw new RuntimeException();
            }
            mUri = (Uri) intent.getData();
            mPage = 1;
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(STATE_URI);
            mPage = savedInstanceState.getInt(STATE_PAGE);
            mZoomLevel = savedInstanceState.getInt(STATE_ZOOM_LEVEL);
        }

        if (mUri != null) {
            loadPdf();
        }
    }

    private void loadPdf() {
        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
            mInputStream = getContentResolver().openInputStream(mUri);
        } catch (IOException e) {
            return;
        }
        mWebView.loadUrl("file:///android_asset/viewer.html");
    }

    private void renderPage() {
        mWebView.evaluateJavascript("onRenderPage()", null);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    private void saveMenuItemState(MenuItem item, boolean state) {
        if (item.getItemId() == R.id.action_zoom_in) {
            mZoomInState = state;
        } else {
            mZoomOutState = state;
        }
    }

    private void restoreMenuItemState(Menu menu) {
        if (menu.findItem(R.id.action_zoom_in).isEnabled() != mZoomInState) {
            menu.findItem(R.id.action_zoom_in).setEnabled(mZoomInState);
        } else if (menu.findItem(R.id.action_zoom_out).isEnabled() != mZoomOutState) {
            menu.findItem(R.id.action_zoom_out).setEnabled(mZoomOutState);
        }
    }

    private void checkDisableMenuItem(MenuItem item) {
        if (item.isEnabled()) {
            item.setEnabled(false);
            item.getIcon().setAlpha(ALPHA_LOW);
            saveMenuItemState(item, false);
        }
    }

    private void checkEnableMenuItem(MenuItem item) {
        if (!item.isEnabled()) {
            item.setEnabled(true);
            item.getIcon().setAlpha(ALPHA_HIGH);
            saveMenuItemState(item, true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mPage);
        savedInstanceState.putInt(STATE_ZOOM_LEVEL, mZoomLevel);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                mUri = resultData.getData();
                mPage = 1;
                loadPdf();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);
        restoreMenuItemState(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        switch (mZoomLevel) {
            case MAX_ZOOM_LEVEL:
                checkDisableMenuItem(menu.findItem(R.id.action_zoom_in));
                return super.onPrepareOptionsMenu(menu);
            case MIN_ZOOM_LEVEL:
                checkDisableMenuItem(menu.findItem(R.id.action_zoom_out));
                return super.onPrepareOptionsMenu(menu);
            default:
                checkEnableMenuItem(menu.findItem(R.id.action_zoom_in));
                checkEnableMenuItem(menu.findItem(R.id.action_zoom_out));
                return super.onPrepareOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                if (mPage > 1) {
                    mPage--;
                    renderPage();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_next:
                if (mPage < mNumPages) {
                    mPage++;
                    renderPage();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_out:
                if (mZoomLevel > 0) {
                    mZoomLevel--;
                    renderPage();
                    invalidateOptionsMenu();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_in:
                if (mZoomLevel < MAX_ZOOM_LEVEL) {
                    mZoomLevel++;
                    renderPage();
                    invalidateOptionsMenu();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_jump_to_page: {
                final NumberPicker picker = new NumberPicker(this);
                picker.setMinValue(1);
                picker.setMaxValue(mNumPages);
                picker.setValue(mPage);

                final FrameLayout layout = new FrameLayout(this);
                layout.addView(picker, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

                new AlertDialog.Builder(this)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            int page = picker.getValue();
                            if (page >= 1 && page <= mNumPages) {
                                mPage = page;
                                renderPage();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();

                return super.onOptionsItemSelected(item);
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
