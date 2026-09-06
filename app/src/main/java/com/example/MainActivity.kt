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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.CalcStateRepository
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
  val context = LocalContext.current
  val database = remember { AppDatabase.getDatabase(context) }
  val repository = remember { CalcStateRepository(database.calcStateDao()) }
  val viewModel: DLCalcViewModel = viewModel(
    factory = DLCalcViewModel.provideFactory(repository)
  )

  val uiState by viewModel.uiState.collectAsState()
  val result by viewModel.calculationResult.collectAsState()

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    containerColor = if (uiState.isDarkMode) DarkBgColor else LightBgColor
  ) { innerPadding ->
    DLCalcScreen(
      uiState = uiState,
      result = result,
      onFileSizeChange = viewModel::onFileSizeChange,
      onFileSizeUnitChange = viewModel::onFileSizeUnitChange,
      onSetFileSizeDropdownExpanded = viewModel::setFileSizeDropdownExpanded,
      onSpeedChange = viewModel::onSpeedChange,
      onSpeedUnitChange = viewModel::onSpeedUnitChange,
      onSetSpeedDropdownExpanded = viewModel::setSpeedDropdownExpanded,
      onClearFileSize = viewModel::clearFileSize,
      onClearSpeed = viewModel::clearSpeed,
      onClearAll = viewModel::clearAll,
      onToggleDarkMode = viewModel::toggleDarkMode,
      modifier = Modifier.padding(innerPadding)
    )
  }
}

@Composable
fun DLCalcScreen(
  uiState: DLCalcUiState = DLCalcUiState(),
  result: DownloadTimeResult = DownloadTimeResult(hasResult = false),
  onFileSizeChange: (String) -> Unit = {},
  onFileSizeUnitChange: (FileSizeUnit) -> Unit = {},
  onSetFileSizeDropdownExpanded: (Boolean) -> Unit = {},
  onSpeedChange: (String) -> Unit = {},
  onSpeedUnitChange: (SpeedUnit) -> Unit = {},
  onSetSpeedDropdownExpanded: (Boolean) -> Unit = {},
  onClearFileSize: () -> Unit = {},
  onClearSpeed: () -> Unit = {},
  onClearAll: () -> Unit = {},
  onToggleDarkMode: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val focusManager = LocalFocusManager.current

  val textColor = if (uiState.isDarkMode) DarkTextColor else LightTextColor
  val underlineColor = if (uiState.isDarkMode) DarkUnderlineColor else LightUnderlineColor
  val backgroundColor = if (uiState.isDarkMode) DarkBgColor else LightBgColor

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
            value = uiState.fileSize,
            onValueChange = onFileSizeChange,
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
              .clickable { onSetFileSizeDropdownExpanded(true) }
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = uiState.fileSizeUnit.label,
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
            expanded = uiState.isFileSizeDropdownExpanded,
            onDismissRequest = { onSetFileSizeDropdownExpanded(false) }
          ) {
            FileSizeUnit.entries.forEach { unit ->
              DropdownMenuItem(
                text = { Text(unit.label, fontWeight = FontWeight.Medium) },
                onClick = {
                  onFileSizeUnitChange(unit)
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
            .clickable { onClearFileSize() }
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
            value = uiState.speed,
            onValueChange = onSpeedChange,
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
              .clickable { onSetSpeedDropdownExpanded(true) }
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = uiState.speedUnit.label,
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
            expanded = uiState.isSpeedDropdownExpanded,
            onDismissRequest = { onSetSpeedDropdownExpanded(false) }
          ) {
            SpeedUnit.entries.forEach { unit ->
              DropdownMenuItem(
                text = { Text(unit.label, fontWeight = FontWeight.Medium) },
                onClick = {
                  onSpeedUnitChange(unit)
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
            .clickable { onClearSpeed() }
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
            containerColor = if (uiState.isDarkMode) Color.White else Color.Black,
            contentColor = if (uiState.isDarkMode) Color.Black else Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .width(105.dp)
            .height(46.dp)
            .testTag("mode_toggle_button")
        ) {
          Text(
            text = if (uiState.isDarkMode) "Dark" else "Light",
            style = TextStyle(
              fontSize = 19.sp,
              fontWeight = FontWeight.Bold,
              color = if (uiState.isDarkMode) Color.Black else Color.White
            )
          )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Clear all button (red rounded box with white text "Clear all")
        Button(
          onClick = {
            onClearAll()
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
    DLCalcScreen(uiState = DLCalcUiState(isDarkMode = false))
  }
}

@Preview(showBackground = true)
@Composable
fun DLCalcScreenDarkPreview() {
  MyApplicationTheme {
    DLCalcScreen(uiState = DLCalcUiState(isDarkMode = true))
  }
}
