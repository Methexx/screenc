# 🕐 Streaming Timer Feature

## Overview

A live streaming timer displays seconds and milliseconds under the close button, showing how long the stream has been active.

## Features

✅ **Live Timer Display**
- Format: `XX.XXXs` (seconds.milliseconds)
- Updates every 50ms for smooth counting
- Example: `5.234s`, `45.890s`, `120.001s`

✅ **Smart Behavior**
- **Starts**: When streaming begins (connection established)
- **Stops**: Immediately when stream fails or disconnects
- **Resets**: When stream starts again (new session)
- **Only visible**: During active streaming

✅ **Position & Compatibility**
- Located directly under the ✕ CLOSE button
- Works in **both landscape and portrait modes**
- Small size (12sp font) - doesn't obstruct view
- Semi-transparent black background (#99000000)

## Visual Layout

```
┌─────────────────────────────┐
│                    ✕ CLOSE  │ ← Red close button (top-right)
│                    0.234s   │ ← Timer (directly under)
│                             │
│      Video Stream Area      │
│                             │
│                             │
└─────────────────────────────┘
```

## Timer Behavior Examples

### Example 1: Normal Streaming
```
00:00 → Start stream → Timer shows: 0.000s
00:05 → Still streaming → Timer shows: 5.234s
00:30 → Still streaming → Timer shows: 30.567s
00:35 → Stop stream → Timer disappears
```

### Example 2: Network Error
```
00:00 → Start stream → Timer shows: 0.000s
00:10 → Streaming → Timer shows: 10.123s
00:12 → Network error! → Timer stops at: 12.456s (then disappears)
```

### Example 3: Multiple Sessions
```
Session 1:
00:00 → Start → Timer: 0.000s
00:20 → Stop → Timer stops

Session 2:
01:00 → Start again → Timer: 0.000s (reset!)
01:15 → Streaming → Timer: 15.789s
```

## Implementation Details

### Android (Kotlin)

**Location**: `VideoReceiverService.kt`

**Components**:
1. **TextView**: Displays timer text
2. **Coroutine Job**: Updates timer every 50ms
3. **Start Time**: Tracks when streaming began

**Key Functions**:
- `startStreamTimer()`: Starts the timer when streaming begins
- `stopStreamTimer()`: Stops timer on disconnect/error
- Timer auto-updates in coroutine loop

**UI Parameters**:
```kotlin
timerTextView = android.widget.TextView(this).apply {
    text = "0.000s"
    setTextColor(android.graphics.Color.WHITE)
    textSize = 12f
    setBackgroundColor(android.graphics.Color.parseColor("#99000000"))
    setPadding(16, 8, 16, 8)
    gravity = Gravity.CENTER
}
```

**Position**:
```kotlin
gravity = Gravity.TOP or Gravity.END
x = 20          // Right margin
y = 140         // Below close button (close button is at y=80)
```

## Usage

### For Users

1. **Start streaming** from the Android app
2. **Look at top-right corner** - you'll see:
   ```
   ✕ CLOSE
   0.234s
   ```
3. **Timer counts up** while streaming (shows seconds.milliseconds)
4. **Stop streaming** or **network error** → Timer stops and disappears
5. **Start again** → Timer resets to 0.000s

### For Developers

The timer:
- Uses `System.currentTimeMillis()` for precise timing
- Updates UI every 50ms via coroutine
- Automatically cleaned up when service stops
- Thread-safe using Kotlin coroutines

## Benefits

✅ **Visual Feedback**: Know exactly how long you've been streaming  
✅ **Debug Tool**: Helps identify connection duration before failures  
✅ **User Confidence**: Shows streaming is actually working  
✅ **Performance Insight**: Monitor stream stability over time  
✅ **Non-Intrusive**: Small size, doesn't block video  

## Technical Specifications

| Property | Value |
|----------|-------|
| Update Rate | 50ms (20 fps) |
| Format | `seconds.milliseconds` |
| Font Size | 12sp |
| Text Color | White (#FFFFFF) |
| Background | Semi-transparent black (#99000000) |
| Position | Top-right, under close button |
| Padding | 16dp horizontal, 8dp vertical |

## Future Enhancements (Optional)

🔮 Possible improvements:
- Option to hide timer (user preference)
- Color change based on duration (green→yellow→red)
- Additional info (FPS, bandwidth)
- Export session duration to logs

---

**Enjoy precise stream timing! ⏱️**
