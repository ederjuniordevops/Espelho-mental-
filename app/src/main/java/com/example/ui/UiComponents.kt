package com.example.ui
 
import android.accounts.AccountManager
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Cosmic Dark Theme Colors ---

val CosmicBackground = Color(0xFF07051A)
val CosmicSurface = Color(0xFF130F30)
val CosmicSurfaceOverlay = Color(0xFF1F1A4A)
val CosmicPrimary = Color(0xFF7C5FE6)
val CosmicPrimaryGlow = Color(0xFF9D7EFF)
val CosmicSecondary = Color(0xFF5B8DEE)
val CosmicAccentRose = Color(0xFFE05FA0)
val CosmicTextPrimary = Color(0xFFE8E0FF)
val CosmicTextSecondary = Color(0x99C8BEFF)
val CosmicBorder = Color(0x287C5FE6)

// --- Tiny Components ---

@Composable
fun Orb(
    size: Dp = 48.dp,
    anim: String = "none",
    glow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")
    
    val currentScale by if (anim == "pulse" || anim == "glow") {
        infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val currentFloatY by if (anim == "float") {
        infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val glowAlpha by if (glow) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    val glowModifier = if (glow) {
        Modifier.shadow(
            elevation = (size.value * 0.45f).dp,
            shape = CircleShape,
            clip = false,
            ambientColor = CosmicPrimary.copy(alpha = glowAlpha),
            spotColor = CosmicPrimary.copy(alpha = glowAlpha)
        )
    } else {
        Modifier
    }

    val movementModifier = if (anim == "float") {
        Modifier.offset(y = currentFloatY.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(movementModifier)
            .then(glowModifier)
            .size(size)
            .scale(currentScale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD4C0FF),
                        CosmicPrimary,
                        Color(0xFF1E0F5C)
                    )
                )
            )
    )
}

@Composable
fun Dots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_anim")
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = i * 200,
                        easing = EaseInOutQuad
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$i"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(CosmicPrimaryGlow.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun Bar(
    value: Float, // 0 to 100
    modifier: Modifier = Modifier,
    color: Color = CosmicPrimary,
    animate: Boolean = false
) {
    val progressValue = value.coerceIn(0f, 100f) / 100f
    
    val widthState = if (animate) {
        val animProgress = remember { Animatable(0f) }
        LaunchedEffect(progressValue) {
            animProgress.animateTo(
                targetValue = progressValue,
                animationSpec = tween(durationMillis = 1000, easing = EaseOutQuad)
            )
        }
        animProgress.value
    } else {
        progressValue
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x1E7C5FE6))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(widthState)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color, color.copy(alpha = 0.65f))
                    )
                )
        )
    }
}

@Composable
fun Pill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CosmicPrimary
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) color else CosmicBorder,
                shape = RoundedCornerShape(50)
            )
            .clip(RoundedCornerShape(50))
            .background(
                if (active) color.copy(alpha = 0.28f) else Color(0x0AFFFFFF)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) CosmicTextPrimary else CosmicTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderStrokeColor: Color = CosmicBorder,
    backgroundColor: Color = Color(0x0AFFFFFF),
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(18.dp), clip = false)
            .border(1.dp, borderStrokeColor, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .then(clickableModifier)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CosmicPrimaryGlow
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        fontFamily = FontFamily.SansSerif,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun BackBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Voltar"
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "← $text",
            color = CosmicTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
fun GoogleSignInDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, name: String) -> Unit
) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                val name = accountName.split("@").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Usuário"
                onConfirm(accountName, name)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Entrar com o Google",
                color = CosmicTextPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Seus diálogos socráticos e MindCores serão sincronizados na nuvem do Google.",
                    color = CosmicTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (!showCustomInput) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        backgroundColor = Color(0x1F7C5FE6),
                        borderStrokeColor = CosmicPrimary.copy(alpha = 0.4f),
                        onClick = {
                            try {
                                val intent = AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                                accountChooserLauncher.launch(intent)
                            } catch (e: Exception) {
                                showCustomInput = true
                                Toast.makeText(context, "Seletor nativo indisponível. Por favor, digite seu e-mail.", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("📱", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                            Column {
                                Text(
                                    text = "Escolher Conta do Celular",
                                    color = CosmicTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Selecionar uma conta Google salva em seu celular",
                                    color = CosmicTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicTextPrimary)
                    ) {
                        Text("Inserir e-mail manualmente", fontSize = 12.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("E-mail do Gmail") },
                        placeholder = { Text("seuemail@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CosmicTextPrimary,
                            unfocusedTextColor = CosmicTextPrimary,
                            focusedBorderColor = CosmicPrimary,
                            unfocusedBorderColor = CosmicTextSecondary.copy(alpha = 0.3f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showCustomInput = false }) {
                            Text("Voltar", color = CosmicTextSecondary)
                        }
                        Button(
                            onClick = {
                                if (emailInput.contains("@")) {
                                    val part = emailInput.split("@").firstOrNull() ?: "Usuário"
                                    onConfirm(emailInput, part.replaceFirstChar { it.uppercase() })
                                }
                            },
                            enabled = emailInput.contains("@"),
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary)
                        ) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!showCustomInput) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = CosmicAccentRose)
                }
            }
        },
        containerColor = Color(0xFF140F3B),
        textContentColor = CosmicTextPrimary,
        titleContentColor = CosmicTextPrimary
    )
}
