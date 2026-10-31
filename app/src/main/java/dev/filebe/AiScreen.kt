package dev.filebe

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

private const val ORIGIN = "https://appassets.androidplatform.net"
private const val ASSISTANT = "$ORIGIN/ui/assistant"

@Composable fun AiScreen(state: UiState, vm: FilebeViewModel) {
    var browser by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("https://developer.android.com") }
    var requestedAddress by rememberSaveable { mutableStateOf("https://developer.android.com") }
    var preview by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.red < .3f
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            FilterChip(selected = !browser, onClick = { browser = false }, label = { Text("Ассистент") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = browser, onClick = { browser = true }, label = { Text("Браузер") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Настройки API") }
        }
        if (state.aiBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.proposal?.let {
            Row(Modifier.fillMaxWidth().glass().padding(6.dp)) {
                TextButton(onClick = { preview = true }, modifier = Modifier.weight(1f)) { Text("Просмотреть предложенную правку") }
                IconButton(onClick = vm::discardProposal) { Icon(Icons.Outlined.Close, "Отклонить правку") }
            }
        }
        if (browser) {
            OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("HTTPS адрес") }, trailingIcon = {
                    IconButton(onClick = {
                        val uri = Uri.parse(address.trim())
                        if (uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.host != "appassets.androidplatform.net") requestedAddress = address.trim()
                        else vm.notify("Введите внешний HTTPS адрес без логина")
                    }) { Icon(Icons.Outlined.ArrowForward, "Открыть") }
                })
            Text("Внешние сайты не имеют доступа к файлу, API-ключу и мосту Filebe.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
        } else Text("Ключ вводится в настройках и хранится только в памяти. Передача файла — по подтверждению.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
        key(browser) { FilebeWebView(browser, requestedAddress, dark, Modifier.fillMaxWidth().weight(1f), vm) }
    }
    if (settings) {
        var key by remember { mutableStateOf(state.apiKey) }
        var endpoint by remember { mutableStateOf(state.apiUrl) }
        var model by remember { mutableStateOf(state.apiModel) }
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Подключение ИИ") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Нужен совместимый с Chat Completions API. Ключ будет отправлен только на указанный адрес. Проверьте доверие к провайдеру; запросы могут быть платными.")
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("HTTPS endpoint") })
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API-ключ") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Модель") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = { vm.api(key.trim(), endpoint.trim(), model.trim()); settings = false }) { Text("Использовать") } },
            dismissButton = { TextButton(onClick = { settings = false }) { Text("Отмена") } })
    }
    val proposal = state.proposal
    if (preview && proposal != null) AlertDialog(onDismissRequest = { preview = false }, title = { Text("Полный новый текст файла") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Применение изменит только буфер редактора. Запись на диск выполняется отдельно.")
                SelectionContainer { Text(proposal.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(enabled = !state.busy, onClick = { preview = false; vm.applyProposal() }) { Text("Применить в редакторе") } },
        dismissButton = { TextButton(onClick = { preview = false; vm.discardProposal() }) { Text("Отклонить") } })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun FilebeWebView(browser: Boolean, address: String, dark: Boolean, modifier: Modifier, vm: FilebeViewModel) {
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
                safeBrowsingEnabled = true
            }
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            // The default client does not grant camera, mic, geolocation or file chooser permissions.
            webChromeClient = WebChromeClient()
            if (browser) {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        val blocked = uri.scheme != "https" || uri.host == "appassets.androidplatform.net" || uri.userInfo != null
                        if (blocked && request.isForMainFrame) vm.notify("Разрешены только внешние HTTPS страницы")
                        return blocked
                    }
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val uri = request.url
                        if (uri.scheme != "https" || uri.host == "appassets.androidplatform.net") return denied()
                        return null
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) vm.notify("Ошибка браузера: ${error.description}")
                    }
                }
                loadUrl(address)
            } else {
                val loader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler("/ui/") { path ->
                        if (path == "assistant") WebResourceResponse("text/html", "UTF-8", AssistantPage.html.byteInputStream(Charsets.UTF_8)) else null
                    }.build()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        loader.shouldInterceptRequest(request.url) ?: denied()
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.toString() != ASSISTANT
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url == ASSISTANT) applyWebTheme(view, dark)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) vm.notify("Ошибка локального ассистента: ${error.description}")
                    }
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "FilebeBridge", setOf(ORIGIN)) { view, message, origin, mainFrame, reply ->
                        if (!mainFrame || origin.toString().trimEnd('/') != ORIGIN) return@addWebMessageListener
                        try {
                            val input = message.data.orEmpty()
                            require(input.length <= 100000) { "Запрос слишком велик" }
                            val json = JSONObject(input)
                            require(json.getString("type") == "ask") { "Неизвестный метод" }
                            val id = json.getString("id").take(80)
                            vm.requestAi(json.getString("prompt"), json.optBoolean("includeFile", false)) { answer ->
                                view.post {
                                    if (view.isAttachedToWindow) reply.postMessage(JSONObject().put("id", id).put("message", answer).toString())
                                }
                            }
                        } catch (error: Exception) {
                            reply.postMessage(JSONObject().put("id", "error").put("message", "Некорректный запрос: ${error.message}").toString())
                        }
                    }
                } else vm.notify("Обновите Android System WebView: безопасный WebMessage API недоступен")
                loadUrl(ASSISTANT)
            }
        }
    }, onRelease = { web ->
        web.stopLoading()
        if (!browser && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(web, "FilebeBridge")
        web.destroy()
    }, update = { web ->
        if (browser && web.tag != address) { web.tag = address; web.loadUrl(address) }
        if (!browser && web.url == ASSISTANT) applyWebTheme(web, dark)
    })
}
private fun applyWebTheme(web: WebView, dark: Boolean) {
    // Only native, constant strings are evaluated, never a prompt, path or model output.
    web.evaluateJavascript("document.documentElement.setAttribute('data-theme', '${if (dark) "dark" else "light"}')", null)
}
private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "Blocked".byteInputStream())
