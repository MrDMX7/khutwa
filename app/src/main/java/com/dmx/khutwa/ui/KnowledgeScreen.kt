package com.dmx.khutwa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.domain.Knowledge
import com.dmx.khutwa.ui.theme.Neon

/**
 * The knowledge section.
 *
 * Several of these entries exist specifically to correct a widely repeated
 * claim, so myths are shown explicitly rather than quietly omitted — being told
 * "180 spm is the target" and simply not seeing it contradicted is how the
 * belief survives. Each card names its evidence.
 */
@Composable
fun KnowledgeScreen(state: KhutwaState) {
    var topic by remember { mutableStateOf<Knowledge.Topic?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val articles = topic?.let { Knowledge.byTopic(it) } ?: Knowledge.articles

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("المعرفة", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "كل ما هنا مبني على أبحاث منشورة، وبعضه يناقض ما هو شائع. المصدر مذكور " +
                    "أسفل كل بطاقة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = topic == null,
                        onClick = { topic = null },
                        label = { Text("الكل") },
                    )
                }
                items(Knowledge.Topic.entries.toList()) { t ->
                    FilterChip(
                        selected = topic == t,
                        onClick = { topic = if (topic == t) null else t },
                        label = { Text(t.title) },
                    )
                }
            }
        }

        items(articles) { article ->
            ArticleCard(
                article = article,
                expanded = expanded == article.id,
                onToggle = { expanded = if (expanded == article.id) null else article.id },
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ArticleCard(
    article: Knowledge.Article,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    article.headline,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = Neon.Steps,
                )
            }

            // The myth line stays visible collapsed: the correction is the
            // point of the card, so it shouldn't be hidden behind a tap.
            article.myth?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.padding(10.dp)) {
                        Text("✕", color = Neon.Run, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text(
                    article.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "المصدر: ${article.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    article.body.lineSequence().first().take(90).trimEnd() + "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
