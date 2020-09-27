package com.breadwallet.presenter.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.breadwallet.BreadApp;
import com.breadwallet.R;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.customviews.LoadingDialog;
import com.breadwallet.presenter.entities.CryptoRequest;
import com.breadwallet.presenter.fragments.FragmentSendCallback;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.manager.SendManager;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.util.CryptoUriParser;
import com.breadwallet.wallet.wallets.ethereum.WalletEthManager;
import com.breadwallet.wallet.wallets.side.ElaSideEthereumWalletManager;
import com.elastos.jni.utils.SchemeStringUtils;
import com.platform.APIClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class ExploreWebActivity extends BRActivity {
    private final String TAG = ExploreWebActivity.class.getName();

    private WebView webView;
    private LoadingDialog mLoadingDialog;
    private BaseTextView mTitleTv;
    private BaseTextView mBackTv;
    private View mMenuLayout;
    private BaseTextView mShare;
    private BaseTextView mCopyURL;
    private BaseTextView mReload;
    private BaseTextView mSwitchNetwork;
    private BaseTextView mBookmark;
    private BaseTextView mCancelTv;
    private boolean isFavorited = false;
    private boolean isLikeToFavorite = false;
    private String mAppId;
    private String webTitle = "";
    private String webURL = "";
    private WebviewScriptConfig config;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expolre_web_layout);

//        String url = getIntent().getStringExtra("explore_url");
//        mAppId = getIntent().getStringExtra("app_id");
        webURL = getIntent().getStringExtra("explore_url");
        mAppId = getIntent().getStringExtra("app_id");

        initView();
        initListener();
    }

    @Override
    protected void onStart() {
        super.onStart();

        webView.loadUrl(webURL);
        checkFavoriteStatus(webURL);
    }

    private String loadJavascriptFile(){
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            inputStream = ExploreWebActivity.this.getAssets().open("www/trust-min-web3-1.2.9.js");
            outputStream = new ByteArrayOutputStream();
            int len = 0;
            byte[] buffer = new byte[2048];
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void initView(){
        webView = findViewById(R.id.web_view);
        mTitleTv = findViewById(R.id.explore_web_title);
        mBackTv = findViewById(R.id.explore_web_back);
        mMenuLayout = findViewById(R.id.explore_web_about_layout);
        mShare = findViewById(R.id.explore_web_share);
        mReload = findViewById(R.id.explore_web_reload);
        mCopyURL = findViewById(R.id.explore_web_copyurl);
        mSwitchNetwork = findViewById(R.id.explore_web_switch);
        mBookmark = findViewById(R.id.explore_web_bookmark);
        mCancelTv = findViewById(R.id.explore_web_cancle);
        webviewSetting();

        mLoadingDialog = new LoadingDialog(this, R.style.progressDialog);
        mLoadingDialog.setCanceledOnTouchOutside(false);

        if(WebviewScriptConfig.getInstance(this).network == WebviewScriptConfig.networkConnectByDapp.ethereumSideChain){
            mSwitchNetwork.setText(R.string.multi_wallet_switch + " - Ethereum");
        }else{
            mSwitchNetwork.setText(R.string.multi_wallet_switch + " - ELA Side Chain");
        }
    }

    private void checkFavoriteStatus(String url) {
        SharedPreferences sp = getSharedPreferences("MyPublicPrefsFile", Context.MODE_PRIVATE);
        Set<String> favorites = sp.getStringSet("favorites", null);

        if(favorites!=null && favorites.size()>0){
            String[] favoriteData;
            for (String favorite : favorites) {
                favoriteData = favorite.split("<\\|>");
                String favoritedURL = favoriteData[1];
                if (url.contains(favoritedURL) || favoritedURL.contains(url)) {
                    isFavorited = true;
                    isLikeToFavorite = true;
                    mBookmark.setText(R.string.disFavoriteIt);
                    return;
                }
            }
        }
    }

    private void initListener(){
        findViewById(R.id.explore_web_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.VISIBLE);
            }
        });

//        findViewById(R.id.explore_web_finish).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                finish();
//            }
//        });
        findViewById(R.id.explore_web_finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLikeToFavorite != isFavorited) {
                    String tempString;
                    SharedPreferences sp = getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE);
                    Set<String> favorites = sp.getStringSet("favorites", new HashSet<String>());

                    if (isLikeToFavorite) {
                        tempString = !webTitle.equals("") ? webTitle : webURL;
                        tempString += "<|>" + webURL;

                        Uri tempUri = Uri.parse(webURL);
                        tempString += "<|>" + tempUri.getScheme() + "://" + tempUri.getAuthority() + "/favicon.ico";

                        favorites.add(tempString);
                    }else{
                        if(favorites != null && favorites.size() > 0){
                            for (String favorite : favorites) {
                                if (webURL.contains(favorite.split("<\\|>")[1])) {
                                    favorites.remove(favorite);
                                    break;
                                }
                            }
                        }
                    }

                    SharedPreferences.Editor editor = sp.edit();
                    editor.putStringSet("favorites",favorites).commit();

                    ((BreadApp)ExploreWebActivity.this.getApplication()).isBookmarkChanged = true;
                }

                finish();
            }
        });

        mBackTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(webView.canGoBack()) webView.goBack();
            }
        });

        mMenuLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });

        mCancelTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.GONE);
            }
        });

        mShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.GONE);

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, ExploreWebActivity.this.webURL);
                shareIntent = Intent.createChooser(shareIntent, "");
                startActivity(shareIntent);
////                Intent intent = new Intent(ExploreWebActivity.this, AppAboutActivity.class);
////                startActivity(intent);
//                if(!StringUtil.isNullOrEmpty(mAppId)){
//                    UiUtils.startMiniAppAboutActivity(ExploreWebActivity.this, mAppId);
//                }
            }
        });

        mCopyURL.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.GONE);

                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText(null, ExploreWebActivity.this.webURL);
                clipboard.setPrimaryClip(clipData);
            }
        });

        mReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.GONE);

                ExploreWebActivity.this.webView.reload();
            }
        });

        mSwitchNetwork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.GONE);

                Context tempContext = ExploreWebActivity.this;
                if(WebviewScriptConfig.getInstance(tempContext).network == WebviewScriptConfig.networkConnectByDapp.ethereumSideChain){
                    WebviewScriptConfig.getInstance(tempContext).switchNetwork(WebviewScriptConfig.networkConnectByDapp.ethereum);
                    Toast.makeText(tempContext, R.string.multi_wallet_switch + " - Ethereum", Toast.LENGTH_SHORT).show();
                    mSwitchNetwork.setText(R.string.multi_wallet_switch + " - ELA Side Chain");
                }else{
                    WebviewScriptConfig.getInstance(tempContext).switchNetwork(WebviewScriptConfig.networkConnectByDapp.ethereumSideChain);
                    Toast.makeText(tempContext, R.string.multi_wallet_switch + " - ELA Side Chain", Toast.LENGTH_SHORT).show();
                    mSwitchNetwork.setText(R.string.multi_wallet_switch + " - Ethereum");
                }
            }
        });

        mBookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMenuLayout.setVisibility(View.GONE);

                isLikeToFavorite = !isLikeToFavorite;
                if (isLikeToFavorite) {
                    Toast.makeText(ExploreWebActivity.this, R.string.favoriteIt, Toast.LENGTH_SHORT).show();
                    mBookmark.setText(R.string.disFavoriteIt);
                } else {
                    Toast.makeText(ExploreWebActivity.this, R.string.removeFromFavorites, Toast.LENGTH_SHORT).show();
                    mBookmark.setText(R.string.favoriteIt);
                }
            }
        });
    }

    private void webviewSetting() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBlockNetworkImage(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadsImagesAutomatically(true);

        WebView.setWebContentsDebuggingEnabled(true);
        webView.addJavascriptInterface(this, "JsBridgeAndroid");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(StringUtil.isNullOrEmpty(url)) return true;
                loadUrl(url);
                Log.d(TAG, "shouldOverrideUrlLoading:"+url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                Log.d(TAG, "onPageStarted:"+url);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!isFinishing() && !mLoadingDialog.isShowing()){
                            mLoadingDialog.show();
                        }
                    }
                });
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                Log.i("schemeLoadurl", "url:"+url);

                webTitle = view.getTitle();

                WebBackForwardList mWebBackForwardList = webView.copyBackForwardList();
                int account = mWebBackForwardList.getCurrentIndex();
                if(url.contains("redpacket")){
                    mTitleTv.setText(getResources().getString(R.string.redpackage_title));
                    mBackTv.setVisibility((account>1)?View.VISIBLE:View.GONE);
                } else {
                    mTitleTv.setText(webTitle);
                    mBackTv.setVisibility((account>0)?View.VISIBLE:View.GONE);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!isFinishing() && mLoadingDialog.isShowing()){
                            mLoadingDialog.dismiss();
                        }
                    }
                });
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);

                if (Build.VERSION.SDK_INT >= 19) {
                    config = WebviewScriptConfig.getInstance(ExploreWebActivity.this);
                    final String jsString = loadJavascriptFile();
                    webView.evaluateJavascript(jsString, new ValueCallback<String>() {
                        @Override public void onReceiveValue(String value) {
                            final String js = "(function(){" +
                                    "var config = {" +
                                    "address: '"+ config.address +"'," +
                                    "chainId: "+ config.chainId +"," +
                                    "rpcUrl: '"+ config.rpcUrl +"'" +
                                    "};" +
                                    "const provider = new window.Trust(config);" +
                                    "window.ethereum = provider;" +
                                    "window.web3 = new window.Web3(window.ethereum);" +
                                    "window.web3.eth.defaultAccount = config.address;" +
                                    "window.chrome = {webstore: {}};" +
                                    "})();";
                            //"if(window.onload){var func=window.onload;func();}";
                            webView.evaluateJavascript(js, new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    Log.i(TAG, "onReceiveValue: Javascript全部加载完成了。");
                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }
        });
    }

    @JavascriptInterface
    public void postMessage(String value){
        Log.d(TAG, "postMessage: 从网页上调用了postMesage!" + value);

        try {
            final JSONObject json = new JSONObject(value);
            final long id = json.getLong("id");
            final JSONObject object = json.getJSONObject("object");
            final String rpc = json.getString("name");

            switch (rpc){
                case "requestAccounts":
                    Log.d(TAG, "postMessage: 从网页上调用了requestAccounts!");

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("");
                    builder.setMessage("This Website would like to connect your account");
                    builder.setCancelable(true);
                    builder.setPositiveButton(R.string.disclaim_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String js = "javascript:window.ethereum.setAddress('"+ config.address +"');" +
                                            "window.ethereum.sendResponse("+id+", ['"+config.address+"'])";
                                    webView.evaluateJavascript(js, new ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String value) {
                                            //
                                        }
                                    });
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(R.string.explore_menu_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    webView.evaluateJavascript("javascript:window.ethereum.sendError("+ id +",'Canceled');", new ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String value) {
                                            //
                                        }
                                    });
                                }
                            });
                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();

                    break;

                case "signTransaction":
                    final String to = object.getString("to");
//                    final String gasPrice = object.getString("gasPrice");
//                    final String gas = object.getString("gas");
                    String amount = object.getString("value") != null ? object.getString("value") : "0x0";
                    final String hexData = object.getString("data");
                    final BigDecimal amountValue = new BigDecimal(Long.decode(amount));

                    final CryptoRequest request = new CryptoRequest(null, false, hexData, to, amountValue);
                    UiUtils.showSendFragmentWithCallback((FragmentActivity) ExploreWebActivity.this, request, new FragmentSendCallback() {
                        @Override
                        public void onSent(String txid) {
                            webView.evaluateJavascript("javascript:window.ethereum.sendResponse(" + id + ",'"+ txid +"')", new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    //
                                }
                            });
                        }

                        @Override
                        public void onCancel() {
                            webView.evaluateJavascript("javascript:window.ethereum.sendResponse(" + id + ",'cancel')", new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    //
                                }
                            });
                        }
                    });
                    break;

                case "signPersonalMessage":
//                    final String data = object.getString("data");
//                    final String signed = APIClient.getInstance(ExploreWebActivity.this).signRequest(data);
//                    webView.evaluateJavascript("javascript:window.ethereum.sendResponse(" + id + ",'" + signed + "')", new ValueCallback<String>() {
//                        @Override
//                        public void onReceiveValue(String value) {
//                            //
//                        }
//                    });
                    break;

                case "ecRecover":
//                    if let object = json["object"] as? AnyObject{
//                    guard let message = object["message"] as? String,
//                            let signature = object["signature"] as? String else {
//                        return
//                    }
//
//                    let walletManager = AuthModel.ShareAuth.walletManagers![Currencies.eth.code] as? EthWalletManager
//                    walletManager?.ecRrcover(message: message, signature: signature){result in
//                        self.wkweb.evaluateJavaScript("window.ethereum.sendResponse(\(id), \"\(result.withHexPrefix)\")", completionHandler: nil)
//                    }
//                }
                    break;

                default:
                    Log.i(TAG, "postMessage: RPC接口：" + rpc);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){
            WebBackForwardList mWebBackForwardList = webView.copyBackForwardList();
            int account = mWebBackForwardList.getCurrentIndex();
            if(account > 0){
                webView.goBack();
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private synchronized void loadUrl(String url){
        Log.d("schemeLoadurl", "url:"+url);
        if(StringUtil.isNullOrEmpty(url)) return;

        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme != null && scheme.equals("elaphant") && host != null) {
            switch (host) {
                case "multitx":
                    UiUtils.startMultiTxActivity(this, uri);
                    return;
                case "multicreate":
                    UiUtils.startMultiCreateActivity(this, uri);
                    return;
                default:
                    break;
            }
        }

        if(url.contains("elaphant") && url.contains("identity")) {
            UiUtils.startAuthorActivity(ExploreWebActivity.this, url);
            finish();
        } else if(url.contains("elaphant") && url.contains("elapay")) {
            UiUtils.startWalletActivity(ExploreWebActivity.this, url);
            finish();
        } else if(url.contains("elaphant") && url.contains("eladposvote")) {
            UiUtils.startCrcActivity(ExploreWebActivity.this, url);
            finish();
        } else if(url.contains("elaphant") && url.contains("sign")) {
            UiUtils.startSignActivity(ExploreWebActivity.this, url);
            finish();
        } else if(url.contains("elaphant") && url.contains("elacrcvote")) {
            UiUtils.startCrcActivity(ExploreWebActivity.this, url);
            finish();
        }else if(mHomeActivity!=null && SchemeStringUtils.isElaphantPrefix(url)) {
            mHomeActivity.showAndDownloadCapsule(url);
            finish();
        } else {
            webView.loadUrl(url);
        }
    }

    @Override
    protected void onDestroy() {
        webView.removeAllViews();
        webView.destroy();

        super.onDestroy();

        if(mLoadingDialog != null){
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
    }
}

