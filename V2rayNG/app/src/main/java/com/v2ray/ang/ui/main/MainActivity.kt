package com.v2ray.ang.ui.main

import android.annotation.SuppressLint
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
        var adminWa by remember { mutableStateOf("6281234567890") }
        var packageList by remember {
            mutableStateOf(
                listOf(
                    VoucherPackage("Voucher 1 Jam", "60 Menit", "Rp 1.000", "Cocok untuk pemakaian cepat & darurat"),
                    VoucherPackage("Voucher 1 Hari", "24 Jam", "Rp 5.000", "Akses penuh seharian tanpa batas"),
                    VoucherPackage("Voucher 1 Minggu", "7 Hari", "Rp 15.000", "Lebih hemat untuk kebutuhan mingguan"),
                    VoucherPackage("Voucher 1 Bulan", "30 Hari", "Rp 75.000", "Paket paling hemat untuk pemakaian rutin")
                )
            )
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val storeData = fetchStoreConfig()
                if (storeData != null) {
                    adminWa = storeData.first
                    if (storeData.second.isNotEmpty()) {
                        packageList = storeData.second
                    }
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
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "LailaNet",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Masukkan kode voucher untuk terhubung",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = voucherCode,
                onValueChange = { voucherCode = it },
                enabled = !isRunning && !isLoading,
                singleLine = true,
                label = { Text("Kode Voucher") },
                placeholder = { Text("Contoh: LAILA-001") },
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

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isRunning) {
                        val input = voucherCode.trim()
                        releaseDeviceLock(input)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = currentStatusText,
                fontSize = 14.sp,
                fontWeight = if (isRunning) FontWeight.Medium else FontWeight.Normal,
                color = if (isRunning) Color(0xFF16A34A) else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            if (isRunning && expiredDate.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Masa Aktif Voucher: $expiredDate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF166534)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showPriceDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF2563EB)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Beli Voucher",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val pesan = "Halo Admin LailaNet, saya butuh bantuan terkait kendala aplikasi / kode voucher saya."
                        openWhatsApp(adminWa, pesan)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF16A34A)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Hubungi Admin",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }

                if (showPriceDialog) {
            AlertDialog(
                onDismissRequest = { showPriceDialog = false },
                title = {
                    Text(
                        text = "Pilih Paket Voucher",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ketuk paket di bawah ini untuk memesan langsung via WhatsApp Admin:",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        packageList.forEach { pkg ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPriceDialog = false
                                        val pesan = "Halo Admin LailaNet, saya ingin membeli:\n" +
                                            "• Paket: ${pkg.nama} (${pkg.durasi})\n" +
                                            "• Harga: ${pkg.harga}\n\n" +
                                            "Mohon info pembayarannya. Terima kasih."
                                        openWhatsApp(adminWa, pesan)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${pkg.nama} (${pkg.durasi})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = pkg.harga,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                    if (pkg.ket.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = pkg.ket,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPriceDialog = false }) {
                        Text("Tutup")
                    }
                }
            )
        }
    }

      private fun openWhatsApp(phone: String, message: String) {
        try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val waDomain = "api.whatsapp.com"
            val waUrl = "https://$waDomain/send?phone=$phone&text=$encodedMsg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
            startActivity(intent)
        } catch (e: Exception) {
            toast("Aplikasi WhatsApp tidak ditemukan di HP ini")
        }
    }

    private fun fetchStoreConfig(): Pair<String, List<VoucherPackage>>? {
        return try {
            val host = "lailanet-voucher.hendrytdr96"
            val url = "https://$host.workers.dev/paket"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val wa = json.optString("wa", "6281234567890")
                val arr = json.optJSONArray("packages")
                val list = mutableListOf<VoucherPackage>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            VoucherPackage(
                                nama = obj.optString("nama"),
                                durasi = obj.optString("durasi"),
                                harga = obj.optString("harga"),
                                ket = obj.optString("ket")
                            )
                        )
                    }
                }
                Pair(wa, list)
            } else {
                null
            }
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
                val expired = result.second ?: "Aktif"
                MmkvManager.encodeSettings("LAST_VOUCHER_CODE", voucherInput)
                MmkvManager.encodeSettings("LAST_VOUCHER_EXP", expired)
                MmkvManager.removeAllServer()
                mainViewModel.onAction(MainAction.ImportBatchConfig(v2rayConfig))

                delay(500)
                val serverList = MmkvManager.decodeServerList("")
                if (serverList.isNotEmpty()) {
                    val targetGuid = serverList.first()
                    MmkvManager.setSelectServer(targetGuid)
                    mainViewModel.updateSelectedGuid(targetGuid)
                    onState(false, "", expired)
                    requestServiceStart()
                } else {
                    onState(false, "Status: Format konfigurasi server salah", null)
                }
            }
        }
    }

    private fun fetchVoucherData(voucher: String): Pair<String?, String?> {
        val lower = voucher.lowercase()
        if (lower.startsWith("vmess://") ||
            lower.startsWith("vless://") ||
            lower.startsWith("trojan://") ||
            lower.startsWith("ss://") ||
            lower.startsWith("hysteria2://")
        ) {
            return Pair(voucher, "Akses Langsung")
        }

        return try {
            val host = "lailanet-voucher.hendrytdr96"
            val devId = fetchHardwareId()
            val apiUrl = "https://$host.workers.dev/?kode=$voucher&device=$devId"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = connection.responseCode
            val stream = if (code == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = JSONObject(responseText)

            if (code == HttpURLConnection.HTTP_OK && json.optString("status") == "ok") {
                Pair(json.getString("config"), json.optString("expired", "Aktif"))
            } else {
                Pair(null, json.optString("message", "Kode voucher tidak valid"))
            }
        } catch (e: Exception) {
            Pair(null, "Gagal terhubung ke server voucher")
        }
    }

    private fun releaseDeviceLock(voucher: String) {
        if (voucher.isEmpty() || voucher.contains("://")) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val host = "lailanet-voucher.hendrytdr96"
                val devId = fetchHardwareId()
                val logoutUrl = "https://$host.workers.dev/logout?kode=$voucher&device=$devId"
                (URL(logoutUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }.inputStream.close()
            } catch (_: Exception) { }
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
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
// AKHIR FILE
