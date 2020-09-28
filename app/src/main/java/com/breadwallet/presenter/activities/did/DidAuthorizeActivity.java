package com.breadwallet.presenter.activities.did;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.did.AuthorInfo;
import com.breadwallet.did.CallbackData;
import com.breadwallet.did.CallbackEntity;
import com.breadwallet.did.ChineseIDCard;
import com.breadwallet.did.DidDataSource;
import com.breadwallet.did.PhoneNumber;
import com.breadwallet.presenter.activities.settings.BaseSettingsActivity;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.customviews.LoadingDialog;
import com.breadwallet.presenter.customviews.RoundImageView;
import com.breadwallet.presenter.entities.AuthorInfoItem;
import com.breadwallet.presenter.entities.MyAppItem;
import com.breadwallet.tools.adapter.AuthorInfoAdapter;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.sqlite.ProfileDataSource;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.wallets.ethereum.WalletEthManager;
import com.elastos.jni.AuthorizeManager;
import com.elastos.jni.Constants;
import com.elastos.jni.UriFactory;
import com.elastos.jni.Utility;
import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DidAuthorizeActivity extends BaseSettingsActivity {
    private static final String TAG = "author_test";

    private Button mDenyBtn;

    private Button mAuthorizeBtn;

    private BaseTextView mAppNameTv;

    private CheckBox mAuthorCbox;

    private BaseTextView mWillTv;

    private RoundImageView mAppIcon;

    private ListView mAuthorInfoLv;

    @Override
    public int getLayoutId() {
        return R.layout.activity_author_layout;
    }

    @Override
    public int getBackButtonId() {
        return R.id.back_button;
    }

    private String mUri;
    private boolean isOnEla = true;
    private LoadingDialog mLoadingDialog;
    private boolean isInternal = true;
    private UriFactory uriFactory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (!StringUtil.isNullOrEmpty(action) && action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                Log.i(TAG, "server mUri: " + uri.toString());
                mUri = uri.toString();
                isInternal = false;
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
                isInternal = true;
            }
        }



        initView();
        initListener();
        initData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            String action = intent.getAction();
            if(StringUtil.isNullOrEmpty(action)) return;
            if (action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                Log.i(TAG, "server mUri: " + uri.toString());
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
            }
        }
    }

    private void initView() {
        mAppNameTv = findViewById(R.id.app_name);
        mDenyBtn = findViewById(R.id.deny_btn);
        mAuthorizeBtn = findViewById(R.id.authorize_btn);
        mAuthorCbox = findViewById(R.id.auto_checkbox);
        mWillTv = findViewById(R.id.auth_info);
        mAppIcon = findViewById(R.id.app_icon);
        mAuthorInfoLv = findViewById(R.id.author_info_list);
        mLoadingDialog = new LoadingDialog(this, R.style.progressDialog);
        mLoadingDialog.setCanceledOnTouchOutside(false);
    }

    MyAppItem myAppItem = null;
    private void initData(){
        if (StringUtil.isNullOrEmpty(mUri)) return;
        uriFactory = new UriFactory();
        uriFactory.parse(mUri);

        if(uriFactory.getHost().equals("identity")){
            isOnEla = true;
        }else if(uriFactory.getHost().equals("ethsign")){
            isOnEla = false;
        }

        mAppNameTv.setText(uriFactory.getAppName());
        mWillTv.setText(String.format(getString(R.string.Did_Will_Get), uriFactory.getAppName()));
        mAuthorCbox.setText(String.format(getString(R.string.Author_Auto_Check), uriFactory.getAppName()));

        boolean isAuto = BRSharedPrefs.isAuthorAuto(this, uriFactory.getAppID());
        mAuthorCbox.setChecked(isAuto);

        String appId = uriFactory.getAppID();
        myAppItem = ProfileDataSource.getInstance(this).getAppInfoById(appId);

//        int iconResourceId = getResources().getIdentifier("unknow", BRConstants.DRAWABLE, getPackageName());
//        if(!StringUtil.isNullOrEmpty(appId)) {
//            if(appId.equals(BRConstants.REA_PACKAGE_ID)){
//                iconResourceId = getResources().getIdentifier("redpackage", BRConstants.DRAWABLE, getPackageName());
//                mAppIcon.setImageDrawable(getDrawable(iconResourceId));
//            } else if(appId.equals(BRConstants.DEVELOPER_WEBSITE) || appId.equals(BRConstants.DEVELOPER_WEBSITE_TEST)){
//                iconResourceId = getResources().getIdentifier("developerweb", BRConstants.DRAWABLE, getPackageName());
//                mAppIcon.setImageDrawable(getDrawable(iconResourceId));
//            } else if(appId.equals(BRConstants.HASH_ID)){
//                iconResourceId = getResources().getIdentifier("hash", BRConstants.DRAWABLE, getPackageName());
//                mAppIcon.setImageDrawable(getDrawable(iconResourceId));
//            } else {
//                if(myAppItem != null){
//                    Bitmap bitmap = null;
//                    if(!StringUtil.isNullOrEmpty(myAppItem.icon)){
//                        bitmap = Utils.getIconFromPath(new File(myAppItem.icon));
//                    }
//                    if(null != bitmap){
//                        mAppIcon.setImageBitmap(bitmap);
//                    } else {
//                        mAppIcon.setImageResource(R.drawable.unknow);
//                    }
//                }
//            }
//        }

        if(myAppItem != null){
            Bitmap bitmap = null;
            if(!StringUtil.isNullOrEmpty(myAppItem.icon)){
                bitmap = Utils.getIconFromPath(new File(myAppItem.icon));
            }
            if(null != bitmap){
                mAppIcon.setImageBitmap(bitmap);
            } else {
                mAppIcon.setImageResource(R.drawable.unknow);
            }
        }

        List infos = createInfoList();
        AuthorInfoAdapter authorAdapter = new AuthorInfoAdapter(this, infos);
        mAuthorInfoLv.setAdapter(authorAdapter);

        if (isAuto) author();
    }

    AuthorInfoItem nickNameItem;
    AuthorInfoItem elaAddressItem;
    AuthorInfoItem btcAddressItem;
    AuthorInfoItem ethAddressItem;
    AuthorInfoItem ethEscAddressItem;
    AuthorInfoItem bchAddressItem;
    AuthorInfoItem usdtAddressItem;
    AuthorInfoItem phoneNumberItem;
    AuthorInfoItem emailItem;
    AuthorInfoItem idcardItem;

    private List<AuthorInfoItem> createInfoList(){
        List<AuthorInfoItem> infos = new ArrayList<>();
        AuthorInfoItem didItem = new AuthorInfoItem(AuthorInfoItem.DID, getString(R.string.Did_Elastos_DID), "required");
        infos.add(didItem);
        AuthorInfoItem publicKeyItem = new AuthorInfoItem(AuthorInfoItem.PUBLIC_KEY, getString(R.string.Did_Public_Key), "required");
        infos.add(publicKeyItem);

        String requestInfo = uriFactory.getRequestInfo();
        if(StringUtil.isNullOrEmpty(requestInfo)) return infos;
        requestInfo = requestInfo.toLowerCase();
        if(requestInfo.contains("Nickname".toLowerCase())){
            nickNameItem = new AuthorInfoItem(AuthorInfoItem.NICK_NAME, getString(R.string.Did_Nick_Name), "check");
            infos.add(nickNameItem);
        }

        if(requestInfo.contains("ELAAddress".toLowerCase())){
            elaAddressItem = new AuthorInfoItem(AuthorInfoItem.ELA_ADDRESS, getString(R.string.Did_Ela_Address), "check");
            infos.add(elaAddressItem);
        }

        if(requestInfo.contains("BTCAddress".toLowerCase())) {
            btcAddressItem = new AuthorInfoItem(AuthorInfoItem.BTC_ADDRESS, getString(R.string.Did_Btc_Address), "check");
            infos.add(btcAddressItem);
        }

        if(requestInfo.contains("ETHAddress".toLowerCase())){
            ethAddressItem = new AuthorInfoItem(AuthorInfoItem.ETH_ADDRESS, getString(R.string.Did_Eth_Address), "check");
            infos.add(ethAddressItem);
        }

        if(requestInfo.contains("ELAETHSCAddress".toLowerCase())) {
            ethEscAddressItem = new AuthorInfoItem(AuthorInfoItem.ETHSC_ADDRESS, getString(R.string.Did_Ethsc_Address), "check");
            infos.add(ethEscAddressItem);
        }

        if(requestInfo.contains("BCHAddress".toLowerCase())){
            bchAddressItem = new AuthorInfoItem(AuthorInfoItem.BCH_ADDRESS, getString(R.string.Did_Bch_Address), "check");
            infos.add(bchAddressItem);
        }

        if(requestInfo.contains("USDTAddress".toLowerCase())){
            usdtAddressItem = new AuthorInfoItem(AuthorInfoItem.USDT_ADDRESS, getString(R.string.Did_Usd_Address), "check");
            infos.add(usdtAddressItem);
        }

        if(requestInfo.contains("PhoneNumber".toLowerCase())){
            phoneNumberItem = new AuthorInfoItem(AuthorInfoItem.PHONE_NUMBER, getString(R.string.Did_PhoneNumber), "check");
            infos.add(phoneNumberItem);
        }

        if(requestInfo.contains("Email".toLowerCase())){
            emailItem = new AuthorInfoItem(AuthorInfoItem.EMAIL, getString(R.string.Did_Email), "check");
            infos.add(emailItem);
        }

        if(requestInfo.contains("ChineseIDCard".toLowerCase())){
            idcardItem = new AuthorInfoItem(AuthorInfoItem.CHINESE_ID_CARD, getString(R.string.Did_Chinese_ID), "check");
            infos.add(idcardItem);
        }

        return infos;
    }

    private void initListener() {
        mDenyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mAuthorizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                author();
            }
        });
        mAuthorCbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (uriFactory == null) return;
                BRSharedPrefs.setIsAuthorAuto(DidAuthorizeActivity.this, uriFactory.getAppID(), b);
            }
        });
    }

    private void author() {
        String mn = getMn();
        if (StringUtil.isNullOrEmpty(mn)) {
            Toast.makeText(DidAuthorizeActivity.this, "Not yet created Wallet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (StringUtil.isNullOrEmpty(mUri)) {
            Toast.makeText(DidAuthorizeActivity.this, "invalid params", Toast.LENGTH_SHORT).show();
            return;
        }

        final String did = uriFactory.getDID();
        final String address = uriFactory.getAddress();
        final String appId = uriFactory.getAppID();
        String appName = uriFactory.getAppName();
        String PK = uriFactory.getPublicKey();
        String randomNumber = uriFactory.getRandomNumber();
        final String target = uriFactory.getTarget();
        if(isOnEla){
            if(StringUtil.isNullOrEmpty(did) || StringUtil.isNullOrEmpty(appId) || StringUtil.isNullOrEmpty(appName)
                    || StringUtil.isNullOrEmpty(PK) || StringUtil.isNullOrEmpty(randomNumber)) {
                Toast.makeText(DidAuthorizeActivity.this, "invalid params", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }else{
            if(StringUtil.isNullOrEmpty(address) || StringUtil.isNullOrEmpty(appId) || StringUtil.isNullOrEmpty(appName)
                    || StringUtil.isNullOrEmpty(PK) || StringUtil.isNullOrEmpty(randomNumber)) {
                Toast.makeText(DidAuthorizeActivity.this, "invalid params", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        boolean isValid;
        if(isOnEla){
            isValid = AuthorizeManager.verify(DidAuthorizeActivity.this, did, PK, appName, appId);
        }else{
            WalletEthManager wem = WalletEthManager.getInstance(DidAuthorizeActivity.this);
            isValid = wem.getAddress().toLowerCase().equals(address.toLowerCase());
        }
        if (!isValid) {
            Toast.makeText(this, "verify failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String backurl = uriFactory.getCallbackUrl();
        final String returnUrl = uriFactory.getReturnUrl();

        cacheAuthorInfo(uriFactory);
        StringBuilder sb = new StringBuilder();
        String pk = Utility.getInstance(DidAuthorizeActivity.this).getSinglePrivateKey(mn);
        String myPK = Utility.getInstance(DidAuthorizeActivity.this).getSinglePublicKey(mn);
        final String myDid = Utility.getInstance(DidAuthorizeActivity.this).getDid(myPK);

        CallbackData callbackData = new CallbackData();
        //require
        callbackData.DID = myDid;
        callbackData.PublicKey = myPK;
        callbackData.RandomNumber = randomNumber;
        sb.append(AuthorInfoItem.DID).append(",").append(AuthorInfoItem.PUBLIC_KEY).append(",");
        //request info
        callbackData.Nickname = (nickNameItem!=null)?nickNameItem.getValue(this)[0] : null;
        if((nickNameItem!=null) && nickNameItem.isChecked()) sb.append(AuthorInfoItem.NICK_NAME).append(",");

        callbackData.ELAAddress = (elaAddressItem!=null)?elaAddressItem.getValue(this)[0] : null;
        if((elaAddressItem!=null) && elaAddressItem.isChecked()) sb.append(AuthorInfoItem.ELA_ADDRESS).append(",");

        callbackData.BTCAddress = (btcAddressItem!=null)?btcAddressItem.getValue(this)[0] : null;
        if((btcAddressItem!=null) && btcAddressItem.isChecked()) sb.append(AuthorInfoItem.BTC_ADDRESS).append(",");

        callbackData.ETHAddress = (ethAddressItem!=null)?ethAddressItem.getValue(this)[0] : null;
        if((ethAddressItem!=null) && ethAddressItem.isChecked()) sb.append(AuthorInfoItem.ETH_ADDRESS).append(",");

        callbackData.ELAETHSCAddress = (ethEscAddressItem !=null)? ethEscAddressItem.getValue(this)[0] : null;
        if((ethEscAddressItem !=null) && ethEscAddressItem.isChecked()) sb.append(AuthorInfoItem.ETHSC_ADDRESS).append(",");

        callbackData.BCHAddress = (bchAddressItem!=null)?bchAddressItem.getValue(this)[0] : null;
        if((bchAddressItem!=null) && bchAddressItem.isChecked()) sb.append(AuthorInfoItem.BCH_ADDRESS).append(",");

        callbackData.USDTAddress = (usdtAddressItem !=null)? usdtAddressItem.getValue(this)[0] : null;
        if((usdtAddressItem !=null) && usdtAddressItem.isChecked()) sb.append(AuthorInfoItem.USDT_ADDRESS).append(",");

        callbackData.Email = (emailItem!=null)?emailItem.getValue(this)[0] : null;
        if((emailItem!=null) && emailItem.isChecked()) sb.append(AuthorInfoItem.EMAIL).append(",");

        if(phoneNumberItem != null){
            PhoneNumber phoneNumber = new PhoneNumber();
            phoneNumber.PhoneNumber = phoneNumberItem.getValue(this)[1];
            phoneNumber.CountryCode = phoneNumberItem.getValue(this)[0];
            callbackData.PhoneNumber = phoneNumber;
            if(phoneNumberItem.isChecked()) sb.append(AuthorInfoItem.PHONE_NUMBER).append(",");
        }
        if(idcardItem != null){
            callbackData.ChineseIDCard = new ChineseIDCard();
            callbackData.ChineseIDCard.RealName = idcardItem.getValue(this)[0];
            callbackData.ChineseIDCard.IDNumber = idcardItem.getValue(this)[1];
            if(idcardItem.isChecked()) sb.append(AuthorInfoItem.CHINESE_ID_CARD).append(",");
        }

        BRSharedPrefs.putRequestInfo(DidAuthorizeActivity.this, sb.toString().toLowerCase());

        final String Data = new Gson().toJson(callbackData);
        final String Sign = AuthorizeManager.sign(DidAuthorizeActivity.this, pk, Data);
        final CallbackEntity entity = new CallbackEntity();
        entity.Data = Data;
        entity.Sign = Sign;


        if (!isFinishing()) mLoadingDialog.show();
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    UiUtils.callbackDataNeedSign(DidAuthorizeActivity.this, backurl, entity);
                    UiUtils.returnDataNeedSign(DidAuthorizeActivity.this, returnUrl, Data, Sign, appId, target, isInternal);
                } catch (Exception e) {
                    showCallbackError();
                } finally {
                    dialogDismiss();
                    finish();
                }
            }
        });
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

    private void showCallbackError() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "callback error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cacheAuthorInfo(UriFactory uriFactory) {
        if (uriFactory == null) return;
        AuthorInfo info = new AuthorInfo();
        info.setAuthorTime(getAuthorTime(0));
        info.setPK(uriFactory.getPublicKey());
        info.setAppId(uriFactory.getAppID());
        info.setNickName(uriFactory.getAppName());
        info.setDid(uriFactory.getDID());
        info.setAppName(uriFactory.getAppName());
        info.setExpTime(getAuthorTime(30));
        if(null!=myAppItem) info.setAppIcon(myAppItem.icon);
        DidDataSource.getInstance(this).putAuthorApp(info);
    }

    private long getAuthorTime(int day) {
        Date date = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(calendar.DATE, day);
        date = calendar.getTime();
        long time = date.getTime();

        return time;
    }

    private String getMn() {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(null != mLoadingDialog){
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
    }
}
