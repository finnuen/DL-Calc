package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        DLCalcApp()
      }
    }
  }
}

// Colors
private val ClearRedColor = Color(0xFFFF1A1A)
private val LightBgColor = Color(0xFFFFFFFF)
private val DarkBgColor = Color(0xFF121212)
private val LightTextColor = Color(0xFF0F172A)
private val DarkTextColor = Color(0xFFF8FAFC)
private val LightUnderlineColor = Color(0xFF0F172A)
private val DarkUnderlineColor = Color(0xFFE2E8F0)

@Composable
fun DLCalcApp() {
  // Requirement 2: make dark mode as default
  var isDarkMode by remember { mutableStateOf(true) }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    containerColor = if (isDarkMode) DarkBgColor else LightBgColor
  ) { innerPadding ->
    DLCalcScreen(
      isDarkMode = isDarkMode,
      onToggleDarkMode = { isDarkMode = !isDarkMode },
      modifier = Modifier.padding(innerPadding)
    )
  }
}

@Composable
fun DLCalcScreen(
  isDarkMode: Boolean = true,
  onToggleDarkMode: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val focusManager = LocalFocusManager.current

  var fileSize by remember { mutableStateOf("") }
  var fileSizeUnit by remember { mutableStateOf(FileSizeUnit.GB) }
  var isFileSizeDropdownExpanded by remember { mutableStateOf(false) }

  var speed by remember { mutableStateOf("") }
  var speedUnit by remember { mutableStateOf(SpeedUnit.MBPS) }
  var isSpeedDropdownExpanded by remember { mutableStateOf(false) }

  val result = remember(fileSize, fileSizeUnit, speed, speedUnit) {
    DownloadCalculator.calculate(fileSize, fileSizeUnit, speed, speedUnit)
  }

  val textColor = if (isDarkMode) DarkTextColor else LightTextColor
  val underlineColor = if (isDarkMode) DarkUnderlineColor else LightUnderlineColor
  val backgroundColor = if (isDarkMode) DarkBgColor else LightBgColor

  fun copyToClipboard(text: String, label: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor)
      .padding(horizontal = 24.dp, vertical = 20.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .width(360.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(10.dp))

      // 1. Static Box - Breakdown (days, hours, minutes, seconds)
      // "1. static box, 2. tap & hold to copy"
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(150.dp)
          .testTag("result_box_time")
          .pointerInput(result) {
            detectTapGestures(
              onLongPress = {
                val copyText = result.toTimeBreakdownString()
                if (copyText.isNotEmpty()) {
                  copyToClipboard(copyText, "Download Time Breakdown")
                }
              }
            )
          }
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          TimeBreakdownRow(
            value = if (result.hasResult) {
              if (result.isBelowOneSecond) "0" else result.days.toString()
            } else "____",
            unit = "days",
            textColor = textColor
          )
          TimeBreakdownRow(
            value = if (result.hasResult) {
              if (result.isBelowOneSecond) "0" else result.hours.toString()
            } else "____",
            unit = "hours",
            textColor = textColor
          )
          TimeBreakdownRow(
            value = if (result.hasResult) {
              if (result.isBelowOneSecond) "0" else result.minutes.toString()
            } else "____",
            unit = "minutes",
            textColor = textColor
          )
          TimeBreakdownRow(
            value = if (result.hasResult) {
              if (result.isBelowOneSecond) "1<" else result.seconds.toString()
            } else "____",
            unit = "seconds",
            textColor = textColor
          )
        }

        // Tap & hold helper copy button (32.dp, aligned with the seconds copy button)
        IconButton(
          onClick = {
            val copyText = result.toTimeBreakdownString()
            if (copyText.isNotEmpty()) {
              copyToClipboard(copyText, "Download Time Breakdown")
            }
          },
          modifier = Modifier
            .size(32.dp)
            .align(Alignment.TopEnd)
            .testTag("copy_breakdown_button")
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy breakdown",
            tint = textColor.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 2. Static Box - Total Seconds
      // "1. static box, 2. tap & hold to copy"
      // Both result boxes have exactly matching padding (horizontal = 16.dp) and end-aligned 32.dp copy buttons
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("result_box_seconds")
          .pointerInput(result) {
            detectTapGestures(
              onLongPress = {
                val copyText = result.toSecondsRawString()
                if (copyText.isNotEmpty()) {
                  copyToClipboard(copyText, "Download Total Seconds")
                }
              }
            )
          }
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          if (result.hasResult) {
            Box(
              modifier = Modifier
                .width(110.dp)
                .height(32.dp)
                .drawBehind {
                  val strokeWidth = 1.5.dp.toPx()
                  val y = size.height - strokeWidth / 2
                  drawLine(
                    color = underlineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                  )
                },
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = result.formattedTotalSeconds,
                style = TextStyle(
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = textColor,
                  textAlign = TextAlign.Center
                )
              )
            }
          } else {
            Box(
              modifier = Modifier
                .width(110.dp)
                .height(32.dp)
                .drawBehind {
                  val strokeWidth = 1.5.dp.toPx()
                  val y = size.height - strokeWidth / 2
                  drawLine(
                    color = underlineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                  )
                }
            )
          }

          Spacer(modifier = Modifier.width(10.dp))

          Text(
            text = "seconds",
            style = TextStyle(
              fontSize = 20.sp,
              fontWeight = FontWeight.Medium,
              color = textColor
            )
          )
        }

        // Copy button perfectly aligned with top copy button (same size 32.dp and horizontal margin)
        IconButton(
          onClick = {
            val copyText = result.toSecondsRawString()
            if (copyText.isNotEmpty()) {
              copyToClipboard(copyText, "Download Total Seconds")
            }
          },
          modifier = Modifier
            .size(32.dp)
            .align(Alignment.CenterEnd)
            .testTag("copy_seconds_button")
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy total seconds",
            tint = textColor.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // 3. File size row: File size: _____ GB v [x]
      // Standardized layout with matching slot widths for perfect vertical alignment of inputs, dropdowns, and X buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = "File size:",
          style = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
          ),
          modifier = Modifier.width(96.dp)
        )

        // Underline input field (static fixed width: 95.dp)
        Box(
          modifier = Modifier
            .width(95.dp)
            .height(40.dp)
            .drawBehind {
              val strokeWidth = 1.5.dp.toPx()
              val y = size.height - strokeWidth / 2
              drawLine(
                color = underlineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
              )
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          BasicTextField(
            value = fileSize,
            onValueChange = { newValue ->
              // Accepts dot and comma for decimal numbers: e.g. "1.5" or "1,5"
              if (newValue.isEmpty() || newValue.matches(Regex("""^\d*([.,]\d*)?$"""))) {
                fileSize = newValue
              }
            },
            singleLine = true,
            textStyle = TextStyle(
              fontSize = 19.sp,
              fontWeight = FontWeight.SemiBold,
              color = textColor,
              textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Decimal,
              imeAction = ImeAction.Next
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("file_size_input")
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // File size unit dropdown (fixed width: 85.dp to match speed unit slot for precise alignment)
        Box(modifier = Modifier.width(85.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(40.dp)
              .testTag("file_size_unit_dropdown")
              .clickable { isFileSizeDropdownExpanded = true }
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = fileSizeUnit.label,
              style = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
              )
            )
            Icon(
              imageVector = Icons.Default.ArrowDropDown,
              contentDescription = "Select file size unit",
              tint = textColor,
              modifier = Modifier.size(22.dp)
            )
          }

          DropdownMenu(
            expanded = isFileSizeDropdownExpanded,
            onDismissRequest = { isFileSizeDropdownExpanded = false }
          ) {
            FileSizeUnit.entries.forEach { unit ->
              DropdownMenuItem(
                text = { Text(unit.label, fontWeight = FontWeight.Medium) },
                onClick = {
                  fileSizeUnit = unit
                  isFileSizeDropdownExpanded = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Red X button to clear value (static fixed size square with rounded corners: 28.dp)
        Box(
          modifier = Modifier
            .size(28.dp)
            .background(ClearRedColor, RoundedCornerShape(4.dp))
            .clickable { fileSize = "" }
            .testTag("clear_file_size_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "X",
            style = TextStyle(
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              lineHeight = 17.sp
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 4. Speed row: Speed: _____ Mbps v [x]
      // Perfectly aligned with File size row (Label 96dp, Input 95dp, Spacer 10dp, Unit 85dp, Spacer 8dp, X button 28dp)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = "Speed:",
          style = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
          ),
          modifier = Modifier.width(96.dp)
        )

        // Underline input field (static fixed width: 95.dp)
        Box(
          modifier = Modifier
            .width(95.dp)
            .height(40.dp)
            .drawBehind {
              val strokeWidth = 1.5.dp.toPx()
              val y = size.height - strokeWidth / 2
              drawLine(
                color = underlineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
              )
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          BasicTextField(
            value = speed,
            onValueChange = { newValue ->
              // Accepts dot and comma for decimal numbers: e.g. "1.5" or "1,5"
              if (newValue.isEmpty() || newValue.matches(Regex("""^\d*([.,]\d*)?$"""))) {
                speed = newValue
              }
            },
            singleLine = true,
            textStyle = TextStyle(
              fontSize = 19.sp,
              fontWeight = FontWeight.SemiBold,
              color = textColor,
              textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Decimal,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("speed_input")
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Speed unit dropdown (static fixed width: 85.dp)
        Box(modifier = Modifier.width(85.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(40.dp)
              .testTag("speed_unit_dropdown")
              .clickable { isSpeedDropdownExpanded = true }
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = speedUnit.label,
              style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
              )
            )
            Icon(
              imageVector = Icons.Default.ArrowDropDown,
              contentDescription = "Select speed unit",
              tint = textColor,
              modifier = Modifier.size(22.dp)
            )
          }

          DropdownMenu(
            expanded = isSpeedDropdownExpanded,
            onDismissRequest = { isSpeedDropdownExpanded = false }
          ) {
            SpeedUnit.entries.forEach { unit ->
              DropdownMenuItem(
                text = { Text(unit.label, fontWeight = FontWeight.Medium) },
                onClick = {
                  speedUnit = unit
                  isSpeedDropdownExpanded = false
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Red X button to clear value (static fixed size square with rounded corners: 28.dp)
        Box(
          modifier = Modifier
            .size(28.dp)
            .background(ClearRedColor, RoundedCornerShape(4.dp))
            .clickable { speed = "" }
            .testTag("clear_speed_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "X",
            style = TextStyle(
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              lineHeight = 17.sp
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // 5. Buttons row: [ Mode Toggle ] [ Clear all ]
      // When in Dark mode (default): White box with dark text "Dark"
      // When in Light mode: Black box with white text "Light"
      Row(
        modifier = Modifier
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(
          onClick = onToggleDarkMode,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDarkMode) Color.White else Color.Black,
            contentColor = if (isDarkMode) Color.Black else Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .width(105.dp)
            .height(46.dp)
            .testTag("mode_toggle_button")
        ) {
          Text(
            text = if (isDarkMode) "Dark" else "Light",
            style = TextStyle(
              fontSize = 19.sp,
              fontWeight = FontWeight.Bold,
              color = if (isDarkMode) Color.Black else Color.White
            )
          )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Clear all button (red rounded box with white text "Clear all")
        Button(
          onClick = {
            fileSize = ""
            speed = ""
            focusManager.clearFocus()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = ClearRedColor,
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .width(135.dp)
            .height(46.dp)
            .testTag("clear_all_button")
        ) {
          Text(
            text = "Clear all",
            style = TextStyle(
              fontSize = 19.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          )
        }
      }
    }
  }
}

@Composable
private fun TimeBreakdownRow(
  value: String,
  unit: String,
  textColor: Color
) {
  Row(
    modifier = Modifier
      .width(220.dp)
      .height(28.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start
  ) {
    Text(
      text = value,
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = textColor,
        textAlign = TextAlign.End
      ),
      modifier = Modifier.width(70.dp)
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = unit,
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        color = textColor
      )
    )
  }
}

@Preview(showBackground = true)
@Composable
fun DLCalcScreenLightPreview() {
  MyApplicationTheme {
    DLCalcScreen(isDarkMode = false)
  }
}

@Preview(showBackground = true)
@Composable
fun DLCalcScreenDarkPreview() {
  MyApplicationTheme {
    DLCalcScreen(isDarkMode = true)
  }
}
