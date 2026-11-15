package app.cardbalances.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cardbalances.domain.CardNetwork

/** The full-size card picture used on the detail and add screens. */
@Composable
fun CardFace(
    nickname: String,
    last4: String,
    network: CardNetwork,
    balance: String?,
    modifier: Modifier = Modifier,
    /** MM/YY, shown as "Valid thru"; null hides it. */
    expiry: String? = null,
    dimmed: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(shape)
            .background(CardPalette.brush(last4.ifEmpty { "0" }))
            .border(1.dp, cardOutline(), shape)
            .drawBehind {
                // Two soft circles for a bit of depth.
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.55f, center = Offset(size.width * 1.05f, -size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.4f, center = Offset(size.width * 0.9f, size.height * 1.1f))
                if (dimmed) drawRect(Color.Black.copy(alpha = 0.35f))
            }
            .padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nickname.ifBlank { "Card nickname" },
                    color = Color.White.copy(alpha = if (nickname.isBlank()) 0.6f else 1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                NetworkMark(network)
            }
            Chip()
            Column {
                if (balance != null) {
                    Text("Balance", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    Text(
                        balance,
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "••••  ••••  ••••  ${last4.padEnd(4, '•')}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (expiry != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("VALID THRU", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, letterSpacing = 1.sp)
                            Text(expiry, color = Color.White.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip() {
    Box(
        Modifier.width(38.dp)
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF6D27A).copy(alpha = 0.9f))
    )
}

/** Network name in the corner of a card. Plain text, not the trademarked logos. */
@Composable
private fun NetworkMark(network: CardNetwork) {
    if (network == CardNetwork.OTHER) return
    Text(
        network.label.uppercase(),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
    )
}

/** A small card thumbnail for list rows. */
@Composable
fun CardThumb(last4: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    Box(
        modifier
            .size(width = 52.dp, height = 34.dp)
            .alpha(if (dimmed) 0.35f else 1f)
            .clip(RoundedCornerShape(7.dp))
            .background(CardPalette.brush(last4))
            .border(1.dp, cardOutline(), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(6.dp)
                .size(width = 10.dp, height = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFF6D27A).copy(alpha = 0.9f))
        )
    }
}
