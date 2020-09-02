package com.breadwallet.presenter.activities;

import android.content.Context;
import android.util.Log;

import com.breadwallet.BuildConfig;
import com.breadwallet.core.ethereum.BREthereumNetwork;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.wallets.ethereum.WalletEthManager;
import com.breadwallet.wallet.wallets.side.ElaSideEthereumWalletManager;

public class WebviewScriptConfig{
    private static WebviewScriptConfig mInstance;

    public static enum networkConnectByDapp{ethereum, ethereumSideChain}

    //    public static var network:networkConnectByDapp = networkConnectByDapp.ethereum
//    public static var config = WKUserScriptConfig(
//            address: ((AuthModel.ShareAuth.walletManagers![Currencies.eth.code] as? EthWalletManager)?.address)!,
//    chainId: 1,
//    rpcUrl: "https://api-eth.elaphant.app/api/1/eth/wrap",
//    privacyMode: false
//            )
    public networkConnectByDapp network = networkConnectByDapp.ethereumSideChain;
    public String address;
    public int chainId;
    public String rpcUrl;

    public WebviewScriptConfig(Context context){
//        address = WalletEthManager.getInstance(context).getAddress();
//        chainId = 1;
//        rpcUrl = "https://api-eth.elaphant.app/api/1/eth/wrap";
        address = ElaSideEthereumWalletManager.getInstance(context).getAddress();
        chainId = 1;
        rpcUrl = "https://escrpc.elaphant.app";
    }

    public static synchronized WebviewScriptConfig getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new WebviewScriptConfig(context);
        }
        return mInstance;
    }
}
