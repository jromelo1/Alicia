# Cloud Functions — Llamada diaria de Alicia

Dos funciones (`src/index.ts`) y los avisos a la familia (`src/familyNotify.ts`):

- **`dailyCallScheduler`** — corre cada 15 minutos, revisa qué círculos tienen la
  llamada diaria activada y a quién le toca según su hora local, y le pide a
  Retell que llame.
- **`retellWebhook`** — recibe el resumen de cada llamada cuando Retell la
  termina de analizar, y lo guarda en Firestore.

## Antes de desplegar

Estas funciones **no van a desplegar** hasta que hagas, tú mismo, estos tres
pasos — ninguno lo puede hacer un asistente de código por ti:

### 1. Plan Blaze

Las funciones programadas (`onSchedule`) y las llamadas salientes a una API
externa (Retell) requieren el plan **Blaze** (pago por uso) de Firebase, no el
plan gratuito Spark. Actualízalo desde la consola de Firebase → Configuración
del proyecto → Uso y facturación. Requiere una tarjeta de pago — el uso normal
de esta app (una llamada diaria por círculo) cae dentro o muy cerca de la capa
gratuita de Cloud Functions, pero el plan en sí necesita estar en Blaze para
existir.

### 2. El API key de Retell, como secreto

```bash
firebase functions:secrets:set RETELL_API_KEY
```

Te va a pedir el valor por consola — pégalo ahí, nunca lo pongas en un archivo
que se vaya a subir al repositorio.

### 3. El agente y el número, como parámetros

Al desplegar por primera vez, Firebase te va a preguntar el valor de
`RETELL_AGENT_ID` y `RETELL_FROM_NUMBER` (definidos con `defineString` en
`src/index.ts`) y los va a guardar. También puedes crear un archivo
`functions/.env` (ya está en `.gitignore`, no se sube) con:

```
RETELL_AGENT_ID=agent_xxxxxxxxxxxxxxxxxxxx
RETELL_FROM_NUMBER=+1XXXXXXXXXX
```

`RETELL_FROM_NUMBER` tiene que ser un número comprado o importado en Retell —
no cualquier número.

## Configurar el webhook en Retell

Después de desplegar (`npm run deploy` desde esta carpeta, o `firebase deploy
--only functions` desde la raíz), vas a tener una URL como:

```
https://us-central1-alicia-53950.cloudfunctions.net/retellWebhook
```

Pégala en la configuración de tu agente en Retell, como webhook del agente
(o del número), para el evento `call_analyzed` como mínimo.

## Llamar a Latinoamérica (número propio por SIP trunk)

Los números que vende Retell son de EE. UU./Canadá y **no pueden llamar a
Colombia ni a casi toda Latinoamérica** (error `Call country not supported: CO`).
La solución de Retell es conectar un número propio por SIP trunk. Con Twilio:

1. **Comprar un número de Bogotá** — Twilio Console → Phone Numbers → Buy a
   number → Colombia. Pide cédula y un recibo de servicios con dirección en la
   zona del número (regulación colombiana).
2. **Permisos de país** — Voice → Settings → Geo Permissions: activar Colombia
   (y los demás países a los que vas a llamar).
3. **Crear el trunk** — Elastic SIP Trunking → Trunks → Create:
   - *Termination*: elegir un Termination SIP URI (`algo.pstn.twilio.com`) y crear
     una Credential List (usuario y contraseña).
   - *Origination*: agregar `sip:sip.retellai.com`.
   - *Numbers*: asociar el número de Bogotá.
4. **Conectarlo en Retell** — Phone Numbers → + → *Connect to your number via SIP
   trunking*: el número (+57…), el Termination URI de Twilio, el usuario y la
   contraseña. Asignar el agente de Alicia para llamadas salientes.
5. **En `functions/.env`:**
   ```
   RETELL_FROM_NUMBER_LATAM=+57XXXXXXXXXX
   ```
   Con esto, las llamadas a cualquier número que no sea +1 salen desde el número
   colombiano; las de EE. UU./Canadá siguen saliendo desde `RETELL_FROM_NUMBER`.

## Avisos a la familia por SMS (Twilio)

Después de cada llamada, `retellWebhook` le escribe por SMS al contacto de
emergencia y a los miembros del círculo con avisos activados:

| Aviso | Cuándo |
|---|---|
| **Alerta** | Retell marcó la llamada con `alerta = true` (caída, dolor fuerte, maltrato, ideas de hacerse daño…). |
| **No contestó** | La persona no contestó o entró el buzón de voz. |
| **Resumen** | La llamada salió bien. Se apaga con `FAMILY_SMS_SUMMARIES=false`. |

Las fallas técnicas de nuestro lado (sin saldo en Retell, errores de red) **no**
se le avisan a la familia; quedan en `firebase functions:log`. Cada llamada envía
como máximo un aviso, aunque Retell reintente el webhook.

### 1. Twilio
Crea una cuenta en twilio.com, compra un número con SMS y copia el **Account SID**
y el **Auth Token** (Console → Account Info). Para enviar SMS a números de
**Estados Unidos**, Twilio exige registrar el número (A2P 10DLC o verificación
toll-free) — mientras tanto, los SMS a EE. UU. pueden ser bloqueados.

### 2. Configuración en Firebase
```bash
firebase functions:secrets:set TWILIO_AUTH_TOKEN
```
Y en `functions/.env` (no se sube al repositorio):
```
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_FROM=+1XXXXXXXXXX
FAMILY_SMS_SUMMARIES=true
```
`TWILIO_FROM` puede ser el número de Twilio o un Messaging Service SID (`MG…`).
Si `TWILIO_ACCOUNT_SID` queda vacío, no se envían SMS y todo lo demás sigue igual.

### 3. Campos de análisis en Retell
En el agente → **Post-Call Analysis** → agregar:
- `alerta` — tipo **Boolean**. Descripción: *"true si la persona mencionó una
  caída, dolor fuerte, dolor de pecho, dificultad para respirar, que no quiere
  vivir o piensa hacerse daño, maltrato, amenazas, que alguien le quita dinero,
  o si mostró confusión fuera de lo normal. Si no, false."*
- `motivo_alerta` — tipo **Text**. Descripción: *"Si alerta es true, una frase
  corta y concreta en español que describa lo que dijo la persona. Si no, vacío."*

### 4. Desplegar
```bash
firebase deploy --only functions
```

## Cobro mensual con Mercado Pago Suscripciones

Después del registro, la web le pide un correo a la familia y llama a `createSubscription`.
Esa función crea una suscripción mensual en Mercado Pago (`POST /preapproval`, sin plan
asociado, `external_reference` = código del círculo) cuyo **primer cobro es el día en que
terminan los 7 días gratis**, y devuelve el link de pago (`init_point`). Mercado Pago avisa
cada cambio a `mercadoPagoWebhook`, que verifica la firma (`x-signature`) y guarda el estado
en `circles/{id}.billing` (`pending` → `authorized`, o `paused` / `cancelled`).

Configuración:

1. Mercado Pago → Tus integraciones → Crear aplicación (producto: Suscripciones).
2. Credenciales de prueba → copia el **Access Token** y guárdalo como secreto (no lo pegues en ningún chat):
   `firebase functions:secrets:set MP_ACCESS_TOKEN`
3. Webhooks → URL `https://mercadopagowebhook-…run.app` (sale al desplegar), evento
   "Planes y suscripciones". Copia la **clave secreta** que muestra y guárdala:
   `firebase functions:secrets:set MP_WEBHOOK_SECRET`
4. Precios (moneda de tu cuenta, Colombia = COP sin decimales) en `functions/.env`:
   ```
   MP_PRICE_ESENCIAL=79900
   MP_PRICE_DIARIO=139900
   MP_PRICE_PLUS=239900
   BILLING_ENFORCE=false
   ```
5. `firebase deploy --only functions,firestore:rules`

`BILLING_ENFORCE=true` hace que el scheduler deje de llamar a los círculos creados en la
web cuya prueba terminó sin suscripción autorizada. Los círculos de la app Android o sin
plan nunca se bloquean. Las reglas de Firestore impiden que el navegador escriba `billing`
o mueva `trialStartedAt`.

## Cancelar el plan

`cancelSubscription` (botón "Cancelar plan" en cuenta.html) cancela la suscripción en
Mercado Pago y guarda `billing.status = "cancelled"` y `billing.paidUntil`. Durante la
prueba las llamadas se detienen el mismo día; si el mes ya estaba pagado, Alicia sigue
hasta `paidUntil`. Cancelar siempre detiene las llamadas, aunque `BILLING_ENFORCE=false`.
Si se cancela desde Mercado Pago, el webhook hace lo mismo.

## Baja y eliminación de datos

- **"No me llames más" en la llamada:** agrega en Retell → Post-Call Analysis un campo Boolean
  `no_llamar` ("true si la persona pidió que Alicia no la vuelva a llamar"). Si llega en true,
  el webhook pone `callProfile.callEnabled = false` y avisa a la familia (aviso OPTOUT).
- **SMS:** todos llevan "Baja: callmealicia.com/baja". En Mi cuenta cada familiar puede apagar
  los avisos de su número (`circles/{id}.smsOptOut`), y `notifyFamily` los excluye.
- **`deleteMyData`** (botón "Eliminar mis datos" de Mi cuenta): cancela Mercado Pago, borra
  las llamadas en Retell (`DELETE /v2/delete-call/{id}`), borra el círculo completo, los
  `users/{uid}` conectados y sus cuentas de Firebase Auth. Deja `deletions/{circleId}` con la fecha.

## Acento por país

`RETELL_VOICES` en `functions/.env` (p. ej. `RETELL_VOICES=CO=voice_id,MX=voice_id`) elige la
voz de Alicia en cada llamada con `agent_override.agent.voice_id`. El país sale del "País de
origen" y, si no se reconoce, del indicativo del celular. Países sin voz → la voz del agente.

## Reglas de cada plan

`src/plans.ts` aplica lo que prometen los Términos: **Esencial** llama lunes, miércoles y
viernes (hora local) con corte a los 9 min; **Diario** todos los días con corte a los 6 min;
**Plus** todos los días con corte a los 11 min (minutos del plan + 1 de margen, vía
`agent_override.agent.max_call_duration_ms`). El agente recibe `{{max_minutes}}` para
despedirse a tiempo. Los círculos sin plan (app Android) llaman a diario sin corte propio.

## Probar localmente

```bash
npm install
npm run build
firebase emulators:start --only functions
```

El emulador no puede recibir webhooks reales de Retell (no tiene URL pública),
pero sirve para revisar errores de compilación y de lógica antes de desplegar.

## Cosas a verificar tú, no asumidas por el código

- `resolveCallStatus()` en `src/index.ts` clasifica el resultado de la llamada
  por `disconnection_reason` con coincidencias parciales (`.includes(...)`),
  porque la documentación de Retell no lista un enum cerrado. Revisa los logs
  (`firebase functions:log`) tras las primeras llamadas reales y ajusta esa
  función si aparecen valores que no caen en ninguna categoría.
- La verificación de firma usa `Retell.verify()` del paquete `retell-sdk`
  (v6.0.1 al escribir esto) — si Retell cambia esa API en una versión futura,
  el build de TypeScript te lo va a marcar como error de tipos al actualizar
  la dependencia.
