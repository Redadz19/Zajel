package com.zajel.app.sms

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/**
 * إرسال SMS يحتاج موافقة صريحة من المستخدم وطلب صلاحية SEND_SMS وقت التشغيل.
 * لا تستدعِ هذه الدالة للإرسال الجماعي قبل تطبيق فحوص opt-in/opt-out وسجل الإرسال.
 */
class SmsGateway(private val context: Context) {
    fun availableSubscriptions() =
        SubscriptionManager.from(context).activeSubscriptionInfoList.orEmpty()

    fun sendOne(subscriptionId: Int, phone: String, body: String) {
        val manager = context.getSystemService(SmsManager::class.java)
            .createForSubscriptionId(subscriptionId)
        manager.sendTextMessage(phone, null, body, null, null)
    }
}
