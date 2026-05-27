# Roadmap — @gachlab/capacitor-background-geolocation

**Contexto:** Plugin de Capacitor para tracking de conductores. Consume un backend propio en drivers-web.
Verticals: flotilla corporativa + rideshare/taxi. Pain point #1: el tracking se cae en background.
Prioridad de plataforma: Android primero.

> **Orden de ejecución (fijado 2026-05-26):** P1 Testing → **P2 Confiabilidad en Background** → Geofencing → resto.
> Las dos secciones marcadas "Prioridad 2" **no** son simultáneas: Confiabilidad en Background va **primero**,
> porque cualquier feature nuevo (Geofencing incluido) hereda el bug de "se cae en background" si no está blindado.
> Background confiable es prerequisito de todo lo demás.
>
> **Rol en el ecosistema:** este plugin además aporta su capa de cola SQLite + sync HTTP como base del futuro
> plugin genérico `capacitor-event-sink`; idealmente su foreground service se comparte (un servicio, una cola, un sync).

---

## Completado

### v1.1 — Kotlin Rewrite (Android)

Reescritura completa del core Android en Kotlin puro bajo `com.gachlab.*`. Sin Java en el árbol principal.

**Eliminado:**
- `com.marianhello.*`, `com.josuelmm.*`, `com.evgenii.*`, `ru.andremoniy.*`, `org.apache.*`, `org.chromium.*`
- Dependencias runtime: `gson`, `slf4j`, `logback-android`, `jparkie-promise`, `android-permissions`
- SyncAdapter / AuthenticatorService / ContentProvider (reemplazados por WorkManager)

**Nuevo:**
- `DrivingEventsDetector` — state machine pura Kotlin, sin imports Android, totalmente testeable en JVM
- `BackgroundSync` — WorkManager worker, reemplaza el SyncAdapter/AuthenticatorService complejo
- `LocationDAO`, `SessionDAO`, `ConfigDAO` — SQL inline, sin ORM de terceros
- `OemHelper` — intents de autostart para Xiaomi, Huawei, Oppo, Vivo, Samsung, OnePlus, Asus
- `NotificationHelper`, `BootReceiver`, `BGFacade`, `BackgroundGeolocationPlugin`
- Suite de tests JUnit 5: `DrivingEventsDetectorTest`, `ConfigMapperTest`, `MockTripBuilder`

**Licencia:** todo código nuevo bajo MIT (`SPDX-License-Identifier: MIT / Copyright (c) 2026 gachlab`).

---

## Prioridad 1 — Testing (Línea Base)

Antes de tocar cualquier feature, necesitamos saber qué funciona y qué no. El repo actualmente solo tiene un smoke test en iOS — no hay forma de saber el impacto de un cambio sin tests.

### 1.1 Unit Tests — TypeScript

**Herramienta:** Vitest (ya está en el ecosistema Capacitor, compatible con el build actual)

| Archivo | Qué probar |
|---------|-----------|
| `src/web.ts` | Cada método: los que usan `navigator.geolocation` funcionan; los native-only lanzan `unimplemented` con mensaje claro |
| `src/definitions.ts` | Validar que las interfaces TypeScript matcheen los payloads reales de los eventos (usar `zod` o type assertions) |
| Serialización | Que `null` / `undefined` en campos opcionales no rompa el payload antes del POST (bug ya visto con Traccar) |

```
npm install -D vitest
```

### 1.2 Unit Tests — Android (JUnit 5)

**Herramienta:** JUnit 5 + Robolectric para correr sin emulador

| Clase | Qué probar |
|-------|-----------|
| `LocationMapper` | Conversión `Location` → `PluginCall` → JSON; campos nulos; mock provider flag |
| `BackgroundGeolocationFacade` | Estados: `start()` → `isRunning=true`; `stop()` → `isRunning=false`; doble `start()` no duplica service |
| `BackgroundGeolocationPlugin` | Que cada método del bridge llama la facade correcta y formatea el resultado |

```
# android/build.gradle — ya tiene testImplementation slot, solo agregar JUnit 5
```

### 1.3 Unit Tests — iOS (XCTest)

**Herramienta:** XCTest (ya configurado, solo falta contenido real)

| Clase | Qué probar |
|-------|-----------|
| `BackgroundGeolocationPlugin.swift` | Que `configure()` persiste la config; `start()` con config válida vs. sin config |
| Event bridge | Que `notifyListeners()` se llama con el nombre y payload correcto en cada notificación nativa |
| Authorization flow | Simular `CLAuthorizationStatus` cambiando → verificar que el evento `authorization` llega |

### 1.4 Integration Tests — Android

**Herramienta:** Android Instrumented Tests + `adb emu geo fix` para simular movimiento

**Escenario base (simular un viaje completo):**
1. `configure({ drivingEvents: { enabled: true }, distanceFilter: 10, interval: 1000 })`
2. `start()` → verificar `serviceRestarted` o `start` event
3. Inyectar 10 fixes GPS con velocidad > `minTripSpeed` → verificar `tripStart`
4. Inyectar fix con velocidad > `speedLimit` → verificar `speeding`
5. Inyectar deceleration > `hardBrakeMps2` → verificar `hardBrake`
6. Inyectar fixes con velocidad 0 por > `stoppedDuration` → verificar `stopped` y luego `tripEnd`
7. Verificar que `tripEnd` incluye `{ distance, durationMs }`
8. `stop()` → verificar `stop` event

**Fixture: `MockTripBuilder`**
```kotlin
// Genera una lista de Location que simula distintos patrones
MockTripBuilder()
  .startAt(19.4326, -99.1332)  // CDMX
  .driveFor(distanceKm = 5, speedKmh = 60, fixIntervalMs = 1000)
  .speedUp(to = 120)           // dispara speeding si limit < 120
  .hardBrake()                  // dispara hardBrake
  .idleFor(minutes = 6)         // dispara idleStart/idleEnd
  .build()
```

### 1.5 Integration Tests — iOS

**Herramienta:** XCTest + `CLLocationSimulator` (Xcode 15+) o GPX route files

Mismo escenario que Android pero en Swift. Los GPX files del repositorio de Apple (`Freeway.gpx`, `City.gpx`) sirven como fixture base.

### 1.6 E2E — Background Survival

**Herramienta:** Detox (React Native) o Appium — verificar que el tracking sobrevive a:
- App enviada a background (home button)
- App removida del task switcher
- Dispositivo bloqueado por 5 minutos
- Vuelta al foreground — verificar que los fixes se guardaron en DB

Este es el test más importante dado el pain point #1.

### 1.7 Coverage Gate

Una vez que la suite base esté, agregar en CI:
- TS: 80% coverage mínimo en `src/`
- Android: 70% en `com.gachlab.geolocation`
- iOS: 70% en `BackgroundGeolocationPlugin.swift`

---

## Prioridad 2 — Confiabilidad en Background

El problema más reportado por los conductores. Antes de cualquier feature nuevo, el plugin debe sobrevivir a los matadores de procesos de Android y al ciclo de vida de iOS.

### 2.1 Android — Foreground Service Hardening

El plugin ya tiene `startForeground` y `enableWatchdog`, pero los OEMs agresivos (Xiaomi, Samsung, Huawei) matan procesos incluso con foreground service activo.

**Tareas:**
- Auditar el `BackgroundGeolocationService` contra la lista de comportamientos OEM documentados en `dontkillmyapp.com`
- Mejorar el `enableWatchdog`: actualmente reinicia si no hay update en ~60s; hacerlo configurable (`watchdogIntervalMs`) y que verifique también si el foreground service sigue vivo
- Agregar `foregroundServiceType=location|dataSync` en el manifest para Android 14+ (API 34 ya lo requiere, verificar que esté correcto)
- Config nueva: `restartOnKill: boolean` — reinicia el servicio si el sistema lo termina, independientemente de `stopOnTerminate`

**Evento nuevo:**
```typescript
'serviceRestarted' → { reason: 'watchdog' | 'system_kill' | 'boot' }
```

### 2.2 Android — Headless Task Robusto

El `headlessTask` actual usa un WebView oculto (`JsEvaluator`). En Android 12+ hay restricciones en cómo se puede lanzar una Activity/WebView desde background.

**Tareas:**
- Migrar la ejecución del headless task a un `WorkManager` worker (más confiable que WebView en background)
- Agregar timeout configurable: `headlessTaskTimeoutMs` (default: 30000)
- Si el headless task falla o timeout, reintentar con backoff exponencial
- Exponer resultado del headless task al plugin: `headlessTaskResult(success | error)`

### 2.3 iOS — Background Survival

iOS no tiene headless task equivalente al de Android. Cuando el sistema cierra la app, no hay ejecución JS hasta el próximo launch.

**Tareas:**
- Implementar `BGProcessingTask` (iOS 13+) para replay de locations almacenadas cuando el sistema otorga tiempo de background
- Mejorar el fallback de `saveBatteryOnBackground`: cuando iOS pausa las actualizaciones, usar `startMonitoringSignificantLocationChanges()` como red de seguridad para no perder el hilo del viaje
- Nuevo config: `iosBackgroundFallback: 'significantChanges' | 'regionMonitoring' | 'none'` (default: `'significantChanges'`)
- Evento: `iosFallbackActivated` → `{ reason: string }` — para que la app notifique al conductor que el tracking está en modo de bajo consumo

### 2.4 Diagnóstico de Background Kill

Los conductores no saben por qué se cayó el tracking. El equipo tampoco.

**Nuevo método:**
```typescript
getBackgroundKillReason(): Promise<{ reason: string | null, timestamp: number | null }>
```

Persiste en SQLite el motivo del último kill (OOM, sistema, usuario) para debugging post-mortem.

---

## Prioridad 2 — Geofencing

Crítico para ambos verticals: flotilla necesita saber cuando el conductor sale del depósito o llega a un cliente; rideshare necesita detectar llegada a zona de recogida.

**API nueva:**

```typescript
// Gestión de zonas
addGeofence(zone: GeofenceConfig): Promise<void>
addGeofences(zones: GeofenceConfig[]): Promise<void>
removeGeofence(id: string): Promise<void>
removeAllGeofences(): Promise<void>
getGeofences(): Promise<GeofenceConfig[]>

interface GeofenceConfig {
  id: string;
  latitude: number;
  longitude: number;
  radius: number;            // metros
  label?: string;
  notifyOnEnter?: boolean;   // default true
  notifyOnExit?: boolean;    // default true
  notifyOnDwell?: boolean;   // default false
  dwellMilliseconds?: number; // tiempo mínimo para disparar dwell
  metadata?: Record<string, unknown>; // payload arbitrario para el backend
}
```

**Eventos nuevos:**
```typescript
'geofenceEnter' → { geofenceId, label, location, metadata }
'geofenceExit'  → { geofenceId, label, location, dwellMs, metadata }
'geofenceDwell' → { geofenceId, label, location, dwellMs, metadata }
```

**Implementación:**
- Android: `GeofencingClient` de Google Play Services (bajo consumo, el OS maneja el monitoreo)
- iOS: `CLCircularRegion` vía Core Location (límite: 20 regiones simultáneas en iOS — documentarlo)
- Persistir las zonas en SQLite para que sobrevivan reinicios del servicio

**Integración con trip detection:**
- Config: `drivingEvents.tripStartGeofenceIds: string[]` — iniciar viaje automáticamente al salir de estas zonas
- Config: `drivingEvents.tripEndGeofenceIds: string[]` — terminar viaje al entrar a estas zonas

---

## Prioridad 3 — Driver Behavior Score

Los eventos de conducción ya existen. Falta agregarlos en algo accionable para el fleet manager.

**API nueva:**
```typescript
getTripScore(options?: { sessionId?: string }): Promise<TripScore>

interface TripScore {
  overall: number;           // 0-100
  breakdown: {
    speeding: number;        // 0-100
    hardBraking: number;     // 0-100
    rapidAcceleration: number;
    sharpTurns: number;
    phoneUsage: number;
  };
  events: ScoredEvent[];     // lista de eventos que penalizaron el score
  tripId: string;
  startedAt: number;
  endedAt: number | null;
}
```

**`tripEnd` enriquecido** (sin breaking change — se agrega campo opcional):
```typescript
'tripEnd' → {
  location: Location,
  distance: number,
  durationMs: number,
  score?: TripScore    // incluido si drivingEvents.enabled: true
}
```

**Algoritmo de scoring:** configurable vía pesos:
```typescript
drivingEvents: {
  scoring: {
    speedingWeight: number,      // default: 30
    hardBrakingWeight: number,   // default: 25
    rapidAccelWeight: number,    // default: 20
    sharpTurnWeight: number,     // default: 15
    phoneUsageWeight: number,    // default: 10
  }
}
```

---

## Prioridad 4 — Idle Detection

Conductor parado con motor encendido: KPI crítico en flotilla para control de combustible.

**Config:**
```typescript
drivingEvents: {
  idleThresholdMs?: number;  // tiempo parado para disparar idleStart, default: 300000 (5 min)
  idleEndThresholdMs?: number; // tiempo en movimiento para cerrar el idle, default: 30000
}
```

**Eventos nuevos:**
```typescript
'idleStart' → { location: Location, startedAt: number }
'idleEnd'   → { location: Location, durationMs: number, startedAt: number }
```

Diferencia con `stopped`: `stopped` es instantáneo (velocidad cero); `idleStart` requiere que el conductor lleve X minutos sin moverse durante un viaje activo.

---

## Prioridad 5 — Shift / Work Hours Management

Para flotilla corporativa: privacidad del conductor fuera de horario y ahorro de batería.

**Config:**
```typescript
interface ShiftConfig {
  enabled: boolean;
  schedule: Array<{
    days: Array<0|1|2|3|4|5|6>;   // 0=domingo
    start: string;                  // 'HH:mm'
    end: string;                    // 'HH:mm'
  }>;
  timezone: string;                 // 'America/Mexico_City'
  pauseOutsideShift?: boolean;      // default: true — pausa el servicio
  notifyDriver?: boolean;           // default: true — notificación al iniciar/terminar turno
}
```

**Métodos:**
```typescript
setShift(config: ShiftConfig): Promise<void>
getShift(): Promise<ShiftConfig | null>
isInShift(): Promise<{ inShift: boolean, nextShiftAt: number | null }>
```

**Eventos:**
```typescript
'shiftStart' → { scheduledAt: number }
'shiftEnd'   → { scheduledAt: number, nextShiftAt: number | null }
```

---

## Prioridad 6 — Priority Sync

Los eventos críticos de seguridad no deben esperar a que la cola de sync alcance el `syncThreshold`.

**Config:**
```typescript
prioritySyncEvents?: Array<
  'possibleCrash' | 'sos' | 'hardBrake' | 'speeding' | 'phoneUsageWhileDriving'
>;
// default: ['possibleCrash', 'sos']
```

**Comportamiento:**
- Cuando ocurre un evento en `prioritySyncEvents`, se hace POST inmediato a `url` con el payload del evento + la última location conocida
- No afecta la cola normal de locations
- Retry con backoff: 3 intentos en 10s, 30s, 60s si falla

---

## Mejoras Técnicas

### Path Compression antes de Sync
Aplicar Ramer-Douglas-Peucker en el cliente antes de enviar batch de locations.
- Config: `syncCompression: { enabled: boolean, epsilonMeters: number }` (default: desactivado, epsilon: 10m)
- Reducción esperada: 40-70% en tramos de autopista

### Anti-Spoofing Mejorado
El `mockLocationPolicy` actual detecta el flag del OS. Agregar detección por comportamiento:
- Velocidad imposible: > 400 km/h entre dos fixes consecutivos → flag o drop
- Teleport: distancia > 50 km en < 60 segundos → flag o drop
- Config: `mockLocationPolicy: 'allow' | 'flag' | 'drop' | 'strict'` (strict = ambos chequeos activos)

### Background Kill Diagnostics
Métrica de salud para el equipo de soporte: ¿cuántas veces se cayó el servicio en la última semana?

```typescript
getServiceHealthReport(): Promise<{
  killCount: number,
  lastKillAt: number | null,
  lastKillReason: string | null,
  uptimePercent: number,   // % del tiempo activo en las últimas 24h
}>
```

---

## Testing — Deuda Técnica

El repo solo tiene un smoke test en iOS. Propuesta por capa:

| Capa | Herramienta | Qué cubrir |
|------|-------------|------------|
| Unit TS | Vitest | `web.ts`, serialización de payloads, score algorithm, shift scheduler |
| Unit Android | JUnit 5 | `LocationMapper`, score calculation, geofence evaluator |
| Unit iOS | XCTest | Swift bridge methods, score calculation |
| Integration Android | Instrumented test + `adb emu geo fix` | Simular viaje completo, verificar eventos tripStart/tripEnd/speeding |
| Integration iOS | XCTest + `CLLocationSimulator` | Mismo flujo en iOS |
| E2E | Capacitor test app + Detox | Permiso flow, start/stop tracking, verificar que background survive |

**Fixtures reusables:**
- `MockTripBuilder` — genera arrays de `Location` que simulan distintos patrones de manejo (autopista, ciudad, idle, frenadas)
- `AssertEventSequence` — helper para verificar que los eventos llegan en el orden correcto

---

## Baja Prioridad — Real-Time Delivery (WebSocket)

El HTTP actual funciona bien. Las otras apps del ecosistema ya manejan tiempo real con SSE events del backend. Dejar para cuando el HTTP se convierta en un cuello de botella real.

**Config futura:**
```typescript
deliveryMode?: 'http' | 'websocket';  // default: 'http'
wsUrl?: string;
wsReconnectMs?: number;
wsAuthToken?: string;
```

---

## Out of Scope (por ahora)

- **Route deviation** — requiere que el backend envíe la ruta esperada al plugin; depende de API de dispatching que no existe aún
- **Dynamic speed limits** — requiere integración con OSM/HERE; complejidad alta para el valor actual
- **Multi-device** — problema de negocio más que de plugin

---

## Notas de Plataforma

- **Android primero** en toda implementación nueva
- Cuando un feature no es posible en iOS (ej: headless task nativo), documentarlo explícitamente en el README con alternativa recomendada
- Web fallback: solo location básico vía `navigator.geolocation` — los features avanzados deben lanzar `unimplemented` con mensaje claro
