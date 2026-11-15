package com.zajel.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

private val ZajelGreen = Color(0xFF078F83)
private data class Recipient(val phone: String)

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
    var french by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var recipients by remember { mutableStateOf(listOf<Recipient>()) }
    var selected by remember { mutableStateOf<Recipient?>(null) }
    var notice by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = selected
        if (granted && target != null) {
            try {
                SmsManager.getDefault().sendTextMessage(target.phone, null, message, null, null)
                notice = if (french) "Demande SMS transmise à Android." else "تم تمرير طلب SMS إلى نظام Android."
            } catch (e: Exception) {
                notice = (if (french) "Échec : " else "تعذر الإرسال: ") + (e.message ?: "SMS")
            }
        } else notice = if (french) "Permission SMS refusée." else "تم رفض صلاحية الرسائل."
        selected = null
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
                        if (p.length >= 7 && p !in recipients.map { it.phone } && consent) {
                            recipients = recipients + Recipient(p); phone = ""; consent = false
                            notice = if (french) "Destinataire ajouté." else "تمت إضافة المستلم."
                        } else notice = if (!consent) (if (french) "Consentement requis." else "يجب تأكيد موافقة المستلم.")
                            else (if (french) "Numéro invalide ou déjà ajouté." else "الرقم غير صالح أو مضاف مسبقاً.")
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (french) "Ajouter" else "إضافة المستلم") }
                }
                item { Text(if (french) "Message" else "نص الرسالة", style = MaterialTheme.typography.titleMedium) }
                item { OutlinedTextField(message, { message = it }, label = { Text(if (french) "Votre message" else "اكتب رسالتك") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                item { Text(if (french) "Destinataires (${recipients.size})" else "المستلمون (${recipients.size})", style = MaterialTheme.typography.titleLarge) }
                items(recipients, key = { it.phone }) { recipient ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(recipient.phone, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { recipients = recipients.filterNot { it.phone == recipient.phone } }) { Text(if (french) "Supprimer" else "حذف") }
                            Button(onClick = {
                                if (message.isBlank()) notice = if (french) "Saisissez un message." else "اكتب نص الرسالة أولاً."
                                else { selected = recipient; showConfirm = true }
                            }) { Text(if (french) "Envoyer" else "إرسال") }
                        }
                    }
                }
                item { Text(if (french) "Envoi individuel uniquement. Vérifiez les consentements et les règles de votre opérateur." else "الإرسال فردي فقط. تأكد من الموافقات والتزم بسياسات شركة الاتصالات.", style = MaterialTheme.typography.bodySmall) }
                if (notice.isNotBlank()) item { Text(notice, color = ZajelGreen) }
            }
        }
    }

    if (showConfirm) AlertDialog(
        onDismissRequest = { showConfirm = false; selected = null },
        title = { Text(if (french) "Confirmer l'envoi" else "تأكيد الإرسال") },
        text = { Text(if (french) "Envoyer ce message au destinataire sélectionné ?" else "هل تريد إرسال هذه الرسالة إلى المستلم المحدد؟") },
        confirmButton = {
            TextButton(onClick = {
                showConfirm = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val target = selected
                    if (target != null) {
                        try {
                            SmsManager.getDefault().sendTextMessage(target.phone, null, message, null, null)
                            notice = if (french) "Demande SMS transmise à Android." else "تم تمرير طلب SMS إلى Android."
                        } catch (e: Exception) { notice = e.message ?: "SMS error" }
                        selected = null
                    }
                } else smsPermission.launch(Manifest.permission.SEND_SMS)
            }) { Text(if (french) "Envoyer" else "إرسال") }
        },
        dismissButton = { TextButton(onClick = { showConfirm = false; selected = null }) { Text(if (french) "Annuler" else "إلغاء") } }
    )
}
