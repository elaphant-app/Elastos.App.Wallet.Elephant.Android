package com.breadwallet.presenter.entities;

import android.content.Context;

import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;

public class AuthorInfoItem {

    public static final String DID = "DID";
    public static final String PUBLIC_KEY = "PublicKey";
    public static final String NICK_NAME = "Nickname";
    public static final String ELA_ADDRESS = "ELAAddress";
    public static final String IOEX_ADDRESS = "IOEXAddress";
    public static final String BTC_ADDRESS = "BTCAddress";
    public static final String ETH_ADDRESS = "ETHAddress";
    public static final String ETHSC_ADDRESS = "ELAETHSCAddress";
    public static final String BCH_ADDRESS = "BCHAddress";
    public static final String USDT_ADDRESS = "USDTAddress";
    public static final String PHONE_NUMBER = "PhoneNumber";
    public static final String EMAIL = "Email";
    public static final String CHINESE_ID_CARD = "ChineseIDCard";

    private String cname;

    private String name;

    private String flag;

    private boolean checked = true;

    public AuthorInfoItem(String cname, String name, String flag){
        this.cname = cname;
        this.name = name;
        this.flag = flag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public String[] getValue(Context context){
        if(checked){
            if(NICK_NAME.equals(cname)){
                return new String[]{BRSharedPrefs.getNickname(context)};
            } if(ELA_ADDRESS.equals(cname)){
                BaseWalletManager ela = WalletsMaster.getInstance(context).getWalletByIso(context, "ELA");
                return new String[]{ela.getAddress()};

            } if(IOEX_ADDRESS.equals(cname)){
                BaseWalletManager ioex = WalletsMaster.getInstance(context).getWalletByIso(context, "IOEX");
                return new String[]{ioex.getAddress()};

            } else if(BTC_ADDRESS.equals(cname)){
                BaseWalletManager btc = WalletsMaster.getInstance(context).getWalletByIso(context, "BTC");
                return new String[]{btc.getAddress()};

            } else if(ETH_ADDRESS.equals(cname) || USDT_ADDRESS.equals(cname) || ETHSC_ADDRESS.equals(cname)){
                BaseWalletManager eth = WalletsMaster.getInstance(context).getWalletByIso(context, "ETH");
                return new String[]{eth.getAddress()};

            } else if(BCH_ADDRESS.equals(cname)){
                BaseWalletManager bch = WalletsMaster.getInstance(context).getWalletByIso(context, "BCH");
                String stringifyAddress = bch.getReceiveAddress(context).stringify();
                return new String[]{bch.decorateAddress(stringifyAddress)};

            } else if(PHONE_NUMBER.equals(cname)){
                String area = BRSharedPrefs.getArea(context);
                String mobile = BRSharedPrefs.getMobile(context);
                return new String[]{area, mobile};

            } else if(EMAIL.equals(cname)){
               String email = BRSharedPrefs.getEmail(context);
                return new String[]{email};

            } else if(CHINESE_ID_CARD.equals(cname)){
                String realName = BRSharedPrefs.getRealname(context);
                String idNumber = BRSharedPrefs.getID(context);
                return new String[]{realName, idNumber};
            }
        }
        return new String[]{null, null};
    }
}
