@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    viewModel: EspelhoViewModel,
    modifier: Modifier = Modifier
) {
    val challenges by viewModel.selectedChallenges.collectAsState()
    val decisionStyle by viewModel.selectedDecisionStyle.collectAsState()
    val nameInput by viewModel.userNameInput.collectAsState()
    val progress by viewModel.onboardingProgress.collectAsState()
    val isActivating by viewModel.isOnboardingActivating.collectAsState()

    var step by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Background cosmic gradient
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CosmicBackground, Color(0xFF0F0B30))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (isActivating) {
            // Processing Activation flow
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Orb(
                    size = 94.dp,
                    anim = "pulse",
                    glow = true,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                val message = when {
                    progress < 25 -> "Analisando seu perfil..."
                    progress < 55 -> "Calibrando para você..."
                    progress < 85 -> "Personalizando experiência..."
                    else -> "Seu IA de espelho mental está pronto!"
                }

                Text(
                    text = "Preparando seu IA de espelho mental...",
                    color = CosmicTextPrimary,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    color = CosmicTextSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.height(24.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                Bar(value = progress.toFloat(), color = CosmicPrimaryGlow, animate = true)

                Text(
                    text = "$progress%",
                    color = CosmicPrimaryGlow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                )
            }
        } else {
            // Stepped Form Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 32.dp)
            ) {
                // Step Indicator Header
                if (step > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { step-- },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(text = "←", color = CosmicTextSecondary, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        val visualProgress = (step.toFloat() / 3f) * 100f
                        Bar(value = visualProgress, color = CosmicPrimary)
                    }
                }

                // Inner Form Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    when (step) {
                        0 -> IntroStep(
                            onNext = { step = 1 },
                            onLoginWithGoogle = { email, name ->
                                viewModel.loginWithGoogle(email, name)
                            }
                        )
                        1 -> ChallengesStep(
                            selected = challenges,
                            onToggle = { viewModel.toggleChallenge(it) }
                        )
                        2 -> DecisionStylesStep(
                            selected = decisionStyle,
                            onSelect = { viewModel.setDecisionStyle(it) }
                        )
                        3 -> NameStep(
                            name = nameInput,
                            onNameChange = { viewModel.userNameInput.value = it },
                            onNext = {
                                keyboardController?.hide()
                                viewModel.activateIA()
                            }
                        )
                    }
                }

                // Primary Bottom Action
                if (step > 0) {
                    val enabled = when (step) {
                        1 -> challenges.isNotEmpty()
                        2 -> decisionStyle != null
                        else -> true
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                if (step < 3) {
                                    step++
                                } else {
                                    keyboardController?.hide()
                                    viewModel.activateIA()
                                }
                            },
                            enabled = enabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CosmicPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0x227C5FE6),
                                disabledContentColor = Color(0x33E8E0FF)
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("continue_button")
                        ) {
                            Text(
                                text = if (step == 3) "✨ Ativar meu IA de espelho mental" else "Continuar →",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        if (step < 3) {
                            TextButton(
                                onClick = { step++ },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "Pular",
                                    color = CosmicTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStep(
    onNext: () -> Unit,
    onLoginWithGoogle: (email: String, name: String) -> Unit
) {
    var showGoogleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Orb(
            size = 96.dp,
            anim = "float",
            glow = true,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Text(
            text = "Olá, eu sou o seu\nIA de espelho mental",
            color = CosmicTextPrimary,
            fontSize = 28.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            lineHeight = 36.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "Uma inteligência que aprende especificamente sobre você — seus padrões, medos e o que você evita — para te guiar em um caminho de autoconhecimento genuíno.",
            color = CosmicTextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 36.dp)
        )

        // Features Grid
        val pillars = listOf(
            Pair("🧠", "Aprende sobre você em cada conversa"),
            Pair("🔒", "Completamente privado e seguro"),
            Pair("⚡", "Sincronize histórico via Conta Google")
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pillars.forEach { pillar ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x0C7C5FE6), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = pillar.first, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        text = pillar.second,
                        color = CosmicTextPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary, contentColor = Color.White),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("get_started_button")
        ) {
            Text(
                text = "Conhecer meu IA de espelho mental →",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(
            onClick = { showGoogleDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔗 ", fontSize = 16.sp)
                Text(
                    text = "Restaurar de Conta Google",
                    color = CosmicPrimaryGlow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        if (showGoogleDialog) {
            GoogleSignInDialog(
                onDismiss = { showGoogleDialog = false },
                onConfirm = { email, name ->
                    showGoogleDialog = false
                    onLoginWithGoogle(email, name)
                }
            )
        }
    }
}

@Composable
private fun ChallengesStep(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Label(text = "ETAPA 1 DE 3")
        Text(
            text = "Qual é seu maior desafio agora?",
            color = CosmicTextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Escolha até 3 opções. Isso me ajudará a te entender melhor.",
            color = CosmicTextSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StaticData.CHALLENGES.forEach { challenge ->
                val active = selected.contains(challenge.id)
                Pill(
                    text = "${challenge.emoji} ${challenge.label}",
                    active = active,
                    onClick = { onToggle(challenge.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecisionStylesStep(
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Label(text = "ETAPA 2 DE 3")
        Text(
            text = "Como você toma decisões difíceis?",
            color = CosmicTextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StaticData.DECISION_STYLES.forEach { style ->
                val active = selected == style.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) CosmicPrimary else CosmicBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) CosmicPrimary.copy(alpha = 0.22f) else Color(0x06FFFFFF)
                        )
                        .clickable { onSelect(style.id) }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = style.label,
                        color = if (active) CosmicTextPrimary else CosmicTextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Label(text = "ETAPA 3 DE 3")
        Text(
            text = "Como posso te chamar?",
            color = CosmicTextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Opcional — pode pular ou usar um codinome se preferir.",
            color = CosmicTextSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text(text = "Seu nome ou apelido...", color = Color(0x55C8BEFF)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0x0CFFFFFF),
                unfocusedContainerColor = Color(0x0CFFFFFF),
                focusedBorderColor = CosmicPrimary,
                unfocusedBorderColor = CosmicBorder,
                focusedTextColor = CosmicTextPrimary,
                unfocusedTextColor = CosmicTextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onNext() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("name_input"),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp
            )
        )
    }
}
