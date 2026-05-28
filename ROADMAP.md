# Roadmap — @gachlab/capacitor-background-geolocation

**Contexto:** Plugin de Capacitor para tracking de conductores. Consume un backend propio en drivers-web.
Verticals: flotilla corporativa + rideshare/taxi. Pain point #1: el tracking se cae en background.
Prioridad de plataforma: Android primero.

> **Estado (v1.6.0):** Kotlin/Swift rewrites, E2E infra, background reliability, geofencing, idle detection, trip scoring, priority sync, y driving-events heuristics están completos. La deuda activa es: integration tests (P1) y Clean Architecture v2.0 (P2).
>
> **Rol en el ecosistema:** este plugin aporta su capa de cola SQLite + sync HTTP como base del futuro plugin genérico `capacitor-event-sink`; idealmente su foreground service se comparte (un servicio, una cola, un sync).

---

## Completado

### v1.6.0 — Crash Confirm + Phone-Usage GPS Heuristic + Web Implementation

- **`crashConfirmWindowMs`**: ventana de confirmación diferida para `possibleCrash`. Elimina falsos positivos por glitch de GPS. Si el vehículo recupera velocidad antes de que expire la ventana, el crash se cancela.
- **`phoneUsageWhileDriving` vía GPS**: heurística de jitter de bearing cuando `sensorFusion: false`. Detecta el patrón de bearing oscilante típico de un conductor mirando el teléfono sin requerir giroscopio. Nuevos campos: `phoneUsageWindowMs`, `phoneUsageCooldownMs`.
- E2E: `e2e-driving-events.sh` con 3 escenarios (crash detection, phone-usage jitter, crash-confirm cancellation) + job CI `android-e2e-driving`.
- Web: `src/web.ts` con location store (in-memory), sessions y sync queue.
- Eliminado: `registerHeadlessTask` de iOS (era no-op; Android sin cambios).

### v1.5.0 — Priority Sync

- POST inmediato para eventos de seguridad (`possibleCrash`, `sos` por defecto).
- Dedup por timestamp, retry con backoff configurable, queue offline.
- Campos: `prioritySyncEvents`, `prioritySyncUrl`, `prioritySyncRetries`, `prioritySyncRetryDelays`.
- Eventos: `prioritySyncSuccess`, `prioritySyncFailed`.

### v1.4.0 — Idle Detection + Trip Scoring

- `idleStart` / `idleEnd` durante viajes activos. Campos: `idleThresholdMs`, `idleEndThresholdMs`.
- Score 0–100 por viaje (`getTripScore()`, `tripEnd.score`). Pesos configurables por categoría.
- Android: `TripScore.kt` + `ScoreCalculator.kt`; iOS: equivalentes Swift.

### v1.3.0 — Geofencing

- `addGeofences`, `removeGeofences`, `getGeofences`, `removeAllGeofences`.
- Eventos: `geofenceEnter`, `geofenceExit`, `geofenceDwell`.
- Android: `GeofencingClient`; iOS: `CLCircularRegion` (límite: 19 zonas de usuario).
- Integración de viaje: `tripStartGeofenceIds` / `tripEndGeofenceIds`.

### v1.2.0 — Background Reliability

- WorkManager headless task en Android (reemplaza JsEvaluator WebView; sobrevive Android 12+ restrictions).
- iOS: `iosBackgroundFallback` config + `iosFallbackActivated` event.
- `getBackgroundKillReason()` en ambas plataformas.

### v1.1.0 — Kotlin + Swift Rewrites, E2E Infrastructure

- Android core reescrito en Kotlin puro bajo `com.gachlab.*`; ObjC removido de iOS → Swift 5.
- `DrivingEventsDetector`: state machine pura Kotlin, sin imports Android, testeable en JVM.
- `OemHelper`: intents de autostart para 7 fabricantes.
- `serviceRestarted` event.
- E2E: `e2e-background-survival.sh` + `android-e2e` CI job.
- Unit tests: JUnit 5 (Android) + XCTest (iOS).

---

## Prioridad 1 — Integration Tests (deuda activa)

Los E2E de fondo (`e2e-background-survival.sh`) y de driving events (`e2e-driving-events.sh`) cubren el happy path end-to-end. Lo que falta es la capa intermedia: tests instrumentados que ejerciten configuraciones y edge cases sin desplegar la app completa.

### Android — Instrumented Tests

**Herramienta:** JUnit 5 + Robolectric / Android Instrumented

| Escenario | Clase |
|-----------|-------|
| Viaje completo: tripStart → speeding → hardBrake → idleStart → idleEnd → tripEnd con score | `DrivingEventsIntegrationTest` |
| `crashConfirmWindowMs`: crash fires, crash cancelled on recovery | `CrashConfirmTest` |
| Geofence enter/exit/dwell | `GeofenceIntegrationTest` |
| Priority sync: POST enviado, dedup, retry | `PrioritySyncIntegrationTest` |
| DB backward compat: abrir DB de v1.0 con schema v1.6 | `DbMigrationTest` |

**Fixture reusable existente:** `MockTripBuilder` (genera `Location` sequences para cualquier patrón).

### iOS — XCUITest Integration

| Escenario | Archivo |
|-----------|---------|
| Viaje completo via GPX route | `TripLifecycleTests.swift` |
| Score calculation end-to-end | `TripScoringTests.swift` |
| iOS geofence enter/exit | `GeofenceTests.swift` |

### Coverage Gate

Una vez que la suite esté completa:
- Android: 70% en `com.gachlab.geolocation` (excluyendo UI y manifest-only classes)
- iOS: 70% en `BackgroundGeolocationCore`
- TypeScript: 80% en `src/`

---

## Prioridad 2 — Clean Architecture v2.0

El código actual tiene `BGFacade`, `ServiceEvent`, `BGConfig` como objetos de dominio implícitos mezclados con infraestructura. Con cada feature nuevo la separación se erosiona. El plan completo está documentado en el repo; aquí el resumen ejecutivo:

**Modelo de dominio target:** `Position`, `Journey`, `Trip`, `TrackingConfig`, `TripConfig`, `GeoEvent`.

**Regla de boundary:** si el comportamiento solo necesita datos que el objeto ya tiene → pertenece al dominio (puro, sin side effects). Si necesita repositorio, publisher o sistema externo → pertenece al use case.

**Estructura target (ambas plataformas):**
```
domain/          ← Position, Journey, Trip, TrackingConfig, TripConfig, GeoEvent
journey/         ← StartJourneyUseCase, EndJourneyUseCase, RecordPositionUseCase, GetCurrentPositionUseCase
config/          ← ConfigureUseCase, ConfigRepository (port)
sync/            ← SyncUseCase, LocationPublisher (port), SyncPublisher (port)
adapters/        ← SqlPositionRepository, SqlJourneyRepository, WorkManagerSyncPublisher, HttpLocationPublisher
```

**Rename npm:** `@gachlab/capacitor-background-geolocation` → `@gachlab/geolocation` (v2.0.0).

**Prerequisito:** integration tests completos (P1) deben estar verdes antes de empezar el refactor para que sirvan como red de seguridad.

---

## Prioridad 3 — Shift / Work Hours Management

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
