package cn.weiekko.dock.ui

import androidx.annotation.DrawableRes
import cn.weiekko.dock.R

/** Maps Hub `action.icon` short names to bundled [Remix Icon](https://remixicon.com/) vectors. */
object RemixIcons {
    @DrawableRes
    fun drawableFor(icon: String?): Int? {
        val key = icon?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return null
        return when (key) {
            "steam" -> R.drawable.ri_steam
            "chrome" -> R.drawable.ri_chrome
            "vscode", "code" -> R.drawable.ri_vscode
            "cursor" -> R.drawable.ri_cursor
            "terminal", "cmd", "shell" -> R.drawable.ri_terminal
            "folder" -> R.drawable.ri_folder
            "music" -> R.drawable.ri_music
            "computer", "pc", "desktop" -> R.drawable.ri_computer
            else -> null
        }
    }
}
