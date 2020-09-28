package com.breadwallet.presenter.activities.sign;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.did.CallbackData;
import com.breadwallet.did.CallbackEntity;
import com.breadwallet.did.DidDataSource;
import com.breadwallet.did.SignCallbackData;
import com.breadwallet.did.SignInfo;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BRButton;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.customviews.LoadingDialog;
import com.breadwallet.presenter.customviews.RoundImageView;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.BRDateUtil;
import com.breadwallet.tools.util.StringUtil;
import com.elastos.jni.AuthorizeManager;
import com.elastos.jni.Constants;
import com.elastos.jni.UriFactory;
import com.elastos.jni.Utility;
import com.google.gson.Gson;

public class SignaureActivity extends BRActivity {

    private static final String TAG =  SignaureActivity.class.getSimpleName() + "_debug";

    private ImageButton mBackBtn;
    private RoundImageView mAppIconIv;
    private BaseTextView mAppNameTv;
    private BaseTextView mAppIdTv;
    private BaseTextView mDidTv;
    private BaseTextView mTimestampTv;
    private BaseTextView mPurposeTv;
    private BaseTextView mContentTv;
    private BRButton mDenyBtn;
    private BRButton mSignBtn;
    private BaseTextView mAddLimitTv;
    private BaseTextView mViewAllTv;

    private String mUri;
    private UriFactory uriFactory;
    private SignInfo mSignInfo = new SignInfo();

    private LoadingDialog mLoadingDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signaure_layout);

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (!StringUtil.isNullOrEmpty(action) && action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
            }
        }

        initView();
        initListener();
        initData();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(null == data) return;
        if(BRConstants.SIGN_PURPOSE_REQUEST == requestCode){
            String purpose = data.getStringExtra("purpose");
            mSignInfo.setPurpose(purpose);
            if(null != purpose) mPurposeTv.setText(purpose);
        }
    }

    public void initView(){
        mBackBtn = findViewById(R.id.back_button);
        mAppIconIv = findViewById(R.id.app_icon);
        mAppNameTv = findViewById(R.id.app_name);
        mAppIdTv = findViewById(R.id.app_id);
        mDidTv = findViewById(R.id.developer_did);
        mTimestampTv = findViewById(R.id.timestamp);
        mPurposeTv = findViewById(R.id.purpose);
        mContentTv = findViewById(R.id.content);
        mDenyBtn = findViewById(R.id.deny_btn);
        mSignBtn = findViewById(R.id.sign_btn);
        mAddLimitTv = findViewById(R.id.add_limitation_btn);
        mViewAllTv = findViewById(R.id.view_all_details_btn);
        mLoadingDialog = new LoadingDialog(this, R.style.progressDialog);
        mLoadingDialog.setCanceledOnTouchOutside(false);
    }

    public void initListener(){
        mBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mDenyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mSignBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sign();
            }
        });
        mAddLimitTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.startSignEditActivity(SignaureActivity.this, "limit",mSignInfo.getPurpose(), BRConstants.SIGN_PURPOSE_REQUEST);
            }
        });
        mViewAllTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.startSignEditActivity(SignaureActivity.this, "viewAll",mSignInfo.getContent(), BRConstants.SIGN_CONTENT_REQUEST);
            }
        });
    }

    private void sign(){
        String phrase = getPhrase();
        if (StringUtil.isNullOrEmpty(phrase)) {
            Toast.makeText(this, "Not yet created Wallet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (StringUtil.isNullOrEmpty(mUri)) {
            Toast.makeText(this, "invalid params", Toast.LENGTH_SHORT).show();
            return;
        }

        final String did = uriFactory.getDID();
        final String appId = uriFactory.getAppID();
        final String target = uriFactory.getTarget();
        String appName = uriFactory.getAppName();
        String PK = uriFactory.getPublicKey();
        if(StringUtil.isNullOrEmpty(did) || StringUtil.isNullOrEmpty(appId) || StringUtil.isNullOrEmpty(appName)
                || StringUtil.isNullOrEmpty(PK)) {
            Toast.makeText(this, "invalid params", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        boolean isValid = AuthorizeManager.verify(this, did, PK, appName, appId);
        if(!isValid) {
            Toast.makeText(this, "verify failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        final String backurl = uriFactory.getCallbackUrl();
        final String returnUrl = uriFactory.getReturnUrl();

        String pk = Utility.getInstance(this).getSinglePrivateKey(phrase);
        String myPK = Utility.getInstance(this).getSinglePublicKey(phrase);
        String myDid = Utility.getInstance(this).getDid(myPK);

        SignCallbackData callbackData = new SignCallbackData();
        callbackData.DID = myDid;
        callbackData.PublicKey = myPK;
        callbackData.RequesterDID = mSignInfo.getDid();
        callbackData.RequestedContent = mSignInfo.getContent();
        callbackData.Timestamp = mSignInfo.getTimestamp() / 1000;
        callbackData.UseStatement = mSignInfo.getPurpose();

        final String Data = new Gson().toJson(callbackData);
        final String Sign = AuthorizeManager.sign(this, pk, Data);

        final CallbackEntity entity = new CallbackEntity();
        entity.Data = Data;
        entity.Sign = Sign;

        if (!isFinishing()) mLoadingDialog.show();
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    UiUtils.callbackDataNeedSign(SignaureActivity.this, backurl, entity);
                    UiUtils.returnDataNeedSign(SignaureActivity.this, returnUrl, Data, Sign, appId, target, false);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "callback error", Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    dialogDismiss();
                    finish();
                }
            }
        });
        DidDataSource.getInstance(this).cacheSignApp(mSignInfo);
    }

    private String getPhrase() {
        byte[] phrase = null;
        try {
            phrase = BRKeyStore.getPhrase(this, 0);
            if (phrase != null) {
                return new String(phrase);
            }
        } catch (UserNotAuthenticatedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void dialogDismiss() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing())
                    mLoadingDialog.dismiss();
            }
        });
    }

    public void initData(){
        if (StringUtil.isNullOrEmpty(mUri)) return;
        uriFactory = new UriFactory();
        uriFactory.parse(mUri);
        long timestamp = System.currentTimeMillis();
        String timeFormat =  BRDateUtil.getAuthorDate(timestamp);

        mSignInfo.setAppName(uriFactory.getAppName());
        mSignInfo.setDid(uriFactory.getDID());
        mSignInfo.setAppId(uriFactory.getAppID());
        mSignInfo.setContent(uriFactory.getRequestedContent());
        mSignInfo.setTimestamp(timestamp);
        mSignInfo.setPurpose(uriFactory.getUseStatement());

        String appName = mSignInfo.getAppName();
        if(!StringUtil.isNullOrEmpty(appName)) mAppNameTv.setText(appName);

        String appId = mSignInfo.getAppId();
        if(!StringUtil.isNullOrEmpty(appId)) mAppIdTv.setText("App ID:"+appId);

        String reqDid = mSignInfo.getDid();
        if(!StringUtil.isNullOrEmpty(reqDid)) mDidTv.setText(reqDid);

        if(!StringUtil.isNullOrEmpty(timeFormat)) mTimestampTv.setText(timeFormat);

        String purpose = mSignInfo.getPurpose();
        if(!StringUtil.isNullOrEmpty(purpose)) mPurposeTv.setText(purpose);

        String content = mSignInfo.getContent();
        if(!StringUtil.isNullOrEmpty(content)) mContentTv.setText(content);

        int iconResourceId = getResources().getIdentifier("unknow", BRConstants.DRAWABLE, getPackageName());
        if(!StringUtil.isNullOrEmpty(appId)) {
            if(appId.equals(BRConstants.REA_PACKAGE_ID)){
                iconResourceId = getResources().getIdentifier("redpackage", BRConstants.DRAWABLE, getPackageName());
            } else if(appId.equals(BRConstants.DEVELOPER_WEBSITE) || appId.equals(BRConstants.DEVELOPER_WEBSITE_TEST)){
                iconResourceId = getResources().getIdentifier("developerweb", BRConstants.DRAWABLE, getPackageName());
            } else if(appId.equals(BRConstants.HASH_ID)){
                iconResourceId = getResources().getIdentifier("hash", BRConstants.DRAWABLE, getPackageName());
            }
        }
        mAppIconIv.setImageDrawable(getDrawable(iconResourceId));
    }
}
