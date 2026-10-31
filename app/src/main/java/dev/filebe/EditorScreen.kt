package dev.filebe

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SyntaxTransformation(private val name: String, private val dark: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.length > 100000) return TransformedText(text, OffsetMapping.Identity)
        val output = AnnotatedString.Builder(text)
        for (token in CodeTools.tokens(text.text, name)) {
            val color = when (token.kind) {
                CodeTools.Kind.COMMENT -> if (dark) Color(0xFF92A0B8) else Color(0xFF546773)
                CodeTools.Kind.STRING -> if (dark) Color(0xFF7DF4D1) else Color(0xFF006F4A)
                CodeTools.Kind.NUMBER -> if (dark) Color(0xFFFFD58D) else Color(0xFF8D4A00)
                CodeTools.Kind.KEYWORD -> if (dark) Color(0xFFC4ADFF) else Color(0xFF6941A5)
            }
            output.addStyle(SpanStyle(color = color), token.start, token.end)
        }
        return TransformedText(output.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable fun EditorScreen(state: UiState, vm: FilebeViewModel) {
    val document = state.opened
    var confirmSave by remember { mutableStateOf(false) }
    if (document == null) {
        Column(Modifier.fillMaxWidth().glass().padding(24.dp)) {
            Text("Откройте файл", style = MaterialTheme.typography.titleLarge)
            Text("UTF-8, до 1 МиБ. Выберите текст, скрипт или исходный код во вкладке «Файлы».")
        }
        return
    }
    val dark = MaterialTheme.colorScheme.background.red < .3f
    val syntax = remember(document.entry.name, dark) { SyntaxTransformation(document.entry.name, dark) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(document.entry.name + if (document.dirty) " •" else "", style = MaterialTheme.typography.titleMedium)
                Text("UTF-8 • ${document.text.toByteArray().size} байт", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { vm.tab(Tab.AI) }) { Text("ИИ") }
            Button(enabled = document.dirty && !state.busy, onClick = { confirmSave = true }) { Text("Сохранить") }
        }
        val vertical = remember(document.entry.ref.id) { androidx.compose.foundation.ScrollState(0) }
        val horizontal = remember(document.entry.ref.id) { androidx.compose.foundation.ScrollState(0) }
        Row(Modifier.weight(1f).fillMaxWidth().glass().verticalScroll(vertical).padding(10.dp)) {
            val lineCount = remember(document.text) { document.text.count { it == '\n' } + 1 }
            Text((1..lineCount).joinToString("\n"), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f), modifier = Modifier.padding(end = 12.dp))
            Box(Modifier.weight(1f).horizontalScroll(horizontal)) {
                BasicTextField(value = document.text, onValueChange = vm::edit, enabled = !state.busy,
                    modifier = Modifier.widthIn(min = 600.dp).padding(bottom = 48.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = syntax,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii))
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).padding(vertical = 8.dp).glass().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("Локальная проверка • не компилятор", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (document.check.isEmpty()) "Эвристика не обнаружила проблем. Это не гарантия корректности." else document.check.joinToString("\n") { "${it.line}: ${it.message}" }, style = MaterialTheme.typography.bodySmall)
            if (document.text.length > 100000) Text("Для больших файлов подсветка отключена ради скорости.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmSave) AlertDialog(onDismissRequest = { confirmSave = false }, title = { Text("Записать изменения?") },
        text = { Text("Текущее содержимое «${document.entry.name}» будет заменено. Для SAF предварительно создаётся копия в разделе восстановления.") },
        confirmButton = { TextButton(onClick = { confirmSave = false; vm.save() }) { Text("Записать") } },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Отмена") } })
}
