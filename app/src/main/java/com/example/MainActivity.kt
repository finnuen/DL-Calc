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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.CalcStateRepository
import com.example.ui.FileSizeVisualTransformation
import com.example.ui.ThousandsSeparatorVisualTransformation
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

// Reusable static KeyboardOptions and Shapes to avoid per-recomposition allocations
private val DecimalNextKeyboardOptions = KeyboardOptions(
  keyboardType = KeyboardType.Decimal,
  imeAction = ImeAction.Next
)
private val FileSizeKeyboardOptions = KeyboardOptions(
  keyboardType = KeyboardType.Phone,
  imeAction = ImeAction.Next
)
private val DecimalDoneKeyboardOptions = KeyboardOptions(
  keyboardType = KeyboardType.Decimal,
  imeAction = ImeAction.Done
)
private val ActionButtonShape = RoundedCornerShape(12.dp)
private val WhiteButtonTextStyle = TextStyle(
  fontSize = 19.sp,
  fontWeight = FontWeight.Bold,
  color = Color.White
)
private val BlackButtonTextStyle = TextStyle(
  fontSize = 19.sp,
  fontWeight = FontWeight.Bold,
  color = Color.Black
)

@Composable
fun DLCalcApp() {
  val context = LocalContext.current
  val database = remember { AppDatabase.getDatabase(context) }
  val repository = remember { CalcStateRepository(database.calcStateDao()) }
  val viewModel: DLCalcViewModel = viewModel(
    factory = DLCalcViewModel.provideFactory(repository)
  )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val result by viewModel.calculationResult.collectAsStateWithLifecycle()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.systemBars,
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
      onDaysChange = viewModel::onDaysChange,
      onHoursChange = viewModel::onHoursChange,
      onMinutesChange = viewModel::onMinutesChange,
      onSecondsChange = viewModel::onSecondsChange,
      onTimeSecondsChange = viewModel::onTimeSecondsChange,
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
  onDaysChange: (String) -> Unit = {},
  onHoursChange: (String) -> Unit = {},
  onMinutesChange: (String) -> Unit = {},
  onSecondsChange: (String) -> Unit = {},
  onTimeSecondsChange: (String) -> Unit = {},
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

  val cursorBrush = remember(textColor) { SolidColor(textColor) }
  val subtleIconTint = remember(textColor) { textColor.copy(alpha = 0.35f) }
  val regularTextStyle = remember(textColor) {
    TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = textColor)
  }
  val boldEndTextStyle = remember(textColor) {
    TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.End)
  }
  val semiBoldEndTextStyle = remember(textColor) {
    TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = textColor, textAlign = TextAlign.End)
  }
  val mediumDropdownTextStyle = remember(textColor) {
    TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium, color = textColor)
  }
  val tooltipTextStyle = remember(textColor) {
    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
  }
  val doneKeyboardActions = remember(focusManager) {
    KeyboardActions(onDone = { focusManager.clearFocus() })
  }

  val showTimeUnderline = (uiState.calcMode != CalcMode.TIME)
  val showSizeUnderline = (uiState.calcMode != CalcMode.SIZE)
  val showSpeedUnderline = (uiState.calcMode != CalcMode.SPEED)

  val displayedFileSizeUnit = if (uiState.calcMode == CalcMode.SIZE && result.calculatedSizeUnit != null) {
    result.calculatedSizeUnit
  } else {
    uiState.fileSizeUnit
  }

  val displayedSpeedUnit = if (uiState.calcMode == CalcMode.SPEED && result.calculatedSpeedUnit != null) {
    result.calculatedSpeedUnit
  } else {
    uiState.speedUnit
  }

  fun copyToClipboard(text: String, label: String) {
    if (text.isEmpty()) return
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      val clip = ClipData.newPlainText(label, text)
      clipboard?.setPrimaryClip(clip)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      // Prevent crash if clipboard service is restricted or unavailable
    }
  }

  val copyBreakdownText = remember(
    uiState.calcMode,
    uiState.days,
    uiState.hours,
    uiState.minutes,
    uiState.seconds,
    result
  ) {
    if (uiState.calcMode == CalcMode.TIME) {
      result.toTimeBreakdownString()
    } else {
      val fromResult = if (result.hasResult) result.toTimeBreakdownString() else ""
      if (fromResult.isNotEmpty()) {
        fromResult
      } else {
        val sb = StringBuilder()
        if (uiState.days.isNotEmpty() && uiState.days != "0") {
          sb.append(uiState.days).append(if (uiState.days == "1") " day" else " days")
        }
        if (uiState.hours.isNotEmpty() && uiState.hours != "0") {
          if (sb.isNotEmpty()) sb.append(", ")
          sb.append(uiState.hours).append(if (uiState.hours == "1") " hour" else " hours")
        }
        if (uiState.minutes.isNotEmpty() && uiState.minutes != "0") {
          if (sb.isNotEmpty()) sb.append(", ")
          sb.append(uiState.minutes).append(if (uiState.minutes == "1") " minute" else " minutes")
        }
        if (uiState.seconds.isNotEmpty() && uiState.seconds != "0") {
          if (sb.isNotEmpty()) sb.append(", ")
          sb.append(uiState.seconds).append(if (uiState.seconds == "1") " second" else " seconds")
        }
        if (sb.isEmpty()) "0 seconds" else sb.toString()
      }
    }
  }

  val copySecondsText = remember(uiState.calcMode, uiState.timeSeconds, result) {
    if (uiState.calcMode == CalcMode.TIME) {
      result.toSecondsRawString()
    } else {
      uiState.timeSeconds
    }
  }

  val currentCopyBreakdownText by rememberUpdatedState(copyBreakdownText)
  val currentCopySecondsText by rememberUpdatedState(copySecondsText)

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
      // In TIME mode, shows calculated days/hours/minutes/seconds without underline.
      // In other modes, accepts inputs for days/hours/minutes/seconds with underlines.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(150.dp)
          .testTag("result_box_time")
          .pointerInput(Unit) {
            detectTapGestures(
              onLongPress = {
                if (currentCopyBreakdownText.isNotEmpty()) {
                  copyToClipboard(currentCopyBreakdownText, "Download Time Breakdown")
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
            value = if (uiState.calcMode == CalcMode.TIME) result.formattedDays else uiState.days,
            unit = "days",
            valueTextStyle = boldEndTextStyle,
            unitTextStyle = regularTextStyle,
            cursorBrush = cursorBrush,
            underlineColor = underlineColor,
            showUnderline = showTimeUnderline,
            isEditable = (uiState.calcMode != CalcMode.TIME),
            onValueChange = onDaysChange
          )
          TimeBreakdownRow(
            value = if (uiState.calcMode == CalcMode.TIME) result.formattedHours else uiState.hours,
            unit = "hours",
            valueTextStyle = boldEndTextStyle,
            unitTextStyle = regularTextStyle,
            cursorBrush = cursorBrush,
            underlineColor = underlineColor,
            showUnderline = showTimeUnderline,
            isEditable = (uiState.calcMode != CalcMode.TIME),
            onValueChange = onHoursChange
          )
          TimeBreakdownRow(
            value = if (uiState.calcMode == CalcMode.TIME) result.formattedMinutes else uiState.minutes,
            unit = "minutes",
            valueTextStyle = boldEndTextStyle,
            unitTextStyle = regularTextStyle,
            cursorBrush = cursorBrush,
            underlineColor = underlineColor,
            showUnderline = showTimeUnderline,
            isEditable = (uiState.calcMode != CalcMode.TIME),
            onValueChange = onMinutesChange
          )
          TimeBreakdownRow(
            value = if (uiState.calcMode == CalcMode.TIME) result.formattedSeconds else uiState.seconds,
            unit = "seconds",
            valueTextStyle = boldEndTextStyle,
            unitTextStyle = regularTextStyle,
            cursorBrush = cursorBrush,
            underlineColor = underlineColor,
            showUnderline = showTimeUnderline,
            isEditable = (uiState.calcMode != CalcMode.TIME),
            onValueChange = onSecondsChange
          )
        }

        // Tap & hold helper copy button (32.dp, aligned with the seconds copy button)
        IconButton(
          onClick = {
            if (copyBreakdownText.isNotEmpty()) {
              copyToClipboard(copyBreakdownText, "Download Time Breakdown")
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
            tint = subtleIconTint,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 2. Seconds Row: _____ seconds
      // In TIME mode, shows calculated seconds. In other modes, accepts input.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("result_box_seconds")
          .pointerInput(Unit) {
            detectTapGestures(
              onLongPress = {
                if (currentCopySecondsText.isNotEmpty()) {
                  copyToClipboard(currentCopySecondsText, "Download Total Seconds")
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
          Box(
            modifier = Modifier
              .width(110.dp)
              .height(32.dp)
              .then(
                if (showTimeUnderline) {
                  Modifier.drawBehind {
                    val strokeWidth = 1.5.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(
                      color = underlineColor,
                      start = Offset(0f, y),
                      end = Offset(size.width, y),
                      strokeWidth = strokeWidth
                    )
                  }
                } else Modifier
              ),
            contentAlignment = Alignment.CenterEnd
          ) {
            if (uiState.calcMode == CalcMode.TIME) {
              Text(
                text = if (result.hasResult) result.formattedTotalSeconds else "",
                style = boldEndTextStyle,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              BasicTextField(
                value = uiState.timeSeconds,
                onValueChange = onTimeSecondsChange,
                singleLine = true,
                visualTransformation = ThousandsSeparatorVisualTransformation,
                textStyle = boldEndTextStyle,
                cursorBrush = cursorBrush,
                keyboardOptions = DecimalNextKeyboardOptions,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("time_seconds_input")
              )
            }
          }

          Spacer(modifier = Modifier.width(10.dp))

          Text(
            text = "seconds",
            style = regularTextStyle
          )
        }

        // Copy button perfectly aligned with top copy button (same size 32.dp and horizontal margin)
        IconButton(
          onClick = {
            if (copySecondsText.isNotEmpty()) {
              copyToClipboard(copySecondsText, "Download Total Seconds")
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
            tint = subtleIconTint,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // 3. File size row: File size: _____ GB v   [ⓘ]
      // Standardized layout with matching slot widths for perfect vertical alignment of inputs, dropdowns, and [copy] column
      var isFileSizeFocused by remember { mutableStateOf(false) }
      var isFileSizeInfoExpanded by remember { mutableStateOf(false) }
      val fileSizeFocusRequester = remember { FocusRequester() }
      val fileSizeScrollState = rememberScrollState()
      val unfocusedFileSizeDisplay = remember(uiState.fileSize, isFileSizeFocused) {
        if (!isFileSizeFocused && uiState.fileSize.contains('+')) {
          val suffix = DownloadCalculator.formatFileSizeResultSuffix(uiState.fileSize)
          if (suffix.isNotEmpty()) {
            FileSizeVisualTransformation.Unfocused.filter(
              AnnotatedString(uiState.fileSize)
            ).text.text
          } else {
            ""
          }
        } else {
          ""
        }
      }
      LaunchedEffect(unfocusedFileSizeDisplay) {
        if (unfocusedFileSizeDisplay.isNotEmpty()) {
          withFrameNanos { }
          fileSizeScrollState.scrollTo(fileSizeScrollState.maxValue)
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = "File size:",
            style = regularTextStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(88.dp)
          )

          // Underline input field (static fixed width: 90.dp)
          Box(
            modifier = Modifier
              .width(90.dp)
              .height(40.dp)
              .then(
                if (showSizeUnderline) {
                  Modifier.drawBehind {
                    val strokeWidth = 1.5.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(
                      color = underlineColor,
                      start = Offset(0f, y),
                      end = Offset(size.width, y),
                      strokeWidth = strokeWidth
                    )
                  }
                } else Modifier
              )
              .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd
          ) {
            if (uiState.calcMode == CalcMode.SIZE) {
              Text(
                text = if (result.hasResult) result.calculatedSize else "",
                style = semiBoldEndTextStyle,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              BasicTextField(
                value = uiState.fileSize,
                onValueChange = onFileSizeChange,
                singleLine = true,
                visualTransformation = ThousandsSeparatorVisualTransformation,
                textStyle = semiBoldEndTextStyle,
                cursorBrush = cursorBrush,
                keyboardOptions = FileSizeKeyboardOptions,
                modifier = Modifier
                  .fillMaxWidth()
                  .focusRequester(fileSizeFocusRequester)
                  .onFocusChanged { focusState ->
                    isFileSizeFocused = focusState.isFocused
                  }
                  .testTag("file_size_input")
              )
              if (unfocusedFileSizeDisplay.isNotEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .horizontalScroll(fileSizeScrollState)
                    .clickable(indication = null, interactionSource = null) {
                      fileSizeFocusRequester.requestFocus()
                    },
                  contentAlignment = Alignment.CenterEnd
                ) {
                  Text(
                    text = unfocusedFileSizeDisplay,
                    style = semiBoldEndTextStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 82.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // File size unit dropdown (fixed width: 74.dp to match speed unit slot for precise alignment)
          Box(modifier = Modifier.width(74.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("file_size_unit_dropdown")
                .clickable { onSetFileSizeDropdownExpanded(true) }
                .padding(horizontal = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = displayedFileSizeUnit.label,
                style = mediumDropdownTextStyle,
                maxLines = 1,
                softWrap = false
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
        }

        // Info button (ⓘ) on the right side of GB dropdown and aligned with the [copy] column
        Box(
          modifier = Modifier
            .size(32.dp)
            .align(Alignment.CenterEnd)
        ) {
          IconButton(
            onClick = {
              isFileSizeInfoExpanded = !isFileSizeInfoExpanded
            },
            modifier = Modifier
              .size(32.dp)
              .testTag("file_size_info_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "you can use + (plus) for multiple file size",
              tint = subtleIconTint,
              modifier = Modifier.size(18.dp)
            )
          }

          DropdownMenu(
            expanded = isFileSizeInfoExpanded,
            onDismissRequest = { isFileSizeInfoExpanded = false }
          ) {
            Text(
              text = "you can use + (plus) for multiple file size",
              style = tooltipTextStyle,
              modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("file_size_info_tooltip")
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 4. Speed row: Speed: _____ Mbps v
      // Perfectly aligned with File size row (Label 88dp, Input 90dp, Spacer 8dp, Unit 74dp)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = "Speed:",
            style = regularTextStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(88.dp)
          )

          // Underline input field (static fixed width: 90.dp)
          Box(
            modifier = Modifier
              .width(90.dp)
              .height(40.dp)
              .then(
                if (showSpeedUnderline) {
                  Modifier.drawBehind {
                    val strokeWidth = 1.5.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(
                      color = underlineColor,
                      start = Offset(0f, y),
                      end = Offset(size.width, y),
                      strokeWidth = strokeWidth
                    )
                  }
                } else Modifier
              )
              .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd
          ) {
            if (uiState.calcMode == CalcMode.SPEED) {
              Text(
                text = if (result.hasResult) result.calculatedSpeed else "",
                style = semiBoldEndTextStyle,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              BasicTextField(
                value = uiState.speed,
                onValueChange = onSpeedChange,
                singleLine = true,
                visualTransformation = ThousandsSeparatorVisualTransformation,
                textStyle = semiBoldEndTextStyle,
                cursorBrush = cursorBrush,
                keyboardOptions = DecimalDoneKeyboardOptions,
                keyboardActions = doneKeyboardActions,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("speed_input")
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Speed unit dropdown (static fixed width: 74.dp)
          Box(modifier = Modifier.width(74.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("speed_unit_dropdown")
                .clickable { onSetSpeedDropdownExpanded(true) }
                .padding(horizontal = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = displayedSpeedUnit.label,
                style = mediumDropdownTextStyle,
                maxLines = 1,
                softWrap = false
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
          shape = ActionButtonShape,
          modifier = Modifier
            .width(105.dp)
            .height(46.dp)
            .testTag("mode_toggle_button")
        ) {
          Text(
            text = if (uiState.isDarkMode) "Dark" else "Light",
            style = if (uiState.isDarkMode) BlackButtonTextStyle else WhiteButtonTextStyle
          )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Clear all button (red rounded box with white text "Clear all")
        // Does not close keyboard or deselect input (Requirement 1)
        Button(
          onClick = onClearAll,
          colors = ButtonDefaults.buttonColors(
            containerColor = ClearRedColor,
            contentColor = Color.White
          ),
          shape = ActionButtonShape,
          modifier = Modifier
            .width(135.dp)
            .height(46.dp)
            .testTag("clear_all_button")
        ) {
          Text(
            text = "Clear all",
            style = WhiteButtonTextStyle
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
  valueTextStyle: TextStyle,
  unitTextStyle: TextStyle,
  cursorBrush: SolidColor,
  underlineColor: Color,
  showUnderline: Boolean,
  isEditable: Boolean,
  onValueChange: (String) -> Unit = {}
) {
  Row(
    modifier = Modifier
      .width(235.dp)
      .height(28.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start
  ) {
    Box(
      modifier = Modifier
        .width(80.dp)
        .height(28.dp)
        .then(
          if (showUnderline) {
            Modifier.drawBehind {
              val strokeWidth = 1.5.dp.toPx()
              val y = size.height - strokeWidth / 2
              drawLine(
                color = underlineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
              )
            }
          } else Modifier
        ),
      contentAlignment = Alignment.CenterEnd
    ) {
      if (isEditable) {
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          visualTransformation = ThousandsSeparatorVisualTransformation,
          textStyle = valueTextStyle,
          cursorBrush = cursorBrush,
          keyboardOptions = DecimalNextKeyboardOptions,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_$unit")
        )
      } else {
        Text(
          text = value,
          style = valueTextStyle,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = unit,
      style = unitTextStyle
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
