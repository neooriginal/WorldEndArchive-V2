package com.worldendarchive.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Hand-written guides that ship with the app, independent of the database: the things you want first when the
   power is out. Kept short, conservative and in line with common civil-protection / first-aid guidance. */

sealed interface Block {
    data class Para(val text: String) : Block
    data class Steps(val title: String, val items: List<String>) : Block
    data class Bullets(val title: String, val items: List<String>) : Block
    data class Warn(val items: List<String>) : Block
    data class Bars(val title: String, val items: List<Pair<String, Float>>, val note: String) : Block
    data class Compare(val title: String, val a: Pair<String, String>, val b: Pair<String, String>) : Block
    data class Cards(val title: String, val items: List<Triple<String, String, String>>) : Block   // icon, title, text
    data class Threes(val items: List<Triple<String, String, String>>) : Block                     // value, unit, need
    data class Flow(val items: List<Pair<String, String>>) : Block                                  // title, text
    data class Letters(val items: List<Triple<String, String, String>>) : Block                    // letter, word, text
    data class Morse(val caption: String) : Block
    data class Ground(val items: List<Pair<String, String>>) : Block                               // symbol, meaning
}

data class Highlight(val icon: String, val accent: Color, val title: String, val teaser: String, val blocks: List<Block>)

fun highlights(lang: String): List<Highlight> = if (lang == "de") DE else EN

private val EN = listOf(
    Highlight(
        "🔋", C.cyan, "Keep your phone alive", "When the grid is down, your phone is your light, map, radio and library.",
        listOf(
            Block.Para("Without power, every percent counts. Most of the battery goes to the screen and to the radio searching for a signal - so control those two first."),
            Block.Bars("What drains the battery", listOf("Screen on" to 1f, "Searching for a weak signal" to 0.8f, "GPS / location" to 0.6f,
                "Apps & data in the background" to 0.5f, "Wi-Fi / Bluetooth scanning" to 0.3f, "Standby in airplane mode" to 0.05f),
                "relative, typical values - phones differ"),
            Block.Compare("How long a full battery lasts", "Normal use" to "about a day", "Airplane mode, off between check-ins" to "several days or more"),
            Block.Steps("Save power now", listOf(
                "Turn on airplane mode. Switch on Wi-Fi or Bluetooth only when you need them.",
                "Turn on battery saver / low power mode.",
                "Lowest brightness, dark mode, short screen timeout.",
                "Turn off location and close apps you don't need.",
                "Switch the phone off completely between check-ins. Agree on fixed times with family to turn phones on.",
                "Send texts instead of calling - they need less power and get through busy networks more easily.",
                "Keep the phone warm, e.g. in an inside pocket. Cold drains batteries fast.",
            )),
            Block.Cards("Ways to recharge", listOf(
                Triple("🚗", "Car", "USB or 12 V adapter while the engine runs, outdoors. Don't drain the car battery."),
                Triple("☀️", "Solar panel", "Face it straight at the sun. Best: charge a power bank during the day, then the phone."),
                Triple("🔌", "Power bank", "Keep one charged before you need it. Charge it whenever power returns."),
                Triple("💻", "Laptop", "A laptop battery can charge a phone several times over USB."),
                Triple("🔄", "Hand crank", "Slow, but works anywhere. Many emergency radios have one."),
            )),
            Block.Warn(listOf(
                "Never run a generator, grill or car engine indoors or in a garage - carbon monoxide is invisible and deadly.",
                "Don't charge a swollen, hot or damaged battery.",
                "Don't charge lithium batteries below freezing.",
            )),
        ),
    ),
    Highlight(
        "⏱", C.purple, "The rule of threes", "What kills first - and what to take care of first.",
        listOf(
            Block.Para("A rough guide for setting priorities. Times vary a lot with weather, health and activity, but the order holds."),
            Block.Threes(listOf(Triple("3", "minutes", "without air"), Triple("3", "hours", "without shelter in harsh cold or heat"),
                Triple("3", "days", "without water"), Triple("3", "weeks", "without food"))),
            Block.Steps("So, in order", listOf(
                "Get out of danger and make sure everyone can breathe. Treat heavy bleeding.",
                "Protect yourself from cold, wet, wind or heat: shelter, dry clothes, insulation from the ground.",
                "Find and make safe drinking water.",
                "Food matters least in the short term. Ration it, but drink.",
            )),
        ),
    ),
    Highlight(
        "💧", Color(0xFF4FC3F7), "Safe drinking water", "Clear-looking water can still make you seriously ill.",
        listOf(
            Block.Flow(listOf(
                "Let it settle, then filter" to "Pour through a clean cloth or coffee filter to remove dirt.",
                "Boil" to "Bring to a rolling boil for 1 minute (3 minutes above 2,000 m). Let it cool covered.",
                "Or disinfect" to "Unscented household bleach (6-8 %): 2 drops per litre, double if cloudy. Stir, wait 30 minutes. It should smell slightly of chlorine.",
                "Or use the sun" to "Clear PET bottle (max. 2 litres), lying in full sun for 6 hours - 2 days if cloudy.",
                "Store safely" to "Clean, covered containers. Pour, don't dip hands or cups in.",
            )),
            Block.Warn(listOf(
                "None of these methods removes chemicals like fuel, pesticides or heavy metals. If the water may be polluted that way, find another source.",
                "Never use scented, color-safe or cleaning bleach with additives.",
            )),
        ),
    ),
    Highlight(
        "🧭", C.green, "Lost? S.T.O.P.", "The first minutes decide - don't run.",
        listOf(
            Block.Letters(listOf(
                Triple("S", "Stop", "Sit down, drink something, calm down. Panic makes people walk further away."),
                Triple("T", "Think", "How did you get here? What do you have with you? Who knows where you are?"),
                Triple("O", "Observe", "Weather, daylight left, water, shelter, landmarks, sounds of roads or water."),
                Triple("P", "Plan", "Usually: stay where you are, make yourself visible and wait. Move only if staying is dangerous."),
            )),
        ),
    ),
    Highlight(
        "🆘", C.red, "Signal for help", "Be seen and heard - rescuers look for patterns.",
        listOf(
            Block.Morse("SOS in Morse code: three short, three long, three short. Repeat with a flashlight, whistle or by tapping."),
            Block.Bullets("Signals in threes", listOf(
                "Three whistle blasts, three fires in a triangle, three flashes - anything in threes means \"help\".",
                "A whistle carries much further than your voice and uses less energy.",
                "A mirror or phone screen can flash sunlight to aircraft many kilometres away.",
            )),
            Block.Ground(listOf("V" to "Need help", "X" to "Need medical help", "→" to "Going this way")),
            Block.Para("Make ground signals big (several metres) and in strong contrast: stones on grass, branches on snow, bright clothing."),
        ),
    ),
)

private val DE = listOf(
    Highlight(
        "🔋", C.cyan, "Handy-Akku ohne Strom", "Wenn das Netz ausfällt, ist dein Handy Lampe, Karte, Radio und Bibliothek.",
        listOf(
            Block.Para("Ohne Strom zählt jedes Prozent. Das meiste verbraucht der Bildschirm und das Funkmodul bei der Netzsuche - die beiden also zuerst kontrollieren."),
            Block.Bars("Was den Akku leert", listOf("Bildschirm an" to 1f, "Suche nach schwachem Netz" to 0.8f, "GPS / Standort" to 0.6f,
                "Apps & Daten im Hintergrund" to 0.5f, "WLAN- / Bluetooth-Suche" to 0.3f, "Standby im Flugmodus" to 0.05f),
                "relative, typische Werte - je nach Handy verschieden"),
            Block.Compare("So lange hält ein voller Akku", "Normale Nutzung" to "etwa ein Tag", "Flugmodus, zwischendurch aus" to "mehrere Tage oder länger"),
            Block.Steps("Jetzt Strom sparen", listOf(
                "Flugmodus an. WLAN oder Bluetooth nur einschalten, wenn du sie brauchst.",
                "Energiesparmodus einschalten.",
                "Geringste Helligkeit, dunkles Design, kurze Bildschirm-Zeitsperre.",
                "Standort aus, nicht benötigte Apps schließen.",
                "Handy zwischendurch ganz ausschalten. Mit der Familie feste Zeiten zum Einschalten ausmachen.",
                "SMS statt Anrufe - braucht weniger Strom und kommt in überlasteten Netzen eher durch.",
                "Handy warm halten, z. B. in der Innentasche. Kälte leert den Akku schnell.",
            )),
            Block.Cards("So lädst du nach", listOf(
                Triple("🚗", "Auto", "USB- oder 12-V-Adapter bei laufendem Motor, im Freien. Autobatterie nicht leer machen."),
                Triple("☀️", "Solarpanel", "Direkt zur Sonne ausrichten. Am besten tagsüber eine Powerbank laden, dann das Handy."),
                Triple("🔌", "Powerbank", "Vorher geladen bereithalten. Immer laden, sobald Strom da ist."),
                Triple("💻", "Laptop", "Ein Laptop-Akku kann ein Handy per USB mehrmals laden."),
                Triple("🔄", "Kurbel", "Langsam, aber funktioniert überall. Viele Notfallradios haben eine."),
            )),
            Block.Warn(listOf(
                "Generator, Grill oder Automotor nie drinnen oder in der Garage laufen lassen - Kohlenmonoxid ist unsichtbar und tödlich.",
                "Aufgeblähte, heiße oder beschädigte Akkus nicht laden.",
                "Lithium-Akkus nicht bei Frost laden.",
            )),
        ),
    ),
    Highlight(
        "⏱", C.purple, "Die Dreier-Regel", "Was zuerst tödlich wird - und worum du dich zuerst kümmerst.",
        listOf(
            Block.Para("Eine grobe Faustregel für Prioritäten. Die Zeiten hängen stark von Wetter, Gesundheit und Anstrengung ab, die Reihenfolge stimmt aber."),
            Block.Threes(listOf(Triple("3", "Minuten", "ohne Luft"), Triple("3", "Stunden", "ohne Schutz bei starker Kälte oder Hitze"),
                Triple("3", "Tage", "ohne Wasser"), Triple("3", "Wochen", "ohne Nahrung"))),
            Block.Steps("Also, der Reihe nach", listOf(
                "Raus aus der Gefahr, Atmung aller sichern. Starke Blutungen stoppen.",
                "Schutz vor Kälte, Nässe, Wind oder Hitze: Unterschlupf, trockene Kleidung, Isolierung vom Boden.",
                "Trinkwasser finden und sicher machen.",
                "Essen ist kurzfristig am unwichtigsten. Einteilen, aber trinken.",
            )),
        ),
    ),
    Highlight(
        "💧", Color(0xFF4FC3F7), "Sicheres Trinkwasser", "Auch klares Wasser kann schwer krank machen.",
        listOf(
            Block.Flow(listOf(
                "Absetzen lassen, filtern" to "Durch ein sauberes Tuch oder einen Kaffeefilter gießen, um Schmutz zu entfernen.",
                "Abkochen" to "1 Minute sprudelnd kochen (über 2.000 m: 3 Minuten). Abgedeckt abkühlen lassen.",
                "Oder desinfizieren" to "Unparfümierte Haushaltsbleiche (6-8 %): 2 Tropfen pro Liter, bei trübem Wasser doppelt. Umrühren, 30 Minuten warten. Es sollte leicht nach Chlor riechen.",
                "Oder Sonne nutzen" to "Klare PET-Flasche (max. 2 Liter) 6 Stunden in die volle Sonne legen - bei Bewölkung 2 Tage.",
                "Sicher lagern" to "Saubere, abgedeckte Behälter. Ausgießen statt mit Händen oder Bechern hineingreifen.",
            )),
            Block.Warn(listOf(
                "Keine dieser Methoden entfernt Chemikalien wie Treibstoff, Pestizide oder Schwermetalle. Wenn das Wasser so verschmutzt sein könnte: andere Quelle suchen.",
                "Niemals parfümierte Bleiche oder Reiniger mit Zusätzen verwenden.",
            )),
        ),
    ),
    Highlight(
        "🧭", C.green, "Verirrt? S.T.O.P.", "Die ersten Minuten entscheiden - nicht loslaufen.",
        listOf(
            Block.Letters(listOf(
                Triple("S", "Stopp", "Hinsetzen, etwas trinken, ruhig werden. Panik lässt Menschen weiter weglaufen."),
                Triple("T", "Tief durchdenken", "Wie bist du hergekommen? Was hast du dabei? Wer weiß, wo du bist?"),
                Triple("O", "Orientieren", "Wetter, verbleibendes Tageslicht, Wasser, Schutz, Orientierungspunkte, Geräusche von Straßen oder Wasser."),
                Triple("P", "Planen", "Meist: bleiben, wo du bist, dich sichtbar machen und warten. Nur weitergehen, wenn Bleiben gefährlich ist."),
            )),
        ),
    ),
    Highlight(
        "🆘", C.red, "Hilfe signalisieren", "Gesehen und gehört werden - Retter suchen nach Mustern.",
        listOf(
            Block.Morse("SOS im Morsecode: dreimal kurz, dreimal lang, dreimal kurz. Mit Taschenlampe, Pfeife oder durch Klopfen wiederholen."),
            Block.Bullets("Signale in Dreiergruppen", listOf(
                "Drei Pfiffe, drei Feuer im Dreieck, drei Lichtblitze - alles in Dreiergruppen bedeutet \"Hilfe\".",
                "Eine Pfeife trägt viel weiter als die Stimme und kostet weniger Kraft.",
                "Mit einem Spiegel oder Handybildschirm lässt sich Sonnenlicht kilometerweit zu Flugzeugen blitzen.",
            )),
            Block.Ground(listOf("V" to "Brauche Hilfe", "X" to "Brauche medizinische Hilfe", "→" to "Gehe in diese Richtung")),
            Block.Para("Bodenzeichen groß (mehrere Meter) und kontrastreich machen: Steine auf Gras, Äste auf Schnee, auffällige Kleidung."),
        ),
    ),
)

// ------------------------------------------------------------------------------------------------ rendering

@Composable
fun HighlightBody(h: Highlight, lang: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        h.blocks.forEach { b ->
            when (b) {
                is Block.Para -> Text(b.text, fontSize = 17.sp, lineHeight = 26.sp, color = C.text)
                is Block.Steps -> Glass {
                    Text(b.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.text)
                    Spacer(Modifier.height(10.dp))
                    b.items.forEachIndexed { i, s -> StepRow(i + 1, s, h.accent) }
                }
                is Block.Bullets -> Glass {
                    Text(b.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.text)
                    Spacer(Modifier.height(8.dp))
                    b.items.forEach { BulletRow(it, h.accent) }
                }
                is Block.Warn -> WarnBox(b.items, lang)
                is Block.Bars -> BarsChart(b, h.accent)
                is Block.Compare -> CompareChart(b)
                is Block.Cards -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.text)
                    b.items.forEach { (icon, t, txt) ->
                        Glass(Modifier.fillMaxWidth(), padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(icon, fontSize = 26.sp, modifier = Modifier.width(44.dp))
                                Column {
                                    Text(t, fontWeight = FontWeight.SemiBold, color = C.text)
                                    Text(txt, color = C.muted, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                }
                is Block.Threes -> ThreesChart(b)
                is Block.Flow -> FlowChart(b, h.accent)
                is Block.Letters -> b.items.forEach { (l, w, t) -> LetterTile(l, w, t, h.accent) }
                is Block.Morse -> MorseSignal(b.caption)
                is Block.Ground -> GroundSignals(b.items)
            }
        }
    }
}

@Composable
fun StepRow(n: Int, text: String, accent: Color) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("$n", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Mono)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = C.text, fontSize = 16.sp, lineHeight = 23.sp)
    }
}

@Composable
fun BulletRow(text: String, accent: Color) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(12.dp))
        Text(text, color = C.text, fontSize = 16.sp, lineHeight = 23.sp)
    }
}

@Composable
fun WarnBox(items: List<String>, lang: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().background(C.warnBg, shape).border(1.dp, C.warnBorder, shape).padding(14.dp)) {
        Text("⚠  " + if (lang == "de") "Sicherheit" else "Safety",
            color = C.warn, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        items.forEach { BulletRow(it, C.warn) }
    }
}

/** Animated horizontal bars, growing in on first show. */
@Composable
fun BarsChart(b: Block.Bars, accent: Color) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    Glass {
        Text(b.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.text)
        Spacer(Modifier.height(12.dp))
        b.items.forEachIndexed { i, (label, v) ->
            val f by animateFloatAsState(if (go) v else 0f, tween(900, delayMillis = i * 120, easing = EaseOutCubic), label = "bar")
            Text(label, color = C.text, fontSize = 14.sp)
            Box(Modifier.padding(top = 4.dp, bottom = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.02f)).clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(C.purple, accent))))
            }
        }
        Label(b.note, C.muted)
    }
}

/** Two battery icons: drained fast vs. lasting long. */
@Composable
fun CompareChart(b: Block.Compare) {
    val t = rememberInfiniteTransition(label = "batt")
    val drain by t.animateFloat(1f, 0.15f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), label = "d")
    val slow by t.animateFloat(1f, 0.75f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), label = "s")
    Glass {
        Text(b.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.text)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple(b.a, drain, C.red), Triple(b.b, slow, C.green)).forEach { (p, level, col) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Battery(level, col)
                    Spacer(Modifier.height(8.dp))
                    Text(p.first, color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                    Text(p.second, color = col, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun Battery(level: Float, color: Color) {
    Canvas(Modifier.size(width = 64.dp, height = 110.dp)) {
        val capW = size.width * 0.4f
        drawRoundRect(color.copy(alpha = 0.6f), Offset((size.width - capW) / 2, 0f), Size(capW, 8.dp.toPx()), CornerRadius(3.dp.toPx()))
        val top = 10.dp.toPx()
        val h = size.height - top
        drawRoundRect(color.copy(alpha = 0.7f), Offset(0f, top), Size(size.width, h), CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()))
        val pad = 7.dp.toPx()
        val inner = h - 2 * pad
        drawRoundRect(color, Offset(pad, top + pad + inner * (1 - level)), Size(size.width - 2 * pad, inner * level), CornerRadius(5.dp.toPx()))
    }
}

/** Rule of threes on a logarithmic time axis: each step is roughly 60-fold longer. */
@Composable
fun ThreesChart(b: Block.Threes) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val colors = listOf(C.red, Color(0xFFFFA94D), Color(0xFF4FC3F7), C.green)
    Glass {
        b.items.forEachIndexed { i, (v, unit, need) ->
            val f by animateFloatAsState(if (go) (i + 1) / 4f else 0f, tween(900, delayMillis = i * 200, easing = EaseOutCubic), label = "t")
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(92.dp)) {
                    Text(v, color = colors[i], fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                    Text(unit, color = colors[i], fontFamily = Mono, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors[i]))
                    Spacer(Modifier.height(6.dp))
                    Text(need, color = C.text, fontSize = 15.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

/** Vertical process with connected numbered nodes. */
@Composable
fun FlowChart(b: Block.Flow, accent: Color) {
    Glass {
        b.items.forEachIndexed { i, (title, text) ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.purple, accent))),
                        contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = C.bg, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    }
                    if (i < b.items.lastIndex) Box(Modifier.width(2.dp).height(54.dp).background(accent.copy(alpha = 0.35f)))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 14.dp)) {
                    Text(title, color = C.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text, color = C.muted, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

@Composable
fun LetterTile(letter: String, word: String, text: String, accent: Color) {
    Glass(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(C.purple, accent))),
                contentAlignment = Alignment.Center) {
                Text(letter, color = C.bg, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(word, color = C.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text, color = C.muted, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** ··· ––– ··· lighting up in sequence, like a flashlight signalling. */
@Composable
fun MorseSignal(caption: String) {
    val marks = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
    val t = rememberInfiniteTransition(label = "morse")
    val pos by t.animateFloat(0f, marks.size + 3f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "p")
    Glass {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            marks.forEachIndexed { i, m ->
                val lit = pos.toInt() == i
                val col = if (lit) C.warn else C.warn.copy(alpha = 0.25f)
                Box(Modifier.padding(horizontal = if (i == 3 || i == 6) 9.dp else 3.dp).size(width = (if (m == 1) 14 else 34).dp, height = 14.dp)
                    .clip(RoundedCornerShape(7.dp)).background(col))
            }
        }
        Text("S   O   S", color = C.muted, fontFamily = Mono, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(caption, color = C.text, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** Ground-to-air signals drawn as if laid out with logs on the ground. */
@Composable
fun GroundSignals(items: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (sym, meaning) ->
            Glass(Modifier.weight(1f), padding = 10.dp) {
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val w = size.width; val h = size.height; val s = 7.dp.toPx(); val c = Color(0xFFD7B47A)
                    val cx = w / 2; val half = h * 0.4f
                    when (sym) {
                        "V" -> { drawLine(c, Offset(cx - half, h * 0.1f), Offset(cx, h * 0.9f), s, StrokeCap.Round)
                                 drawLine(c, Offset(cx + half, h * 0.1f), Offset(cx, h * 0.9f), s, StrokeCap.Round) }
                        "X" -> { drawLine(c, Offset(cx - half, h * 0.1f), Offset(cx + half, h * 0.9f), s, StrokeCap.Round)
                                 drawLine(c, Offset(cx + half, h * 0.1f), Offset(cx - half, h * 0.9f), s, StrokeCap.Round) }
                        else -> { drawLine(c, Offset(cx - half, h / 2), Offset(cx + half, h / 2), s, StrokeCap.Round)
                                  drawLine(c, Offset(cx + half, h / 2), Offset(cx + half * 0.35f, h * 0.15f), s, StrokeCap.Round)
                                  drawLine(c, Offset(cx + half, h / 2), Offset(cx + half * 0.35f, h * 0.85f), s, StrokeCap.Round) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(meaning, color = C.text, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 17.sp)
            }
        }
    }
}
