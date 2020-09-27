package com.breadwallet.presenter.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.did.DidDataSource;
import com.breadwallet.presenter.activities.settings.BaseSettingsActivity;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.customviews.LoadingDialog;
import com.breadwallet.presenter.entities.VoteEntity;
import com.breadwallet.presenter.interfaces.BRAuthCompletion;
import com.breadwallet.tools.adapter.VoteNodeAdapter;
import com.breadwallet.tools.manager.BRClipboardManager;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.security.AuthManager;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.vote.PayLoadEntity;
import com.breadwallet.vote.ProducerEntity;
import com.breadwallet.wallet.wallets.ela.BRElaTransaction;
import com.breadwallet.wallet.wallets.ela.ElaDataSource;
import com.breadwallet.wallet.wallets.ela.WalletElaManager;
import com.breadwallet.wallet.wallets.ela.data.DposProducer;
import com.breadwallet.wallet.wallets.ela.data.DposProducers;
import com.elastos.jni.AuthorizeManager;
import com.elastos.jni.UriFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class VoteActivity extends BaseSettingsActivity {

    private String mUri;
    private UriFactory uriFactory;
    private TextView mVoteCountTv;
    private TextView mBalanceTv;
    private TextView mVoteElaAmountTv;
    private Button mCancleBtn;
    private Button mConfirmBtn;
    private ListView mVoteNodeLv;
    private TextView mVotePasteTv;
    private BaseTextView mNodeListTitle;
    private BaseTextView mFeeHintTv;

    private LoadingDialog mLoadingDialog;

    @Override
    public int getLayoutId() {
        return R.layout.activity_vote_layout;
    }

    @Override
    public int getBackButtonId() {
        return R.id.back_button;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vote_layout);

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (!StringUtil.isNullOrEmpty(action) && action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra("vote_scheme_uri");
            }
        }
        Log.i("VoteActivity", "uri:"+mUri);

        findView();
        initListener();
        initData();
    }


    private void findView(){
        mVoteCountTv = findViewById(R.id.voting_hint);
        mBalanceTv = findViewById(R.id.vote_ela_balance);
        mVoteElaAmountTv = findViewById(R.id.vote_ela_amount);
        mVotePasteTv = findViewById(R.id.vote_paste_tv);
        mNodeListTitle = findViewById(R.id.vote_nodes_list_title);
        mCancleBtn = findViewById(R.id.vote_cancle_btn);
        mConfirmBtn = findViewById(R.id.vote_confirm_btn);
        mVoteNodeLv = findViewById(R.id.vote_node_lv);
        mFeeHintTv = findViewById(R.id.vote_text_hint1);
        mLoadingDialog = new LoadingDialog(this, R.style.progressDialog);
        mLoadingDialog.setCanceledOnTouchOutside(false);
    }

    private void initListener(){
        mCancleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mConfirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BigDecimal balance = BRSharedPrefs.getCachedBalance(VoteActivity.this, "ELA");
                if(balance.longValue() <= 0){
                    Toast.makeText(VoteActivity.this, getString(R.string.vote_balance_not_insufficient), Toast.LENGTH_SHORT).show();
                    return;
                }
                if(mCandidates.size()>36) {
                    Toast.makeText(VoteActivity.this, getString(R.string.beyond_max_vote_node), Toast.LENGTH_SHORT).show();
                    return;
                }
                if(verifyUri()){
                    sendTx();
                }
            }
        });

        mVotePasteTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyText();
            }
        });
    }

    private void copyText() {
        StringBuilder sb = new StringBuilder();
        if(mProducers==null || mProducers.size()<=0) return;
        for(ProducerEntity producerEntity : mProducers){
            sb.append(producerEntity.Nickname).append("\n");
        }
        BRClipboardManager.putClipboard(this, sb.toString());
        Toast.makeText(this, getString(R.string.Receive_copied), Toast.LENGTH_SHORT).show();
    }

    private boolean verifyUri(){
        String did = uriFactory.getDID();
        String appId = uriFactory.getAppID();
        String appName = uriFactory.getAppName();
        String PK = uriFactory.getPublicKey();
        boolean isValid = AuthorizeManager.verify(this, did, PK, appName, appId);

        return isValid;
    }


    private void callReturnUrl(String txId){
        if(StringUtil.isNullOrEmpty(txId)) return;
        String returnUrl = uriFactory.getReturnUrl();
        if(StringUtil.isNullOrEmpty(returnUrl)) {
            Toast.makeText(VoteActivity.this, "returnurl is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        String url;
        if (returnUrl.contains("?")) {
            url = returnUrl + "&TXID=" + txId;
        } else {
            url = returnUrl + "?TXID=" + txId;
        }
        DidDataSource.getInstance(VoteActivity.this).callReturnUrl(url);
    }

    private void callBackUrl(String txid){
        try {
            if(StringUtil.isNullOrEmpty(txid)) return;
            String backurl = uriFactory.getCallbackUrl();
            if(StringUtil.isNullOrEmpty(backurl)) return;
            VoteEntity txEntity = new VoteEntity();
            txEntity.TXID = txid;
            String ret = DidDataSource.getInstance(this).urlPost(backurl, new Gson().toJson(txEntity));
        } catch (Exception e) {
            Toast.makeText(VoteActivity.this, "callback error", Toast.LENGTH_SHORT);
            e.printStackTrace();
        }
    }

    private void sendTx(){
        AuthManager.getInstance().authPrompt(this, this.getString(R.string.pin_author_vote), getString(R.string.pin_author_vote_msg), true, false, new BRAuthCompletion() {
            @Override
            public void onComplete() {
                showDialog();
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("posvote", "mCandidatesStr:"+mCandidatesStr);
                        String address = WalletElaManager.getInstance(VoteActivity.this).getAddress();
                        long amout = 0L;
                        List<PayLoadEntity> publickeys = new ArrayList<>();
                        for(String candidate : mCandidates) {
                            PayLoadEntity payLoadEntity = new PayLoadEntity();
                            payLoadEntity.value = amout;
                            payLoadEntity.candidate = candidate;
                        }
                        List<BRElaTransaction> transactions = ElaDataSource.getInstance(VoteActivity.this).createElaTx(address, address, amout, "vote", publickeys);
                        if(null == transactions) {
                            dismissDialog();
                            finish();
                            return;
                        }
                        String mRwTxid = ElaDataSource.getInstance(VoteActivity.this).sendElaRawTx(transactions);
                        if(StringUtil.isNullOrEmpty(mRwTxid)) {
                            dismissDialog();
                            finish();
                            return;
                        }
                        callBackUrl(mRwTxid);
                        callReturnUrl(mRwTxid);
                        if(null==mCandidates || mCandidates.size()<=0) {
                            BRSharedPrefs.cacheDposCd(VoteActivity.this, "");
//                            ElaDataSource.getInstance(VoteActivity.this).deleteAllTxProducer();
                        } else {
                            BRSharedPrefs.cacheDposCd(VoteActivity.this, mCandidatesStr);
//                            cacheTxProducer(mRwTxid);
                        }
                        dismissDialog();
                        finish();
                    }
                });
            }

            @Override
            public void onCancel() {
                //nothing
            }
        });

    }

    private void cacheTxProducer(String txid){
        if(StringUtil.isNullOrEmpty(txid)) return;
        if(null==mProducers || mProducers.size()<=0) return;
        List<DposProducers> txProducersEntities = new ArrayList<>();
        DposProducers dposProducers = new DposProducers();
        dposProducers.Txid = txid;
        dposProducers.Producer = new ArrayList<>();
        for(ProducerEntity entity : mProducers){
            DposProducer dposProducer = new DposProducer(entity.Producer_public_key, entity.Producer_public_key, entity.Nickname);
            dposProducers.Producer.add(dposProducer);
        }
        txProducersEntities.add(dposProducers);
        ElaDataSource.getInstance(this).cacheDposProducer(txProducersEntities);
    }

    private BigDecimal mAmount;
    private String  mCandidatesStr;
    private VoteNodeAdapter mAdapter;
    private List<String> mCandidates = new ArrayList<>();
    private List<ProducerEntity> mProducers = new ArrayList<>();
    private void initData(){
        if (StringUtil.isNullOrEmpty(mUri)) return;

//        if(mUri.contains("%20")) {
//            mUri = Uri.decode(mUri);
//        }

        uriFactory = new UriFactory();
        uriFactory.parse(mUri);

        mCandidatesStr = uriFactory.getCandidatePublicKeys();
        if(StringUtil.isNullOrEmpty(mCandidatesStr)) {
            mVoteCountTv.setText(String.format(getString(R.string.vote_nodes_count), 0));
            mNodeListTitle.setText(String.format(getString(R.string.node_list_title), 0));
            mBalanceTv.setText(String.format(getString(R.string.vote_balance), "0"));
            return;
        }
        mCandidatesStr = mCandidatesStr.trim();
        List<String> tmpCandidates = null;
        if(mCandidatesStr.contains("[")){
            tmpCandidates = new Gson().fromJson(mCandidatesStr, new TypeToken<List<String>>(){}.getType());
        } else {
            tmpCandidates = Utils.spliteByComma(mCandidatesStr);
        }
        if(null!=tmpCandidates && tmpCandidates.size()>0){
            mCandidates.clear();
            mCandidates.addAll(tmpCandidates);
        }
        BigDecimal balance = BRSharedPrefs.getCachedBalance(this, "ELA");

        mVoteCountTv.setText(String.format(getString(R.string.vote_nodes_count), mCandidates.size()));
        mNodeListTitle.setText(String.format(getString(R.string.node_list_title), mCandidates.size()));
        mBalanceTv.setText(String.format(getString(R.string.vote_balance), balance.toString()));
        mAmount = balance.subtract(new BigDecimal(0.0001));
        mVoteElaAmountTv.setText(mAmount.longValue()+"");

        List<ProducerEntity> tmp = ElaDataSource.getInstance(VoteActivity.this).queryDposProducers(mCandidates);
        if(tmp!=null && tmp.size()>0) {
            mProducers.clear();
            mProducers.addAll(tmp);
        }
        mAdapter = new VoteNodeAdapter(this, mProducers);
        mVoteNodeLv.setAdapter(mAdapter);

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long fee = ElaDataSource.getInstance(VoteActivity.this).getNodeFee();
                    final String feeStr = new BigDecimal(fee).divide(new BigDecimal(100000000), 8, BRConstants.ROUNDING_MODE).toString();
                    BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            mFeeHintTv.setText(String.format(getString(R.string.vote_hint), feeStr));
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void dismissDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing())
                    mLoadingDialog.dismiss();
            }
        });
    }


    private void showDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing())
                    mLoadingDialog.show();
            }
        });
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
