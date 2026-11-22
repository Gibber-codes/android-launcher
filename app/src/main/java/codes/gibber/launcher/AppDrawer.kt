package codes.gibber.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Every app, A to Z, with search and an alphabet strip down the right side. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    favorites: List<String>,
    onFavoritesChange: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val letters = remember(shown) { shown.map { it.letter }.distinct() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun jumpTo(letter: Char) {
        val index = shown.indexOfFirst { it.letter == letter }
        if (index >= 0) scope.launch { listState.scrollToItem(index) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2101014))
            .systemBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                shown.firstOrNull()?.let { Apps.launch(context, it) }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
        )

        Row(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(shown, key = { it.key }) { app ->
                    val isFav = app.key in favorites
                    AppRow(
                        app = app,
                        textSize = 18.sp,
                        iconSize = 36.dp,
                        actions = listOf(
                            if (isFav) RowAction("Remove from home") { onFavoritesChange(favorites - app.key) }
                            else RowAction("Add to home") { onFavoritesChange(favorites + app.key) },
                            RowAction("App info") { Apps.openInfo(context, app) },
                        ),
                    )
                }
            }
            if (query.isBlank() && letters.size > 1) {
                AlphabetStrip(letters = letters, onLetter = ::jumpTo)
            }
        }
    }
}

@Composable
private fun AlphabetStrip(letters: List<Char>, onLetter: (Char) -> Unit) {
    fun letterAt(y: Float, height: Int): Char {
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[i]
    }
    Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .pointerInput(letters) {
                detectTapGestures { offset -> onLetter(letterAt(offset.y, size.height)) }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ ->
                    onLetter(letterAt(change.position.y, size.height))
                }
            },
    ) {
        letters.forEach { c ->
            Box { Text(c.toString(), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp) }
        }
    }
}
