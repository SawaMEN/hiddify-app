package com.hiddify.hiddify;

interface IServiceCallback {
  void onServiceStatusChanged(int status);
  void onServiceAlert(int type, String message);
}