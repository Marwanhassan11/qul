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
        SmsParser.parse(body, System.currentTimeMillis())?.let { Store.addAll(c, listOf(it)) }
    }
}
