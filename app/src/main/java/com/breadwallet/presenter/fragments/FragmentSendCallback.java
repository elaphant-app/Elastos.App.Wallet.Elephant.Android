package com.breadwallet.presenter.fragments;

public interface FragmentSendCallback{
    void onSent(String txid);
    void onCancel();
}
