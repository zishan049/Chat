package com.chat.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.chat.app.telemetry.AppDiagnosticsTestRunner
import com.chat.app.telemetry.DiagnosticTestState

@Composable
fun DiagnosticTestOverlay() {
    val testState by AppDiagnosticsTestRunner.testState.collectAsState()
    val overlayVisible by AppDiagnosticsTestRunner.overlayVisible.collectAsState()

    AnimatedVisibility(
        visible = testState != null && overlayVisible,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150)),
        modifier = Modifier.zIndex(9999f)
    ) {
        val state = testState ?: return@AnimatedVisibility
        val isStress = state.testType == "STRESS"

        val primaryColor = if (isStress) Color(0xFFF59E0B) else Color(0xFF10B981)
        val accentColor = if (isStress) Color(0xFF8B5CF6) else Color(0xFF06B6D4)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDF050608))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!state.isRunning) {
                        AppDiagnosticsTestRunner.dismissOverlay()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb clicks */ }
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(primaryColor, accentColor)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1117))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ── Top Header ──────────────────────────────────────────────
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(primaryColor.copy(alpha = 0.15f))
                                .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(primaryColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isStress) "⚡ LIVE STRESS TEST ACTIVE" else "🔒 ZERO-TRUST SECURITY AUDIT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = state.testName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = state.phase,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    // ── Middle Benchmark Progress & Live Gauges ─────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progress",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "${state.progressPercent}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = state.progressPercent.coerceIn(0, 100) / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = primaryColor,
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Current Metric Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF161A24), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF262C3D), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "CURRENT PARAMETER BENCHMARK",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.currentMetric,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Terminal Logs List
                        Text(
                            text = "DIAGNOSTIC LOG TICKER",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(Color(0xFF0A0C10), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E2433), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.logs) { log ->
                                    Text(
                                        text = "› $log",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (log.contains("✔")) Color(0xFF10B981) else if (log.contains("⚠️")) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ── Bottom Action Button ────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Controlled via PC Loger",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )

                        if (state.isRunning) {
                            Button(
                                onClick = { AppDiagnosticsTestRunner.cancelTest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Abort Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { AppDiagnosticsTestRunner.dismissOverlay() },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Dismiss HUD", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
