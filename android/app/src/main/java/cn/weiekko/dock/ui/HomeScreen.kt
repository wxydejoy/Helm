package cn.weiekko.dock.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
private val TileBg = Color(0xFF181818)
private val TileOn = Color(0xFF2A261C)

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        val tileGap = 10.dp
        val tileH = min(maxHeight.value * 0.22f, 84f).dp
        val tileW = min(maxWidth.value * 0.20f, tileH.value * 1.7f).dp
        val timeSp = min(maxWidth.value * 0.28f, maxHeight.value * 0.52f)
        val timeLift = -(maxHeight * 0.12f)

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
            PcMonitorRow(
                pc = pc,
                stale = state.stale || pc?.online != true,
                onClick = onOpenSettings,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                now.format(TimeFmt),
                fontSize = timeSp.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-8).sp,
                color = colors.onBackground,
                lineHeight = timeSp.sp,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false,
                    ),
                ),
            )
            Text(
                now.format(DateFmt),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
            )
        }

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
) {
    val colors = MaterialTheme.colorScheme
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
                value = gpu?.percent?.let(::formatPercent) ?: gpu?.name ?: "--",
                sub = gpu?.tempCelsius?.let(::formatTemp),
            ),
        )
        pc?.fps?.let { fps ->
            add(Item(label = "FPS", value = fps.roundToInt().toString()))
        }
    }
    Row(
        modifier = Modifier
            .alpha(if (stale) 0.45f else 1f)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    item.value,
                    color = colors.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
                item.sub?.let { sub ->
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
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
    Surface(
        modifier = Modifier.alpha(if (dimmed) 0.5f else 1f),
        color = TileBg,
        shape = RoundedCornerShape(18.dp),
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
                    color = colors.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    val context = LocalContext.current
    val lit = media.playing
    val bg by animateColorAsState(
        targetValue = if (lit) TileOn else TileBg,
        animationSpec = tween(180),
        label = "mediaTile",
    )
    val title = media.title?.takeIf { it.isNotBlank() } ?: if (media.playing) "正在播放" else "未在播放"
    val subtitle = buildList {
        media.artist?.takeIf { it.isNotBlank() }?.let(::add)
        add(media.app?.takeIf { it.isNotBlank() } ?: "手机")
    }.joinToString("  ·  ")

    Surface(
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
        color = bg,
        shape = MaterialTheme.shapes.medium,
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
                    tint = if (lit) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
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
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(if (emphasized) 36.dp else 32.dp)
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
            tint = if (emphasized) colors.primary else colors.onSurfaceVariant,
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
    val type = device.deviceType()
    val lit = device.on && device.online
    val bg by animateColorAsState(
        targetValue = if (lit) TileOn else TileBg,
        animationSpec = tween(200),
        label = "deviceTile",
    )
    Surface(
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
        color = bg,
        shape = MaterialTheme.shapes.medium,
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
                        if (lit) colors.primary.copy(alpha = 0.18f) else Color(0xFF242424),
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
                    tint = if (lit) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    color = colors.onBackground,
                    fontSize = 16.sp,
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
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 14.sp,
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
    val bg by animateColorAsState(
        targetValue = if (selected) TileOn else TileBg,
        animationSpec = tween(180),
        label = "winTile",
    )
    Surface(
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
        color = bg,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (selected) colors.primary else colors.onSurfaceVariant
            val remix = RemixIcons.drawableFor(app.icon ?: app.id)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.18f) else Color(0xFF242424),
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
                        fontSize = 18.sp,
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
                color = colors.onBackground,
                fontSize = 16.sp,
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
