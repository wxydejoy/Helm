package cn.weiekko.dock.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.weiekko.dock.R
import cn.weiekko.dock.data.DemoSnapshot
import cn.weiekko.dock.data.DeviceType
import cn.weiekko.dock.data.HubDevice
import cn.weiekko.dock.data.MediaInfo
import cn.weiekko.dock.data.PcStatus
import cn.weiekko.dock.data.WeatherInfo
import cn.weiekko.dock.data.WinApp
import cn.weiekko.dock.data.deviceType
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val DateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val StatShadow = Shadow(color = Color.Black.copy(alpha = 0.95f), offset = Offset(0f, 2f), blurRadius = 22f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: DockUiState,
    onOpenSettings: () -> Unit,
    onPower: (HubDevice, Boolean) -> Unit,
    onWinClick: (WinApp) -> Unit,
    onMedia: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val now = rememberNow()
    val indoor = state.snapshot?.temperature
    val devices = state.snapshot?.devices.orEmpty()
    val pc = state.snapshot?.pc
    val videoUri = state.backgroundVideoUri
    val timeShadow = if (videoUri != null) {
        Shadow(color = Color.Black.copy(alpha = 0.72f), offset = Offset.Zero, blurRadius = 22f)
    } else {
        Shadow()
    }

    CompositionLocalProvider(
        LocalTileLook provides state.tileLook,
        LocalTypeLook provides state.typeLook,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (videoUri == null) colors.background else Color.Transparent),
        ) {
        if (videoUri != null) {
            VideoBackground(uri = videoUri)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f)),
            )
        }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        val type = state.typeLook
        val family = type.font.toFamily()
        val tileGap = 10.dp
        val tileH = min(maxHeight.value * 0.22f, 84f).dp
        val tileW = min(maxWidth.value * 0.20f, tileH.value * 1.7f).dp
        val timeSp = min(maxWidth.value * 0.22f, maxHeight.value * 0.36f) *
            (type.clockScalePercent / 100f)
        val timeLift = -(maxHeight * 0.04f)

        if (state.preview) {
            Text(
                "预览",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 4.dp),
            )
        }

        state.banner?.let { banner ->
            Text(
                banner,
                style = MaterialTheme.typography.labelMedium,
                color = colors.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .padding(horizontal = 48.dp),
            )
        }

        val sideDevices = devices
            .filter { it.deviceType() == DeviceType.Light || it.deviceType() == DeviceType.Switch }
            .take(2)

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = timeLift * 0.25f),
            verticalArrangement = Arrangement.spacedBy(tileGap),
        ) {
            state.winApps.take(3).forEach { app ->
                WinTile(
                    app = app,
                    selected = app.id == state.selectedWinId,
                    busy = app.id in state.busyIds,
                    width = tileW,
                    height = tileH,
                    onClick = { onWinClick(app) },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = timeLift),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                now.format(TimeFmt),
                fontSize = timeSp.sp,
                fontFamily = family,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-6).sp,
                color = colors.onBackground,
                lineHeight = timeSp.sp,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false,
                    ),
                    shadow = timeShadow,
                ),
            )
            Text(
                now.format(DateFmt),
                fontFamily = family,
                fontSize = (type.chipSize + 1).sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.86f),
                style = androidx.compose.ui.text.TextStyle(shadow = timeShadow),
            )
        }

        PcMonitorRow(
            pc = pc,
            stale = state.stale || pc?.online != true,
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = timeLift * 0.25f),
            verticalArrangement = Arrangement.spacedBy(tileGap),
        ) {
            sideDevices.forEach { device ->
                DeviceTile(
                    device = device,
                    busy = device.id in state.busyIds,
                    width = tileW,
                    height = tileH,
                    onToggle = { onPower(device, !device.on) },
                )
            }
            MediaTile(
                media = state.media,
                width = tileW,
                height = tileH,
                onAction = onMedia,
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.weather?.let { weather ->
                WeatherChip(weather = weather)
            }
            indoor?.let { temp ->
                IndoorChip(
                    celsius = temp.celsius,
                    humidity = temp.humidity,
                    name = temp.name,
                    online = temp.online && !state.stale,
                )
            }
        }
        }
    }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    return now
}

@Composable
private fun PcMonitorRow(
    pc: PcStatus?,
    stale: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Item(val label: String, val value: String, val sub: String? = null)
    val items = buildList {
        val cpu = pc?.cpu
        add(
            Item(
                label = "CPU",
                value = cpu?.percent?.let(::formatPercent) ?: "--",
                sub = cpu?.tempCelsius?.let(::formatTemp),
            ),
        )
        add(
            Item(
                label = "MEM",
                value = pc?.memory?.percent?.let(::formatPercent) ?: "--",
            ),
        )
        val gpu = pc?.gpu
        add(
            Item(
                label = "GPU",
                value = gpu?.percent?.let(::formatPercent) ?: "--",
                sub = gpu?.tempCelsius?.let(::formatTemp) ?: gpu?.name?.take(10),
            ),
        )
        pc?.fps?.let { fps ->
            add(Item(label = "FPS", value = fps.roundToInt().toString()))
        }
    }
    val type = LocalTypeLook.current
    val family = type.font.toFamily()
    val size = type.statsSize
    val labelSize = (size * 0.72f).coerceAtLeast(10f)
    val colW = (size * 5.4f + 12f).dp
    TilePanel(
        modifier = modifier
            .wrapContentWidth()
            .alpha(if (stale) 0.78f else 1f)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.width(colW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.label,
                        color = Color.White.copy(alpha = 0.78f),
                        fontFamily = family,
                        fontSize = labelSize.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.1.sp,
                        maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(shadow = StatShadow),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            item.value,
                            color = Color.White,
                            fontFamily = family,
                            fontSize = size.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(shadow = StatShadow),
                        )
                        item.sub?.let { sub ->
                            Text(
                                sub,
                                color = Color.White.copy(alpha = 0.88f),
                                fontFamily = family,
                                fontSize = size.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                style = androidx.compose.ui.text.TextStyle(shadow = StatShadow),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPercent(value: Double): String =
    "${value.roundToInt()}%"

private fun formatTemp(value: Double): String =
    String.format(Locale.US, "%.0f°", value)

@Composable
private fun WeatherChip(weather: WeatherInfo) {
    StatusChip(
        icon = Icons.Outlined.Cloud,
        title = String.format(Locale.US, "%.1f°", weather.celsius),
        subtitle = "${weather.condition}  ${weather.city}",
    )
}

@Composable
private fun IndoorChip(
    celsius: Double,
    humidity: Double?,
    name: String,
    online: Boolean,
) {
    val line = buildString {
        append(name)
        humidity?.let { append("  ${it.roundToInt()}%") }
        if (!online) append("  离线")
    }
    StatusChip(
        icon = Icons.Outlined.Thermostat,
        title = String.format(Locale.US, "%.1f°", celsius),
        subtitle = line,
        dimmed = !online,
    )
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    dimmed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val type = LocalTypeLook.current
    val family = type.font.toFamily()
    val size = type.chipSize
    TilePanel(
        modifier = Modifier.alpha(if (dimmed) 0.55f else 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontFamily = family,
                    fontSize = size.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(shadow = StatShadow),
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = family,
                    fontSize = (size * 0.78f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(shadow = StatShadow),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    media: MediaInfo,
    width: Dp,
    height: Dp,
    onAction: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = LocalTypeLook.current
    val family = type.font.toFamily()
    val size = type.tileSize
    val context = LocalContext.current
    val lit = media.playing
    val title = media.title?.takeIf { it.isNotBlank() } ?: if (media.playing) "正在播放" else "未在播放"
    val subtitle = buildList {
        media.artist?.takeIf { it.isNotBlank() }?.let(::add)
        add(media.app?.takeIf { it.isNotBlank() } ?: "手机")
    }.joinToString("  ·  ")

    TilePanel(
        modifier = Modifier
            .width(width)
            .height(height)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
                onLongClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ),
        highlighted = lit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ri_music),
                    contentDescription = null,
                    tint = if (lit) colors.primary else Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontFamily = family,
                        fontSize = size.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        fontFamily = family,
                        fontSize = (size * 0.78f).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaButton(
                    drawable = R.drawable.ri_skip_back,
                    enabled = true,
                    onClick = { onAction("previous") },
                    label = "上一首",
                )
                MediaButton(
                    drawable = if (media.playing) R.drawable.ri_pause else R.drawable.ri_play,
                    enabled = true,
                    onClick = { onAction("toggle") },
                    label = if (media.playing) "暂停" else "播放",
                    emphasized = true,
                )
                MediaButton(
                    drawable = R.drawable.ri_skip_forward,
                    enabled = true,
                    onClick = { onAction("next") },
                    label = "下一首",
                )
            }
        }
    }
}

@Composable
private fun MediaButton(
    drawable: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    label: String,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(if (emphasized) 38.dp else 34.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = label,
            tint = if (emphasized) Color.White else Color.White.copy(alpha = 0.88f),
            modifier = Modifier.size(if (emphasized) 22.dp else 18.dp),
        )
    }
}

@Composable
private fun DeviceTile(
    device: HubDevice,
    busy: Boolean,
    width: Dp,
    height: Dp,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalTypeLook.current
    val family = look.font.toFamily()
    val size = look.tileSize
    val type = device.deviceType()
    val lit = device.on && device.online
    TilePanel(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(if (device.online) 1f else 0.5f)
            .clickable(
                enabled = device.online && !busy && type != DeviceType.Unknown,
                role = Role.Switch,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            ),
        highlighted = lit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (lit) colors.primary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (type == DeviceType.Light) {
                        Icons.Outlined.Lightbulb
                    } else {
                        Icons.Outlined.PowerSettingsNew
                    },
                    contentDescription = null,
                    tint = if (lit) colors.primary else Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    color = Color.White,
                    fontFamily = family,
                    fontSize = size.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !device.online -> "离线"
                        busy -> "执行中"
                        device.on && type == DeviceType.Light && device.brightness != null ->
                            "开  ${device.brightness}%"
                        device.on -> "开"
                        else -> "关"
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = family,
                    fontSize = (size * 0.88f).sp,
                )
            }
        }
    }
}

@Composable
private fun WinTile(
    app: WinApp,
    selected: Boolean,
    busy: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalTypeLook.current
    val family = look.font.toFamily()
    val size = look.tileSize
    TilePanel(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(if (app.online) 1f else 0.5f)
            .clickable(
                enabled = app.online && !busy,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        highlighted = selected,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (selected) colors.primary else Color.White.copy(alpha = 0.88f)
            val remix = RemixIcons.drawableFor(app.icon ?: app.id)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (remix != null) {
                    Icon(
                        painter = painterResource(remix),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        app.name.take(1),
                        color = tint,
                        fontFamily = family,
                        fontSize = size.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    busy -> "启动中"
                    !app.online -> "离线"
                    else -> app.name
                },
                color = Color.White,
                fontFamily = family,
                fontSize = size.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 914, heightDp = 411)
@Composable
private fun HomePreview() {
    DockTheme {
        HomeScreen(
            state = DockUiState(
                prefsReady = true,
                preview = true,
                snapshot = DemoSnapshot.create(),
                media = DemoSnapshot.media,
            ),
            onOpenSettings = {},
            onPower = { _, _ -> },
            onWinClick = {},
            onMedia = {},
        )
    }
}
