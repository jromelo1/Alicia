package com.gutigu.alicia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.PrivacyConsentRepository
import com.gutigu.alicia.feature.auth.AuthRepository
import com.gutigu.alicia.feature.auth.LoginScreen
import com.gutigu.alicia.feature.checkin.CheckInScreen
import com.gutigu.alicia.feature.checkin.FamiliarCheckInScreen
import com.gutigu.alicia.feature.circle.CircleScreen
import com.gutigu.alicia.feature.circle.JoinCircleScreen
import com.gutigu.alicia.feature.circle.ShareCircleCodeScreen
import com.gutigu.alicia.feature.dailycall.DailyCallScreen
import com.gutigu.alicia.feature.familiar.FamiliarStatusScreen
import com.gutigu.alicia.feature.medications.MedicationScreen
import com.gutigu.alicia.feature.memories.MemoriesScreen
import com.gutigu.alicia.feature.notes.NotesScreen
import com.gutigu.alicia.feature.profile.OnboardingScreen
import com.gutigu.alicia.feature.profile.PrivacyConsentScreen
import com.gutigu.alicia.feature.profile.ProfileRepository
import com.gutigu.alicia.feature.profile.UserProfile
import com.gutigu.alicia.feature.safezones.SafeZonesScreen
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentBlue
import com.gutigu.alicia.ui.theme.AliciaAlert
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaCircle
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private data class Tab(val label: String, val icon: ImageVector, val color: Color = AliciaAccent)

// Paleta centralizada en ui/theme/Color.kt — estos nombres locales se conservan
// solo por legibilidad dentro de este archivo.
private val AppBg       = AliciaBackground
private val AppSurface  = AliciaSurface
private val AccentGreen = AliciaAccent
private val TextSecondary = AliciaTextSecondary

// Colores por ícono — para que el Adulto Mayor identifique cada pestaña a
// simple vista por su color, no solo por el texto (a veces cortado en
// pantallas angostas).
private val TabColorSos       = AliciaAlert       // rojo — reservado para emergencias reales
private val TabColorBienestar = Color(0xFFEC407A) // rosa — bienestar/ánimo
private val TabColorLlamada   = AliciaAccentBlue  // azul — llamada diaria
private val TabColorRecuerdos = AliciaCircle      // morado suave — coincide con Recuerdos
private val TabColorMedicina  = Color(0xFFE67E22) // naranja — medicamentos
private val TabColorNotas     = Color(0xFF9C27B0) // magenta — coincide con el ícono de Notas
private val TabColorCirculo   = AliciaAccent      // verde — círculo de confianza

// Tabs para el perfil Adulto Mayor
private val TABS_ADULTO = listOf(
    Tab("SOS",       Icons.Default.Warning, TabColorSos),
    Tab("Bienestar", Icons.Default.Favorite, TabColorBienestar),
    Tab("Llamada",   Icons.Default.Call, TabColorLlamada),
    Tab("Recuerdos", Icons.Default.PhotoLibrary, TabColorRecuerdos),
    Tab("Medicina",  Icons.Default.Notifications, TabColorMedicina),
    Tab("Notas",     Icons.Default.Edit, TabColorNotas),
    Tab("Círculo",   Icons.Default.AccountCircle, TabColorCirculo)
)

// Tabs para el perfil Familiar
private val TABS_FAMILIAR = listOf(
    Tab("Estado",    Icons.Default.Favorite),
    Tab("Bienestar", Icons.Default.Notifications),
    Tab("Llamada",   Icons.Default.Call),
    Tab("Zonas",     Icons.Default.LocationOn),
    Tab("Recuerdos", Icons.Default.PhotoLibrary),
    Tab("Círculo",   Icons.Default.AccountCircle)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var circleSessionRepository: CircleSessionRepository
    @Inject lateinit var privacyConsentRepository: PrivacyConsentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AliciaTheme {
                AppRoot(profileRepository, authRepository, circleSessionRepository, privacyConsentRepository)
            }
        }
    }
}

/**
 * Flujo de inicio:
 *   1. Elegir perfil (Adulto Mayor | Familiar) — sin red, es lo primero siempre.
 *   2. Adulto Mayor: sesión anónima silenciosa → crear círculo → mostrar código.
 *      Familiar: LoginScreen (correo/contraseña) → unirse a un círculo con el código.
 *   3. Con perfil + círculo listos → MainNavigation.
 */
@Composable
private fun AppRoot(
    profileRepository: ProfileRepository,
    authRepository: AuthRepository,
    circleSessionRepository: CircleSessionRepository,
    privacyConsentRepository: PrivacyConsentRepository
) {
    val scope = rememberCoroutineScope()

    // null = aún cargando desde DataStore (< 1 frame)
    var profileOpt by remember { mutableStateOf<Optional<UserProfile>>(Optional.Loading) }
    var userName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        profileOpt = Optional.from(profileRepository.getProfile())
        userName = profileRepository.getUserName()
    }

    // Salida de emergencia del onboarding: borra el perfil y el círculo guardados
    // localmente y regresa a la selección de perfil. Así, elegir "Adulto Mayor" o
    // "Familiar" por error —o simplemente arrepentirse a mitad de la configuración—
    // deja de ser una decisión sin vuelta atrás.
    val onChangeProfile: () -> Unit = {
        scope.launch {
            profileRepository.clearProfile()
            circleSessionRepository.clearCircleId()
            userName = ""
            profileOpt = Optional.None
        }
    }

    when (val opt = profileOpt) {
        is Optional.Loading -> LoadingScreen()
        is Optional.None -> {
            OnboardingScreen { selectedProfile, selectedName ->
                scope.launch {
                    profileRepository.saveProfile(selectedProfile, selectedName)
                    userName = selectedName
                    profileOpt = Optional.Some(selectedProfile)
                }
            }
        }
        is Optional.Some -> {
            PrivacyGate(
                profile = opt.value,
                userName = userName,
                authRepository = authRepository,
                circleSessionRepository = circleSessionRepository,
                privacyConsentRepository = privacyConsentRepository,
                onChangeProfile = onChangeProfile
            )
        }
    }
}

/** Pide aceptar el aviso de privacidad una sola vez antes de continuar con el círculo. */
@Composable
private fun PrivacyGate(
    profile: UserProfile,
    userName: String,
    authRepository: AuthRepository,
    circleSessionRepository: CircleSessionRepository,
    privacyConsentRepository: PrivacyConsentRepository,
    onChangeProfile: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hasAccepted by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        hasAccepted = privacyConsentRepository.hasAcceptedCurrent()
    }

    when (hasAccepted) {
        null -> SetupScaffold(onChangeProfile) { LoadingScreen() }
        false -> SetupScaffold(onChangeProfile) {
            PrivacyConsentScreen(
                onAccept = {
                    scope.launch {
                        privacyConsentRepository.acceptCurrent()
                        hasAccepted = true
                    }
                }
            )
        }
        true -> CircleGate(
            profile = profile,
            userName = userName,
            authRepository = authRepository,
            circleSessionRepository = circleSessionRepository,
            onChangeProfile = onChangeProfile
        )
    }
}

/**
 * Resuelve, para el perfil ya elegido, lo que falta antes de entrar a la app:
 *   Adulto Mayor → sesión anónima + círculo propio (con código para compartir)
 *   Familiar     → login con cuenta real + unirse a un círculo con código
 */
@Composable
private fun CircleGate(
    profile: UserProfile,
    userName: String,
    authRepository: AuthRepository,
    circleSessionRepository: CircleSessionRepository,
    onChangeProfile: () -> Unit
) {
    var isLoggedIn by rememberSaveable { mutableStateOf(authRepository.isLoggedIn) }
    // null = aún cargando la caché local; false = sin círculo todavía; true = listo
    var isCircleLinked by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isCircleLinked = circleSessionRepository.getCircleId() != null
    }

    when {
        profile == UserProfile.FAMILIAR && !isLoggedIn -> {
            SetupScaffold(onChangeProfile) {
                LoginScreen(onAuthenticated = { isLoggedIn = true })
            }
        }
        isCircleLinked == null -> {
            SetupScaffold(onChangeProfile) { LoadingScreen() }
        }
        isCircleLinked == false && profile == UserProfile.ADULTO_MAYOR -> {
            SetupScaffold(onChangeProfile) {
                ShareCircleCodeScreen(
                    userName = userName,
                    onContinue = { isCircleLinked = true }
                )
            }
        }
        isCircleLinked == false && profile == UserProfile.FAMILIAR -> {
            SetupScaffold(onChangeProfile) {
                JoinCircleScreen(
                    memberName = userName,
                    onJoined = { isCircleLinked = true }
                )
            }
        }
        else -> MainNavigation(profile = profile)
    }
}

/**
 * Envuelve cualquier pantalla del tramo de configuración (privacidad, login, código de
 * círculo) con una salida siempre visible. Nunca se usa dentro de [MainNavigation]: una
 * vez que la app ya está en uso normal, este atajo desaparece.
 *
 * El botón/gesto Atrás del sistema hace lo mismo que "Cambiar de perfil" — antes no
 * estaba interceptado en ninguna de estas pantallas (Login, unirse/recuperar círculo,
 * aviso de privacidad), así que Atrás cerraba la app directamente sin explicación,
 * porque este tramo de la app no tiene un back stack real (ver hallazgo en §3.6b/§4
 * del spec). "Cambiar de perfil" ya es, visualmente, una flecha de "volver" — Atrás
 * ahora hace exactamente eso en vez de salir de la app.
 */
@Composable
private fun SetupScaffold(
    onChangeProfile: () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onChangeProfile)

    Column(modifier = Modifier.fillMaxSize().background(AppBg)) {
        TextButton(
            onClick = onChangeProfile,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cambiar de perfil", color = TextSecondary, fontSize = 15.sp)
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

/** Reemplaza las pantallas en blanco del arranque: nunca deja al usuario sin saber si algo está pasando. */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(AppBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = AccentGreen)
            Text("Un momento…", color = TextSecondary, fontSize = 16.sp)
        }
    }
}

/** Contenedor mínimo para distinguir "cargando" de "null guardado". */
private sealed class Optional<out T> {
    object Loading : Optional<Nothing>()
    object None    : Optional<Nothing>()
    data class Some<T>(val value: T) : Optional<T>()

    companion object {
        fun <T> from(value: T?): Optional<T> = if (value != null) Some(value) else None
    }
}

@Composable
private fun MainNavigation(profile: UserProfile) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = if (profile == UserProfile.ADULTO_MAYOR) TABS_ADULTO else TABS_FAMILIAR

    // Si cambia el perfil, reset a primer tab
    if (selectedTab >= tabs.size) selectedTab = 0

    Scaffold(
        containerColor = AppBg,
        bottomBar = {
            NavigationBar(containerColor = AppSurface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = tab.color,
                            selectedTextColor   = tab.color,
                            indicatorColor      = tab.color.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (profile == UserProfile.ADULTO_MAYOR) {
                AdultoMayorContent(selectedTab)
            } else {
                FamiliarContent(selectedTab)
            }
        }
    }
}

@Composable
private fun AdultoMayorContent(tab: Int) {
    when (tab) {
        0 -> PanicScreen()
        1 -> CheckInScreen()
        2 -> DailyCallScreen()
        3 -> MemoriesScreen()
        4 -> MedicationScreen()
        5 -> NotesScreen()
        6 -> CircleScreen()
    }
}

@Composable
private fun FamiliarContent(tab: Int) {
    when (tab) {
        0 -> FamiliarStatusScreen()
        1 -> FamiliarCheckInScreen()
        2 -> DailyCallScreen()
        3 -> SafeZonesScreen()
        4 -> MemoriesScreen()
        5 -> CircleScreen()
    }
}

