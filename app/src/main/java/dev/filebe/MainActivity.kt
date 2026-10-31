package dev.filebe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: FilebeViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (state.theme) { ThemeMode.SYSTEM -> systemDark; ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            FilebeTheme(dark) { FilebeApp(vm, state) }
        }
    }
}

@Composable private fun FilebeApp(vm: FilebeViewModel, state: UiState) {
    val snackbar = remember { SnackbarHostState() }
    var themeMenu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current
    var allFiles by remember { mutableStateOf(hasStorage(activity)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { allFiles = hasStorage(activity); vm.refresh() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { allFiles = hasStorage(activity); vm.refresh() }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allFiles = hasStorage(activity)
        if (!allFiles) vm.notify("Разрешение не выдано. Можно пользоваться SAF и папкой приложения.")
        vm.refresh()
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.let { data -> data.data?.let { vm.grant(it, data.flags) } }
    }
    val requestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                settings.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")))
            } catch (error: Exception) {
                vm.notify("Настройка приложения недоступна: ${error.message}. Открываю общий список разрешений.")
                try { settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                catch (fallback: Exception) { vm.notify("Системная настройка недоступна: ${fallback.message}. Используйте SAF.") }
            }
        } else legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        vm.clearMessage()
        scope.launch { snackbar.showSnackbar(message) }
    }
    BackHandler(enabled = state.tab != Tab.FILES || state.folder != null || state.opened?.dirty == true) {
        if (state.tab != Tab.FILES) vm.tab(Tab.FILES)
        else if (!vm.up() && state.opened?.dirty == true) confirmExit = true
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("Выйти без сохранения?") },
        text = { Text("В редакторе есть несохранённые изменения. Они могут быть потеряны после завершения процесса.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; (activity as? android.app.Activity)?.finish() }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmExit = false; vm.tab(Tab.OPEN) }) { Text("К редактору") } })
    LiquidBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Row(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Filebe", style = MaterialTheme.typography.headlineMedium); Text("BUILD • BROWSE • FLOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    Box {
                        IconButton(onClick = { themeMenu = true }) { Icon(Icons.Outlined.Palette, "Тема") }
                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                            listOf(ThemeMode.SYSTEM to "Системная", ThemeMode.DARK to "Тёмная", ThemeMode.LIGHT to "Дневная").forEach { (mode, title) ->
                                DropdownMenuItem(text = { Text(title) }, onClick = { vm.theme(mode); themeMenu = false })
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(Modifier.navigationBarsPadding().imePadding().padding(12.dp).glass(), containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val icon = when (tab) { Tab.FILES -> Icons.Outlined.Folder; Tab.OPEN -> Icons.Outlined.Code; Tab.AI -> Icons.Outlined.AutoAwesome }
                        NavigationBarItem(selected = state.tab == tab, onClick = { vm.tab(tab) }, icon = { Icon(icon, tab.title) }, label = { Text(tab.title) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 12.dp).fillMaxSize()) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (state.tab) {
                    Tab.FILES -> FilesScreen(state, vm, allFiles, requestPermission, {
                        try {
                            tree.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
                        } catch (error: Exception) { vm.notify("Системный выбор папки недоступен: ${error.message}") }
                    })
                    Tab.OPEN -> EditorScreen(state, vm)
                    Tab.AI -> AiScreen(state, vm)
                }
            }
        }
        if (state.pendingAi != null) AlertDialog(
            onDismissRequest = { vm.confirmAi(false) },
            title = { Text("Отправить файл провайдеру ИИ?") },
            text = { Text("Будут переданы полный текст и путь текущего файла: ${state.opened?.entry?.ref?.id}\n\nАдрес: ${state.apiUrl}\nНе отправляйте пароли, токены и чужие секреты.") },
            confirmButton = { TextButton(onClick = { vm.confirmAi(true) }) { Text("Отправить") } },
            dismissButton = { TextButton(onClick = { vm.confirmAi(false) }) { Text("Отмена") } }
        )
    }
}
private fun hasStorage(context: android.content.Context): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
