package com.breadwallet.tools.manager;

import android.content.Context;
import android.os.Handler;
import android.os.NetworkOnMainThreadException;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.breadwallet.BreadApp;
import com.breadwallet.BuildConfig;
import com.breadwallet.presenter.entities.CurrencyEntity;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.sqlite.RatesDataSource;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.bitcoin.WalletBitcoinManager;
import com.breadwallet.wallet.wallets.ela.ElaDataSource;
import com.breadwallet.wallet.wallets.side.ElaSideEthereumWalletManager;
import com.elastos.jni.utils.HexUtils;
import com.platform.APIClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Request;

/**
 * BreadWallet
 * <p>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 7/22/15.
 * Copyright (c) 2016 breadwallet LLC
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class BRApiManager {
    private static final String TAG = BRApiManager.class.getName();

    private static BRApiManager instance;
    private Timer timer;

    private TimerTask timerTask;

    private Handler handler;

    public static final String HEADER_WALLET_ID = "X-Wallet-Id";
    public static final String HEADER_IS_INTERNAL = "X-Is-Internal";
    public static final String HEADER_TESTFLIGHT = "X-Testflight";
    public static final String HEADER_TESTNET = "X-Bitcoin-Testnet";
    public static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";

    private BRApiManager() {
        handler = new Handler();
    }

    public static BRApiManager getInstance() {

        if (instance == null) {
            instance = new BRApiManager();
        }
        return instance;
    }

    @WorkerThread
    private void updateRates(Context context, BaseWalletManager walletManager) {
        if (UiUtils.isMainThread()) {
            throw new NetworkOnMainThreadException();
        }
        Set<CurrencyEntity> set = new LinkedHashSet<>();
        try {
            JSONArray arr = fetchRates(context, walletManager);

            if (arr != null) {
                int length = arr.length();
                for (int i = 0; i < length; i++) {
                    CurrencyEntity tmp = new CurrencyEntity();
                    try {
                        JSONObject tmpObj = (JSONObject) arr.get(i);
                        tmp.name = tmpObj.getString("name");
                        tmp.code = tmpObj.getString("code");
                        tmp.rate = Float.valueOf(tmpObj.getString("rate"));
                        tmp.iso = walletManager.getIso();

//                        if(tmp.code.equalsIgnoreCase("ETH")) {
//                            CurrencyEntity elaEthEntity = new CurrencyEntity();
//                            elaEthEntity.name = ElaSideEthereumWalletManager.ETH_SCHEME;
//                            elaEthEntity.code = ElaSideEthereumWalletManager.getInstance(context).getIso();
//                            elaEthEntity.rate = Float.valueOf(tmpObj.getString("rate"));
//                            elaEthEntity.iso = ElaSideEthereumWalletManager.getInstance(context).getIso();
//                            set.add(elaEthEntity);
//                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    set.add(tmp);
                }

            } else {
                Log.e(TAG, "getCurrencies: failed to get currencies, response string: " + arr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (set.size() > 0) RatesDataSource.getInstance(context).putCurrencies(context, set);

    }


    private void initializeTimerTask(final Context context) {
        timerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp
                handler.post(new Runnable() {
                    public void run() {
                        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                            @Override
                            public void run() {
                                updateData(context);
                            }
                        });
                    }
                });
            }
        };
    }

    @WorkerThread
    private void updateData(final Context context) {

        if (BreadApp.isAppInBackground(context)) {
            Log.e(TAG, "doInBackground: Stopping timer, no activity on.");
            stopTimerTask();
            return;
        }
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                updateErc20Rates(context);
            }
        });

        List<BaseWalletManager> list = new ArrayList<>(WalletsMaster.getInstance(context).getAllWallets(context));

        for (final BaseWalletManager w : list) {
            //only update stuff for non erc20 for now, API endpoint BUG
            if (w.getIso().equalsIgnoreCase("BTC") || w.getIso().equalsIgnoreCase("BCH")
                    || w.getIso().equalsIgnoreCase("ETH")) {
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        w.updateFee(context);
                    }
                });
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        //get each wallet's rates
                        updateRates(context, w);

                    }
                });
            }
//            if(w.getIso().equalsIgnoreCase("IOEX")){
//                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
//                    @Override
//                    public void run() {
//                        WalletIoexManager.getInstance(context).updateTxHistory();
//                    }
//                });
//            }
            if(w.getIso().equalsIgnoreCase("ELA")){
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        w.refreshCachedBalance(context);
                    }
                });
//                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
//                    @Override
//                    public void run() {
//                        WalletElaManager.getInstance(context).updateTxHistory();
//                    }
//                });
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        ElaDataSource.getInstance(context).getAllDposProducers();
                    }
                });

            }
        }

    }

    @WorkerThread
    private synchronized void updateErc20Rates(Context context) {
        //get all erc20 rates.
//        String url = "https://api.coinmarketcap.com/v1/ticker/?limit=1000&convert=BTC";
//        String url = "https://api-wallet-ela.elastos.org/api/1/cmc?limit=1000";
        String url = "https://api-price.elaphant.app/api/1/cmc?limit=1000";
        String result = urlGET(context, url);
        Log.i(TAG, "updateErc20Rates result:"+result);
        try {
            if (Utils.isNullOrEmpty(result)) {
                Log.e(TAG, "updateErc20Rates: Failed to fetch");
                return;
            }
            JSONArray arr = new JSONArray(result);
            if (arr.length() == 0) {
                Log.e(TAG, "updateErc20Rates: empty json");
                return;
            }
            String object = null;
            Set<CurrencyEntity> tmp = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {

                Object obj = arr.get(i);
                if (!(obj instanceof JSONObject)) {
                    object = obj.getClass().getSimpleName();
                    continue;
                }
                JSONObject json = (JSONObject) obj;
                String code = "BTC";
                String name = json.getString("name");
                String rate = json.getString("price_btc");
                String iso = json.getString("symbol");

                if(iso.equalsIgnoreCase("ela")) {
                    ElaSideEthereumWalletManager elaSideEthereumWalletManager = ElaSideEthereumWalletManager.getInstance(context);
                    if(null != elaSideEthereumWalletManager) {
                        CurrencyEntity elaEthEnt = new CurrencyEntity(code, elaSideEthereumWalletManager.getName(), Float.valueOf(rate), elaSideEthereumWalletManager.getIso());
                        tmp.add(elaEthEnt);
                    }
                }

                CurrencyEntity ent = new CurrencyEntity(code, name, Float.valueOf(rate), iso);
                tmp.add(ent);

            }

            RatesDataSource.getInstance(context).putCurrencies(context, tmp);
            if (object != null)
                BRReportsManager.reportBug(new IllegalArgumentException("JSONArray returns a wrong object: " + object));
        } catch (JSONException e) {
            BRReportsManager.reportBug(e);
            e.printStackTrace();
        }

    }

    public void startTimer(Context context) {
        //set a new Timer
        if (timer != null) return;
        timer = new Timer();
        Log.e(TAG, "startTimer: started...");
        //initialize the TimerTask's job
        initializeTimerTask(context);

        timer.schedule(timerTask, 1000, 1 * 60 * 1000);
    }

    public void stopTimerTask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @WorkerThread
    public static JSONArray fetchRates(Context app, BaseWalletManager walletManager) {
        String url = "https://" + BreadApp.HOST + "/rates?currency=" + walletManager.getIso();
        String jsonString = urlGET(app, url);
        JSONArray jsonArray = null;
        if (jsonString == null) {
            Log.e(TAG, "fetchRates: failed, response is null");
            return null;
        }
        try {
            JSONObject obj = new JSONObject(jsonString);
            jsonArray = obj.getJSONArray("body");

        } catch (JSONException ignored) {
        }
        return jsonArray == null ? backupFetchRates(app, walletManager) : jsonArray;
    }

    @WorkerThread
    public static JSONArray backupFetchRates(Context app, BaseWalletManager walletManager) {
        if (!walletManager.getIso().equalsIgnoreCase(WalletBitcoinManager.getInstance(app).getIso())) {
            //todo add backup for BCH
            return null;
        }
        String jsonString = urlGET(app, "https://bitpay.com/rates");

        JSONArray jsonArray = null;
        if (jsonString == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonString);

            jsonArray = obj.getJSONArray("data");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    public static String Encrypt(String strSrc) {
        MessageDigest md;
        String strDes;
        byte[] bt = strSrc.getBytes();
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update(bt);
            strDes = HexUtils.bytesToHex(md.digest()); // to HexString
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return strDes;
    }

    private static String getApiKey(String timestamp){
        String data = BuildConfig.API_KEY + timestamp;
        return Encrypt(data);
    }

    private static String getTimestamp(){
        return String.valueOf(System.currentTimeMillis());
    }

    @WorkerThread
    public static String urlGET(Context app, String myURL) {
        Map<String, String> headers = BreadApp.getBreadHeaders();

        String stamp = getTimestamp();
        Request.Builder builder = new Request.Builder()
                .url(myURL)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-agent", Utils.getAgentString(app, "android/HttpURLConnection"))
                .header("Apikey", getApiKey(stamp))
                .header("Timestamp", stamp)
                .get();
        Iterator it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            builder.header((String) pair.getKey(), (String) pair.getValue());
        }

        Request request = builder.build();
        String bodyText = null;
        APIClient.BRResponse resp = APIClient.getInstance(app).sendRequest(request, false);

        try {
            bodyText = resp.getBodyText();
            String strDate = resp.getHeaders().get("date");
            if (strDate == null) {
                Log.e(TAG, "urlGET: strDate is null!");
                return bodyText;
            }
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            Date date = formatter.parse(strDate);
            long timeStamp = date.getTime();
            BRSharedPrefs.putSecureTime(app, timeStamp);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return bodyText;
    }

}
