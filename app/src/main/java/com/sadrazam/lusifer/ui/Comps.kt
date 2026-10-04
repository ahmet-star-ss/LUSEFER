package com.sadrazam.lusifer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Mono = FontFamily.Monospace

@Composable
fun N(
    text: String, size: TextUnit = 14.sp, alpha: Float = 1f,
    bold: Boolean = false, modifier: Modifier = Modifier
) {
    Text(
        text, modifier = modifier, color = Neon.copy(alpha = alpha), fontSize = size,
        fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun PageFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            N("‹", 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp))
            N(title.uppercase(), 18.sp, bold = true)
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().padding(vertical = 5.dp)
        .border(1.dp, Neon.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(14.dp)
    Column(m, content = content)
}

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(14.dp))
    N(text, 13.sp, 0.7f, bold = true)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun SwitchRow(label: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            N(label, 14.sp, bold = true)
            if (desc != null) N(desc, 11.sp, 0.6f)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = Neon,
                uncheckedThumbColor = Neon, uncheckedTrackColor = Color.Black,
                uncheckedBorderColor = Neon.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun RadioRow(label: String, desc: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Neon, unselectedColor = Neon.copy(alpha = 0.5f))
        )
        Column(Modifier.padding(start = 6.dp)) {
            N(label, 14.sp, bold = selected)
            if (desc != null) N(desc, 11.sp, 0.6f)
        }
    }
}

@Composable
fun SliderRow(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    fmt: (Float) -> String, steps: Int = 0, onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            N(label, 13.sp)
            N(fmt(value), 13.sp, 0.8f, bold = true)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Neon, activeTrackColor = Neon, inactiveTrackColor = NeonDim
            )
        )
    }
}

@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .border(1.5.dp, Neon, RoundedCornerShape(50))
            .background(NeonDim.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { N(text, 13.sp, bold = true) }
}

@Composable
fun NeonField(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, password: Boolean = false, singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        label = { Text(label, color = Neon.copy(alpha = 0.7f), fontSize = 12.sp) },
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Neon, unfocusedTextColor = Neon, cursorColor = Neon,
            focusedBorderColor = Neon, unfocusedBorderColor = Neon.copy(alpha = 0.4f)
        )
    )
}
