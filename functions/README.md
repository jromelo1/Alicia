# Cloud Functions — Llamada diaria de Alicia

Dos funciones (`src/index.ts`):

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
