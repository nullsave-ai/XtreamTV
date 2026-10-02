package com.xtreamtv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.design.*
import com.xtreamtv.ui.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { if (msg != null) { delay(2_500); msg = null } }
    val first = remember { FocusRequester() }
    RequestFocusOnce(first)
    val timeouts = listOf(4, 6, 8, 12)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.u, vertical = 28.u)) {
        UText("الإعدادات", 26, FontWeight.Bold)
        Spacer(Modifier.height(16.u))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.u), contentPadding = PaddingValues(vertical = 6.u)) {
            item {
                SettingRow("صيغة البث المباشر", if (s.liveFormat == "m3u8") "HLS (m3u8) — يتحول تلقائيًا إلى TS عند الفشل" else "MPEG-TS — يتحول تلقائيًا إلى HLS عند الفشل", first) {
                    vm.updateSettings { it.copy(liveFormat = if (it.liveFormat == "m3u8") "ts" else "m3u8") }
                }
            }
            item {
                SettingRow("إخفاء قائمة القنوات تلقائيًا بعد", "${s.panelTimeoutSec} ثوانٍ") {
                    vm.updateSettings { it.copy(panelTimeoutSec = timeouts[(timeouts.indexOf(it.panelTimeoutSec) + 1).mod(timeouts.size)]) }
                }
            }
            item {
                SettingRow("فتح آخر قناة عند دخول البث", if (s.resumeLast) "مفعّل" else "متوقف") {
                    vm.updateSettings { it.copy(resumeLast = !it.resumeLast) }
                }
            }
            item { SettingRow("تحديث البيانات الآن", "إعادة تحميل القنوات والأفلام والمسلسلات من السيرفر") { vm.refresh(); msg = "جارٍ تحديث البيانات…" } }
            item { SettingRow("مسح الكاش", "حذف البيانات المحفوظة مؤقتًا (المفضلة والحسابات تبقى)") { vm.clearCache(); msg = "تم مسح الكاش" } }
            item { SettingRow("حول التطبيق", "Xtream TV 1.0 — Kotlin • Compose • Media3") { } }
        }
        msg?.let { Spacer(Modifier.height(10.u)); UText(it, 14, FontWeight.SemiBold, C.accent) }
    }
}

@Composable
private fun SettingRow(title: String, value: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(onClick, Modifier.fillMaxWidth().height(64.u), shape = RoundedCornerShape(14.u), focusScale = 1.02f, background = C.glass, focusRequester = focusRequester) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.u), verticalArrangement = Arrangement.Center) {
            UText(title, 16, FontWeight.Bold)
            UText(value, 12, color = C.text2)
        }
    }
}
