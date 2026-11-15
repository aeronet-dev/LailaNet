package com.v2ray.ang.ui.main

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
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
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VoucherPackage(
    val nama: String,
    val harga: String
)

data class DataPackage(
    val operator: String,
    val nama: String,
    val harga: String
)

class MainActivity : HelperBaseComponentActivity() {

    private val workerBaseUrl = "https://api.lailanet.my.id"

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    @SuppressLint("HardwareIds")
    private fun fetchHardwareId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }

        val uiState by mainViewModel.uiState.collectAsState()
        val isRunning = uiState.isRunning

        var voucherCode by remember {
            mutableStateOf(MmkvManager.decodeSettingsString("LAST_VOUCHER_CODE") ?: "")
        }
        var expiredDate by remember {
            mutableStateOf(MmkvManager.decodeSettingsString("LAST_VOUCHER_EXP") ?: "")
        }
        var isLoading by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("") }

        var showPriceDialog by remember { mutableStateOf(false) }
        var showDataDialog by remember { mutableStateOf(false) }
        var targetPhone by remember { mutableStateOf("") }
        var adminWa by remember { mutableStateOf("6285890014896") }

        var packageList by remember {
            mutableStateOf(
                listOf(
                    VoucherPackage("Voucher 1 Hari Unlimited", "Rp 5.000"),
                    VoucherPackage("Voucher 7 Hari Unlimited", "Rp 15.000"),
                    VoucherPackage("Voucher 30 Hari Unlimited", "Rp 50.000")
                )
            )
        }

        var dataPackageList by remember {
            mutableStateOf(
                listOf(
                    DataPackage("Telkomsel", "Kuota 5GB 30 Hari", "Rp 20.000"),
                    DataPackage("XL / Axis", "Kuota 10GB 30 Hari", "Rp 28.000")
                )
            )
        }

        LaunchedEffect(Unit) {
            // Cek otomatis jika aplikasi dibuka setelah batas waktu lewat
            val endMs = MmkvManager.decodeSettingsLong("LAST_VOUCHER_END_MS", 0L)
            if (endMs > 0L && System.currentTimeMillis() >= endMs && isRunning) {
                MmkvManager.encodeSettings("LAST_VOUCHER_EXP", "")
                MmkvManager.encodeSettings("LAST_VOUCHER_END_MS", 0L)
                LauncherManager.stopService(this@MainActivity)
                toast("Masa aktif voucher telah habis")
            }

            withContext(Dispatchers.IO) {
                val cfg = fetchStoreConfig()
                if (cfg != null) {
                    adminWa = cfg.first
                    if (cfg.second.isNotEmpty()) packageList = cfg.second
                    if (cfg.third.isNotEmpty()) dataPackageList = cfg.third
                }
            }
        }

        val currentStatusText = when {
            isLoading -> statusMessage
            isRunning -> "Status: Terhubung (LailaNet Aktif)"
            statusMessage.isNotEmpty() -> statusMessage
            else -> "Status: Terputus"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F7FA))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Logo LailaNet",
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("LailaNet", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text("Masukkan kode voucher untuk terhubung", fontSize = 14.sp, color = Color(0xFF64748B))

            Spacer(modifier = Modifier.height(22.dp))

            OutlinedTextField(
                value = voucherCode,
                onValueChange = { voucherCode = it },
                enabled = !isRunning && !isLoading,
                singleLine = true,
                label = { Text("Kode Voucher") },
                placeholder = { Text("Contoh: LAILA01") },
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (isRunning) {
                        val input = voucherCode.trim()
                        cancelExpireAlarm()
                        pauseVoucherSession(input)
                        statusMessage = ""
                        LauncherManager.stopService(this@MainActivity)
                    } else {
                        val input = voucherCode.trim()
                        if (input.isEmpty()) {
                            toast("Masukkan kode voucher terlebih dahulu")
                            return@Button
                        }
                        processVoucherAndConnect(input) { loading, msg, exp ->
                            isLoading = loading
                            statusMessage = msg
                            if (exp != null) expiredDate = exp
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF2563EB)
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = when {
                        isLoading -> "MEMPROSES..."
                        isRunning -> "PUTUSKAN"
                        else -> "HUBUNGKAN"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentStatusText,
                fontSize = 14.sp,
                fontWeight = if (isRunning) FontWeight.Medium else FontWeight.Normal,
                color = if (isRunning) Color(0xFF16A34A) else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

                        if (isRunning && expiredDate.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = expiredDate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF166534),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showPriceDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF2563EB)),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Beli Voucher", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                }

                OutlinedButton(
                    onClick = { showDataDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Beli Paket Data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C3AED))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    val pesan = "Halo Admin LailaNet, saya butuh bantuan terkait aplikasi / voucher."
                    openWhatsApp(adminWa, pesan)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF16A34A)),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("Hubungi Admin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Info: Pastikan HP terhubung ke Wi-Fi atau kartu seluler dengan kuota aktif / paket khusus agar voucher dapat terhubung.",
                    fontSize = 12.sp,
                    color = Color(0xFF1E40AF),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }

        if (showPriceDialog) {
            AlertDialog(
                onDismissRequest = { showPriceDialog = false },
                title = { Text("Pilih Paket Voucher", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Ketuk paket di bawah untuk memesan via WhatsApp Admin:", fontSize = 12.sp, color = Color(0xFF64748B))
                        packageList.forEach { pkg ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showPriceDialog = false
                                    val pesan = "Halo Admin LailaNet, saya ingin membeli:\n• Paket: ${pkg.nama}\n• Harga: ${pkg.harga}\n\nMohon info pembayarannya. Terima kasih."
                                    openWhatsApp(adminWa, pesan)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pkg.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                    Text(pkg.harga, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showPriceDialog = false }) { Text("Tutup") } }
            )
        }

        if (showDataDialog) {
            AlertDialog(
                onDismissRequest = { showDataDialog = false },
                title = { Text("Toko Paket Data", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetPhone,
                            onValueChange = { targetPhone = it },
                            singleLine = true,
                            label = { Text("Nomor HP Tujuan") },
                            placeholder = { Text("Contoh: 081234567890") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Isi nomor HP di atas, lalu ketuk paket untuk order via WhatsApp:", fontSize = 12.sp, color = Color(0xFF64748B))
                        dataPackageList.forEach { dp ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val phone = targetPhone.trim()
                                    if (phone.length < 9) {
                                        toast("Masukkan nomor HP tujuan yang valid terlebih dahulu")
                                        return@clickable
                                    }
                                    showDataDialog = false
                                    val pesan = "Halo Admin LailaNet, saya mau beli Paket Data:\n• Operator: ${dp.operator}\n• Paket: ${dp.nama}\n• Harga: ${dp.harga}\n• Nomor Tujuan: $phone\n\nMohon segera diproses. Terima kasih."
                                    openWhatsApp(adminWa, pesan)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dp.operator, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                                        Text(dp.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                    }
                                    Text(dp.harga, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDataDialog = false }) { Text("Tutup") } }
            )
        }
    }
    
    private fun scheduleExpireAlarm(remainingMs: Long) {
        if (remainingMs <= 0L) return
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val expIntent = Intent(this, VoucherExpireReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            expIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + remainingMs
        alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun cancelExpireAlarm() {
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val expIntent = Intent(this, VoucherExpireReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            expIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(pendingIntent)
    }

    private fun pauseVoucherSession(voucher: String) {
        if (voucher.isEmpty() || voucher.contains("://")) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val devId = URLEncoder.encode(fetchHardwareId(), "UTF-8")
                val encCode = URLEncoder.encode(voucher, "UTF-8")
                val pauseUrl = "$workerBaseUrl/api/voucher/pause?code=$encCode&device_id=$devId"
                (URL(pauseUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 5000
                    readTimeout = 5000
                }.inputStream.close()
            } catch (_: Exception) { }
        }
    }

    private fun openWhatsApp(phone: String, message: String) {
        try {
            var cleanPhone = phone.replace(Regex("[^0-9]"), "")
            if (cleanPhone.startsWith("0")) {
                cleanPhone = "62" + cleanPhone.substring(1)
            }
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val waUrl = "https://wa.me"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
        } catch (e: Exception) {
            toast("Aplikasi WhatsApp tidak ditemukan di HP ini")
        }
    }

    private fun fetchStoreConfig(): Triple<String, List<VoucherPackage>, List<DataPackage>>? {
        return try {
            val connection = (URL("$workerBaseUrl/api/config").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val wa = json.optString("wa_admin", "6285890014896")

                val vpArr = json.optJSONArray("voucher_packages")
                val vpList = mutableListOf<VoucherPackage>()
                if (vpArr != null) {
                    for (i in 0 until vpArr.length()) {
                        val obj = vpArr.getJSONObject(i)
                        vpList.add(VoucherPackage(obj.optString("name"), obj.optString("price")))
                    }
                }

                val dpArr = json.optJSONArray("data_packages")
                val dpList = mutableListOf<DataPackage>()
                if (dpArr != null) {
                    for (i in 0 until dpArr.length()) {
                        val obj = dpArr.getJSONObject(i)
                        dpList.add(
                            DataPackage(
                                operator = obj.optString("operator"),
                                nama = obj.optString("name"),
                                harga = obj.optString("price")
                            )
                        )
                    }
                }
                Triple(wa, vpList, dpList)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun processVoucherAndConnect(
        voucherInput: String,
        onState: (Boolean, String, String?) -> Unit
    ) {
        onState(true, "Status: Memeriksa kode voucher...", null)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = fetchVoucherData(voucherInput)
            withContext(Dispatchers.Main) {
                if (result.first == null) {
                    val errMsg = result.second ?: "Kode voucher tidak valid"
                    onState(false, "Status: $errMsg", null)
                    toast(errMsg)
                    return@withContext
                }
                val v2rayConfig = result.first!!
                val infoText = result.second ?: "Aktif"
                val remainingMs = result.third

                MmkvManager.encodeSettings("LAST_VOUCHER_CODE", voucherInput)
                MmkvManager.encodeSettings("LAST_VOUCHER_EXP", infoText)
                MmkvManager.encodeSettings("LAST_VOUCHER_END_MS", System.currentTimeMillis() + remainingMs)
                MmkvManager.removeAllServer()
                mainViewModel.onAction(MainAction.ImportBatchConfig(v2rayConfig))

                delay(500)
                val serverList = MmkvManager.decodeServerList("")
                if (serverList.isNotEmpty()) {
                    val targetGuid = serverList.first()
                    MmkvManager.setSelectServer(targetGuid)
                    mainViewModel.updateSelectedGuid(targetGuid)
                    onState(false, "", infoText)
                    requestServiceStart()

                    if (remainingMs > 0) {
                        scheduleExpireAlarm(remainingMs)
                        lifecycleScope.launch {
                            delay(remainingMs)
                            if (mainViewModel.uiState.value.isRunning) {
                                LauncherManager.stopService(this@MainActivity)
                                toast("Waktu voucher LailaNet telah habis")
                            }
                        }
                    }
                } else {
                    onState(false, "Status: Format konfigurasi server salah", null)
                }
            }
        }
    }

    private fun fetchVoucherData(voucher: String): Triple<String?, String?, Long> {
        val lower = voucher.lowercase()
        if (lower.startsWith("vmess://") || lower.startsWith("vless://") ||
            lower.startsWith("trojan://") || lower.startsWith("ss://")
        ) {
            return Triple(voucher, "Akses Langsung", 0L)
        }

        return try {
            val devId = URLEncoder.encode(fetchHardwareId(), "UTF-8")
            val encCode = URLEncoder.encode(voucher, "UTF-8")
            val apiUrl = "$workerBaseUrl/api/voucher/verify?code=$encCode&device_id=$devId"

            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val code = connection.responseCode
            val stream = if (code == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = JSONObject(responseText)

            if (code == HttpURLConnection.HTTP_OK && json.optString("status") == "ok") {
                val link = json.optString("vless_link", "")
                if (link.isEmpty()) {
                    return Triple(null, "Link VLESS belum diisi admin pada voucher ini", 0L)
                }
                val isUnlimited = json.optBoolean("is_unlimited", false)
                val expMs = json.optLong("expired_at", 0L)
                val validUntilMs = json.optLong("valid_until", expMs)
                val remMs = json.optLong("remaining_ms", 0L)
                val remUptimeMs = json.optLong("remaining_uptime_ms", 0L)

                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val infoText = if (isUnlimited) {
                    "Unlimited Waktu | Berlaku s/d: ${fmt.format(Date(validUntilMs))}"
                } else {
                    val sisaMenit = Math.ceil(remUptimeMs / 60000.0).toInt()
                    "Sisa Kuota: ${sisaMenit} Menit | Berlaku s/d: ${fmt.format(Date(validUntilMs))}"
                }
                Triple(link, infoText, remMs)
            } else {
                Triple(null, json.optString("message", "Kode voucher tidak valid"), 0L)
            }
        } catch (e: Exception) {
            Triple(null, "Error: ${e.javaClass.simpleName} - ${e.message}", 0L)
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast("Profil server belum terpilih")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
