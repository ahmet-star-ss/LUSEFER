package com.sadrazam.lusifer.ui

import androidx.compose.ui.graphics.Color

val Neon = Color(0xFF39FF14)
val NeonDim = Color(0xFF0B3D1E)

/** Asistanın görsel durumu. Aşama 3'te wake word / STT / TTS olayları buna bağlanacak. */
enum class AssistantState { IDLE, WAKE, LISTENING, THINKING, SPEAKING }
