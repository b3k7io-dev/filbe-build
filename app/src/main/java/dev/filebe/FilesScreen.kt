package dev.filebe

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class NameAction(val title: String, val initial: String = "", val perform: (String) -> Unit)
private data class Confirmation(val message: String, val perform: () -> Unit)

@Composable fun FilesScreen(state: UiState, vm: FilebeViewModel, allFiles: Boolean, requestPermission: () -> Unit, pickTree: () -> Unit) {
    var naming by remember { mutableStateOf<NameAction?>(null) }
    var confirm by remember { mutableStateOf<Confirmation?>(null) }
    var properties by remember { mutableStateOf<Entry?>(null) }
    var createMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (state.folder == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
                        Text("Рабочее пространство", style = MaterialTheme.typography.titleLarge)
                        Text("Общие файлы, проекты и подключённые папки.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = requestPermission) { Text(if (allFiles) "Управление доступом ко всем файлам" else "Разрешить доступ к файлам") }
                        OutlinedButton(onClick = pickTree) { Text("Подключить папку через SAF") }
                        Text("Доступ к / ограничен правами ОС. SAF и разрешение всех файлов не предоставляют root и доступ к приватным данным других приложений.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { LocationCard("Общая память", "Память устройства", { vm.shared() }) }
                item { LocationCard("Корень / без root", "Только доступные Android пути", { vm.home("/") }) }
                item { LocationCard("Root / через su", "Требуется рутированное устройство; опасно для системных файлов", {
                    confirm = Confirmation("Запросить superuser-доступ? Операции смогут изменять и удалять системные файлы. Разрешите запрос в установленном менеджере root. Filebe не рутирует устройство.") { vm.enableRoot() }
                }) }
                item { LocationCard("Папка Filebe", "Работает без разрешений", { vm.appFiles() }) }
                item { LocationCard("Восстановление SAF", "Оригиналы и правки перед записью", { vm.recovery() }) }
                items(state.trees, key = { it.id }) { ref -> LocationCard("Подключённая папка", ref.id, { vm.home(ref.id) }) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { vm.up() }, enabled = !state.busy) { Icon(Icons.Outlined.ArrowBack, "Назад") }
                Text(state.folder.id, Modifier.weight(1f).padding(vertical = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { vm.refresh() }, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Обновить") }
                Box {
                    IconButton(onClick = { createMenu = true }, enabled = !state.busy) { Icon(Icons.Outlined.Add, "Создать") }
                    DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                        DropdownMenuItem(text = { Text("Текстовый файл") }, onClick = { createMenu = false; naming = NameAction("Создать файл", "script.kt") { vm.make(it, false) } })
                        DropdownMenuItem(text = { Text("Папку") }, onClick = { createMenu = false; naming = NameAction("Создать папку", "project") { vm.make(it, true) } })
                    }
                }
            }
            OutlinedTextField(value = state.query, onValueChange = vm::query, modifier = Modifier.fillMaxWidth(), singleLine = true,
                enabled = !state.busy, label = { Text("Поиск по имени") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Включая подпапки", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
                Switch(checked = state.recursive, onCheckedChange = vm::recursive, enabled = !state.busy)
            }
            state.clipboard?.let { clipboard ->
                Row(Modifier.fillMaxWidth().glass().padding(6.dp)) {
                    TextButton(enabled = !state.busy, onClick = {
                        if (clipboard.move) confirm = Confirmation("Переместить «${clipboard.entry.name}» сюда? После копирования исходник будет удалён.") { vm.paste() }
                        else vm.paste()
                    }, modifier = Modifier.weight(1f)) { Text(if (clipboard.move) "Вставить и переместить" else "Вставить: ${clipboard.entry.name}", maxLines = 1) }
                    IconButton(onClick = vm::cancelClipboard) { Icon(Icons.Outlined.Close, "Очистить буфер") }
                }
            }
            Text("${state.entries.size} объектов", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
            if (state.entries.isEmpty() && !state.busy) Text("Нет объектов. Если доступ запрещён, выдайте разрешение или подключите папку через SAF.", Modifier.padding(20.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.entries, key = { it.ref.id }) { entry ->
                    FileRow(entry, enabled = !state.busy, open = {
                        if (!entry.directory && state.opened?.dirty == true) confirm = Confirmation("В редакторе есть несохранённые изменения. Отбросить и открыть «${entry.name}»?") { vm.enter(entry) }
                        else vm.enter(entry)
                    }, action = { command ->
                        when (command) {
                            "copy" -> vm.copy(entry, false)
                            "move" -> vm.copy(entry, true)
                            "rename" -> naming = NameAction("Переименовать", entry.name) { vm.rename(entry, it) }
                            "delete" -> confirm = Confirmation("Безвозвратно удалить «${entry.name}»${if (entry.directory) " и всё содержимое" else ""}? Это не корзина.") { vm.delete(entry) }
                            "zip" -> vm.zip(entry)
                            "properties" -> properties = entry
                        }
                    })
                }
            }
        }
    }
    naming?.let { action ->
        var name by remember(action) { mutableStateOf(action.initial) }
        AlertDialog(onDismissRequest = { naming = null }, title = { Text(action.title) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Имя") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { action.perform(name); naming = null }) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Отмена") } })
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Подтверждение") }, text = { Text(action.message) },
        confirmButton = { TextButton(onClick = { action.perform(); confirm = null }) { Text("Подтвердить") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } }) }
    properties?.let { entry -> AlertDialog(onDismissRequest = { properties = null }, title = { Text(entry.name) },
        text = { androidx.compose.foundation.text.selection.SelectionContainer { Text("Путь / URI: ${entry.ref.id}\n\nТип: ${if (entry.directory) "папка" else "файл"}\nРазмер: ${if (entry.directory) "не вычислялся" else "${entry.size} байт"}\nИзменён: ${if (entry.modified > 0) DateFormat.format("yyyy-MM-dd HH:mm:ss", entry.modified) else "неизвестно"}\nДоступ: ${if (entry.ref.root) "root / su" else if (entry.ref.saf) "SAF" else "права процесса Android"}") } },
        confirmButton = { TextButton(onClick = { properties = null }) { Text("Закрыть") } }) }
}

@Composable private fun LocationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().clickable(onClick = onClick).padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun FileRow(entry: Entry, enabled: Boolean, open: () -> Unit, action: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().glass().clickable(enabled = enabled, onClick = open).padding(10.dp)) {
        Icon(if (entry.directory) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(28.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(if (entry.directory) "Папка" else "${entry.size} байт", style = MaterialTheme.typography.labelSmall)
        }
        Box {
            IconButton(enabled = enabled, onClick = { expanded = true }) { Icon(Icons.Outlined.MoreVert, "Действия") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("copy" to "Копировать", "move" to "Переместить", "rename" to "Переименовать", "zip" to "Создать ZIP", "properties" to "Свойства", "delete" to "Удалить").forEach { (command, title) ->
                    DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; action(command) })
                }
            }
        }
    }
}
