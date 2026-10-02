package com.xtreamtv.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtreamtv.core.Fmt
import com.xtreamtv.design.*
import com.xtreamtv.domain.Account
import com.xtreamtv.domain.AccountInfo
import com.xtreamtv.domain.normalizeServer
import com.xtreamtv.ui.AppViewModel
import com.xtreamtv.ui.nav.Nav
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(vm: AppViewModel, nav: Nav) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<Account?>(null) }   // edit target
    var adding by remember { mutableStateOf(accounts.isEmpty()) }
    var actions by remember { mutableStateOf<Account?>(null) }
    var confirmDelete by remember { mutableStateOf<Account?>(null) }
    var statusDialog by remember { mutableStateOf<String?>(null) }
    val addFocus = remember { FocusRequester() }
    RequestFocusOnce(addFocus)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.u, vertical = 28.u)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                UText("الحسابات", 26, FontWeight.Bold)
                UText("أضف أكثر من حساب Xtream وبدّل بينها في أي وقت.", 13, color = C.text2)
            }
            TvButton("إضافة حساب", { adding = true }, icon = Ic.Plus, primary = true, focusRequester = addFocus)
        }
        Spacer(Modifier.height(18.u))
        if (accounts.isEmpty()) EmptyMessage("لا توجد حسابات بعد.\nأضف حسابك للبدء.", icon = Ic.User)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.u), contentPadding = PaddingValues(vertical = 6.u)) {
            items(accounts, key = { it.id }) { a ->
                val active = a.id == activeId
                FocusBox({ actions = a }, Modifier.fillMaxWidth().height(68.u), shape = RoundedCornerShape(14.u), focusScale = 1.02f, background = C.glass) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.u), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.User, 24, if (active) C.accent else C.text2)
                        Spacer(Modifier.width(16.u))
                        Column(Modifier.weight(1f)) {
                            UText(a.name, 17, FontWeight.Bold)
                            UText("${a.username} • ${a.server.removePrefix("http://").removePrefix("https://")}", 12, color = C.text3)
                        }
                        if (active) UText("نشط", 12, FontWeight.Bold, C.ok)
                    }
                }
            }
        }
    }

    if (adding) AccountForm(vm, null, onDismiss = { adding = false }, isFirst = accounts.isEmpty())
    form?.let { AccountForm(vm, it, onDismiss = { form = null }, isFirst = false) }

    actions?.let { a ->
        TvDialog({ actions = null }, 400) {
            UText(a.name, 20, FontWeight.Bold)
            Spacer(Modifier.height(14.u))
            val first = remember { FocusRequester() }
            RequestFocusOnce(first)
            Column(verticalArrangement = Arrangement.spacedBy(8.u)) {
                TvButton(if (a.id == activeId) "الحساب النشط حاليًا" else "تفعيل هذا الحساب", {
                    vm.switchAccount(a.id); actions = null
                }, Modifier.fillMaxWidth(), icon = Ic.Check, primary = a.id != activeId, focusRequester = first)
                TvButton("فحص حالة الاشتراك", {
                    actions = null; statusDialog = "جارٍ الفحص…"
                    scope.launch {
                        statusDialog = vm.checkAccount(a).fold({ describe(it) }, { it.message ?: "فشل الفحص" })
                    }
                }, Modifier.fillMaxWidth(), icon = Ic.Info)
                TvButton("تعديل", { form = a; actions = null }, Modifier.fillMaxWidth(), icon = Ic.Edit)
                TvButton("حذف", { confirmDelete = a; actions = null }, Modifier.fillMaxWidth(), icon = Ic.Trash)
            }
        }
    }

    confirmDelete?.let { a ->
        TvDialog({ confirmDelete = null }, 400) {
            UText("حذف الحساب؟", 20, FontWeight.Bold)
            Spacer(Modifier.height(8.u))
            UText("سيتم حذف \"${a.name}\" من هذا الجهاز. لن يتأثر اشتراكك لدى المزوّد.", 14, color = C.text2, maxLines = 3)
            Spacer(Modifier.height(16.u))
            val fr = remember { FocusRequester() }
            RequestFocusOnce(fr)
            Row(horizontalArrangement = Arrangement.spacedBy(10.u)) {
                TvButton("نعم، احذف", { vm.deleteAccount(a.id); confirmDelete = null }, primary = true, focusRequester = fr)
                TvButton("إلغاء", { confirmDelete = null })
            }
        }
    }

    statusDialog?.let { msg ->
        TvDialog({ statusDialog = null }, 400) {
            UText("حالة الحساب", 20, FontWeight.Bold)
            Spacer(Modifier.height(10.u))
            UText(msg, 15, color = C.text2, maxLines = 8)
            Spacer(Modifier.height(16.u))
            val fr = remember { FocusRequester() }
            RequestFocusOnce(fr)
            TvButton("إغلاق", { statusDialog = null }, focusRequester = fr)
        }
    }
}

private fun describe(i: AccountInfo): String = buildString {
    appendLine("الحالة: ${i.status ?: "غير معروفة"}")
    appendLine("ينتهي في: ${i.expiry?.let { Fmt.date(it) } ?: "غير محدد"}")
    appendLine("الاتصالات: ${i.activeConnections ?: "?"} / ${i.maxConnections ?: "?"}")
}

@Composable
private fun AccountForm(vm: AppViewModel, edit: Account?, onDismiss: () -> Unit, isFirst: Boolean) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var server by remember { mutableStateOf(edit?.server ?: "") }
    var user by remember { mutableStateOf(edit?.username ?: "") }
    var pass by remember { mutableStateOf(edit?.password ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (saving) return
        if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "املأ رابط السيرفر واسم المستخدم وكلمة المرور"; return }
        val acc = Account(
            id = edit?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { user }, server = normalizeServer(server), username = user.trim(), password = pass.trim(),
        )
        saving = true; error = null
        scope.launch {
            val err = vm.saveAccount(acc, makeActive = edit == null || isFirst)
            saving = false
            if (err == null) onDismiss() else error = err
        }
    }

    TvDialog(onDismiss, 520) {
        UText(if (edit == null) "إضافة حساب" else "تعديل الحساب", 20, FontWeight.Bold)
        Spacer(Modifier.height(12.u))
        val first = remember { FocusRequester() }
        RequestFocusOnce(first)
        TvTextField(name, { name = it }, "اسم الحساب (اختياري)", Modifier.focusRequesterIf(first))
        Spacer(Modifier.height(8.u))
        TvTextField(server, { server = it }, "رابط السيرفر  (مثال: http://example.com:8080)", ltr = true, keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(8.u))
        TvTextField(user, { user = it }, "اسم المستخدم", ltr = true)
        Spacer(Modifier.height(8.u))
        TvTextField(pass, { pass = it }, "كلمة المرور", ltr = true, last = true, onDone = { submit() })
        error?.let { Spacer(Modifier.height(8.u)); UText(it, 13, color = C.danger, maxLines = 3) }
        Spacer(Modifier.height(16.u))
        Row(horizontalArrangement = Arrangement.spacedBy(10.u), verticalAlignment = Alignment.CenterVertically) {
            TvButton(if (saving) "جارٍ التحقق…" else "اختبار وحفظ", { submit() }, icon = Ic.Check, primary = true)
            TvButton("إلغاء", onDismiss)
            if (saving) Spinner(24)
        }
    }
}

private fun Modifier.focusRequesterIf(fr: FocusRequester): Modifier = this.then(Modifier.focusRequester(fr))
