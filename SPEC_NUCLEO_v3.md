# Círculo de Confianza — Spec v3 (Núcleo + Funciones completas)

> Cubre: Check-in de Bienestar, SOS, Círculo de Confianza (emparejamiento), Dashboard Familiar, Notas, Alicia (chat IA), Zonas Seguras, Medicamentos, Citas médicas, Memorias.
> Con esta versión el spec queda **completo respecto al código actual**: ya no hay funciones "fuera de alcance" como en v2. La feature de "Juegos" mencionada en versiones anteriores al v2 fue confirmada como **eliminada** — no existe rastro de ella en el código (ver §0.3).

---

## 0 · Qué cambió respecto al spec v2, y por qué

### 0.1 Cambio de dueño en el horario de Check-in

El spec v2 (§3.2) asumía que el **Adulto Mayor** configuraba su propia alarma diaria (interruptor, hora, tiempo de espera). Esto ya no es así: el código actual mueve esa configuración a una pantalla nueva del **Familiar** (`FamiliarCheckInScreen`, pestaña "Bienestar"), que escribe el horario a Firestore. El dispositivo del Adulto Mayor solo **escucha** ese documento y reprograma su alarma local — nunca lo edita. La razón implícita en el código: un adulto mayor que vive solo es precisamente quien menos beneficio tiene en tener que configurar temporizadores y tolerancias; quien sí necesita ese control fino es la familia que va a recibir (o no) el aviso. Ver §3.2.

### 0.2 Historial persistente de Check-in (nuevo, no existía en v2)

Antes había solo el estado "de hoy" (DataStore). Ahora existe una segunda capa, `checkin_history` en Room (60 días, reconciliado con Firestore), que alimenta una sección nueva "Historial reciente" en el Dashboard Familiar. Ver §6.2.

### 0.3 Confirmación: la feature de "Juegos" no existe

Búsqueda exhaustiva en el código (Memoria de Pares, Secuencia de Colores/Simon, cualquier `Game*`): no hay ningún archivo. Se elimina toda referencia a Juegos de este documento. Si en algún momento se reconstruye, será una función nueva, no una migración.

### 0.4 Corrección de copy y de una función fantasma en el Dashboard Familiar

El botón "Mensaje de voz" que el spec v2 (§6.2) todavía listaba junto a "Llamar a [nombre]" fue **retirado del código**, con un comentario explícito del equipo: prometía grabar y enviar un audio, función que nunca se implementó. Ya no debe aparecer en ningún diseño nuevo. También cambia el copy del encabezado: de *"Hola, estás cuidando a [nombre]"* a *"Hola, estás monitoreando a [nombre]"*.

### 0.5 Funciones nuevas documentadas por primera vez

Notas, Alicia (chat con IA), Zonas Seguras, Medicamentos, Citas médicas y Memorias ya estaban construidas pero nunca se habían documentado con el rigor de un spec (v1 las mencionaba de pasada, v2 las declaraba explícitamente fuera de alcance). Este documento las cubre todas con el mismo nivel de detalle que el núcleo.

### 0.6 Tercer tono de rojo, formalizado como token

El código usa consistentemente un rojo distinto (`#C0392B`) para acciones destructivas (eliminar miembro, eliminar nota, eliminar medicamento/cita/zona/recuerdo) que **no** es el rojo de emergencia real (`#E8342A`). El spec v2 solo hablaba de `alert` para emergencia y `warning`/texto secundario para errores de formulario, sin un tercer token. Se formaliza aquí como `destructive` (ver §1.1).

---

## 1 · Sistema visual

### 1.1 Paleta

| Token | Uso | Valor |
|---|---|---|
| `bg` | Fondo de pantalla | `#FAF8FB` |
| `surface` | Tarjetas, hojas inferiores, campos | `#FFFFFF` |
| `surface-alt` | Fondo de tarjeta secundaria / fila de historial | `#F1EEF6` |
| `text` | Texto principal | `#2F2E41` |
| `text-secondary` | Texto de apoyo, subtítulos | `#6B6880` |
| `accent` | Verde — vida, seguridad, confirmación (Bienestar, medicamentos, Adulto Mayor en Onboarding) | `#2ECC71` |
| `accent-soft` | Fondo tenue del accent (chips, halos) | `#E3F9EC` |
| `accent-blue` | Azul — Citas médicas, rol "Familiar" en Onboarding, chat de Alicia usa su propio dorado (ver 1.1.1) | `#2980B9` |
| `circle-purple` | Morado — exclusivo de Círculo y de Memorias (dos funciones de vínculo, no de emergencia) | `#8E7FA6` |
| `alert` | Rojo — **reservado exclusivamente para emergencia real** | `#E8342A` |
| `alert-soft` | Fondo tenue del alert (banners de error no críticos) | `#FCE8E6` |
| `destructive` | Rojo de acciones destructivas irreversibles (eliminar miembro/nota/medicamento/cita/zona/recuerdo) — **distinto del `alert`**, nunca se usa para emergencia | `#C0392B` |
| `warning` | Ámbar — advertencias no urgentes (batería, permisos, falta de guardianes) | `#F39C12` |
| `line` | Bordes, separadores | `#E4E0EE` |
| `inactive` | Iconos/texto deshabilitado, tab sin seleccionar | `#B8B4C6` |
| `shadow` | Sombra de tarjeta | `#8E7FA6` al 12% |

Regla dura heredada de v1/v2, sin cambios: **el rojo `alert` solo aparece en un evento de emergencia real** (SOS activo, alerta de check-in perdido). Errores de formulario usan `warning` o texto secundario; acciones de "eliminar" usan `destructive`; ninguno de los dos sustituye a `alert`.

#### 1.1.1 Notas sobre tokens de función (no promovidos a la paleta global)

Algunas pantallas usan color como taxonomía interna de la propia función, no como estado semántico global — se documentan en su sección, no aquí:
- **Notas**: cada categoría automática tiene su color propio (Salud rojo oscuro `#D32F2F`, Tarea verde `#2E7D32`, Recordatorio ámbar `#B7891A`, Del familiar morado `#7A1F8A`, Compras azul `#1E6FA8`, Familia naranja `#C0622E`, General = `text-secondary`).
- **Alicia (chat)**: dorado `#B8860B` como acento de marca del asistente — no se reutiliza en ninguna otra función.
- **Medicina "con comida"**: naranja `#B9770E` — matiz distinto de `warning`, exclusivo de esa etiqueta.

### 1.2 Tipografía

Sin cambios respecto a v1/v2:

| Rol | Fuente | Tamaño mínimo |
|---|---|---|
| Títulos grandes (`displayLarge`, `displayMedium`) | Lora, bold | 26–32sp |
| Encabezados de sección (`headlineLarge`) | Lora, bold | 24sp |
| Botones y etiquetas (`titleLarge`, `labelLarge`) | Plus Jakarta Sans, bold/semibold | 16–20sp |
| Cuerpo (`bodyLarge`, `bodyMedium`) | Plus Jakarta Sans, regular | 16sp mínimo — nunca menos |

### 1.3 Iconografía — patrón tarjeta-ícono

Sin cambios de criterio respecto a v2: tarjeta con esquinas redondeadas (20dp), fondo de color sólido semántico, ícono de línea blanca centrado, etiqueta corta en Plus Jakarta Sans bold. Tamaño mínimo interactivo: 96×96dp.

Asignación de color por función, actualizada con las funciones nuevas:
- SOS → `alert`
- Bienestar / Check-in → `accent`
- Círculo → `circle-purple`
- Memorias → `circle-purple` (comparte función de vínculo con Círculo)
- Estado (dashboard familiar) → `accent`
- Medicamentos → `accent`
- Citas médicas → `accent-blue`
- Notas → sin color único de acceso; usa el color de categoría dentro de la propia pantalla
- Alicia (chat) → dorado de marca (no forma parte de la paleta semántica)
- Zonas → hereda `accent` (activa) / `inactive` (desactivada)

### 1.4 Ilustración

Sin cambios: línea simple, 2 colores máximo, nunca en pantallas de error o emergencia.

### 1.5 Tono de copy

Sin cambios: cálido, directo, nunca "configurar/sincronizar/usuario/parámetros". Frases cortas. Se mantiene también en las funciones nuevas — p. ej. Notas dice "Habla y yo recuerdo", no "Grabar nota de voz".

### 1.6 Antirreferentes — lo que NO se hace, nunca

Sin cambios respecto a v2 (fondo oscuro, texto denso, elementos chicos, todo encajonado, chip decorativo sin función) — aplican ahora también a las seis funciones nuevas documentadas en este spec.

---

## 2 · Leyenda de dueños

| Ícono | Dueño | Significado |
|---|---|---|
| 👤 | **Persona** | El adulto mayor o el familiar toma una acción explícita. |
| 🔊 | **Alicia-voz** | El motor de texto-a-voz del sistema habla (saludo de alarma, confirmaciones). No confundir con "Alicia" el asistente conversacional de IA (§8), que es una función distinta aunque comparta nombre de personaje. |
| 🤖 | **Alicia-IA** | El asistente conversacional (Claude vía API) genera una respuesta de chat. Solo aplica a la función de Chat (§8). |
| ⚙️ | **Sistema** | Ocurre automáticamente (temporizador, sincronización, notificación push, reprogramación tras reinicio). |

---

## 3 · Flujo: Check-in de Bienestar

### 3.1 Resumen del flujo

```
👤 Familiar configura horario + tiempo de espera (pestaña Bienestar)
        ↓
⚙️ Se sincroniza a Firestore
        ↓
⚙️ El dispositivo del Adulto Mayor escucha el cambio y reprograma su alarma local
        ↓
⚙️ Alarma diaria programada suena
        ↓
🔊 Alicia-voz saluda según la hora (fase Despertar, 0–5s)
        ↓
👤 ¿Tocó "¡Estoy bien!"? ──── Sí ──→ ⚙️ Registra + notifica al círculo → 🔊 Alicia-voz despide con calidez
        │
        No (sigue esperando, fase Principal 5–20s → fase Aviso final 20–30s)
        ↓
⚙️ Countdown de 30s agotado → ⚙️ Notifica al círculo como "no respondió" + SMS
```

### 3.2 Pantalla: Configuración del Check-in — ahora dividida en dos, por rol

#### 3.2a `FamiliarCheckInScreen` (pestaña "Bienestar" del Familiar) — **nueva, reemplaza al dueño anterior de esta configuración**

| | |
|---|---|
| **Dueño** | 👤 Familiar |
| **Qué ve** | Interruptor "Activar alarma diaria", selector de hora (pasos de 30 min), chips de tiempo de espera (1h/2h/4h). Se guarda directo a Firestore al cambiar cualquier valor (`saveSchedule`). |
| **Datos incompletos — sin nadie en el círculo del familiar** | Banner ámbar (`warning`): *"Todavía no hay nadie en el círculo de tu familiar. Únete o agrega un guardián antes de activar la alarma, o no le avisará a nadie."* Interruptor deshabilitado hasta resolverlo — mismo guardrail que v2, pero ahora aplicado al lado del Familiar. |
| **Permiso de notificaciones denegado (nuevo)** | Banner ámbar arriba del anterior, sin bloquear nada: *"Sin notificaciones activadas no vas a ver las alertas de tu familiar (pánico o check-in perdido)."* + botón "Activar en Ajustes" → abre la configuración de notificaciones de la app. Se pide el permiso automáticamente al entrar a esta pantalla si falta (ver §7.2). |

#### 3.2b `CheckInScreen` — configuración del Adulto Mayor, ahora de solo lectura para el horario

| | |
|---|---|
| **Dueño** | 👤 Adulto Mayor (solo para lo que le queda: teléfono propio y probar alarma) |
| **Qué ve** | Sección "Chequeo diario" en modo lectura: *"Tu familia configura este horario desde su app."* Si ya está activo: *"Activo — recibirás el saludo a las HH:MM"*. Si el Familiar aún no lo activó: *"Tu familia aún no ha activado el chequeo diario."* + *"Puede activarlo desde su propia pestaña Bienestar."* Debajo, el Adulto Mayor conserva control sobre: campo de teléfono propio y botón "Probar alarma ahora". |
| **Datos incompletos — sin teléfono propio** | Igual que v2: no bloquea nada del check-in en sí, pero sin teléfono el Dashboard Familiar no puede ofrecer "Llamar a [nombre]" (§6.2). |
| **Permiso de notificaciones denegado (nuevo)** | Banner ámbar arriba de "Estado de hoy", sin bloquear nada: *"Sin notificaciones activadas no vas a escuchar tu alarma de bienestar ni la de tus medicamentos."* + botón "Activar en Ajustes". Se pide el permiso automáticamente al entrar a esta pantalla si falta — ver el hallazgo completo en §7.2 (sin esto, la alarma diaria nunca sonaba en un teléfono nuevo). |
| **Guardado** | Local-first (DataStore) para lo que el Adulto Mayor sí controla; sincronización a Firestore en segundo plano, reintento silencioso si falla — sin pedirle al usuario que "reintente" nada. |

### 3.3–3.6 Fases de la alarma y respuesta

Sin cambios respecto a v2 — se mantienen íntegras las fases Despertar (0–5s, TTS *"Hola, hace un rato que no nos saludamos. ¿Estás por ahí?"*), Principal (5–20s, botón 200dp con latido), Aviso final (20–30s, TTS *"Voy a avisar a tu familia en 10 segundos..."*, countdown 110sp) y Respondido con éxito (pantalla verde, TTS *"¡Perfecto! Seguimos conectados. Que tengas un lindo día."*).

### 3.6a Botón/gesto Atrás durante Login / unirse o recuperar círculo / aviso de privacidad (nuevo)

Mismo hallazgo que §3.6b, pero en el tramo de configuración inicial (`SetupScaffold`, que envuelve `PrivacyConsentScreen`, `LoginScreen`, `JoinCircleScreen` y `ShareCircleCodeScreen`): Atrás no estaba interceptado en ninguna, así que cerraba la app directamente en mitad de un login o de escribir el código del círculo, sin explicación. La decisión: Atrás ahora hace exactamente lo mismo que el enlace "Cambiar de perfil" que ya vive arriba de cada una de estas pantallas (que además ya se mostraba con una flecha de "volver") — vuelve a la selección de perfil, en vez de salir de la app. Se implementó una sola vez en `SetupScaffold`, no en cada pantalla, así que cubre las cuatro por igual.

No se tocó la lógica interna de "No acepto" en el Aviso de Privacidad (que sigue mostrando su propio diálogo de confirmación antes de cerrar la app del todo, ver §5.3) — son dos acciones distintas y legítimas: Atrás es "quiero elegir otro perfil", no "no acepto la política de privacidad".

### 3.6b Botón/gesto Atrás durante la alarma activa (nuevo)

Hallazgo de una auditoría UX: en todo el proyecto, solo `MedicationAlarmActivity` manejaba explícitamente el botón Atrás — el resto de la app usa el back nativo de Android sin interceptarlo, y como `MainActivity` no tiene un back stack real (la navegación es estado de Compose, no `NavController`), Atrás **cierra la app** en cualquier pantalla que no lo intercepte. Durante la cuenta regresiva de 30s del check-in eso era grave: la cuenta seguía corriendo en el `ViewModel` aunque la vista desapareciera, y la familia terminaba recibiendo un aviso de "no respondió" con el adulto mayor sentado ahí con el teléfono en la mano. Ahora `CheckInScreen` absorbe el botón/gesto Atrás mientras la alarma está activa (`isPending`) — sin acción de "cancelar", igual que la alarma de medicamento: la única salida es responder.

### 3.7 Errores del flujo

Sin cambios respecto a v2 (registro local-first inmediato, SMS/Firestore pendientes reintentables; permiso de notificaciones denegado sigue como decisión abierta).

### 3.8 Nota técnica: reprogramación tras reinicio (`BootReceiver`)

No es una pantalla ni afecta copy — es un guardrail de confiabilidad. Al reiniciarse el dispositivo, Android cancela los `WorkManager` jobs pendientes; `BootReceiver` los reprograma automáticamente para las tres funciones que dependen de alarmas temporizadas: Check-in diario, Medicamentos (recurrente 24h) y Citas médicas (una sola vez, solo las futuras). Sin esto, un reinicio del teléfono dejaría al Adulto Mayor sin alarmas hasta que alguien abra la app manualmente.

---

## 4 · Flujo: SOS / Botón de Pánico

Sin cambios de fondo respecto a v2. Se mantiene íntegro: Idle (botón 180dp con anillos de pulso) → Countdown 5s cancelable → Enviando (~1.5s) → Alerta activa (dos botones "Falsa alarma" / "Estoy bien") → Resuelta (vuelve a Idle a los 3s) → Error al enviar (siempre ofrece *"⚠️ Llama directamente al 123 o a un familiar"*). Ver v2 §4 para el detalle completo de cada pantalla.

**Nuevo — botón/gesto Atrás (mismo hallazgo que §3.6b)**: antes, Atrás cerraba la app sin más desde cualquier estado (Countdown, Enviando, Activa), dejando una secuencia de pánico a medias sin que el usuario supiera qué pasó. Ahora: en Countdown, Atrás cancela (mismo efecto que el botón CANCELAR — sí tiene una acción natural de "deshacer"); en Enviando y en Alerta activa, Atrás se absorbe sin hacer nada, porque ahí no existe un "cancelar" — la alerta ya se envió o se está enviando, y debe resolverse con los botones explícitos "Estoy bien" / "Falsa alarma". Idle, Resuelta y Error se dejaron sin interceptar — no hay nada en curso que proteger.

---

## 5 · Flujo: Círculo de Confianza (emparejamiento entre dispositivos)

### 5.1 Resumen del flujo — con el detalle de autenticación que v2 no desarrollaba

```
👤 Elige perfil (Adulto Mayor | Familiar) + nombre
        ↓
👤 Acepta el aviso de privacidad (una sola vez)
        ↓
   ┌─── Adulto Mayor ───┐         ┌─── Familiar ───┐
   │ ⚙️ Sesión anónima   │         │ 👤 Login o crear cuenta (correo/contraseña) │
   │ ⚙️ Crea círculo     │         │ 👤 Escribe el código + nombre del familiar   │
   │ 👤 Comparte código  │         │ 👤 Escribe su teléfono (con código de país)  │
   └─────────────────────┘         │ 👤 Elige rol (Guardián/Miembro)              │
                                    └───────────────────────────────────────────┘
        ↓                                        ↓
              ⚙️ Ambos dispositivos comparten el mismo círculo en Firestore
```

### 5.1.1 Pantalla: Login / Crear cuenta (`LoginScreen`) — solo Familiar, nueva en este documento

| | |
|---|---|
| **Dueño** | 👤 Familiar |
| **Qué ve** | Título "Círculo de Confianza" + subtítulo dinámico ("Bienvenido de vuelta" en modo login / "Crear cuenta" en modo registro). Campos Email y Contraseña (con mostrar/ocultar), y solo en modo registro: Confirmar contraseña. Botón principal cambia de texto según el modo ("Iniciar sesión" / "Crear cuenta"), muestra spinner mientras carga. Enlace inferior para alternar entre modos: *"¿Nuevo aquí? Crear cuenta"* / *"¿Ya tienes cuenta? Inicia sesión"*. |
| **Validación local (antes de llamar a Firebase)** | Campos vacíos → *"Completa todos los campos"*. Contraseñas distintas (registro) → *"Las contraseñas no coinciden"*. Contraseña &lt; 6 caracteres → *"La contraseña debe tener al menos 6 caracteres"*. |
| **Error — credenciales** | *"Email o contraseña incorrectos"* |
| **Error — email ya registrado** | *"Este email ya tiene una cuenta. Inicia sesión."* |
| **Error — email mal formado** | *"Email no válido"* |
| **Error — otro** | Mensaje crudo de Firebase, o *"Error desconocido"* |
| **Nota de arquitectura** | El Adulto Mayor **nunca ve esta pantalla** — usa sesión anónima de Firebase (`signInAnonymously`), instantánea, sin credenciales. Esa sesión en sí **no sobrevive una desinstalación o un cambio de teléfono** (Firebase crea un uid nuevo), pero desde que existe el flujo de recuperación (ver §5.4a) eso ya no significa perder el círculo — solo perder la sesión, que se reconecta con el mismo código. |

### 5.2 Pantalla: Elegir perfil (`OnboardingScreen`)

Coincide con v2, con un matiz de color no documentado antes: cada tarjeta usa un color de rol distinto — verde (`accent`) para "Soy Adulto Mayor", azul (`accent-blue`) para "Soy Familiar" — y cada una trae su propia descripción de una línea: Adulto Mayor *"Quiero que mi familia sepa que estoy bien cada día."*, Familiar *"Quiero estar al tanto de mi ser querido y saber que está bien."*

### 5.3 Pantalla: Aviso de privacidad

Coincide con v2 en estructura y en que cerrar sin aceptar cierra la app. Detalle adicional: al tocar "No acepto" aparece un diálogo intermedio de confirmación (*"¿Seguro que no aceptas?"* + explicación + botones "Cerrar la app" / "Seguir viendo") antes de cerrar de verdad — evita un cierre accidental por un toque. Incluye sección de contacto (contacto@gutigu.com), más las secciones agregadas para alinear la estructura con la Ley 1581/2012 (responsable, datos sensibles, plazo de conservación, queja ante la SIC, transferencia internacional — ver detalle y pendientes en §7.2). El texto sigue **sin la revisión final de un abogado** — pendiente.

### 5.4a Pantalla: Compartir código (solo Adulto Mayor) — con recuperación nueva de círculo existente

Coincide con v2 en el flujo de creación — sesión anónima + creación de círculo en segundo plano, código de 8 caracteres, botón compartir nativo, timeout de red a los 15s, mensajes de error idénticos (*"No hay respuesta del servidor..."*, *"No se pudo iniciar tu sesión: [detalle]"*, *"No se pudo crear tu círculo: [detalle]"*).

**Nuevo**: un enlace fijo abajo, *"¿Ya tenías un círculo con esta app? Recupéralo con tu código"*, resuelve el escenario de reinstalación o cambio de teléfono documentado en §5.1.1 (la sesión anónima no sobrevive, pero el círculo en Firestore vive bajo el código, no bajo el uid). Al tocarlo:

| | |
|---|---|
| **Qué ve** | "🔑 Recupera tu círculo" + campo de código (mismo formato que el de Familiar en §5.4b) + botón "Recuperar mi círculo" (habilitado con 6+ caracteres) + enlace "Prefiero crear un círculo nuevo" para volver. |
| **Qué hace** (`recoverCircleAsElder`, `CircleOnboardingViewModel`) | Crea una sesión anónima nueva (o reutiliza una si ya existía), verifica que el código corresponda a un círculo real, y reconecta ese uid nuevo al mismo `circleId` (`ownerUid` en el documento del círculo + `users/{uid}.circleId`). Si el Adulto Mayor escribió su nombre en el onboarding, se sincroniza al perfil del círculo igual que en la creación. |
| **Qué se recupera** | Todo lo que vive en Firestore bajo ese `circleId`: nombre, horario de check-in, historial reciente, miembros del círculo, notas del familiar. |
| **Qué NO se recupera** | Lo que solo vivía en el teléfono anterior (Room local): notas propias, medicamentos, citas médicas. Es una limitación real de arquitectura, no de esta pantalla — pendiente si algún día se decide sincronizar esas tablas a Firestore. |
| **Éxito** | *"🎉 ¡Recuperamos tu Círculo!"* + *"Tu familia sigue conectada contigo, igual que antes."* + botón "Continuar". |
| **Error — código inválido** | *"Ese código no corresponde a ningún círculo"*, mismo mensaje y tratamiento visual que en §5.4b. |

### 5.4b Pantalla: Unirse a un círculo (solo Familiar) — con dos campos nuevos no documentados en v2

| | |
|---|---|
| **Dueño** | 👤 Familiar |
| **Qué ve** | "🔗 Únete a un círculo" + campo de código (8 caracteres, mayúsculas automáticas) + **campo nuevo "¿Cómo se llama tu familiar?"** (obligatorio — el nombre del adulto mayor tal como lo conoce el Familiar) + selector de código de país + campo de teléfono propio + selector de rol (Guardián "Recibo todas las alertas: pánico, check-in y zonas" / Miembro "Solo recibo alertas de emergencia") + botón "Unirme al círculo". |
| **Estado inicial** | El botón se habilita en cuanto código + nombre del familiar + teléfono son válidos — nunca nace deshabilitado con spinner fantasma (mismo guardrail que v2). |
| **Datos incompletos** | Botón deshabilitado mientras falte el código, el nombre del familiar, o el teléfono tenga menos de **6 dígitos** (v2 decía 7 — corregido aquí al valor real del código). |
| **Error — código inválido** | *"Ese código no corresponde a ningún círculo"* en `alert-soft`/`alert`, sin limpiar el formulario. |
| **Error — timeout de red** | *"No hay respuesta del servidor. Revisa tu conexión a internet e intenta de nuevo."* |
| **Éxito** | Navega directo a la app principal, sin pantalla intermedia. |

### 5.5 Pantalla: Círculo (lista de miembros)

Coincide con v2 en estructura completa. Matiz de color no documentado antes: el botón de eliminar miembro y el badge "Emergencia" usan `destructive` (`#C0392B`), no `alert` (`#E8342A`) — refuerza la regla dura de que el rojo de emergencia real nunca se reutiliza decorativamente, formalizada ahora como token propio (§1.1).

---

## 6 · Flujo: Dashboard Familiar

### 6.1 Resumen del flujo

```
⚙️ El dashboard combina en tiempo real: check-ins (hoy + historial 60 días), alerta de pánico, perfil del círculo
        ↓
👤 ¿Hay una alerta de pánico O un check-in perdido activos?
        │
        Sí → Pantalla roja de emergencia (ver 6.3). El pánico siempre gana sobre un check-in perdido si ambos ocurren.
        │
        No → Dashboard normal: estado de hoy + línea de vida + historial reciente + mensaje a Alicia
```

### 6.2 Pantalla: Dashboard normal — con historial nuevo y corrección de un botón fantasma

| | |
|---|---|
| **Dueño** | 👤 (lectura) + ⚙️ (actualización en tiempo real) |
| **Qué ve** | *"Hola, estás monitoreando a [nombre]"* (copy corregido — antes decía "cuidando a") + Hero circular con estado ("Todo en orden" / "Esperando respuesta" / "No respondió hoy" en caso de alerta ya resuelta recientemente / "Sin datos aún") + **"Próxima cita médica"** (nuevo, solo si hay una futura — tarjeta azul de solo lectura con médico/especialidad, fecha/hora y lugar) + "Actividad de hoy" (línea de tiempo con tres tipos: check-in, medicamento, **cita médica** — cada uno con su color e ícono; las notas del Adulto Mayor nunca aparecen aquí, son privadas — ver §7.2) + **"Historial reciente"** + tarjeta "Mensaje para Alicia". Barra inferior fija: **solo** botón "Llamar a [nombre]" — el botón "Mensaje de voz" que existía en v2 fue eliminado (prometía una función nunca implementada). |
| **Historial reciente (nuevo)** | Lista de los últimos 7 días (sin contar hoy), cada fila con punto de color + etiqueta ("Bien" verde / "Alerta" rojo / "Pendiente" ámbar) + fecha relativa ("Hoy"/"Ayer"/"d de MMMM"). Se alimenta de una tabla local (Room, 60 días) reconciliada con Firestore: si Firestore ya tiene el dato de una fecha, ese gana; si no, se usa el respaldo local. |
| **Estado vacío — sin actividad hoy** | Sin cambios respecto a v2. |
| **Datos incompletos — sin teléfono del adulto mayor** | Sin cambios respecto a v2 — botón deshabilitado con texto "Sin teléfono", nunca oculto. |
| **Estado "esperando respuesta"** | Sin cambios respecto a v2 — hero ámbar, pulso animado. |
| **Corrección de un bug ya resuelto** | El nombre mostrado en el encabezado usa siempre el perfil sincronizado por Firestore; el nombre que el propio Familiar escribió al unirse solo se usa como respaldo si Firestore aún no tiene datos. Antes esto estaba invertido y llegó a mostrar por error el nombre del Familiar en vez del Adulto Mayor — ya corregido, se documenta aquí para que no se reintroduzca. |

### 6.3 Pantalla: Alerta activa (modo rojo)

Sin cambios respecto a v2 — pantalla roja completa, ícono según origen (🆘 pánico / ⚠️ check-in perdido), botón "Llamar a [nombre]" solo si hay teléfono, sección "Coordinar con el círculo" con botón por guardián. Se confirma explícitamente en el código que una alerta de pánico activa siempre tiene prioridad de visualización sobre un check-in perdido si coincidieran.

### 6.4 Pantalla: Mensaje para Alicia

Sin cambios respecto a v2 — diálogo con explicación, campo 3–5 líneas, campo opcional "Tu nombre", botón "📤 Enviar a Alicia", cierre sin confirmación adicional.

---

## 7 · Anexo del núcleo

### 7.1 Patrón de estados usado en todo el núcleo

Sin cambios respecto a v2 — Idle/Neutral, Cargando (máx. 15s antes de Error), Listo, Error (mensaje específico + acción de recuperación siempre visible). Este mismo patrón se extiende a las funciones nuevas documentadas en §§8–13, con la excepción de Notas, Medicamentos, Citas y Memorias, cuyas listas se alimentan directo de Room/Flow sin un estado de "cargando" inicial explícito (ver notas en cada sección).

### 7.2 Pendiente para la siguiente pasada

- ~~Definir el flujo de permiso de notificaciones denegado.~~ **Resuelto**: el hallazgo real era más grave que "sin flujo definido" — la app nunca pedía `POST_NOTIFICATIONS` en ningún lado (`targetSdk 36`, obligatorio desde Android 13), así que la alarma diaria de check-in, que depende por completo de una notificación con `setFullScreenIntent` (`CheckInWorker`, §3.3), nunca sonaba ni aparecía por defecto en un teléfono nuevo — sin error visible, sin registro, nada. Ahora `CheckInScreen` (Adulto Mayor) y `FamiliarCheckInScreen` (Familiar) piden el permiso apenas se detecta que falta, y muestran un banner ámbar persistente con botón "Activar en Ajustes" si sigue denegado — mismo patrón visual que los banners de guardianes (§3.2a) y de Zonas Seguras (§10.2). El banner se revisa de nuevo cada vez que la app vuelve a primer plano, por si el cambio se hizo desde Ajustes del sistema. Pendiente si se decide extender el mismo aviso a Medicamentos (§11): ahí el riesgo es menor porque `MedicationWorker` lanza la pantalla de alarma directo (`startActivity`), sin depender de la notificación — solo perdería la notificación de respaldo, no la alarma completa.
- **Aviso de privacidad — en progreso, no cerrado**: se amplió el texto (`PrivacyConsentScreen`, §5.3) con las secciones que la Ley 1581/2012 y el Decreto 1377/2013 exigen en estructura — responsable del tratamiento, aviso explícito de datos sensibles (salud/ubicación), plazo de conservación, derecho a queja ante la SIC, tiempos de respuesta legales, y transferencia internacional (Firebase/Google Cloud). Sigue bloqueante para producción: (1) la identidad real del responsable (razón social/NIT/domicilio) — no se puede completar sin esos datos reales, queda marcada en rojo en la app (`RESPONSABLE_PENDIENTE`) para que no pase inadvertida; (2) confirmar con un abogado si aplica registro en el RNBD de la SIC; (3) validar legalmente el mecanismo de transferencia internacional; (4) decisión de producto/legal pendiente sobre `AddMemberSheet` (§5, permite agregar un guardián desde la agenda de contactos, que nunca pasa por esta pantalla de consentimiento él mismo). Nada de esto sustituye la revisión de un abogado antes de tener usuarios reales.
- ~~El copy de "Tiempo agotado" en la alarma de medicamento (§11.2) afirma "Tu familiar ha sido notificado", pero el código no envía notificación real.~~ **Resuelto**: `MedicationAlarmViewModel.onTimeout` ahora alerta de verdad al círculo cuando expira el countdown — SMS a los guardianes + push FCM (`FcmSender.notifyMedicationMissed`), mismo patrón que el check-in perdido (§3, `CheckInViewModel.onCountdownExpired`). El copy y el TTS de la pantalla `ExpiredScreen`/`MedicationAlarmScreen` (§11.3) ya eran correctos, solo faltaba la acción detrás. Además, ahora `MedicationScreen` (§11.1) y `SafeZonesScreen` (§10.2) advierten proactivamente al Adulto Mayor si su círculo no tiene ningún guardián — mismo guardrail visual que ya existía en `FamiliarCheckInScreen` (§3.2a), extendido a las dos pantallas donde quien configura la función es el propio Adulto Mayor, no el Familiar. El SMS de salida de zona (§10.4) sigue sin guardianes a quienes escribir en ese caso, pero ahora el Adulto Mayor lo sabe de antemano en vez de descubrirlo tras una alerta que no llegó a nadie.
- Unificar los colores de `MedicationAlarmScreen` (hardcodeados localmente: `#FBDAD3`, `#D63A1F`, `#1B5E20`, `#0D1B2A`) contra los tokens centralizados de `Color.kt`, para que un futuro cambio de paleta no deje esa pantalla desincronizada.
- Zonas Seguras sigue sobre un repositorio mock en memoria (dos zonas de ejemplo, no persistente entre reinicios de proceso) — pendiente reemplazo por una versión respaldada en Firestore, igual que ya ocurrió con Check-in y Círculo.
- ~~La "Línea de Vida" del Dashboard Familiar mostraba medicamentos tomados y notas creadas leyendo `MedicationDao`/`NoteDao` directo de Room — invisible para el Familiar en el uso real de dos teléfonos.~~ **Resuelto para Medicamentos, resuelto de forma distinta para Notas**:
  - **Medicamentos**: mismo patrón que Citas médicas (Room local + espejo en Firestore). `MedicationRepository` ahora publica el medicamento completo en `circles/{circleId}/medications/{id}` al crear/editar/activar/desactivar/borrar, y también al marcarlo tomado — desde los dos caminos que existen para eso: la alarma en pantalla completa (`MedicationAlarmViewModel.confirmTaken`) y la acción rápida "✓ Tomado" de la notificación de respaldo (`MedicationResponseReceiver`, que construye `FirestoreRepository` a mano por no participar de Hilt, igual que `CheckInTimeoutWorker`). `FamiliarViewModel` ahora lee `observeMedications` de Firestore en vez de Room.
  - **Notas — NO se sincronizaron, a propósito**: investigar esto reveló que el código violaba la propia promesa de privacidad del producto (§8.2: *"Tu diario privado — no se comparte con tu familia"*). `FamiliarViewModel` leía **todas** las notas de Room, incluidas las de voz/texto privadas del Adulto Mayor, y mostraba hasta 60 caracteres de contenido en la Línea de Vida — invisible en dos teléfonos reales solo por el mismo bug de Room, no por diseño. Sincronizar eso a Firestore para "arreglarlo" habría sido convertir un bug inofensivo (por accidente) en una fuga de privacidad real. En cambio, se **retiró por completo** `ActivityType.NOTE`, la dependencia de `NoteDao`/`notesFlow` y toda referencia a notas de `FamiliarViewModel` y `FamiliarStatusScreen` — el diario privado ahora es privado también en el código, no solo en el copy. Las notas *del* Familiar hacia el Adulto Mayor (§8, "Mensaje para Alicia") no se tocaron — ese flujo ya era correcto y unidireccional por Firestore.

---

## 8 · Flujo: Notas (diario privado por voz)

### 8.1 Resumen del flujo

```
👤 Adulto Mayor toca el micrófono
        ↓
⚙️ Reconocimiento de voz (Android SpeechRecognizer) transcribe en vivo
        ↓
⚙️ Se categoriza automáticamente el texto por palabras clave
        ↓
👤 Confirma "¿Guardo esto?" ──── Descartar ──→ vuelve a Idle, nada se guarda
        │
        Guardar
        ↓
   ¿Categoría accionable (Salud/Tarea/Recordatorio)? ── Sí ──→ 👤 Elige hora de recordatorio (o "Sin recordatorio")
        │ No
        ↓
⚙️ Se guarda en Room (local) + notas del familiar se sincronizan desde Firestore y se anuncian por voz
```

### 8.2 Pantalla principal (`NotesScreen`)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | "🎤 Mis Notas" + "Tu diario privado — no se comparte con tu familia". Ícono de lápiz para nota manual. |
| **Banner de notas del familiar sin leer** | Solo si hay al menos una: 💌 *"Tu familiar te dejó 1 nota nueva"* (o plural *"...te dejó {n} notas nuevas"*), en morado. |
| **Estado vacío** | 🎤 grande, *"Habla y yo recuerdo"* + *"Toca el micrófono y dile a Alicia\nqué quieres que recuerde por ti.\nEs privado: tu familia no lo verá."* + botón "Hablar con Alicia". |
| **Estado con notas** | Lista de tarjetas: color y emoji de categoría, badge "NUEVA" si es de un familiar sin leer, contenido (máx. 3 líneas), emoji de fuente (🎤 voz / 💌 familiar / ⌨️ texto), fecha `d MMM, HH:mm`, hora de recordatorio "⏰ HH:mm" si aplica, botón eliminar. Al mostrarse una nota no leída, se marca como leída automáticamente. |

### 8.3 Escuchar (overlay de voz)

| | |
|---|---|
| **Dueño** | 👤 luego ⚙️ |
| **Qué ve** | Barras animadas tipo ecualizador, transcripción parcial en vivo (o *"Te escucho…"* mientras no hay texto), botón "Detener". |

### 8.4 Confirmar nota

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Tarjeta con emoji/color de categoría, *"¿Guardo esto?"*, contenido transcrito, fila *"⏰ Recordatorio a las HH:mm"* si aplica. Botones "Descartar" / "✓ Guardar". |

### 8.5 Diálogo de hora de recordatorio

Solo aparece si la categoría detectada es accionable (Salud, Tarea, Recordatorio). "⏰ ¿A qué hora te lo recuerdo?" + selector `HH:mm` en pasos de 30 min (con wraparound 24h, default 9:00) + botones "Sin recordatorio" / "Aceptar".

### 8.6 Errores de reconocimiento de voz

| Caso técnico | Copy exacto |
|---|---|
| Sin resultado de texto | "No se entendió el audio. Intenta de nuevo." |
| `ERROR_NO_MATCH` | "No se reconoció ninguna palabra" |
| `ERROR_SPEECH_TIMEOUT` | "No se detectó voz" |
| `ERROR_INSUFFICIENT_PERMISSIONS` | "Permiso de micrófono denegado" |
| `ERROR_NETWORK` / `ERROR_NETWORK_TIMEOUT` | "Sin conexión para el reconocimiento" |
| `ERROR_AUDIO` | "Error de audio del micrófono" |
| `ERROR_SERVER` | "Error del servidor de voz" |

Se muestran como banner rojo (`alert`) flotante abajo.

### 8.7 Nota manual (diálogo "Nueva nota")

Campo "¿Qué quieres anotar?" (3–6 líneas), botón "Guardar" deshabilitado si el texto está en blanco. A diferencia de las notas por voz, **nunca** pasa por el flujo de confirmación/recordatorio — se guarda directo.

### 8.8 Reglas de negocio

- Categorización automática por palabras clave, orden de prioridad: Salud → Compras → Familia → Recordatorio → Tarea → si nada coincide, General.
- Solo Salud, Tarea y Recordatorio son "accionables" (disparan el diálogo de hora).
- Notas del familiar se deduplican por id remoto de Firestore y se marcan leídas también en Firestore para no reenviarlas.
- Anuncio por voz de nota nueva del familiar: *"{nombre} te dejó una nota: {contenido}"*.
- Sin estado de carga inicial ni de error en la lista (Room/Flow entrega directo).

---

## 9 · Flujo: Alicia (chat conversacional con IA)

### 9.1 Resumen del flujo

```
👤 Abre la pestaña Alicia
        ↓
   ¿Primera vez / sin historial? ── Sí ──→ 🤖 Alicia envía saludo proactivo según hora del día e intereses
        │ No
        ↓
👤 Escribe y envía mensajes ←→ 🤖 Alicia responde (llamada a la API de Anthropic)
        │
        👤 Puede editar su perfil (nombre + intereses) o reiniciar la conversación en cualquier momento
```

### 9.2 Pantalla principal (`ChatScreen`)

| | |
|---|---|
| **Dueño** | 👤 + 🤖 |
| **Top bar** | "Alicia" (dorado) + "IA Vecinal · Círculo de Confianza". Ícono de perfil ("Mis intereses"), ícono de refrescar ("Nueva conversación"). |
| **Mensajes** | Burbujas de usuario (celeste, derecha) vs. Alicia (superficie alterna, izquierda, avatar dorado "A"). |
| **Cargando (`isLoading`)** | Indicador "escribiendo…" (tres puntos dorados con rebote). Input deshabilitado mientras carga. |
| **Error** | Banner rojo (`alert`) sobre el input con el mensaje y botón "OK" para descartarlo. |
| **Input** | Placeholder "Escribe un mensaje…", botón enviar circular (activo solo con texto y sin carga en curso). |

### 9.3 Bottom sheet "Mi perfil"

Campo "Tu nombre" (default "Vecino"), 16 chips de intereses con emoji (🎸 Metal/Rock, 🎵 Música, ✈️ Viajes, 🍳 Cocina, 🌱 Jardín, 📚 Libros, 🎬 Películas, 📷 Fotografía, ⚽ Fútbol, 🏃 Ejercicio, 🐱 Mascotas, 🧩 Manualidades, 🎭 Teatro/Arte, 🌿 Naturaleza, 🕹️ Videojuegos, 🧘 Bienestar), selección múltiple. Botón "Guardar y reiniciar chat" — **reinicia toda la conversación** con un saludo nuevo acorde a los intereses actualizados.

### 9.4 Reglas de negocio / restricciones de contenido

- Historial persistido en `SharedPreferences`, últimos 30 mensajes; perfil (nombre + intereses) en otro `SharedPreferences` separado.
- **UI optimista**: el mensaje del usuario aparece de inmediato; si la llamada falla, se revierte y se muestra el error, sin dejar el mensaje fallido visible.
- El "system prompt" de Alicia impone reglas estrictas, relevantes para cualquier ampliación futura de esta función:
  - Responder siempre en español, cálido, oraciones cortas.
  - Mencionar vecinos con intereses en común (datos mock hoy; están pensados para venir de Firestore `circles/{id}/members` en el futuro).
  - Si detecta urgencia, recordar el Botón de Pánico de la app.
  - **No dar consejos médicos, legales ni financieros.**
  - **Regla innegociable**: nunca explicar para qué sirve un medicamento, qué significa una dosis, ni dar diagnóstico u opinión sobre consecuencias de no tomarlo — debe redirigir con calidez a la familia o al médico, incluso si el usuario insiste.
- Requiere `ANTHROPIC_API_KEY` configurada; si falta, el error visible es literal: *"Falta ANTHROPIC_API_KEY..."* — esto es un detalle de configuración de build, no algo que el usuario final deba ver en producción.
- Sin estado de carga inicial en la lista de mensajes — el saludo proactivo reutiliza el mismo `isLoading` que un mensaje normal.

---

## 10 · Flujo: Zonas Seguras (geocercas)

### 10.1 Resumen del flujo

```
👤 Adulto Mayor concede permisos de ubicación
        ↓
👤 Agrega una zona (nombre + radio, centrada en su ubicación actual)
        ↓
⚙️ Se registra la geocerca ante Google Play Services (si hay permiso de segundo plano)
        ↓
⚙️ El sistema detecta ENTER/EXIT en segundo plano, incluso con la app cerrada
        ↓
   ¿Salió de una zona activa? ── Sí ──→ ⚙️ SMS automático a guardianes + notificación local
```

### 10.2 Pantalla principal (`SafeZonesScreen`)

| | |
|---|---|
| **Dueño** | 👤 (mapa/lista) + ⚙️ (detección) |
| **Estados de carga** | `Loading` (spinner verde centrado) → `Error(mensaje)` (texto rojo `#FF6B6B`, mensaje propagado desde el repositorio) → `Ready` (mapa + lista). |
| **Mapa** | Marcador verde en la ubicación actual (o Bogotá como fallback). Cada zona: círculo relleno (verde si activa, gris si inactiva) + marcador con snippet "Radio: {N} m · Activa/Inactiva". |
| **Banner de permisos** | Sin ubicación fina: *"Activa la ubicación para ver el mapa"* + "Activar". Con ubicación fina pero sin segundo plano: *"Activa 'Siempre' en ubicación para alertas en segundo plano"* + "Activar". |
| **Banner sin guardianes en el círculo (nuevo)** | Si el círculo no tiene ningún miembro con rol Guardián: banner ámbar apilado debajo del de permisos (mismo estilo visual, sin botón de acción): *"Todavía no hay ningún guardián en tu círculo. Si sales de una zona segura, no habrá nadie a quien avisar."* Informativo, no bloquea crear ni activar zonas — igual criterio que el banner equivalente en Medicamentos (§11.1). |
| **Panel de zonas (colapsable)** | "Zonas Seguras ({N})". Vacío: *"Toca + para agregar tu primera zona"*. Cada fila: punto de color, ícono (casa si el nombre contiene "Casa"), nombre, "{radio} m", switch activar/desactivar, papelera. |
| **Eliminar zona** | Confirmación: *"¿Eliminar zona?"* + *"Se eliminará "{nombre}" del mapa."* — botones "Eliminar" (`destructive`) / "Cancelar". |

### 10.3 Diálogo "Nueva Zona Segura"

Campo "Nombre" (placeholder "Ej: Casa, Parque, Panadería"), campo "Radio en metros" (solo dígitos, acotado entre 50 y 1000, default 150), texto *"Se centrará en tu ubicación actual."* Botón "Agregar" deshabilitado si el nombre está en blanco.

### 10.4 Notificaciones y SMS

- Entrar a zona: *"Llegaste a: {zona}"*.
- Salir de zona: *"Saliste de: {zona}. Tu círculo fue notificado."*
- Permanencia (dwell): *"Llevas un rato en: {zona}"*.
- SMS a guardianes al salir: *"Círculo de Confianza: {nombreUsuario} salió de la zona segura "{zona}"."* — solo se envía si hay al menos un guardián con teléfono no vacío; si no hay ninguno, queda un warning silencioso en log, sin feedback visible al usuario (posible mejora a futuro: avisarle al Adulto Mayor que su alerta de zona no le llegó a nadie).

### 10.5 Reglas de negocio y privacidad

- **La ubicación en tiempo real no se publica de forma continua** — solo se comparte en el SMS puntual cuando ocurre un evento real (salida de zona, o check-in perdido). Es una decisión de privacidad explícita en el código, vale la pena mantenerla visible en cualquier comunicación al usuario sobre qué datos se comparten.
- Radio acotado 50–1000 m. Zona nueva siempre centrada en la ubicación actual al momento de crearla.
- Sin registro de background permission, la zona se guarda en la lista pero no genera ninguna alerta real.
- Repositorio actual es un mock en memoria con dos zonas de ejemplo (Casa 100m, Parque Cercano 200m) — no persistente entre reinicios de proceso; pendiente reemplazo por Firestore (ver §7.2).

---

## 11 · Flujo: Medicamentos

### 11.1 Pantalla principal — sección "Medicamentos" (comparte pantalla con Citas médicas, §12)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | "Medicamentos" + "Recordatorios para tomar tus medicamentos". |
| **Estado "subiendo foto"** | Banner con spinner verde + *"Subiendo foto de la pastilla…"* |
| **Banner sin guardianes en el círculo (nuevo)** | Si el círculo no tiene ningún miembro con rol Guardián: banner ámbar (`warning`) sobre la lista, mismo patrón visual que el de `FamiliarCheckInScreen` (§3.2a): *"Todavía no hay ningún guardián en tu círculo. Si no confirmas que tomaste tu medicamento, no habrá nadie a quien avisar."* No bloquea nada — a diferencia del check-in, el Adulto Mayor sigue pudiendo agregar y activar medicamentos sin guardianes, solo se le advierte. |
| **Estado vacío** | 💊 grande, "Sin medicamentos" + *"Agrega tus medicamentos y\nrecibirás un recordatorio a la hora indicada."* |
| **Con datos** | Tarjetas ordenadas por hora: foto circular o ícono 💊, nombre, hora en verde + dosis opcional + 🍽️ si es "con comida", nota (1 línea), switch activar/desactivar, papelera. Tarjeta atenuada (opacidad 0.5) si está inactiva. |

### 11.2 Diálogo "Nuevo medicamento"

Foto (cámara o galería, opcional), campo "Nombre del medicamento" (**obligatorio**), "Dosis (opcional)", "Instrucción adicional (opcional)", switch "Con comida / En ayunas" (con subtítulo explicativo), selector de hora en pasos de 30 min. Botón "Guardar" deshabilitado si el nombre está vacío.

### 11.3 Alarma de medicamento a pantalla completa (`MedicationAlarmActivity`)

Experiencia tipo "llamada entrante": aparece sobre la pantalla bloqueada, enciende la pantalla, el botón atrás está deshabilitado.

| Estado | Qué ve / qué dice Alicia-voz |
|---|---|
| **Activa** (0–120s) | 💊 "Es hora de tu medicina", foto grande con pulso animado (o ícono de respaldo), nombre + dosis, "🍽️ Tómala con comida" si aplica, nota. Botón "✅ YA LA TOMÉ". Countdown normal en gris; en los últimos 30s, fondo rojo suave y "⏳ Quedan {n} s" en rojo. TTS al abrir: *"Hola, es hora de tomar {nombre}, {dosis}, recuerda tomarla con comida. {nota}."* (cada parte condicional). |
| **Tomado** | ✅ "¡Perfecto!" + "{medicamento} registrado" + "Tu familiar puede ver que lo tomaste 💚". TTS: *"¡Perfecto! Ya registré que tomaste {medName}. ¡Que te haga bien!"* Se cierra sola a los 2.5s. |
| **Expirado (120s sin confirmar)** | ⏰ "Tiempo agotado" + "Tu familiar ha sido notificado.\nEsta pantalla se cerrará pronto." TTS: *"No recibí tu confirmación. Le avisé a tu familiar."* Se cierra sola a los 3s. El copy ahora es fiel al código: al expirar, se envía SMS a los guardianes y un push FCM al círculo (ver §7.2). |

En paralelo a la Activity, el `MedicationWorker` también muestra una notificación heads-up de respaldo con acción rápida "✓ Tomado" — ambas superficies pueden confirmar la toma.

### 11.4 Reglas de negocio

- Recordatorio diario recurrente (`PeriodicWorkRequest` de 24h). Desactivar cancela el work sin borrar el registro; reactivar reprograma desde el próximo horario válido.
- Subir foto requiere Círculo activo; si falla, el medicamento ya quedó guardado igual — el fallo de foto no bloquea el guardado.
- El registro de "tomado" (`takenAt`) alimenta la Línea de Vida del Dashboard Familiar (§6.2) — vía Firestore (`circles/{circleId}/medications/{id}`), no vía Room compartido (ver el hallazgo y la corrección en §7.2).
- Sin edición de medicamentos existentes — solo crear/activar/desactivar/eliminar.

---

## 12 · Flujo: Citas médicas

No existe una pantalla separada — vive como una segunda sección dentro de la misma `MedicationScreen`, con su propio FAB apilado arriba del de medicamentos.

**Cambio de arquitectura**: hasta ahora las citas eran estrictamente locales (Room), sin ninguna relación con el Círculo — ni siquiera de solo lectura, a diferencia de Medicamentos. Eso generaba una asimetría notoria: la familia podía ver si se tomó una pastilla, pero no si había una cita médica programada o si el adulto mayor ya había ido al médico. Ahora `AppointmentRepository` publica (y retira) cada cita en Firestore al crearla/eliminarla — mismo patrón de solo-lectura ya usado para Memorias y para el check-in, ver §6.2.

### 12.1 Pantalla — sección "Citas médicas"

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | "Citas médicas" + "Te avisamos el día y la hora de cada cita". |
| **Estado vacío** | 🩺, "Sin citas médicas" + *"Agrega tu próxima cita y te avisamos\nel día y la hora que elijas."* |
| **Con datos** | Tarjetas ordenadas por fecha más próxima primero: ícono 🩺 con borde azul (deliberadamente distinto del verde de medicamentos, "para distinguirse de un vistazo"), "{doctor} · {especialidad}", fecha/hora en azul bold (ej. *"Viernes 12 de septiembre, 09:00"*), "📍 {lugar}" si existe, nota, papelera. |

### 12.2 Diálogo "Nueva cita médica"

Campo "Con quién es la cita" (**obligatorio**), "Especialidad (opcional)", "Lugar (opcional)", "Nota adicional (opcional)". Fecha por defecto: **mañana** (una cita "hoy mismo" rara vez es realista de agendar así, según el propio criterio del código). Selector de fecha con `DatePicker` restringido a hoy o después — no se pueden agendar citas en el pasado. Hora en pasos de 30 min. Botón "Guardar" deshabilitado si el nombre del médico está vacío.

### 12.3 Reglas de negocio

- Evento único (`OneTimeWorkRequest`), no recurrente. Solo dispara una notificación normal informativa a la hora de la cita — **sin** pantalla de alarma a pantalla completa, sin TTS, sin countdown, sin confirmación de "atendida" (eso sigue sin existir — ver más abajo).
- **Visible para el Familiar, de solo lectura (nuevo)**: cada cita se publica en `circles/{circleId}/appointments/{id}` al crearse, y se retira al eliminarse (`AppointmentRepository`, `FirestoreRepository.publishAppointment`/`deleteAppointmentRemote`). El Adulto Mayor es el único que escribe; el Familiar solo observa (`FirestoreRepository.observeAppointments`).
- **Lo que sigue sin existir**: ningún campo ni interacción de "asistida" — a diferencia de Medicamentos (que sí tiene el gesto de "Ya la tomé"), aquí no hay forma de que el adulto mayor confirme que fue a la cita, ni el Familiar puede marcarla como cumplida. La visibilidad nueva es sobre la existencia y el horario de la cita, no sobre si ocurrió.
- Sin edición — solo crear/eliminar.

---

## 13 · Flujo: Memorias (línea de tiempo de recuerdos)

### 13.1 Resumen del flujo

```
👤 Familiar agrega una foto + título + fecha pasada + personas etiquetadas (nombre + parentesco)
        ↓
⚙️ Se sube la foto a Firebase Storage, luego se publica el recuerdo en Firestore
        ↓
👤 Adulto Mayor ve la línea de tiempo en modo lectura, con frases generadas ("Este es tu hijo Gustavo")
```

### 13.2 Pantalla (`MemoriesScreen`) — misma pantalla, dos vistas según rol

| | |
|---|---|
| **Dueño** | 👤 Familiar (crea/edita/elimina) — 👤 Adulto Mayor (solo lectura) |
| **Título** | "Línea de Tiempo". Subtítulo por rol: Familiar → *"Agrega fotos y momentos para que tu familiar los recuerde"*; Adulto Mayor → *"Tus recuerdos y las personas que quieres"*. |
| **Vista Familiar** | FAB "Agregar recuerdo" (`circle-purple`), tarjetas compactas (foto + título + fecha + papelera). |
| **Vista Adulto Mayor** | Tarjetas grandes tipo postal (foto 4:3 arriba, texto abajo), con chips de personas etiquetadas mostrando la frase generada (ej. *"Este es tu hijo Gustavo"*, *"Esta es tu nieta Sofía"* — heurística de género por terminación de la palabra de parentesco). Sin acciones de edición. |
| **Estado vacío** | 📔, "Aún no hay recuerdos" + copy por rol: Familiar → *"Agrega una foto familiar con nombres y parentescos para que tu familiar la recuerde."*; Adulto Mayor → *"Cuando tu familia agregue fotos y momentos, aparecerán aquí."* |
| **Subiendo foto** | Banner con spinner + *"Subiendo foto del recuerdo…"* |
| **Error de subida** | Texto rojo con el mensaje o *"Error al subir la foto"*, descartable. |

### 13.3 Diálogo "Nuevo recuerdo" (solo Familiar)

Foto (cámara con permiso runtime, o galería), campo Título (**obligatorio**, habilita "Guardar"), Descripción opcional, selector de fecha **restringido a fechas pasadas** ("un recuerdo es del pasado"), y un mini-formulario para etiquetar personas (nombre + parentesco) que se acumulan como chips removibles antes de guardar.

### 13.4 Reglas de negocio

- La foto se sube primero a Storage y solo entonces se publica el documento en Firestore — evita recuerdos "rotos" sin imagen si la subida falla a mitad de camino.
- Sin relación con notificaciones o alertas — es puramente una función de vínculo afectivo, no de seguridad.

---

## 14 · Glosario técnico rápido (para quien lea este spec sin contexto de código)

| Término | Qué es |
|---|---|
| Firestore | Base de datos en la nube (Firebase) que sincroniza el Círculo entre los dispositivos del Adulto Mayor y del Familiar. |
| Room | Base de datos local en el dispositivo (SQLite), usada para lo que debe funcionar sin internet (notas, medicamentos, citas, historial reciente de check-in). |
| DataStore / SharedPreferences | Almacenamiento simple local para configuración (horarios, perfil de chat, etc.), más liviano que Room. |
| WorkManager | Sistema de Android para programar tareas en segundo plano (alarmas, recordatorios) que sobreviven incluso si la app está cerrada. |
| Geofencing | API de Google que detecta cuándo un dispositivo entra o sale de una zona geográfica definida, sin que la app esté abierta. |
