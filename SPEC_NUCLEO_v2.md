# Círculo de Confianza — Spec v2 (Núcleo)

> Cubre: Check-in de Bienestar, SOS, Círculo de Confianza (emparejamiento), Dashboard Familiar.
> No cubre todavía: Juegos, Notas, Alicia (chat), Zonas Seguras — quedan con el comportamiento del spec v1 hasta la siguiente pasada; solo heredarán la paleta nueva cuando se actualicen.

---

## 0 · Qué cambió respecto al spec v1, y por qué

**Paleta: de oscura a clara.** El spec v1 elegía fondo oscuro (`#0D1B2A`) explícitamente para "reducir fatiga visual". Los referentes de esta revisión apuntan lo contrario para este público: sesiones prolongadas sobre fondo oscuro cansan más a un adulto mayor que un fondo claro y suave con buen contraste. Se invierte la decisión. Esto **rompe visualmente todo lo ya construido** — cada pantalla del prototipo actual hardcodea la paleta oscura localmente; adoptar este spec implica retrabajar el sistema de tema y cada pantalla del núcleo.

**Tipografía: no cambia.** Lora (títulos) + Plus Jakarta Sans (cuerpo) ya están bundleadas como archivos reales en el proyecto y funcionan igual de bien sobre fondo claro. Se mantienen.

**Iconografía nueva: tarjeta-ícono.** Referente 1 (aunque es un diagrama de metodología, no una app) aporta un patrón reutilizable: ícono de línea blanca delgada centrado sobre una tarjeta de color sólido con esquinas redondeadas. Se adopta como el patrón para accesos a función (SOS, Bienestar, Círculo, Estado, etc.).

**Ilustración: minimalista, con dibujos simples.** Referente 2 aporta el tono — ilustraciones lineales simples, mucho aire, mensajes cortos y cálidos ("Let yourself pause. You're in good hands" es el nivel de calidez a igualar en español). Se usa en estados vacíos y de éxito, nunca como decoración sin función.

**Fuente de los flujos.** Este documento deriva los pasos, decisiones y puntos de control de la sección 06 del spec v1 más la arquitectura de emparejamiento por círculo (código de invitación, roles Guardián/Miembro, sesión anónima para el Adulto Mayor) definida y construida en la sesión de corrección de prototipo previa a este documento.

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
| `accent` | Verde — vida, seguridad, confirmación | `#2ECC71` |
| `accent-soft` | Fondo tenue del accent (chips, halos) | `#E3F9EC` |
| `alert` | Rojo — **reservado exclusivamente para emergencia real** | `#E8342A` |
| `alert-soft` | Fondo tenue del alert (banners de error no críticos) | `#FCE8E6` |
| `warning` | Ámbar — advertencias no urgentes (batería, permisos) | `#F39C12` |
| `line` | Bordes, separadores | `#E4E0EE` |
| `inactive` | Iconos/texto deshabilitado, tab sin seleccionar | `#B8B4C6` |
| `shadow` | Sombra de tarjeta | `#8E7FA6` al 12% |

Regla dura heredada del spec v1: **el rojo `alert` solo aparece en un evento de emergencia real** (SOS activo, alerta de check-in perdido). Nunca se usa decorativamente ni para errores de formulario — esos usan `warning` o texto simple en `text-secondary`.

### 1.2 Tipografía

Sin cambios respecto al spec v1 — ya implementada con archivos de fuente reales:

| Rol | Fuente | Tamaño mínimo |
|---|---|---|
| Títulos grandes (`displayLarge`, `displayMedium`) | Lora, bold | 26–32sp |
| Encabezados de sección (`headlineLarge`) | Lora, bold | 24sp |
| Botones y etiquetas (`titleLarge`, `labelLarge`) | Plus Jakarta Sans, bold/semibold | 16–20sp |
| Cuerpo (`bodyLarge`, `bodyMedium`) | Plus Jakarta Sans, regular | 16sp mínimo — nunca menos |

### 1.3 Iconografía — patrón tarjeta-ícono

Cada acceso a función es una tarjeta con esquinas redondeadas (20dp), fondo de color sólido de la paleta semántica de esa función, ícono de línea blanca (peso 2dp, sin relleno) centrado, y una etiqueta corta debajo o al lado en Plus Jakarta Sans bold. Tamaño mínimo de tarjeta interactiva: 96×96dp (además del ya definido 100dp para "¡Estoy bien!" y 180dp para SOS, que se mantienen).

Ejemplos de asignación de color por función (para mantener asociación consistente en todo el núcleo):
- SOS → `alert` (`#E8342A`) — es la única superficie que usa este color por defecto, no solo en emergencia activa.
- Bienestar / Check-in → `accent` (`#2ECC71`)
- Círculo → morado suave `#8E7FA6` (nuevo, exclusivo de la función Círculo)
- Estado (dashboard familiar) → `accent`
- Zonas (fuera de alcance de esta pasada, hereda color existente)

### 1.4 Ilustración

Ilustraciones de línea simple (2 colores máximo: `text` + un acento de la paleta), sin degradados, sin caras hiperdetalladas — un trazo cálido y humano, no corporativo. Se usan en:
- Estados vacíos (círculo sin miembros, sin historial de check-in)
- Confirmaciones de éxito (check-in respondido, alerta resuelta)
- Nunca en pantallas de error o de emergencia — ahí el foco es 100% la acción, sin distracción visual.

### 1.5 Tono de copy (sin cambios respecto a v1)

Cálido, directo, como una conversación entre personas que se quieren. Nunca "configurar", "sincronizar", "usuario", "parámetros". Frases cortas.

### 1.6 Antirreferentes — lo que NO se hace, nunca

- **Nunca fondo oscuro** en el núcleo (Check-in, SOS, Círculo, Dashboard Familiar). Es la inversión explícita de esta revisión.
- **Nunca texto denso.** Máximo 2 frases cortas seguidas antes de un salto visual (ícono, espacio, tarjeta). Si una pantalla necesita más de 3 bloques de texto, hay que rediseñar la jerarquía, no comprimir.
- **Nunca elementos chicos.** Todo objetivo táctil ≥ 60dp (heredado de v1); ningún texto de cuerpo bajo 16sp; ningún ícono bajo 24dp.
- **Nunca todo "encajonado".** Padding mínimo de pantalla: 20dp. Entre tarjetas: 12–16dp. Nada pegado al borde ni entre sí.
- **Nunca un chip/badge decorativo sin función.** Si algo parece una etiqueta de estado, debe comunicar un estado real (ver leyenda de dueños abajo), no decorar.

---

## 2 · Leyenda de dueños

Cada paso de cada flujo está etiquetado con quién lo posee:

| Ícono | Dueño | Significado |
|---|---|---|
| 👤 | **Persona** | El adulto mayor o el familiar toma una acción explícita (toca, escribe, decide). |
| 🔊 | **Alicia** | El asistente de voz habla o reacciona — no es una persona ni el sistema genérico, es el personaje. |
| ⚙️ | **Sistema** | Ocurre automáticamente, sin que nadie lo pida en ese momento (temporizador, sincronización, notificación push). |

---

## 3 · Flujo: Check-in de Bienestar

### 3.1 Resumen del flujo

```
⚙️ Alarma diaria programada
        ↓
🔊 Alicia saluda según la hora (fase Despertar, 0–5s)
        ↓
👤 ¿Tocó "¡Estoy bien!"? ──── Sí ──→ ⚙️ Registra + notifica al círculo → 🔊 Alicia despide con calidez
        │
        No (sigue esperando, fase Principal 5–20s → fase Aviso final 20–30s)
        ↓
⚙️ Countdown de 30s agotado → ⚙️ Notifica al círculo como "no respondió" + SMS
```

### 3.2 Pantalla: Configuración del Check-in (`CheckInSettingsScreen`)

| | |
|---|---|
| **Dueño principal** | 👤 |
| **Qué ve** | Estado de hoy (tarjeta con ícono + texto), interruptor "Activar alarma diaria", selector de hora, chips de tiempo de espera (1h/2h/4h), campo de teléfono propio, botón "Probar alarma ahora". |
| **Estado vacío (primera vez)** | Tarjeta de estado dice "Sin alarma hoy aún — Recibirás la alarma a la hora configurada". Interruptor apagado por defecto hasta que haya al menos un guardián. |
| **Datos incompletos — sin guardianes** | Banner ámbar (`warning`) fijo arriba de la tarjeta de interruptor: *"Todavía no tienes a nadie en tu círculo. Agrega al menos un familiar en la pestaña Círculo antes de activar la alarma, o no le avisará a nadie."* El interruptor queda **deshabilitado** (no solo advertido) hasta resolverlo — evita la falsa sensación de seguridad. |
| **Datos incompletos — sin teléfono propio** | El campo queda vacío con placeholder "Ej. 3001234567"; no bloquea activar la alarma (el teléfono habilita el botón de llamada del familiar, no el check-in en sí), pero si está vacío el Dashboard Familiar no podrá ofrecer "Llamar a [nombre]" — ver §6.2. |
| **Error — falla al guardar configuración** | No hay error visible por diseño: el guardado es local-first (DataStore) y siempre exitoso; la sincronización a Firestore ocurre en segundo plano sin bloquear la UI. Si Firestore falla, se reintenta silenciosamente en el próximo cambio — no se le pide al usuario que "reintente" nada. |

### 3.3 Pantalla: Alarma activa — fase Despertar (0–5s)

| | |
|---|---|
| **Dueño** | 🔊 luego 👤 |
| **Qué pasa** | 🔊 Alicia dice en voz (TTS es-CO): *"Hola, hace un rato que no nos saludamos. ¿Estás por ahí?"* Vibración suave (3 pulsos cortos). Fondo cálido, saludo grande con el nombre: "Hola, [nombre]" + "¿Estás por ahí?". |
| **Acción disponible** | 👤 Tocar el botón grande (100dp) "SÍ, ESTOY BIEN" — disponible desde el primer segundo, no hay que esperar a que termine el saludo. |

### 3.4 Pantalla: Alarma activa — fase Principal (5–20s)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué pasa** | El botón crece a 200dp con un latido animado. Ícono de corazón. Barra de progreso ámbar agotándose. Texto: "Presiona si estás bien". |
| **Acción disponible** | 👤 Tocar el botón. |

### 3.5 Pantalla: Alarma activa — fase Aviso final (20–30s)

| | |
|---|---|
| **Dueño** | 🔊 luego ⚙️ |
| **Qué pasa** | 🔊 Alicia advierte: *"Voy a avisar a tu familia en 10 segundos para que sepa que no respondes. Presiona el botón si todo está en orden."* Cuenta regresiva gigante (110sp), fondo cambia a tono de urgencia (naranja/rojo suave, nunca el `alert` puro — esto no es todavía una emergencia confirmada). Barra roja. |
| **Si nadie responde** | ⚙️ Al llegar a 0: registra MISSED, cancela notificación de alarma, notifica al círculo (push + SMS a guardianes), sincroniza a Firestore. Sin confirmación adicional — es automático por diseño (spec v1 guardrail: sin fricción). |

### 3.6 Pantalla: Respondido con éxito

| | |
|---|---|
| **Dueño** | ⚙️ luego 🔊 |
| **Qué ve** | Pantalla verde con ✅ grande, ilustración de línea simple (opcional), "¡Perfecto, [nombre]!" + "Seguimos conectados. Que tengas un lindo día." + chip "Tu familia ya sabe que estás bien 💚". |
| **En paralelo** | 🔊 Alicia dice: *"¡Perfecto! Seguimos conectados. Que tengas un lindo día."* ⚙️ Se notifica al círculo (push + SMS) y se sincroniza a Firestore. |

### 3.7 Errores que pueden ocurrir en este flujo

| Caso | Qué ve el usuario | Qué puede hacer |
|---|---|---|
| Sin señal / sin datos al momento de responder | Nada distinto en pantalla — el registro local (DataStore + Room) ya ocurrió, es la fuente de verdad inmediata. El SMS y la sincronización a Firestore quedan pendientes y se reintentan cuando vuelva la señal. | Nada — es transparente. Si el familiar no ve la actualización en su dashboard, el historial se pondrá al día solo cuando el teléfono del adulto mayor recupere señal. |
| Permiso de notificaciones denegado | La alarma diaria no puede mostrarse como notificación de pantalla completa. | (Pendiente de definir en esta pasada: se recomienda un banner permanente en Configuración del Check-in explicando que sin este permiso la alarma no sonará — **queda como decisión abierta para la siguiente iteración**, no se resuelve en este documento.) |

---

## 4 · Flujo: SOS / Botón de Pánico

### 4.1 Resumen del flujo

```
👤 Un toque en el botón SOS (180dp)
        ↓
⚙️ Countdown visible de 5s, cancelable
        ↓
👤 ¿Canceló? ──── Sí ──→ Vuelve a Idle, sin rastro, sin aviso a nadie
        │
        No
        ↓
⚙️ Envía SMS con ubicación a todo el círculo + push + marca alerta activa en Firestore
        ↓
👤 En el Dashboard Familiar: la pantalla se pone roja de inmediato (ver §6.3)
        ↓
👤 El adulto mayor confirma: "Estoy bien" o "Falsa alarma" → ⚙️ limpia la alerta
```

### 4.2 Pantalla: Idle (estado de reposo)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Botón circular grande (180dp), rojo (`alert`), con anillos de pulso suave. Texto: "Toca para alertar a tu círculo". |
| **Datos incompletos — sin nadie en el círculo** | Debajo del botón, texto ámbar: *"⚠️ Todavía no tienes a nadie en tu círculo — agrega a un familiar para que esta alerta le llegue a alguien."* El botón **sigue activo** (a diferencia del check-in) — nunca se bloquea la posibilidad de intentar pedir ayuda, aunque hoy no le llegue a nadie del círculo. |

### 4.3 Pantalla: Countdown (0–5s)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Contador circular grande, número gigante (72sp) sobre fondo de color de emergencia. Botón "CANCELAR" grande (72dp) siempre visible y siempre habilitado. Texto: "Toca CANCELAR si estás bien". |
| **Regla dura** | Un solo toque activa el countdown — nunca hay un segundo menú ni una confirmación intermedia antes de esto (guardrail de producto, no negociable). |

### 4.4 Pantalla: Enviando

| | |
|---|---|
| **Dueño** | ⚙️ |
| **Qué ve** | Spinner + "Alertando a tu círculo..." + "Obteniendo tu ubicación y notificando a familia". Sin acciones disponibles — dura ~1.5s. |

### 4.5 Pantalla: Alerta activa

| | |
|---|---|
| **Dueño** | ⚙️ luego 👤 |
| **Qué ve** | "¡ALERTA ENVIADA!" con ícono parpadeante. Tarjeta de estado: "Tu círculo fue notificado" + "Ubicación compartida" (solo estas dos — nunca afirmar que se envió algo que no se envió, p. ej. batería o info médica, si no está implementado). Dos botones: "Falsa alarma" (outline) y "Estoy bien" (verde, ícono check). |
| **Acción disponible** | 👤 Tocar cualquiera de los dos botones para resolver. |

### 4.6 Pantalla: Resuelta

| | |
|---|---|
| **Dueño** | ⚙️ |
| **Qué ve** | "✅" grande, "Tu círculo fue avisado que estás bien", "Gracias por confirmar". Vuelve sola a Idle a los 3s. |

### 4.7 Pantalla: Error al enviar

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Ícono de advertencia, "No se pudo enviar la alerta" + el motivo específico si se conoce, y siempre: *"⚠️ Llama directamente al 123 o a un familiar"* — nunca un flujo que retrase o reemplace esta opción. |
| **Qué puede hacer** | Llamar directo al 123 (fuera de la app), o intentar de nuevo tocando el botón desde Idle. |

---

## 5 · Flujo: Círculo de Confianza (emparejamiento entre dispositivos)

### 5.1 Resumen del flujo

```
👤 Elige perfil (Adulto Mayor | Familiar) + nombre
        ↓
👤 Acepta el aviso de privacidad (una sola vez)
        ↓
   ┌─── Adulto Mayor ───┐         ┌─── Familiar ───┐
   │ ⚙️ Sesión anónima   │         │ 👤 Login (correo/contraseña) │
   │ ⚙️ Crea círculo     │         │ 👤 Escribe el código         │
   │ 👤 Comparte código  │         │ 👤 Escribe su teléfono       │
   └─────────────────────┘         │ 👤 Elige rol (Guardián/Miembro) │
                                    └───────────────────────────────┘
        ↓                                        ↓
              ⚙️ Ambos dispositivos comparten el mismo círculo en Firestore
```

### 5.2 Pantalla: Elegir perfil (`OnboardingScreen`)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Dos tarjetas grandes: "Soy Adulto Mayor" / "Soy Familiar", cada una con descripción de una línea. Al elegir una, aparece el campo de nombre y el botón "Comenzar". |
| **Datos incompletos** | Botón "Comenzar" deshabilitado hasta que el nombre no esté vacío. |

### 5.3 Pantalla: Aviso de privacidad

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Qué datos se guardan, para qué, con quién se comparten, derechos del usuario — en lenguaje simple. Botón "Acepto y continúo" / "No acepto". |
| **Si no acepta** | Se cierra la app — no hay una versión degradada que funcione sin consentimiento, porque el consentimiento cubre justamente los datos mínimos que el servicio necesita para funcionar (nombre, teléfono, contactos del círculo). |

### 5.4a Pantalla: Compartir código (solo Adulto Mayor)

| | |
|---|---|
| **Dueño** | ⚙️ luego 👤 |
| **Qué pasa al entrar** | ⚙️ Sesión anónima silenciosa (sin pedir nada al usuario) + creación del círculo en segundo plano. Mientras tanto, spinner. |
| **Qué ve al terminar** | "🎉 ¡Ya tienes tu Círculo!" + código grande (8 caracteres, `#2ECC71` sobre tarjeta blanca) + botón "Compartir código" (abre el selector nativo de compartir — WhatsApp, SMS, etc.) + botón "Continuar". |
| **Estado vacío/carga** | Mientras se crea el círculo: spinner centrado, sin texto adicional (la operación dura segundos). |
| **Error — timeout de red** | *"No hay respuesta del servidor. Revisa tu conexión a internet e intenta de nuevo."* + botón "Reintentar". Ocurre a los 15s si Firebase no responde. |
| **Error — falla la sesión** | *"No se pudo iniciar tu sesión: [detalle]"* + botón "Reintentar". |
| **Error — falla crear el círculo** | *"No se pudo crear tu círculo: [detalle]"* + botón "Reintentar". |
| **Después** | El código sigue disponible permanentemente en la pestaña Círculo, por si hay que compartirlo de nuevo con otro familiar. |

### 5.4b Pantalla: Unirse a un círculo (solo Familiar)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | "🔗 Únete a un círculo" + "Pídele a tu familiar el código que le aparece al configurar su teléfono" + campo de código (8 caracteres, mayúsculas automáticas) + campo de teléfono propio + selector de rol (dos tarjetas: Guardián "Recibo todas las alertas: pánico, check-in y zonas" / Miembro "Solo recibo alertas de emergencia") + botón "Unirme al círculo". |
| **Estado inicial (antes de tocar nada)** | El botón está **habilitado en cuanto el código y el teléfono son válidos** — nunca debe nacer deshabilitado con un spinner fantasma (bug ya corregido: el estado inicial del formulario es neutral, no "cargando"). |
| **Datos incompletos** | Botón deshabilitado mientras el código esté vacío o el teléfono tenga menos de 7 dígitos. Sin mensajes de error prematuros — solo se deshabilita, sin regañar antes de que el usuario termine de escribir. |
| **Error — código inválido** | *"Ese código no corresponde a ningún círculo"* debajo del formulario, en `alert`-soft con texto `alert`. El formulario no se limpia — el usuario puede corregir el código sin volver a escribir el teléfono. |
| **Error — timeout de red** | *"No hay respuesta del servidor. Revisa tu conexión a internet e intenta de nuevo."* |
| **Éxito** | Navega directo a la app principal — sin pantalla intermedia de confirmación (consistente con "sin fricción" del spec v1). |

### 5.5 Pantalla: Círculo (lista de miembros)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Código del círculo visible arriba (chip pequeño, no protagonista). Título "Tu Círculo de Confianza" + conteo de personas. Leyenda de roles (Guardián = todas las alertas, Miembro = solo emergencias). Lista de tarjetas por miembro: avatar con iniciales, nombre, teléfono formateado, chips de qué notificaciones recibe, botones llamar/editar/quitar. FAB "Agregar miembro". |
| **Estado vacío** | Ilustración de línea simple (👥 o dibujo de personas), "Tu círculo está vacío" + "Agrega a las personas de confianza que quieres que sepan si estás bien." + botón grande "Agregar primer miembro". |
| **Agregar miembro — sin instalar la app** | El Adulto Mayor puede agregar a alguien manualmente (nombre + teléfono, importable desde Contactos) sin que esa persona instale nada — solo recibe SMS. Esto es intencional y coexiste con el flujo de código: quien se une por código además puede ver el dashboard. |
| **Quitar miembro** | Confirmación simple: "¿Quitar a [nombre]?" + "Ya no recibirá notificaciones de tu círculo." — botones Quitar / Cancelar. |

---

## 6 · Flujo: Dashboard Familiar

### 6.1 Resumen del flujo

```
⚙️ El dashboard combina en tiempo real: check-ins, alerta de pánico, perfil del círculo
        ↓
👤 ¿Hay una alerta de pánico O un check-in perdido activos?
        │
        Sí → Pantalla roja de emergencia (ver 6.3)
        │
        No → Dashboard normal: estado de hoy + línea de vida + historial + mensaje a Alicia
```

### 6.2 Pantalla: Dashboard normal

| | |
|---|---|
| **Dueño** | 👤 (lectura) + ⚙️ (actualización en tiempo real) |
| **Qué ve** | "Hola, estás cuidando a [nombre]" + Hero circular grande con estado ("Todo en orden" / "Esperando respuesta" / "Sin datos aún") + "Actividad de hoy" (línea de tiempo: check-in, medicinas tomadas, notas) + historial de últimos 7 días + tarjeta "Mensaje para Alicia". Barra inferior fija: botón "Llamar a [nombre]" + botón "Mensaje de voz". |
| **Estado vacío — sin actividad hoy** | Ilustración de línea simple + "Sin actividades registradas hoy. Aquí verás medicamentos tomados, check-ins y notas." |
| **Datos incompletos — sin teléfono del adulto mayor** | El botón "Llamar a [nombre]" aparece **deshabilitado** con texto "Sin teléfono" — nunca oculto (el familiar debe entender por qué no puede llamar, no solo notar que falta el botón). Se resuelve cuando el adulto mayor completa su teléfono en Configuración del Check-in (§3.2), que sincroniza automáticamente. |
| **Estado "esperando respuesta"** | Hero en ámbar, pulso animado, "Alarma sonó a las [hora]" — no es todavía una alerta, es informativo. |

### 6.3 Pantalla: Alerta activa (modo rojo)

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Pantalla completa en rojo de emergencia. Ícono distinto según el origen: 🆘 si es pánico, ⚠️ si es check-in perdido. Mensaje distinto: *"[nombre] activó el botón de pánico."* vs *"[nombre] no respondió el check-in de hoy."* Botón grande "Llamar a [nombre]" (directo al teléfono del adulto mayor, no a un guardián) — solo visible si hay teléfono configurado. Debajo, "Coordinar con el círculo:" con botones para llamar a cada guardián. |
| **Nunca** | Esta pantalla nunca compite con o retrasa la posibilidad de que el familiar llame directamente al 123 por su cuenta — la app no reemplaza esa decisión, solo la facilita. |

### 6.4 Pantalla: Mensaje para Alicia

| | |
|---|---|
| **Dueño** | 👤 |
| **Qué ve** | Diálogo: "Mensaje para [nombre]" + explicación "Alicia leerá este mensaje en voz alta a tu familiar." + campo de texto (3–5 líneas) + campo opcional "Tu nombre" + botón "Enviar a Alicia". |
| **Datos incompletos** | Botón deshabilitado si el mensaje está vacío. El nombre del remitente es opcional — si se deja vacío, se usa "Tu familiar". |
| **Después de enviar** | El diálogo se cierra sin confirmación adicional — no hay "mensaje enviado con éxito" porque no es necesario (guardrail: minimizar notificaciones no esenciales, incluso las de confirmación). |

---

## 7 · Anexo

### 7.1 Patrón de estados usado en todo el núcleo

Todas las pantallas que dependen de red siguen el mismo patrón de 4 estados, sin excepción:

1. **Idle/Neutral** — recién entrando, nada en curso, controles habilitados según validación de campos (nunca deshabilitados "porque sí").
2. **Cargando** — una operación real está en curso; máximo 15s antes de pasar a Error si no hay respuesta.
3. **Listo** — éxito, muestra el resultado.
4. **Error** — mensaje específico (no genérico "algo salió mal") + acción de recuperación siempre visible (Reintentar, o instrucción concreta).

### 7.2 Pendiente para la siguiente pasada

- Aplicar esta paleta y patrón de tarjeta-ícono a Juegos, Notas, Alicia (chat) y Zonas Seguras.
- Definir el flujo de permiso de notificaciones denegado (§3.7).
- Revisión legal del aviso de privacidad (ya marcado como pendiente en el código).
