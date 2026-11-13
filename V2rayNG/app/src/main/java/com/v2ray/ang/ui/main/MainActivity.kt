package com.v2ray.ang.ui.main

import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
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
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LailaNet",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Masukkan kode voucher untuk terhubung",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(36.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isRunning) {
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
                    .height(60.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentStatusText,
                fontSize = 14.sp,
                fontWeight = if (isRunning) FontWeight.Medium else FontWeight.Normal,
                color = if (isRunning) Color(0xFF16A34A) else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            if (isRunning && expiredDate.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
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
                if (result == null) {
                    onState(false, "Status: Kode voucher tidak valid atau kedaluwarsa", null)
                    toast("Kode voucher tidak valid")
                    return@withContext
                }
                val (v2rayConfig, expired) = result
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

    private fun fetchVoucherData(voucher: String): Pair<String, String>? {
        val lower = voucher.lowercase()
        if (lower.startsWith("vmess://") ||
            lower.startsWith("vless://") ||
            lower.startsWith("trojan://") ||
            lower.startsWith("ss://") ||
            lower.startsWith("hysteria2://")
        ) {
            return Pair(voucher, "Akses Langsung (Non-Voucher)")
        }

                return try {
            val host = "lailanet-voucher.hendrytdr96"
val apiUrl = "https://$host.workers.dev/?kode=$voucher"

            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optString("status") == "ok") {
                    val cfg = json.getString("config")
                    val exp = json.optString("expired", "Aktif")
                    Pair(cfg, exp)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
