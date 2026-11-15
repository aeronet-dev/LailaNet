package com.v2ray.ang.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager

class VoucherExpireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MmkvManager.encodeSettings("LAST_VOUCHER_EXP", "")
        MmkvManager.encodeSettings("LAST_VOUCHER_END_MS", 0L)
        LauncherManager.stopService(context)
        context.toast("Waktu voucher LailaNet telah habis")
    }
}
