package com.breadwallet.wallet.wallets.ioex;

public class BRIoexTransaction {
    private String tx;
    private String txId;

    public String getTx() {
        return tx;
    }

    public String getTxId(){
        return txId;
    }

    public void setTx(String tx){
        this.tx = tx;
    }

    public void setTxId(String txId){
        this.txId = txId;
    }
}
