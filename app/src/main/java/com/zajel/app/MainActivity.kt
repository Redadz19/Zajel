package com.zajel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

private val ZajelGreen = Color(0xFF159B62)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZajelApp() }
    }
}

@Composable
private fun ZajelApp() {
    var language by remember { mutableStateOf("العربية") }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var recipients by remember { mutableStateOf(listOf<String>()) }
    MaterialTheme(colorScheme = lightColorScheme(primary = ZajelGreen)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("زاجل | ZAJEL") }, actions = {
                TextButton(onClick = { language = if (language == "العربية") "Français" else "العربية" }) {
                    Text(language)
                }
            })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(if (language == "العربية") "إدارة الرسائل والمستلمين" else "Messages et destinataires", style = MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(phone, { phone = it }, label = { Text(if (language == "العربية") "رقم الهاتف" else "Numéro de téléphone") }, modifier = Modifier.fillMaxWidth()) }
                item { Button(onClick = { if (phone.isNotBlank() && phone !in recipients) { recipients = recipients + phone; phone = "" } }, modifier = Modifier.fillMaxWidth()) { Text(if (language == "العربية") "إضافة مستلم" else "Ajouter") } }
                item { OutlinedTextField(message, { message = it }, label = { Text(if (language == "العربية") "نص الرسالة" else "Texte du message") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                item { Text(if (language == "العربية") "المستلمون (${recipients.size})" else "Destinataires (${recipients.size})", style = MaterialTheme.typography.titleMedium) }
                items(recipients) { Text(it) }
                item { Text("نسخة تأسيسية: الإرسال الفعلي غير مفعّل بعد.", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
