//
//  SymbolIcons.kt
//  Starshower Run
//
//  The shared core names icons with SF Symbol names (so iOS and Android use
//  identical catalog data). This is the single place that maps them to
//  Material icons.
//

package com.starshower.run.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Filter7
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Looks5
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

/** Material icon for an SF Symbol name used by the shared catalog. */
@Suppress("FunctionName")
fun Symbol(name: String): ImageVector = when (name) {
    // Modes & modifiers
    "infinity", "infinity.circle.fill" -> Icons.Filled.AllInclusive
    "stopwatch.fill", "stopwatch" -> Icons.Filled.Timer
    "timer" -> Icons.Filled.AvTimer
    "calendar.badge.clock" -> Icons.Filled.EventRepeat
    "leaf.fill" -> Icons.Filled.Eco
    "circle" -> Icons.Filled.RadioButtonUnchecked
    "circle.fill" -> Icons.Filled.Circle
    "circle.circle.fill" -> Icons.Filled.RadioButtonChecked
    "hare.fill" -> Icons.AutoMirrored.Filled.DirectionsRun
    "tortoise.fill" -> Icons.Filled.SlowMotionVideo
    "sparkles", "sparkle" -> Icons.Filled.AutoAwesome
    "water.waves" -> Icons.Filled.Waves
    "smallcircle.filled.circle" -> Icons.Filled.Adjust
    "moon.fill" -> Icons.Filled.DarkMode
    "flame.fill" -> Icons.Filled.LocalFireDepartment
    "flame.circle.fill" -> Icons.Filled.Whatshot
    "hand.raised.fill" -> Icons.Filled.PanTool

    // Pickups
    "star.fill" -> Icons.Filled.Star
    "star" -> Icons.Filled.StarBorder
    "star.circle.fill", "star.circle" -> Icons.Filled.Stars
    "star.leadinghalf.filled" -> Icons.AutoMirrored.Filled.StarHalf
    "shield.fill" -> Icons.Filled.Shield
    "shield.lefthalf.filled" -> Icons.Filled.Security
    "bolt.shield.fill" -> Icons.Filled.GppGood
    "dot.radiowaves.left.and.right" -> Icons.Filled.Sensors
    "arrow.down.right.and.arrow.up.left" -> Icons.Filled.CloseFullscreen
    "burst.fill" -> Icons.Filled.Flare
    "hourglass" -> Icons.Filled.HourglassEmpty
    "hourglass.circle.fill" -> Icons.Filled.HourglassFull

    // Missions & achievements
    "scope" -> Icons.Filled.GpsFixed
    "arrow.left.and.right" -> Icons.Filled.SwapHoriz
    "play.fill" -> Icons.Filled.PlayArrow
    "bolt.fill" -> Icons.Filled.Bolt
    "heart.fill" -> Icons.Filled.Favorite
    "bolt.heart.fill" -> Icons.Filled.MonitorHeart
    "multiply.circle.fill" -> Icons.Filled.Cancel
    "multiply.circle" -> Icons.Filled.HighlightOff
    "calendar" -> Icons.Filled.CalendarToday
    "calendar.badge.checkmark" -> Icons.Filled.EventAvailable
    "calendar.circle.fill" -> Icons.Filled.CalendarMonth
    "chart.line.uptrend.xyaxis" -> Icons.AutoMirrored.Filled.TrendingUp
    "figure.walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "figure.walk.motion" -> Icons.Filled.Animation
    "clock.fill", "clock" -> Icons.Filled.Schedule
    "crown.fill" -> Icons.Filled.WorkspacePremium
    "gauge.with.dots.needle.50percent", "gauge.with.dots.needle.67percent",
    "gauge.with.dots.needle.100percent", "speedometer" -> Icons.Filled.Speed
    "1.circle.fill" -> Icons.Filled.LooksOne
    "5.circle.fill" -> Icons.Filled.Looks5
    "7.circle.fill" -> Icons.Filled.Filter7
    "trophy.fill" -> Icons.Filled.EmojiEvents
    "wind" -> Icons.Filled.Air
    "viewfinder" -> Icons.Filled.CenterFocusStrong
    "figure.dance" -> Icons.Filled.SportsMartialArts
    "bag.fill" -> Icons.Filled.ShoppingBag
    "tshirt.fill" -> Icons.Filled.Checkroom
    "gamecontroller", "gamecontroller.fill" -> Icons.Filled.SportsEsports
    "map.fill" -> Icons.Filled.Map
    "checkmark.circle.fill" -> Icons.Filled.CheckCircle
    "checklist" -> Icons.Filled.Checklist
    "checklist.checked" -> Icons.Filled.TaskAlt
    "checkmark.seal.fill" -> Icons.Filled.Verified
    "checkmark" -> Icons.Filled.Check

    // Cosmetics
    "circle.hexagongrid.fill" -> Icons.Filled.Hexagon
    "scribble.variable" -> Icons.Filled.Gesture
    "paintpalette.fill" -> Icons.Filled.Palette

    // Interface
    "person.fill" -> Icons.Filled.Person
    "gearshape.fill" -> Icons.Filled.Settings
    "pause.fill" -> Icons.Filled.Pause
    "speaker.wave.2.fill", "speaker.wave.3.fill" -> Icons.AutoMirrored.Filled.VolumeUp
    "speaker.slash.fill" -> Icons.AutoMirrored.Filled.VolumeOff
    "music.note" -> Icons.Filled.MusicNote
    "music.note.list" -> Icons.Filled.MusicOff
    "iphone.radiowaves.left.and.right" -> Icons.Filled.Vibration
    "gift.fill" -> Icons.Filled.CardGiftcard
    "rosette" -> Icons.Filled.MilitaryTech
    "arrow.counterclockwise" -> Icons.Filled.Replay
    "house.fill" -> Icons.Filled.Home
    "square.and.arrow.up" -> Icons.Filled.Share
    "list.number" -> Icons.Filled.FormatListNumbered
    "hand.draw.fill" -> Icons.Filled.TouchApp
    "lock.fill" -> Icons.Filled.Lock
    "circle.dashed" -> Icons.Filled.RadioButtonUnchecked
    "list.bullet.rectangle" -> Icons.AutoMirrored.Filled.ListAlt
    "nosign" -> Icons.Filled.Block
    "questionmark" -> Icons.Filled.QuestionMark
    "square.grid.2x2", "square.grid.2x2.fill" -> Icons.Filled.GridView
    "chart.bar.fill" -> Icons.Filled.BarChart
    "pencil" -> Icons.Filled.Edit
    "lightbulb.fill" -> Icons.Filled.Lightbulb
    "globe" -> Icons.Filled.Public
    "person.crop.circle.badge.questionmark", "person.crop.circle" -> Icons.Filled.AccountCircle
    "eye.fill" -> Icons.Filled.Visibility
    "circle.lefthalf.filled" -> Icons.Filled.Contrast
    "bell.fill" -> Icons.Filled.Notifications
    "bell.badge.fill" -> Icons.Filled.NotificationsActive
    "person.2.fill" -> Icons.Filled.People
    "externaldrive.fill" -> Icons.Filled.Storage
    "trash.fill" -> Icons.Filled.Delete
    "info.circle.fill" -> Icons.Filled.Info
    "arrow.up.right.square" -> Icons.AutoMirrored.Filled.OpenInNew
    "chevron.right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    "chevron.left" -> Icons.AutoMirrored.Filled.ArrowBack
    "accessibility" -> Icons.Filled.AccessibilityNew
    "hammer.fill" -> Icons.Filled.Construction
    "waveform" -> Icons.Filled.GraphicEq
    "exclamationmark.triangle.fill" -> Icons.Filled.Warning
    "arrow.up.circle.fill" -> Icons.Filled.ArrowCircleUp
    else -> Icons.Filled.Circle
}
