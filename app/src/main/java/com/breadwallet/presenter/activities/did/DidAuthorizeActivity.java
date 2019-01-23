package com.breadwallet.presenter.activities.did;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.did.AuthorInfo;
import com.breadwallet.did.CallbackData;
import com.breadwallet.did.CallbackEntity;
import com.breadwallet.did.DidDataSource;
import com.breadwallet.presenter.activities.settings.BaseSettingsActivity;
import com.breadwallet.presenter.customviews.SwitchButton;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.StringUtil;
import com.elastos.jni.Utility;
import com.google.gson.Gson;

import org.wallet.library.AuthorizeManager;
import org.wallet.library.Constants;
import org.wallet.library.entity.LoginResponse;
import org.wallet.library.entity.SignWrapper;
import org.wallet.library.entity.UriFactory;

public class DidAuthorizeActivity extends BaseSettingsActivity {
    private static final String TAG = "author_test";

    private SwitchButton mNickNameSb;

    private SwitchButton mAddressSb;

    private Button mDenyBtn;

    private Button mAuthorizeBtn;

    @Override
    public int getLayoutId() {
        return R.layout.activity_author_layout;
    }

    @Override
    public int getBackButtonId() {
        return R.id.back_button;
    }

    private String mUri;
    private String packageName;
    private String activityCls;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if(intent != null) {
            String action = intent.getAction();
            if(action.equals(Intent.ACTION_VIEW)){
                Uri uri = intent.getData();
                Log.i(TAG, "server mUri: "+ uri.toString());
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
                packageName = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.PACKAGE_NAME);
                activityCls = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.ACTIVITY_CLASS);
            }
        }
        initView();
        initListener();
    }

    private void initView(){
        mNickNameSb = findViewById(R.id.nickname_switch_btn);
        mAddressSb = findViewById(R.id.receive_switch_btn);
        mDenyBtn = findViewById(R.id.deny_btn);
        mAuthorizeBtn = findViewById(R.id.authorize_btn);
    }

    private void initListener(){
        mDenyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mAuthorizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String mn = getMn();
                if(StringUtil.isNullOrEmpty(mn)) {
                    Toast.makeText(DidAuthorizeActivity.this, "还未创建钱包", Toast.LENGTH_SHORT).show();
                    return;
                }

                UriFactory uriFactory = new UriFactory();
                uriFactory.parse(mUri);
                final String did = uriFactory.getDID();
                String appId = uriFactory.getAppID();
                String sign = uriFactory.getSignature();
                String PK = uriFactory.getPublicKey();
                final String backurl = uriFactory.getCallbackUrl();
                final String returnUrl = uriFactory.getReturnUrl();
                boolean isValid = AuthorizeManager.verify(DidAuthorizeActivity.this, did, PK, appId, sign);

                if(isValid){
                    if(!StringUtil.isNullOrEmpty(backurl)){
                        final CallbackEntity entity = new CallbackEntity();
                        String pk = Utility.getInstance(DidAuthorizeActivity.this).getSinglePrivateKey(mn);
                        String myPK = Utility.getInstance(DidAuthorizeActivity.this).getSinglePublicKey(mn);
                        String myAddress = Utility.getInstance(DidAuthorizeActivity.this).getAddress(myPK);
                        final String myDid = Utility.getInstance(DidAuthorizeActivity.this).getDid(myPK);
                        CallbackData callbackData = new CallbackData();
                        callbackData.NickName = BRSharedPrefs.getNickname(DidAuthorizeActivity.this);
                        callbackData.ELAAddress = myAddress;
                        entity.Data = new Gson().toJson(callbackData);
                        entity.PublicKey = myPK;
                        entity.Sign = AuthorizeManager.sign(DidAuthorizeActivity.this, pk, entity.Data);


                        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                            @Override
                            public void run() {
                                String ret = DidDataSource.getInstance(DidAuthorizeActivity.this).callBackUrl(backurl, entity);
                                if(!StringUtil.isNullOrEmpty(ret)) {
                                    if(ret.contains("err code:")) {
                                        Toast.makeText(DidAuthorizeActivity.this, ret, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Uri uri = Uri.parse(returnUrl+"&did="+myDid);
                                        Log.i("xidaokun", "did:"+uri.toString());
                                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                        startActivity(intent);
                                    }
                                }
                            }
                        });
                    }

//                    AuthorizeManager.startClientActivity(DidAuthorizeActivity.this, response, packageName, activityCls);
                }
                finish();
            }
        });
    }

    private String getMn(){
        byte[] phrase = null;
        try {
            phrase = BRKeyStore.getPhrase(this, 0);
            if(phrase != null) {
               return new String(phrase);
            }
        } catch (UserNotAuthenticatedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
