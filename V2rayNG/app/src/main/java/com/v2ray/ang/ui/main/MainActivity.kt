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
import androidx.compose.foundation.horizontalScroll
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VoucherPackage(
    val nama: String,
    val harga: String
)

data class DigiflazzProduct(
    val sku: String,
    val nama: String,
    val brand: String,
    val kategori: String,
    val harga: Int,
    val ket: String
)

data class QrisOrderResult(
    val orderId: String,
    val qrString: String,
    val paymentUrl: String
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
        var showGuideDialog by remember { mutableStateOf(false) }
        var targetPhone by remember { mutableStateOf("") }
        var adminWa by remember { mutableStateOf("6285890014896") }

        var digiflazzProducts by remember { mutableStateOf<List<DigiflazzProduct>>(emptyList()) }
        var isLoadingProducts by remember { mutableStateOf(false) }
        var selectedCategory by remember { mutableStateOf("Data") }
        var selectedBrand by remember { mutableStateOf("SEMUA") }
        var isCreatingOrder by remember { mutableStateOf(false) }
        var activeOrder by remember { mutableStateOf<Pair<DigiflazzProduct, QrisOrderResult>?>(null) }

        var packageList by remember {
            mutableStateOf(
                listOf(
                    VoucherPackage("Voucher Hemat 1 Hari (Stabil & Irit Kuota)", "Rp 2.000"),
                    VoucherPackage("Voucher Stabil 7 Hari (Gaming & Sosmed)", "Rp 5.000"),
                    VoucherPackage("Voucher Puas 30 Hari (Prioritas Jakarta)", "Rp 15.000")
                )
            )
        }

        LaunchedEffect(Unit) {
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
                }
            }
        }

        LaunchedEffect(showDataDialog, selectedCategory) {
            if (showDataDialog) {
                isLoadingProducts = true
                selectedBrand = "SEMUA"
                val list = withContext(Dispatchers.IO) {
                    fetchDigiflazzProducts(category = selectedCategory)
                }
                digiflazzProducts = list
                isLoadingProducts = false
            }
        }

        val currentStatusText = when {
            isLoading -> statusMessage
            isRunning -> "Status: Terhubung (Pengaman & Penghemat Aktif)"
            statusMessage.isNotEmpty() -> statusMessage
            else -> "Status: Terputus"
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F7FA))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Logo LailaNet",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "LailaNet",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = "VPN Penghemat Kuota & Pengaman Wi-Fi",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = voucherCode,
                onValueChange = { voucherCode = it },
                enabled = !isRunning && !isLoading,
                singleLine = true,
                label = { Text("Masukan Kode Voucher LailaNet") },
                placeholder = { Text("Ketik Kode Voucher Di sini") },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = when {
                        isLoading -> "MEMPROSES..."
                        isRunning -> "PUTUSKAN KONEKSI"
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
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text(
                        text = "Beli Voucher",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                }

                OutlinedButton(
                    onClick = { showDataDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text(
                        text = "Beli Paket & Pulsa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7C3AED)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showGuideDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "Panduan Pakai",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0284C7)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val devId = fetchHardwareId()
                        val pesan = "Halo Admin LailaNet, saya butuh bantuan terkait layanan / voucher.\n• ID Perangkat: $devId"
                        openWhatsApp(adminWa, pesan)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF16A34A)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "Hubungi Admin",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF16A34A)
                    )
                }
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
                    text = "Penting: LailaNet adalah layanan VPN pengaman & penghemat kuota resmi (bukan internet gratis). Pastikan kartu Anda memiliki kuota aktif atau terhubung ke Wi-Fi.",
                    fontSize = 12.sp,
                    color = Color(0xFF1E40AF),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }

                if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = {
                    Text(
                        text = "Panduan Penggunaan LailaNet",
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
                            text = "1. Wajib Memiliki Kuota Aktif:\nLailaNet bekerja di atas jaringan internet kartu seluler (Telkomsel/by.U, Indosat, XL, Tri, Smartfren) atau Wi-Fi aktif Anda.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155),
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "2. Fungsi Penghemat Kuota & Stabilizer:\nLailaNet menjaga kecepatan internet tetap stabil dan mencegah aplikasi video menyedot kuota berlebihan secara mendadak, sehingga paket mingguan/bulanan lebih awet.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155),
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "3. Beli Paket Otomatis via QRIS:\nPesan kuota atau pulsa langsung di menu \"Beli Paket & Pulsa\" dengan pembayaran QRIS otomatis.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155),
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) {
                        Text("Mengerti")
                    }
                }
            )
        }

        if (showPriceDialog) {
            AlertDialog(
                onDismissRequest = { showPriceDialog = false },
                title = {
                    Text(
                        text = "Pilih Voucher LailaNet",
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
                            text = "Khusus bagi Anda yang sudah memiliki kuota operator / Wi-Fi sendiri. Ketuk paket untuk memesan via WhatsApp Admin:",
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
                                        val devId = fetchHardwareId()
                                        val pesan = "Halo Admin LailaNet, saya ingin membeli Voucher:\n• Paket: ${pkg.nama}\n• Harga: ${pkg.harga}\n• ID Perangkat: $devId\n\nMohon info pembayarannya. Terima kasih."
                                        openWhatsApp(adminWa, pesan)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pkg.nama,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pkg.harga,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF2563EB)
                                    )
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

        if (showDataDialog) {
            val categories = listOf("Data", "Pulsa", "Games", "E-Money")
            val availableBrands = remember(digiflazzProducts) {
                listOf("SEMUA") + digiflazzProducts.map { it.brand }.distinct()
            }
            val filteredProducts = remember(digiflazzProducts, selectedBrand) {
                if (selectedBrand == "SEMUA") digiflazzProducts
                else digiflazzProducts.filter { it.brand.equals(selectedBrand, ignoreCase = true) }
            }

            AlertDialog(
                onDismissRequest = {
                    if (!isCreatingOrder) showDataDialog = false
                },
                title = {
                    Text(
                        text = "Beli Paket & Pulsa (QRIS Otomatis)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetPhone,
                            onValueChange = { targetPhone = it },
                            singleLine = true,
                            enabled = !isCreatingOrder,
                            label = { Text("Nomor HP / ID Tujuan") },
                            placeholder = { Text("Contoh: 08123456789") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { if (!isCreatingOrder) selectedCategory = cat },
                                    label = { Text(cat, fontSize = 12.sp) }
                                )
                            }
                        }

                        if (availableBrands.size > 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableBrands.forEach { br ->
                                    FilterChip(
                                        selected = selectedBrand == br,
                                        onClick = { if (!isCreatingOrder) selectedBrand = br },
                                        label = { Text(br, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        if (isCreatingOrder) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Membuat tagihan QRIS Duitku...", fontSize = 13.sp)
                            }
                              } else if (isLoadingProducts) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Memuat daftar produk...", fontSize = 13.sp)
                            }
                        } else if (filteredProducts.isEmpty()) {
                            Text(
                                text = "Belum ada produk tersedia pada kategori ini.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Text(
                                text = "Isi nomor tujuan, lalu ketuk produk untuk bayar via QRIS:",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            filteredProducts.forEach { prod ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val tujuan = targetPhone.trim()
                                            if (tujuan.length < 5) {
                                                toast("Masukkan nomor HP / ID tujuan terlebih dahulu")
                                                return@clickable
                                            }
                                            isCreatingOrder = true
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val orderRes = createQrisOrder(
                                                    sku = prod.sku,
                                                    tujuan = tujuan,
                                                    harga = prod.harga,
                                                    nama = prod.nama
                                                )
                                                withContext(Dispatchers.Main) {
                                                    isCreatingOrder = false
                                                    if (orderRes != null && orderRes.paymentUrl.isNotEmpty()) {
                                                        showDataDialog = false
                                                        activeOrder = Pair(prod, orderRes)
                                                        openBrowserUrl(orderRes.paymentUrl)
                                                    } else {
                                                        toast("Gagal membuat tagihan QRIS. Coba lagi.")
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = prod.brand,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7C3AED)
                                            )
                                            Text(
                                                text = prod.nama,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            if (prod.ket.isNotEmpty()) {
                                                Text(
                                                    text = prod.ket,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatRupiah(prod.harga),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isCreatingOrder,
                        onClick = { showDataDialog = false }
                    ) {
                        Text("Tutup")
                    }
                }
            )
        }

        activeOrder?.let { (prod, order) ->
            AlertDialog(
                onDismissRequest = { activeOrder = null },
                title = {
                    Text(
                        text = "Tagihan QRIS Dibuat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ID Pesanan: ${order.orderId}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Produk: ${prod.nama}", fontSize = 13.sp)
                        Text("Total Bayar: ${formatRupiah(prod.harga)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        Text(
                            text = "Silakan selesaikan pembayaran QRIS pada halaman Duitku yang terbuka. Produk otomatis diproses setelah pembayaran berhasil.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { openBrowserUrl(order.paymentUrl) }) {
                        Text("Buka Halaman QRIS")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeOrder = null }) {
                        Text("Selesai")
                    }
                }
            )
        }

    private fun formatRupiah(amount: Int): String {
        val formatter = NumberFormat.getInstance(Locale("in", "ID"))
        return "Rp ${formatter.format(amount)}"
    }
 }

    private fun openBrowserUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("Tidak dapat membuka browser pembayaran")
        }
    }

    private fun fetchDigiflazzProducts(
        brand: String? = null,
        category: String? = null
    ): List<DigiflazzProduct> {
        return try {
            val params = mutableListOf<String>()
            if (!brand.isNullOrBlank()) params.add("brand=${URLEncoder.encode(brand, "UTF-8")}")
            if (!category.isNullOrBlank()) params.add("category=${URLEncoder.encode(category, "UTF-8")}")
            val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""

            val connection = (URL("$workerBaseUrl/api/digiflazz/products$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (json.optString("status") == "ok") {
                    val arr = json.optJSONArray("products")
                    val list = mutableListOf<DigiflazzProduct>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                DigiflazzProduct(
                                    sku = obj.optString("sku"),
                                    nama = obj.optString("nama"),
                                    brand = obj.optString("brand"),
                                    kategori = obj.optString("kategori"),
                                    harga = obj.optInt("harga"),
                                    ket = obj.optString("ket")
                                )
                            )
                        }
                    }
                    return list
                }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun createQrisOrder(
        sku: String,
        tujuan: String,
        harga: Int,
        nama: String
    ): QrisOrderResult? {
        return try {
            val payload = JSONObject().apply {
                put("sku", sku)
                put("tujuan", tujuan)
                put("harga", harga)
                put("nama", nama)
            }.toString()

            val connection = (URL("$workerBaseUrl/api/order/qris").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
            }

            connection.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (json.optString("status") == "ok") {
                    return QrisOrderResult(
                        orderId = json.optString("orderId"),
                        qrString = json.optString("qrString"),
                        paymentUrl = json.optString("paymentUrl")
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
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
        var cleanPhone = phone.replace(Regex("[^0-9]"), "")
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "62" + cleanPhone.substring(1)
        }
        val encodedMsg = URLEncoder.encode(message, "UTF-8")

        try {
            val directUri = Uri.parse("whatsapp://send?phone=$cleanPhone&text=$encodedMsg")
            startActivity(Intent(Intent.ACTION_VIEW, directUri))
        } catch (_: Exception) {
            try {
                val webUri = Uri.parse("https://whatsapp.com")
                startActivity(Intent(Intent.ACTION_VIEW, webUri))
            } catch (_: Exception) {
                toast("Aplikasi WhatsApp tidak ditemukan di HP ini")
            }
        }
    }

    private fun fetchStoreConfig(): Triple<String, List<VoucherPackage>, List<Any>>? {
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
                Triple(wa, vpList, emptyList())
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
                val rawInfo = result.second ?: "Aktif"
                val paketLabel = when {
                    v2rayConfig.contains(":443") -> "Paket Hemat • 350 Kbps"
                    v2rayConfig.contains(":8443") -> "Paket Reguler • 650 Kbps"
                    v2rayConfig.contains(":2053") -> "Paket Turbo • 900 Kbps"
                    v2rayConfig.contains(":2083") -> "Paket VIP • 1300 Kbps"
                    v2rayConfig.contains(":2087") -> "Paket Sultan • 1800 Kbps"
                    else -> "Paket Standar"
                }
                val infoText = "$paketLabel | $rawInfo"
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
                    return Triple(null, "Link server belum diisi admin pada voucher ini", 0L)
                }
                val isUnlimited = json.optBoolean("is_unlimited", false)
                val expMs = json.optLong("expired_at", 0L)
                val validUntilMs = json.optLong("valid_until", expMs)
                val remMs = json.optLong("remaining_ms", 0L)
                val remUptimeMs = json.optLong("remaining_uptime_ms", 0L)

                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val infoText = if (isUnlimited) {
                    "Aktif | Berlaku s/d: ${fmt.format(Date(validUntilMs))}"
                } else {
                    val sisaMenit = Math.ceil(remUptimeMs / 60000.0).toInt()
                    "Sisa Waktu: ${sisaMenit} Menit | Berlaku s/d: ${fmt.format(Date(validUntilMs))}"
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
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
