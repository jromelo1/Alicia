package com.gutigu.alicia.feature.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
//  Paleta de colores del chat — centralizada en ui/theme/Color.kt donde coincide;
//  el dorado y la burbuja de usuario son propios de esta pantalla.
// ─────────────────────────────────────────────

private val NavyBg       = AliciaBackground   // fondo de pantalla
private val NavyCard     = AliciaSurface      // superficie de barra/diálogo
private val SurfaceAlt   = AliciaSurfaceAlt
private val AliciaGold   = Color(0xFFB8860B)
private val AliciaBubble = AliciaSurfaceAlt
private val UserBubble   = Color(0xFFD6E8F7)
private val TextPrimary  = AliciaText
private val TextSecondary= AliciaTextSecondary
private val ErrorRed     = AliciaFormAlert

// ─────────────────────────────────────────────
//  Pantalla principal
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val listState    = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll al final cuando llega un mensaje nuevo
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        containerColor = NavyBg,
        topBar = { ChatTopBar(state.userProfile, viewModel) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Lista de mensajes ─────────────────────────
            LazyColumn(
                state        = listState,
                reverseLayout = true,
                modifier     = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Indicador de "escribiendo..."
                if (state.isLoading) {
                    item { TypingIndicator() }
                }

                // Mensajes (sólo los visibles)
                items(
                    items = state.messages.filter { !it.hidden }.reversed(),
                    key   = { it.id }
                ) { message ->
                    MessageBubble(message)
                }
            }

            // ── Snackbar de error ─────────────────────────
            state.error?.let { error ->
                ErrorBanner(error) { viewModel.dismissError() }
            }

            // ── Input ─────────────────────────────────────
            ChatInput(
                text        = state.inputText,
                isLoading   = state.isLoading,
                onTextChange = viewModel::onInputChange,
                onSend      = viewModel::sendMessage
            )
        }
    }

    // ── Bottom sheet de intereses ─────────────────────────
    if (state.showInterestsSheet) {
        InterestsSheet(
            currentProfile = state.userProfile,
            onDismiss      = viewModel::closeInterestsSheet,
            onSave         = viewModel::saveInterests
        )
    }
}

// ─────────────────────────────────────────────
//  Top Bar
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(profile: UserProfile, viewModel: ChatViewModel) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = NavyCard,
            titleContentColor = TextPrimary,
            actionIconContentColor = TextSecondary
        ),
        title = {
            Column {
                Text(
                    text       = "Alicia",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                    color      = AliciaGold
                )
                Text(
                    text     = "IA Vecinal · Círculo de Confianza",
                    fontSize = 12.sp,
                    color    = TextSecondary
                )
            }
        },
        actions = {
            // Editar intereses
            IconButton(onClick = viewModel::openInterestsSheet) {
                Icon(Icons.Default.Person, contentDescription = "Mis intereses")
            }
            // Reiniciar conversación
            IconButton(onClick = viewModel::clearChat) {
                Icon(Icons.Default.Refresh, contentDescription = "Nueva conversación")
            }
        }
    )
}

// ─────────────────────────────────────────────
//  Burbuja de mensaje
// ─────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Avatar de Alicia
            Box(
                modifier          = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AliciaGold),
                contentAlignment  = Alignment.Center
            ) {
                Text("A", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier           = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser) {
                Text(
                    "Alicia",
                    fontSize   = 11.sp,
                    color      = AliciaGold,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart    = if (isUser) 18.dp else 4.dp,
                    topEnd      = if (isUser) 4.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd   = 18.dp
                ),
                color = if (isUser) UserBubble else AliciaBubble,
                tonalElevation = 2.dp
            ) {
                Text(
                    text     = message.content,
                    color    = TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        if (isUser) Spacer(Modifier.width(8.dp))
    }
}

// ─────────────────────────────────────────────
//  Indicador "escribiendo..."
// ─────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier           = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment  = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AliciaGold),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = AliciaBubble
        ) {
            Row(
                modifier           = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment  = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue   = 0f,
                        targetValue    = -6f,
                        animationSpec  = infiniteRepeatable(
                            animation  = tween(400, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 150)
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(AliciaGold.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Campo de entrada
// ─────────────────────────────────────────────

@Composable
private fun ChatInput(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(color = NavyCard, tonalElevation = 4.dp) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = text,
                onValueChange = onTextChange,
                placeholder   = {
                    Text(
                        "Escribe un mensaje…",
                        color    = TextSecondary,
                        fontSize = 16.sp
                    )
                },
                modifier      = Modifier.weight(1f),
                textStyle     = LocalTextStyle.current.copy(
                    color    = TextPrimary,
                    fontSize = 16.sp
                ),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AliciaGold,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                    cursorColor          = AliciaGold
                ),
                shape         = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines      = 4,
                enabled       = !isLoading
            )

            Spacer(Modifier.width(8.dp))

            FloatingActionButton(
                onClick            = onSend,
                containerColor     = if (text.isBlank() || isLoading) TextSecondary else AliciaGold,
                contentColor       = NavyBg,
                modifier           = Modifier.size(52.dp),
                elevation          = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Banner de error
// ─────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(color = ErrorRed.copy(alpha = 0.15f)) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = message,
                color    = ErrorRed,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Bottom Sheet: Mis Intereses
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InterestsSheet(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (interests: List<String>, name: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name      by remember { mutableStateOf(currentProfile.name) }
    var selected  by remember { mutableStateOf(currentProfile.interests.toSet()) }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = NavyCard,
        dragHandle        = {
            Box(
                modifier          = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Mi perfil",
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            // Campo nombre
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Tu nombre", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                textStyle     = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 16.sp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AliciaGold,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                    cursorColor          = AliciaGold
                ),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "¿Qué te gusta? (elige varios)",
                color    = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Chips de intereses
            FlowRow(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement  = Arrangement.spacedBy(8.dp)
            ) {
                AVAILABLE_INTERESTS.forEach { option ->
                    val isSelected = option.keyword in selected
                    FilterChip(
                        selected  = isSelected,
                        onClick   = {
                            selected = if (isSelected)
                                selected - option.keyword
                            else
                                selected + option.keyword
                        },
                        label     = {
                            Text(option.label, fontSize = 14.sp)
                        },
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AliciaGold,
                            selectedLabelColor     = NavyBg,
                            containerColor         = SurfaceAlt,
                            labelColor             = TextPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = { onSave(selected.toList(), name.ifBlank { "Vecino" }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AliciaGold),
                shape    = RoundedCornerShape(26.dp)
            ) {
                Text(
                    "Guardar y reiniciar chat",
                    color      = NavyBg,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp
                )
            }
        }
    }
}
