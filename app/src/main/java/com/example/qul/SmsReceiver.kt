package com.example.qul

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (msgs.isNullOrEmpty()) return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val t = if (msgs[0].timestampMillis > 0) msgs[0].timestampMillis else System.currentTimeMillis()
        SmsParser.parse(body, t)?.let { Store.addAll(c, listOf(it)) }
    }
}
