package com.worldendarchive.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class Category(val name: String) : Screen
    data class Article(val id: Long) : Screen
    data class Guide(val index: Int) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WeaTheme { App(this) } }
    }
}

@Composable
fun App(ctx: Context) {
    var archive by remember { mutableStateOf<Archive?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Archive.open(ctx) } }
            .onSuccess { archive = it }.onFailure { error = it.message }
    }
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var lang by remember { mutableStateOf(prefs.getString("lang", null) ?: if (java.util.Locale.getDefault().language == "de") "de" else "en") }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = stack.size > 1 || query.isNotEmpty()) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else query = ""
    }

    LiquidBackground {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            val a = archive
            when {
                error != null -> Center { Text("Could not open the archive: $error", color = C.red) }
                a == null -> Center {
                    CircularProgressIndicator(color = C.cyan)
                    Spacer(Modifier.height(16.dp))
                    Text(if (lang == "de") "Archiv wird vorbereitet…" else "Preparing the archive…", color = C.muted)
                }
                else -> when (val s = stack.last()) {
                    Screen.Home -> HomeScreen(a, lang, query, { query = it },
                        onLang = { lang = it; prefs.edit().putString("lang", it).apply() }, open = { stack.add(it) })
                    is Screen.Category -> ListScreen(s.name, a.byCategory(s.name, lang), lang, back = { stack.removeAt(stack.lastIndex) }) { stack.add(it) }
                    is Screen.Article -> ArticleScreen(a, s.id, lang, back = { stack.removeAt(stack.lastIndex) }) { stack[stack.lastIndex] = it }
                    is Screen.Guide -> GuideScreen(highlights(lang)[s.index], lang) { stack.removeAt(stack.lastIndex) }
                }
            }
        }
    }
}

@Composable
fun Center(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally, content = content)

// ------------------------------------------------------------------------------------------------ home

@Composable
fun HomeScreen(a: Archive, lang: String, query: String, onQuery: (String) -> Unit, onLang: (String) -> Unit, open: (Screen) -> Unit) {
    val de = lang == "de"
    val results = remember(query, lang) { if (query.isBlank()) emptyList() else a.search(query, lang) }
    val essentials = remember(lang) { a.essentials(lang) }
    val cats = remember(lang) { a.categories(lang) }
    val guides = highlights(lang)
    val listState = rememberLazyListState()

    LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GradientTitle("WorldEndArchive", 32)
                Text(if (de) "Wissen bewahren, für die Zukunft." else "Preserving knowledge for the future.",
                    color = C.muted, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { SearchBar(query, onQuery, lang, onLang) }

        if (query.isNotBlank()) {
            item { SectionTitle(if (de) "Ergebnisse" else "Results") }
            if (results.isEmpty()) item { Text(if (de) "Nichts gefunden." else "Nothing found.", color = C.muted) }
            items(results, key = { it.id }) { r -> RowCard(r, lang) { open(Screen.Article(r.id)) } }
        } else {
            item { SectionTitle(if (de) "Wenn der Strom weg ist" else "When the grid goes down") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(guides.size) { i -> GuideCard(guides[i]) { open(Screen.Guide(i)) } }
                }
            }
            item { SectionTitle(if (de) "Kategorien" else "Categories") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { (c, n) -> Chip("$c  $n") { open(Screen.Category(c)) } }
                }
            }
            item { SectionTitle(if (de) "Das Wichtigste" else "Essentials") }
            items(essentials, key = { it.id }) { r -> RowCard(r, lang) { open(Screen.Article(r.id)) } }
            item {
                Text("${a.count(lang)} ${if (de) "Einträge" else "entries"} · ${a.info["built"] ?: ""} · offline",
                    fontFamily = Mono, fontSize = 12.sp, color = C.muted, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQuery: (String) -> Unit, lang: String, onLang: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(16.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(54.dp).background(C.glass, shape)
            .border(1.dp, if (query.isNotEmpty()) C.cyan.copy(alpha = 0.7f) else C.border, shape).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(if (lang == "de") "Archiv durchsuchen…" else "Search the archive…", color = C.muted, fontSize = 16.sp)
            BasicTextField(query, onQuery, singleLine = true, cursorBrush = SolidColor(C.cyan),
                textStyle = androidx.compose.ui.text.TextStyle(color = C.text, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.height(54.dp).clip(shape).border(1.dp, C.border, shape)) {
            listOf("en", "de").forEach { l ->
                Box(Modifier.fillMaxHeight().width(46.dp).background(if (l == lang) C.cyan else C.glass).clickable { onLang(l) },
                    contentAlignment = Alignment.Center) {
                    Text(l.uppercase(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (l == lang) C.bg else C.muted)
                }
            }
        }
    }
}

@Composable
fun GuideCard(h: Highlight, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.width(220.dp).height(150.dp).clip(shape)
        .background(Brush.linearGradient(listOf(h.accent.copy(alpha = 0.18f), C.purple.copy(alpha = 0.10f))))
        .border(1.dp, h.accent.copy(alpha = 0.35f), shape).clickable(onClick = onClick).padding(16.dp)) {
        Text(h.icon, fontSize = 28.sp)
        Spacer(Modifier.height(6.dp))
        Text(h.title, color = C.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(h.teaser, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(text, fontFamily = Mono, fontSize = 13.sp, color = C.muted,
        modifier = Modifier.clip(shape).border(1.dp, C.border, shape).background(C.glass).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun RowCard(r: Row, lang: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(shape).background(C.glass).border(1.dp, C.border, shape)
        .clickable(onClick = onClick).padding(16.dp)) {
        Text(r.title, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Label(r.category + if (r.kind == "howto") (if (lang == "de") " · anleitung" else " · guide") else "")
        Spacer(Modifier.height(4.dp))
        Text(r.summary, color = C.muted, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------------------------------------ lists & entries

@Composable
fun TopBar(lang: String, back: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(if (lang == "de") "← zurück" else "← back", back)
        extra()
    }
}

@Composable
fun ListScreen(title: String, rows: List<Row>, lang: String, back: () -> Unit, open: (Screen) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { TopBar(lang, back) }
        item { Text(title, color = C.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 14.dp)) }
        items(rows, key = { it.id }) { r -> RowCard(r, lang) { open(Screen.Article(r.id)) } }
    }
}

@Composable
fun ArticleScreen(a: Archive, id: Long, lang: String, back: () -> Unit, replace: (Screen) -> Unit) {
    val e = remember(id) { a.entry(id) } ?: return Center { Text("Not found", color = C.muted) }
    val de = e.lang == "de"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar(lang, back) {
            e.otherLangId?.let { other -> Chip(if (de) "EN" else "DE") { replace(Screen.Article(other)) } }
        }
        Glass(padding = 20.dp) {
            Label("${e.category} · ${if (e.kind == "howto") (if (de) "anleitung" else "guide") else e.kind} · ${e.importance}/5")
            Spacer(Modifier.height(8.dp))
            Text(e.title, color = C.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
            Spacer(Modifier.height(10.dp))
            Text(e.summary, color = C.text, fontSize = 18.sp, lineHeight = 27.sp)
            if (e.warnings.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                WarnBox(e.warnings, e.lang)
            }
            Spacer(Modifier.height(8.dp))
            Markdown(e.body)
            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp).background(C.border))
            Spacer(Modifier.height(10.dp))
            Text("${if (de) "Quelle" else "Source"}: ${e.sourceTitle} · ${e.license}", fontFamily = Mono, fontSize = 12.sp, color = C.muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun GuideScreen(h: Highlight, lang: String, back: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar(lang, back)
        Text(h.icon, fontSize = 44.sp)
        Text(h.title, color = C.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 35.sp)
        Text(h.teaser, color = h.accent, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        HighlightBody(h, lang)
        Spacer(Modifier.height(32.dp))
    }
}

/** Minimal markdown for entry bodies: ## headings, - bullets, 1. steps, **bold**, *italic*. */
@Composable
fun Markdown(src: String) {
    var n = 0
    src.lines().forEach { raw ->
        val line = raw.trimEnd()
        val h = Regex("^(#{1,4})\\s+(.*)").find(line)
        val b = Regex("^\\s*[-*•]\\s+(.*)").find(line)
        val o = Regex("^\\s*(\\d+)[.)]\\s+(.*)").find(line)
        when {
            h != null -> { n = 0; Text(inline(h.groupValues[2]), color = C.text, fontWeight = FontWeight.Bold,
                fontSize = if (h.groupValues[1].length <= 2) 20.sp else 17.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)) }
            b != null -> Row(Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.padding(top = 10.dp).size(5.dp).clip(RoundedCornerShape(50)).background(C.cyan))
                Spacer(Modifier.width(12.dp))
                Text(inline(b.groupValues[1]), color = C.text, fontSize = 16.sp, lineHeight = 24.sp)
            }
            o != null -> { n++; Row(Modifier.padding(vertical = 4.dp)) {
                Text("${o.groupValues[1]}.", color = C.cyan, fontWeight = FontWeight.Bold, fontFamily = Mono, modifier = Modifier.width(30.dp))
                Text(inline(o.groupValues[2]), color = C.text, fontSize = 16.sp, lineHeight = 24.sp)
            } }
            line.isNotBlank() -> { n = 0; Text(inline(line), color = C.text, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(vertical = 5.dp)) }
        }
    }
}

private fun inline(s: String) = buildAnnotatedString {
    var i = 0
    val re = Regex("\\*\\*(.+?)\\*\\*|\\*([^*\\s][^*]*?)\\*")
    for (m in re.findAll(s)) {
        append(s.substring(i, m.range.first))
        if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFFD8D8D8))) { append(m.groupValues[2]) }
        i = m.range.last + 1
    }
    append(s.substring(i))
}
