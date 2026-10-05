package dev.frontek.reads.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.frontek.reads.R
import dev.frontek.reads.data.Article
import dev.frontek.reads.ui.theme.Brand
import dev.frontek.reads.ui.theme.KickerStyle
import dev.frontek.reads.ui.theme.LocalFrontekColors

/** The dark band at the top of each section (web: .feedhead). */
@Composable
fun SectionHeader(kicker: String, title: String, content: @Composable ColumnScope.() -> Unit = {}) {
    val c = LocalFrontekColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.header)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
    ) {
        Text(kicker.uppercase(), style = KickerStyle, color = Brand.Gold)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.onHeader)
        content()
    }
}

/** Rounded filter pill on the dark header (web: .srcchip). */
@Composable
fun HeaderChip(text: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalFrontekColors.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) Brand.Gold else Color.White.copy(alpha = .12f))
            .border(1.dp, if (active) Brand.Gold else Color.White.copy(alpha = .22f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (active) Brand.Dark else c.onHeader.copy(alpha = .92f),
        )
    }
}

@Composable
fun Pill(text: String) {
    val c = LocalFrontekColors.current
    Text(
        text.uppercase(),
        modifier = Modifier
            .clip(CircleShape)
            .background(c.chip)
            .border(1.dp, c.line, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp, color = c.muted, maxLines = 1,
    )
}

@Composable
fun EmptyState(icon: String, title: String?, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = LocalFrontekColors.current.title)
            Spacer(Modifier.height(6.dp))
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalFrontekColors.current.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }
}

@Composable
fun cardBorder() = BorderStroke(1.dp, LocalFrontekColors.current.line)

@Composable
fun FavToggle(on: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (on) R.string.fav_remove else R.string.fav_add)
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = if (on) Brand.Coral else LocalFrontekColors.current.muted)) {
        Icon(if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = label)
    }
}

@Composable
fun ReadLaterToggle(on: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (on) R.string.read_later_remove else R.string.read_later_add)
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = if (on) MaterialTheme.colorScheme.primary else LocalFrontekColors.current.muted)) {
        Icon(if (on) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = label)
    }
}

/** One article in a list (web: .article card). */
@Composable
fun ArticleCard(
    article: Article,
    read: Boolean,
    favorite: Boolean,
    readLater: Boolean,
    onOpen: () -> Unit,
    onOpenOriginal: () -> Unit,
    onToggleFav: () -> Unit,
    onToggleReadLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalFrontekColors.current
    val ctx = LocalContext.current
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = if (read) c.readCard else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (read) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.Top) {
                var showImg by remember(article.image) { mutableStateOf(article.image.isNotEmpty()) }
                if (showImg) {
                    AsyncImage(
                        model = article.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { showImg = false },
                        modifier = Modifier
                            .padding(end = 14.dp, top = 2.dp)
                            .size(84.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.chip)
                            .alpha(if (read) .55f else 1f),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Brand.Coral))
                        Text(
                            article.source, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (article.date > 0) Text(fmtDate(ctx, article.date), color = c.muted, fontSize = 12.sp, maxLines = 1)
                        if (read) Text("✓ " + stringResource(R.string.read_badge), color = Brand.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        article.title.ifEmpty { stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (read) c.muted else c.title,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    if (article.summary.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            article.summary, style = MaterialTheme.typography.bodyMedium,
                            color = c.muted.copy(alpha = if (read) .75f else 1f),
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.card_read_here), color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 10.dp, horizontal = 2.dp),
                )
                Spacer(Modifier.width(16.dp))
                if (article.link.isNotEmpty()) Text(
                    stringResource(R.string.reader_open_original), color = c.muted,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenOriginal).padding(vertical = 10.dp, horizontal = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                FavToggle(favorite, onToggleFav)
                ReadLaterToggle(readLater, onToggleReadLater)
            }
        }
    }
}

/** A feed suggestion in Discover (web: .fcard). */
@Composable
fun FeedCard(
    label: String,
    title: String,
    detail: String,
    detailIsUrl: Boolean,
    icon: String = "",
    subscribed: Boolean,
    busy: Boolean = false,
    subscribeText: String = stringResource(R.string.discover_subscribe),
    onSubscribe: () -> Unit,
    site: String = "",
) {
    val c = LocalFrontekColors.current
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon.isNotEmpty()) {
                    var show by remember(icon) { mutableStateOf(true) }
                    if (show) AsyncImage(
                        model = icon, contentDescription = null, onError = { show = false },
                        modifier = Modifier.padding(end = 10.dp).size(24.dp).clip(RoundedCornerShape(6.dp)),
                    )
                }
                Pill(label)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    detail, color = c.muted, fontSize = if (detailIsUrl) 13.sp else 14.sp,
                    maxLines = if (detailIsUrl) 2 else 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subscribed) {
                    OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.discover_subscribed)) }
                } else {
                    Button(onClick = onSubscribe, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text(if (busy) stringResource(R.string.discover_searching) else subscribeText)
                    }
                }
                if (site.isNotEmpty()) OutlinedButton(onClick = { openUrl(ctx, site) }) { Text(stringResource(R.string.site_link)) }
            }
        }
    }
}
