package com.zajel.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.zajel.app.data.Recipient
import com.zajel.app.data.ZajelDatabase
import com.zajel.app.importing.CsvReader
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val ZajelGreen = Color(0xFF078F83)

private data class BulkSendResult(val accepted: Int, val failed: Int)

private suspend fun sendBulkSms(phoneNumbers: List<String>, body: String): BulkSendResult {
    val manager = SmsManager.getDefault()
    var accepted = 0
    var failed = 0
    phoneNumbers.forEachIndexed { index, phone ->
        try {
            val parts = manager.divideMessage(body)
            if (parts.size > 1) manager.sendMultipartTextMessage(phone, null, parts, null, null)
            else manager.sendTextMessage(phone, null, body, null, null)
            accepted++
        } catch (_: Exception) {
            failed++
        }
        if (index < phoneNumbers.lastIndex) delay(1_500)
    }
    return BulkSendResult(accepted, failed)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZajelApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZajelApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember {
        Room.databaseBuilder(context.applicationContext, ZajelDatabase::class.java, "zajel.db").build()
    }
    val dao = remember { database.recipientDao() }
    val scope = rememberCoroutineScope()
    val recipients by dao.observeAll().collectAsState(initial = emptyList())

    var french by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Recipient?>(null) }
    var notice by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var showBulkConfirm by remember { mutableStateOf(false) }
    var bulkSendPending by remember { mutableStateOf(false) }
    var pendingBulkPhones by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingBulkMessage by remember { mutableStateOf("") }
    var bulkSending by remember { mutableStateOf(false) }
    var contactCandidates by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedContactPhones by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showContactsPicker by remember { mutableStateOf(false) }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = selected
        if (granted && bulkSendPending) {
            bulkSendPending = false
            bulkSending = true
            scope.launch {
                val result = sendBulkSms(pendingBulkPhones, pendingBulkMessage)
                notice = if (french) "Accepted by Android: ${result.accepted}. Failed: ${result.failed}." else "قبل نظام Android إرسال ${result.accepted} رسالة. تعذّر تمرير ${result.failed}."
                bulkSending = false
                pendingBulkPhones = emptyList()
                pendingBulkMessage = ""
            }
        } else if (target != null && granted && target.optedIn && !target.optedOut) {
            try {
                SmsManager.getDefault().sendTextMessage(target.phone, null, message, null, null)
                notice = if (french) "Demande SMS transmise à Android." else "تم تمرير طلب SMS إلى نظام Android."
            } catch (e: Exception) {
                notice = (if (french) "Échec : " else "تعذر الإرسال: ") + (e.message ?: "SMS")
            }
        } else notice = if (french) "Permission refusée ou consentement absent." else "الصلاحية مرفوضة أو الموافقة غير متوفرة."
        selected = null
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val csv = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val rows = CsvReader.recipientRows(csv)
                scope.launch {
                    var added = 0
                    var skipped = 0
                    rows.forEach { (name, number) ->
                        if (dao.findByPhone(number) == null) {
                            val id = dao.insert(Recipient(phone = number, displayName = name, optedIn = false))
                            if (id != -1L) added++ else skipped++
                        } else skipped++
                    }
                    notice = if (french) "Import CSV : $added ajouté(s), $skipped ignoré(s). Consentement à confirmer." 
                        else "استيراد CSV: أُضيف $added، وتم تجاوز $skipped. يجب تأكيد الموافقة قبل الإرسال."
                }
            } catch (e: Exception) {
                notice = (if (french) "Erreur de lecture CSV : " else "تعذر قراءة ملف CSV: ") + (e.message ?: "")
            }
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val imported = mutableListOf<Pair<String, String>>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val number = if (numberColumn >= 0) cursor.getString(numberColumn)?.trim().orEmpty() else ""
                    val name = if (nameColumn >= 0) cursor.getString(nameColumn)?.trim().orEmpty() else ""
                    if (number.length >= 7) imported += name to number
                }
            }
            contactCandidates = imported.distinctBy { it.second }
            selectedContactPhones = emptySet()
            showContactsPicker = true
            if (contactCandidates.isEmpty()) notice = if (french) "Aucun contact avec numéro." else "لا توجد جهات اتصال تحتوي على أرقام."
        } else notice = if (french) "Accès aux contacts refusé." else "تم رفض صلاحية جهات الاتصال."
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = ZajelGreen)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("زاجل | ZAJEL") }, actions = {
                TextButton(onClick = { french = !french }) { Text(if (french) "العربية" else "Français") }
            })
        }) { inset ->
            LazyColumn(Modifier.fillMaxSize().padding(inset).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F6F3))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.zajel_logo), null, Modifier.size(68.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("زاجل | Zajel", style = MaterialTheme.typography.headlineSmall, color = ZajelGreen)
                                Text(if (french) "Simple. Rapide. Proche." else "تواصل أقرب... بكل سهولة")
                            }
                        }
                    }
                }
                item { Text(if (french) "Ajouter un destinataire" else "إضافة مستلم", style = MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(phone, { phone = it }, label = { Text(if (french) "Numéro de téléphone" else "رقم الهاتف") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(if (french) "Le destinataire a accepté de recevoir des messages." else "أؤكد أن المستلم وافق على تلقي الرسائل.")
                    }
                }
                item {
                    Button(onClick = {
                        val p = phone.trim()
                        if (p.length >= 7 && consent) {
                            scope.launch {
                                val id = if (dao.findByPhone(p) == null) dao.insert(Recipient(phone = p, optedIn = true)) else -1L
                                notice = if (id == -1L) {
                                    if (french) "Numéro déjà enregistré." else "الرقم مسجل مسبقاً."
                                } else {
                                    if (french) "Destinataire enregistré sur cet appareil." else "تم حفظ المستلم على هذا الجهاز."
                                }
                                if (id != -1L) { phone = ""; consent = false }
                            }
                        } else notice = if (!consent) (if (french) "Consentement requis." else "يجب تأكيد موافقة المستلم.")
                            else (if (french) "Numéro invalide." else "رقم الهاتف غير صالح.")
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (french) "Ajouter" else "إضافة المستلم") }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { csvPicker.launch("text/*") }, modifier = Modifier.weight(1f)) {
                            Text(if (french) "Importer CSV" else "استيراد CSV")
                        }
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                            } else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (french) "Contacts" else "جهات الاتصال")
                        }
                    }
                }
                item {
                    Text(
                        if (french) "Les imports n'accordent jamais le consentement automatiquement. Confirmez-le uniquement après accord réel."
                        else "الاستيراد لا يمنح الموافقة تلقائياً. لا تؤكد الموافقة إلا بعد الحصول عليها فعلياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B625B)
                    )
                }
                item { Text(if (french) "Message" else "نص الرسالة", style = MaterialTheme.typography.titleMedium) }
                item { OutlinedTextField(message, { message = it }, label = { Text(if (french) "Votre message" else "اكتب رسالتك") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (french) "Destinataires (${recipients.size})" else "المستلمون (${recipients.size})", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            if (message.isBlank()) notice = if (french) "Saisissez un message." else "اكتب نص الرسالة أولاً."
                            else if (recipients.none { it.optedIn && !it.optedOut }) notice = if (french) "Aucun destinataire autorisé." else "لا يوجد مستلمون لديهم موافقة."
                            else showBulkConfirm = true
                        }) { Text(if (french) "Envoyer à tous" else "إرسال للجميع") }
                    }
                }
                items(recipients, key = { it.id }) { recipient ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(recipient.displayName?.takeIf { it.isNotBlank() }?.let { "$it • ${recipient.phone}" } ?: recipient.phone, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (recipient.optedOut) (if (french) "Désinscrit" else "أوقف استقبال الرسائل")
                                else if (recipient.optedIn) (if (french) "Consentement enregistré" else "الموافقة مسجلة")
                                else (if (french) "Consentement à confirmer" else "الموافقة غير مؤكدة"),
                                color = if (recipient.optedOut) Color(0xFFB3261E) else ZajelGreen,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!recipient.optedIn && !recipient.optedOut) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            dao.confirmConsent(recipient.phone)
                                            notice = if (french) "Consentement confirmé. Assurez-vous qu'il est réel." else "تم تسجيل الموافقة. تأكد من وجود موافقة فعلية."
                                        }
                                    }) { Text(if (french) "Confirmer accord" else "تأكيد الموافقة") }
                                }
                                Button(
                                    enabled = recipient.optedIn && !recipient.optedOut,
                                    onClick = {
                                        if (message.isBlank()) notice = if (french) "Saisissez un message." else "اكتب نص الرسالة أولاً."
                                        else { selected = recipient; showConfirm = true }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (french) "Envoyer" else "إرسال") }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        if (!recipient.optedOut) {
                                            dao.optOut(recipient.phone)
                                            notice = if (french) "Désinscription enregistrée." else "تم تسجيل إيقاف الرسائل لهذا الرقم."
                                        } else {
                                            dao.delete(recipient)
                                            notice = if (french) "Destinataire supprimé." else "تم حذف المستلم."
                                        }
                                    }
                                }) { Text(if (recipient.optedOut) (if (french) "Supprimer" else "حذف") else (if (french) "Désinscrire" else "إيقاف")) }
                            }
                        }
                    }
                }
                item {
                    if (bulkSending) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (french) "Envoi progressif en cours..." else "جارٍ الإرسال بالتتابع...")
                        }
                    }
                    Text(if (french) "L'envoi à tous concerne uniquement les contacts ayant donné leur accord et non désinscrits." else "الإرسال للجميع يشمل فقط المستلمين الذين سجلوا موافقتهم ولم يوقفوا الرسائل.", style = MaterialTheme.typography.bodySmall)
                }
                if (notice.isNotBlank()) item { Text(notice, color = ZajelGreen) }
            }
        }
    }


    if (showContactsPicker) AlertDialog(
        onDismissRequest = { showContactsPicker = false },
        title = { Text(if (french) "Choisir les contacts" else "اختر جهات الاتصال") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedContactPhones = contactCandidates.map { it.second }.toSet() }) { Text(if (french) "Tout sélectionner" else "تحديد الكل") }
                    TextButton(onClick = { selectedContactPhones = emptySet() }) { Text(if (french) "Tout désélectionner" else "إلغاء التحديد") }
                }
                LazyColumn {
                    items(contactCandidates, key = { it.second }) { (name, number) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = number in selectedContactPhones, onCheckedChange = { checked ->
                                selectedContactPhones = if (checked) selectedContactPhones + number else selectedContactPhones - number
                            })
                            Column {
                                Text(name.ifBlank { number })
                                if (name.isNotBlank()) Text(number, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showContactsPicker = false
                val chosen = contactCandidates.filter { it.second in selectedContactPhones }
                scope.launch {
                    var added = 0
                    var skipped = 0
                    chosen.forEach { (name, number) ->
                        if (dao.findByPhone(number) == null) {
                            val id = dao.insert(Recipient(phone = number, displayName = name.takeIf { it.isNotBlank() }, optedIn = false))
                            if (id != -1L) added++ else skipped++
                        } else skipped++
                    }
                    notice = if (french) "Contacts sélectionnés : $added ajouté(s), $skipped ignoré(s). Accord requis." else "جهات الاتصال المحددة: أُضيف $added، وتجاوزنا $skipped. الموافقة مطلوبة قبل الإرسال."
                }
            }) { Text(if (french) "Importer la sélection" else "استيراد المحدد") }
        },
        dismissButton = { TextButton(onClick = { showContactsPicker = false }) { Text(if (french) "Annuler" else "إلغاء") } }
    )

    if (showBulkConfirm) AlertDialog(
        onDismissRequest = { showBulkConfirm = false },
        title = { Text(if (french) "Confirmer l'envoi groupé" else "تأكيد الإرسال للجميع") },
        text = { Text(if (french) "Envoyer le message à ${recipients.count { it.optedIn && !it.optedOut }} destinataire(s) autorisé(s) ?" else "إرسال الرسالة إلى ${recipients.count { it.optedIn && !it.optedOut }} مستلم لديهم موافقة مسجلة؟ لن تُرسل إلى من لم يوافق أو أوقف الرسائل.") },
        confirmButton = {
            TextButton(onClick = {
                showBulkConfirm = false
                val eligible = recipients.filter { it.optedIn && !it.optedOut }
                pendingBulkPhones = eligible.map { it.phone }
                pendingBulkMessage = message
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    bulkSending = true
                    scope.launch {
                        val result = sendBulkSms(pendingBulkPhones, pendingBulkMessage)
                        notice = if (french) "Accepted by Android: ${result.accepted}. Failed: ${result.failed}." else "قبل نظام Android إرسال ${result.accepted} رسالة. تعذّر تمرير ${result.failed}."
                        bulkSending = false
                        pendingBulkPhones = emptyList()
                        pendingBulkMessage = ""
                    }
                } else {
                    bulkSendPending = true
                    smsPermission.launch(Manifest.permission.SEND_SMS)
                }
            }) { Text(if (french) "Envoyer" else "إرسال") }
        },
        dismissButton = { TextButton(onClick = { showBulkConfirm = false }) { Text(if (french) "Annuler" else "إلغاء") } }
    )

    if (showConfirm) AlertDialog(
        onDismissRequest = { showConfirm = false; selected = null },
        title = { Text(if (french) "Confirmer l'envoi" else "تأكيد الإرسال") },
        text = { Text(if (french) "Envoyer ce message au destinataire sélectionné ?" else "هل تريد إرسال هذه الرسالة إلى المستلم المحدد؟") },
        confirmButton = {
            TextButton(onClick = {
                showConfirm = false
                val target = selected
                if (target == null || !target.optedIn || target.optedOut) {
                    notice = if (french) "Consentement invalide." else "لا توجد موافقة صالحة لهذا المستلم."
                    selected = null
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        SmsManager.getDefault().sendTextMessage(target.phone, null, message, null, null)
                        notice = if (french) "Demande SMS transmise à Android." else "تم تمرير طلب SMS إلى Android."
                    } catch (e: Exception) { notice = e.message ?: "SMS error" }
                    selected = null
                } else smsPermission.launch(Manifest.permission.SEND_SMS)
            }) { Text(if (french) "Envoyer" else "إرسال") }
        },
        dismissButton = { TextButton(onClick = { showConfirm = false; selected = null }) { Text(if (french) "Annuler" else "إلغاء") } }
    )
}
