package com.breadwallet.presenter.activities.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.webkit.ValueCallback;

import com.breadwallet.BreadApp;
import com.breadwallet.R;
import com.breadwallet.presenter.activities.DisabledActivity;
import com.breadwallet.presenter.activities.ExploreWebActivity;
import com.breadwallet.presenter.activities.HomeActivity;
import com.breadwallet.presenter.activities.InputPinActivity;
import com.breadwallet.presenter.activities.InputWordsActivity;
import com.breadwallet.presenter.activities.WalletActivity;
import com.breadwallet.presenter.activities.WalletNameActivity;
import com.breadwallet.presenter.activities.WebviewScriptConfig;
import com.breadwallet.presenter.activities.intro.IntroActivity;
import com.breadwallet.presenter.activities.intro.RecoverActivity;
import com.breadwallet.presenter.activities.intro.WriteDownActivity;
import com.breadwallet.presenter.entities.CryptoRequest;
import com.breadwallet.presenter.fragments.FragmentSendCallback;
import com.breadwallet.tools.animation.BRDialog;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.manager.BRApiManager;
import com.breadwallet.tools.manager.BRPublicSharedPrefs;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.manager.InternetManager;
import com.breadwallet.tools.manager.SendManager;
import com.breadwallet.tools.security.AuthManager;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.security.PostAuth;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.util.CryptoUriParser;
import com.breadwallet.wallet.wallets.ethereum.WalletEthManager;
import com.breadwallet.wallet.wallets.side.ElaSideEthereumWalletManager;
import com.elastos.jni.UriFactory;
import com.elastos.jni.utils.SchemeStringUtils;
import com.platform.HTTPServer;
import com.platform.tools.BRBitId;

import java.math.BigDecimal;
import java.net.URL;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan on <mihail@breadwallet.com> 5/23/17.
 * Copyright (c) 2017 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class BRActivity extends FragmentActivity implements BreadApp.OnAppBackgrounded {
    private static final String TAG = BRActivity.class.getName();
    public static final Point screenParametersPoint = new Point();
    private static final String PACKAGE_NAME = BreadApp.getBreadContext() == null ? null : BreadApp.getBreadContext().getApplicationContext().getPackageName();
    protected static HomeActivity mHomeActivity;

    static {
        try {
            System.loadLibrary(BRConstants.NATIVE_LIB_NAME);
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            Log.d(TAG, "Native code library failed to load.\\n\" + " + e);
            Log.d(TAG, "Installer Package Name -> " + (PACKAGE_NAME == null ? "null" : BreadApp.getBreadContext().getPackageManager().getInstallerPackageName(PACKAGE_NAME)));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        BreadApp.activityCounter.decrementAndGet();
        BreadApp.onStop(this);

        //open back to HomeActivity if needed
        if (this instanceof WalletActivity)
            BRSharedPrefs.putAppBackgroundedFromHome(this, false);
        else if (this instanceof HomeActivity)
            BRSharedPrefs.putAppBackgroundedFromHome(this, true);

    }

    @Override
    protected void onResume() {
        init(this);
        super.onResume();
        BreadApp.backgroundedTime = 0;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BRConstants.CAMERA_REQUEST_ID) {
            // BEGIN_INCLUDE(permission_result)
            // Received permission result for camera permission.
            Log.i(TAG, "Received response for CAMERA_REQUEST_ID permission request.");

            // Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission has been granted, preview can be displayed
                Log.i(TAG, "CAMERA permission has now been granted. Showing preview.");
                UiUtils.openScanner(this, BRConstants.SCANNER_REQUEST);
            } else {
                Log.i(TAG, "CAMERA permission was NOT granted.");
                BRDialog.showSimpleDialog(this, getString(R.string.Send_cameraUnavailabeTitle_android), getString(R.string.Send_cameraUnavailabeMessage_android));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        // 123 is the qrCode result
        switch (requestCode) {

            case BRConstants.PAY_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPublishTxAuth(BRActivity.this, null, true, null);
                        }
                    });
                }
                break;
            case BRConstants.REQUEST_PHRASE_BITID:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onBitIDAuth(BRActivity.this, true);
                        }
                    });

                }
                break;

            case BRConstants.PAYMENT_PROTOCOL_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPaymentProtocolRequest(BRActivity.this, true);
                        }
                    });

                }
                break;

            case BRConstants.CANARY_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onCanaryCheck(BRActivity.this, true);
                        }
                    });
                } else {
                    finish();
                }
                break;

            case BRConstants.SHOW_PHRASE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPhraseCheckAuth(BRActivity.this, true, false);
                        }
                    });
                }
                break;
            case BRConstants.PROVE_PHRASE_REQUEST:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onPhraseProveAuth(BRActivity.this, true, false);
                        }
                    });
                }
                break;
            case BRConstants.PUT_PHRASE_RECOVERY_WALLET_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    final boolean restart = BRPublicSharedPrefs.getRecoverNeedRestart(BRActivity.this);
                    final boolean recover = BRPublicSharedPrefs.getIsRecover(BRActivity.this);
                    final String walletName = BRPublicSharedPrefs.getRecoverWalletName(BRActivity.this);
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onRecoverWalletAuth(BRActivity.this,
                                    true, restart, recover,
                                    StringUtil.isNullOrEmpty(walletName) ? UiUtils.getDefaultWalletName(BRActivity.this) : walletName);
                        }
                    });
                } else {
                    finish();
                }
                break;

            case BRConstants.SCANNER_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            String result = data.getStringExtra("result");
                            String type = data.getStringExtra("type");
                            if(!StringUtil.isNullOrEmpty(type)) {
                                if(StringUtil.isNullOrEmpty(result)) return;
                                if(type.equals(BRConstants.CHAT_SINGLE_TYPE)) {
                                    mHomeActivity.showChatFragment(result);
                                } else {
                                    UiUtils.startGroupNameActivity(BRActivity.this, result);
                                }
                            } else if (CryptoUriParser.isCryptoUrl(BRActivity.this, result))
                                CryptoUriParser.processRequest(BRActivity.this, result,
                                        WalletsMaster.getInstance(BRActivity.this).getCurrentWallet(BRActivity.this));
                            else if (BRBitId.isBitId(result))
                                BRBitId.signBitID(BRActivity.this, result, null);
                            else
                                Log.e(TAG, "onActivityResult: not bitcoin address NOR bitID");
                        }
                    });

                }
                break;

            case BRConstants.PUT_PHRASE_NEW_WALLET_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            PostAuth.getInstance().onCreateWalletAuth(BRActivity.this,
                                    true, false, UiUtils.getDefaultWalletName(BRActivity.this));
                        }
                    });

                } else {
                    Log.e(TAG, "WARNING: resultCode != RESULT_OK");
                    WalletsMaster m = WalletsMaster.getInstance(BRActivity.this);
                    m.wipeWalletButKeystore(this);
                    finish();
                }
                break;
            case InputPinActivity.SET_PIN_REQUEST_CODE:
                if (data != null) {
                    boolean isPinAccepted = data.getBooleanExtra(InputPinActivity.EXTRA_PIN_ACCEPTED, false);
                    if (isPinAccepted) {
                        if (Utils.isNullOrEmpty(BRKeyStore.getMasterPublicKey(this))) {
                            UiUtils.startWalletNameActivity(this, WalletNameActivity.WALLET_NAME_TYPE_NEW, false);
                        } else {
                            UiUtils.startBreadActivity(this, false);
                        }

                    }

                }
                break;

            case BRConstants.SCANNER_DID_OR_ADD_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    String url = data.getStringExtra("result");
                    if(StringUtil.isNullOrEmpty(url)) return;

                    final UriFactory uri = new UriFactory(url);
                    String scheme = uri.getScheme();
                    String host = uri.getHost();
                    if (!SchemeStringUtils.isNullOrEmpty(scheme) && !SchemeStringUtils.isNullOrEmpty(host)) {
                        if(scheme.equals("elaphant") || scheme.equals("elastos")) {
                            switch (host) {
                                case "multitx":
                                    UiUtils.startMultiTxActivity(this, Uri.parse(url));
                                    return;
                                case "multicreate":
                                    UiUtils.startMultiCreateActivity(this, Uri.parse(url));
                                    return;

                                case "identity":
                                case "ethsign":
                                    UiUtils.startAuthorActivity(this, url);
                                    return;

                                case "calleth":
                                case "elapay":
                                    UiUtils.startWalletActivity(this, url);
                                    return;

                                case "sign":
                                    UiUtils.startSignActivity(this, url);
                                    return;
                                case "eladposvote":
                                case "elacrcvote":
                                    UiUtils.startCrcActivity(this, url);
                                    return;

                                default:
                                    if(mHomeActivity != null) {
                                        mHomeActivity.showAndDownloadCapsule(url);
                                    } else {
                                        UiUtils.startBreadActivity(this, true);
                                    }
                                    break;
                            }
                        } else {
                            mHomeActivity.showAndDownloadCapsule(url);
                        }
                    } else {
                        mHomeActivity.showAndDownloadCapsule(url);
                    }
                }
                break;

        }
    }

    public void init(Activity app) {
        //set status bar color
//        ActivityUTILS.setStatusBarColor(app, android.R.color.transparent);
        InternetManager.getInstance();
        if (!(app instanceof IntroActivity || app instanceof RecoverActivity || app instanceof WriteDownActivity))
            BRApiManager.getInstance().startTimer(app);
        //show wallet locked if it is and we're not in an illegal activity
        if (!(app instanceof InputPinActivity || app instanceof InputWordsActivity)) {
            if (AuthManager.getInstance().isWalletDisabled(app)) {
                AuthManager.getInstance().setWalletDisabled(app);
            }
        }
        BreadApp.setBreadContext(app);


        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                if (!HTTPServer.isStarted()) {
                    HTTPServer.startServer();
                    BreadApp.addOnBackgroundedListener(BRActivity.this);
                }
            }
        });
        lockIfNeeded(this);
    }

    private void lockIfNeeded(Activity app) {
        long start = System.currentTimeMillis();
        //lock wallet if 3 minutes passed
        if (BreadApp.backgroundedTime != 0
                && ((System.currentTimeMillis() - BreadApp.backgroundedTime) >= 180 * 1000)
                && !(app instanceof DisabledActivity)) {
            if (!BRKeyStore.getPinCode(app).isEmpty()) {
                UiUtils.startBreadActivity(app, true);
            }
        }

    }

    @Override
    public void onBackgrounded() {
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                HTTPServer.stopServer();
            }
        });
    }
}
