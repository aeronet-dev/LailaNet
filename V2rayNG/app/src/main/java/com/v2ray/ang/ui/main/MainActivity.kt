package com.v2ray.ang.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class VoucherPackage(
    val nama: String,
    val durasi: String,
    val harga: String,
    val ket: String
)

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LauncherManager.handlePermission(this, PermissionType.NOTIFICATION)
            }
            startV2Ray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            androidx.compose.ui.platform.ComposeView(this).apply {
                setContent {
                    MaterialTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFFF8FAFC)
                        ) {
                            LailaNetScreen(
                                mainViewModel = mainViewModel,
                                onConnectClick = { configLink ->
                                    importAndStartV2Ray(configLink)
                                },
                                onDisconnectClick = {
                                    mainViewModel.stopV2Ray()
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    private fun importAndStartV2Ray(configLink: String) {
        val existingKeys = MmkvManager.decodeServerList()
        existingKeys.forEach { MmkvManager.removeServer(it) }
        val count = AngConfigManager.importBatchConfig(configLink, "", false)
        if (count > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startV2Ray()
            }
        } else {
            toast("Format server dari voucher tidak valid")
        }
    }

    private fun startV2Ray() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            requestVpnPermission.launch(intent)
        } else {
            mainViewModel.startV2Ray()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@SuppressLint("HardwareIds")
@Composable
fun LailaNetScreen(
    mainViewModel: MainViewModel,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("lailanet_prefs", Context.MODE_PRIVATE) }

    val apiUrl = "https://workers.dev"
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    val isRunning by mainViewModel.isRunning.collectAsState()
    var voucherCode by remember { mutableStateOf(prefs.getString("saved_voucher", "") ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Status: Terputus") }
    var remainingTimeText by remember { mutableStateOf("") }

    var waNumber by remember { mutableStateOf("6281234567890") }
    var packagesRaw by remember {
        mutableStateOf(
            "Paket 1 Jam | 60 Menit | Rp 1.000 | Cocok untuk tes jaringan\n" +
            "Paket 1 Hari | 24 Jam | Rp 5.000 | Internetan seharian penuh\n" +
            "Paket 1 Minggu | 7 Hari | Rp 15.000 | Hemat untuk penggunaan rutin\n" +
            "Paket 1 Bulan | 30 Hari | Rp 50.000 | Bebas akses sebulan penuh"
        )
    }
    var dataPackagesRaw by remember {
        mutableStateOf(
            "XL Reguler 800MB | 30 Hari | Rp 10.000 | Kuota utama 24 jam semua jaringan\n" +
            "Telkomsel 1GB All Jaringan | 28 Hari | Rp 14.000 | Kuota utama semua jaringan\n" +
            "Indosat Mini 3GB | 30 Hari | Rp 20.000 | Kuota utama 24 jam semua jaringan\n" +
            "AXIS Data Max 2.5GB | 30 Hari | Rp 17.000 | Kuota utama + apps 30 hari"
        )
    }

    var showPriceDialog by remember { mutableStateOf(false) }
    var showDataShopDialog by remember { mutableStateOf(false) }
    var customerPhone by remember { mutableStateOf("") }

    LaunchedEffect(isRunning) {
        statusText = if (isRunning) "Status: Terhubung (LailaNet Aktif)" else "Status: Terputus"
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write("""{"code":"GET_INFO","device_id":"$deviceId"}""".toByteArray())
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val settings = json.optJSONObject("settings")
                if (settings != null) {
                    waNumber = settings.optString("wa_number", waNumber)
                    packagesRaw = settings.optString("packages", packagesRaw)
                    if (settings.has("data_packages") && settings.optString("data_packages").isNotBlank()) {
                        dataPackagesRaw = settings.optString("data_packages")
                    }
                }
            } catch (_: Exception) { }
        }
    }

    BackHandler(enabled = true) {
        (context as? android.app.Activity)?.moveTaskToBack(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_lailanet),
            contentDescription = "Logo LailaNet",
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text("LailaNet", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text("Masukkan kode voucher untuk terhubung", fontSize = 14.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = voucherCode,
            onValueChange = { voucherCode = it.uppercase() },
            label = { Text("Kode Voucher") },
            singleLine = true,
            enabled = !isRunning && !isLoading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, letterSpacing = 2.sp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isRunning) {
                    onDisconnectClick()
                } else {
                    if (voucherCode.isBlank()) {
                        context.toast("Masukkan kode voucher terlebih dahulu")
                        return@Button
                    }
                    isLoading = true
                    statusText = "Memeriksa voucher..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.setRequestProperty("Content-Type", "application/json")
                                conn.connectTimeout = 10000
                                conn.readTimeout = 10000
                                conn.doOutput = true
                                val payload = """{"code":"${voucherCode.trim()}","device_id":"$deviceId"}"""
                                conn.outputStream.write(payload.toByteArray())
                                conn.inputStream.bufferedReader().readText()
                            } catch (e: Exception) {
                                """{"status":"error","message":"Gagal terhubung ke server. Cek internet Anda."}"""
                            }
                        }

                        isLoading = false
                        try {
                            val json = JSONObject(result)
                            val status = json.optString("status")
                            val settings = json.optJSONObject("settings")
                            if (settings != null) {
                                waNumber = settings.optString("wa_number", waNumber)
                                packagesRaw = settings.optString("packages", packagesRaw)
                                if (settings.has("data_packages") && settings.optString("data_packages").isNotBlank()) {
                                    dataPackagesRaw = settings.optString("data_packages")
                                }
                            }

                            if (status == "active") {
                                val config = json.optString("config")
                                remainingTimeText = json.optString("remaining_text", "")
                                prefs.edit().putString("saved_voucher", voucherCode.trim()).apply()
                                onConnectClick(config)
                            } else {
                                val msg = json.optString("message", "Voucher tidak valid")
                                statusText = "Status: $msg"
                                context.toast(msg)
                            }
                        } catch (e: Exception) {
                            statusText = "Status: Kesalahan membaca respon server"
                        }
                    }
                }
            },
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF2563EB)),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isRunning) "PUTUSKAN" else "HUBUNGKAN", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(statusText, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isRunning) Color(0xFF16A34A) else Color(0xFF475569), textAlign = TextAlign.Center)

        if (remainingTimeText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(10.dp)).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Masa Aktif Voucher: $remainingTimeText", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { showPriceDialog = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF2563EB)),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("Beli Voucher", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
            }

            OutlinedButton(
                onClick = { showDataShopDialog = true },
