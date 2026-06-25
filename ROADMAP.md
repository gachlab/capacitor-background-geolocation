# Roadmap — @gachlab/capacitor-background-geolocation

**Contexto:** Plugin de Capacitor para tracking de conductores. Consume un backend propio en drivers-web.
Verticals: flotilla corporativa + rideshare/taxi. Pain point #1: el tracking se cae en background.
Prioridad de plataforma: Android primero.

> **Estado (v1.6.7):** Kotlin/Swift rewrites, E2E infra, background reliability, geofencing, idle detection, trip scoring, priority sync, y driving-events heuristics están completos. v1.6.1–1.6.7 fueron patches de estabilidad (build AGP 9.x, HTTP -1 en Android, template resolver, force-unwrap en iOS BackgroundSync) — ver CHANGELOG. La deuda activa es: integration tests (P1) y Clean Architecture v2.0 (P2).
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
buffer/          ← PositionBuffer (UMA/L2): caché en RAM zero-copy entre bloques  ★ NUEVO
config/          ← ConfigureUseCase, ConfigRepository (port)
sync/            ← SyncUseCase, LocationPublisher (port), SyncPublisher (port)
adapters/        ← SqlPositionRepository, SqlJourneyRepository, WorkManagerSyncPublisher, HttpLocationPublisher
```

**Rename npm:** `@gachlab/capacitor-background-geolocation` → `@gachlab/geolocation` (v2.0.0).

**Prerequisito:** integration tests completos (P1) deben estar verdes antes de empezar el refactor para que sirvan como red de seguridad.

---

## Migración a Apple Silicon — fases (todo minor sobre 2.x)

El modelo del [`ARCHITECTURE.md`](./ARCHITECTURE.md) (SoC: dominio puro + UMA + chiplets) no se entrega de un salto. Se migra del **hub gordo + SQLite-como-bus** actual al modelo de **bloques pares + buffer UMA** de forma incremental. Dos reglas:

- **Red en cada salto.** Los integration tests (P1) afirman comportamiento; cualquier re-cableado que rompa algo se cae en CI. Sin P1 verde, el refactor es a ciegas.
- **ISA libre de mejorar, no congelada.** Hay un solo consumidor (`drivers-web`, superficie mínima: location/error + 6 métodos). Romper la ISA para una mejor interfaz es aceptable **si** se actualizan tests + `drivers-web` en el mismo PR. No hay nuevo major: **todo esto se entrega como minors sobre 2.x.**

| Fase | Qué | Por qué en ese orden | Riesgo | Entrega |
|---|---|---|---|---|
| **0 — Red** | = P1 integration tests verdes, gate 70/70/80 | Sin red, el resto es a ciegas | Bajo | — |
| **1 — Dominio puro** | Sacar `Position·Journey·Trip·GeoEvent` de `BGFacade`/detectores a `domain/` (sin I/O) | Vocabulario compartido *antes* de compartir memoria; código puro = casi cero riesgo | Bajo | minor (2.x) |
| **2 — Buffer UMA** ★ | `PositionBuffer` en RAM entre bloques y SQLite; los bloques leen last-fix/trip del buffer, no re-consultan DAO | El corazón: zero-copy entre bloques, deja de round-trip a disco para que dos bloques se hablen | Medio | minor (2.x) |
| **3 — Hub → ports** | Reemplazar cableado de `LocationService`/`BGFacade` por ports; extraer `sync/` como primer chiplet | Solo posible tras UMA+dominio; `sync/` primero = IP block reusable del futuro `capacitor-event-sink` | Medio | minor (2.x) |
| **4 — Die web** | Re-basar `web.ts` sobre el mismo `domain/` (TS compartido) | Cierra paridad **de datos**, no solo de comportamiento; web deja de ser monolito aislado | Bajo | minor (2.x) |
| **5 — Fabric** (futuro) | Formalizar SSE como bus de coherencia/invalidación | Ya existe en backend; multi-device sigue out-of-scope (coherencia entre dies) | — | — |

### Política de flush del UMA (modelo Transistorsoft)

El buffer **no** es write-behind a disco. Lección del líder de batería: el disco es barato, la **red** es cara (un HTTP POST gasta mucha más energía/seg que el GPS). Por eso:

1. **Disco eager/durable.** Cada fix se persiste a SQLite de inmediato; el UMA es caché de *lectura/compartición* entre bloques, no un diferidor de escrituras a disco. La recuperación tras kill (`getBackgroundKillReason` / `START_STICKY`) no cambia.
2. **Write-behind en la red, batcheado.** El event-sink (`sync/`) acumula y sube por lotes (estilo `batchSync` + `autoSyncThreshold`).
3. **Flush-on-motion-transition.** Vaciar la cola al pasar a *stationary* (antes de que el SO duerma el proceso) y al volver a *moving* (reportar el cambio ya).
4. **Write-through para lo crítico.** `possibleCrash`/`sos` siguen bypaseando el caché (priority sync) — nunca esperan al batch.

**Riesgo único que importa (Fase 2):** un fix en RAM sin persistir ante un kill. Mitigado por (1) disco eager arriba y (4) write-through de eventos de seguridad.

---

## Motor de movimiento unificado — Tier ISP/AOP (aprendizajes de Transistorsoft)

**No copiamos Transistorsoft; adoptamos su modelo de operación.** Su breadth genérica (headless JS, SDK de propósito general) no nos interesa — nuestro diferenciador es el Neural Engine (driver insights), que ellos no tienen. Pero su **motor de movimiento** lleva una década puliéndose y resuelve justo nuestro pain point #1.

**El problema es por-die, no uniforme** (verificado en código):

- **Android (el gap real):** el estacionario del `DistanceFilterLocationProvider` usa **`AlarmManager` para polling** (`stationaryAlarmPI` / `stationaryLocationPollingPI`) — y **Doze throttlea AlarmManager**, que es exactamente por qué "el tracking se cae en background". El gating por `STILL` vive aparte en el Activity provider. Nunca se combinan.
- **iOS (casi resuelto):** el `DistanceFilterLocationProvider.swift` **ya** duerme sobre `stationaryRegion: CLCircularRegion` + `startMonitoringSignificantLocationChanges` + gating por `CMMotionActivityManager`. Eso **ya es** el modelo de Transistorsoft; el único pendiente es consolidar los 3 providers en una máquina de estados, no el backstop (que ya existe).
- **Web (no aplica):** el navegador no tiene background — `watchPosition` corre solo con la pestaña viva. No hay motor que arreglar; **Transistorsoft tampoco targetea web**, así que no hay lección que tomar. Ver "El die web no tiene AOP" abajo.

**El modelo target (lo que Transistorsoft demostró que funciona):** una sola máquina de estados **moving / stationary** en el tier ISP/AOP del SoC:

| Pieza | Reemplaza | Por qué |
|---|---|---|
| **Geofence nativa de stationary como backstop** | el polling por `AlarmManager` | el SO la monitorea con **cero CPU de app**; sobrevive Doze. Salir ~200m re-engancha GPS aunque el motion sensor falle |
| **Gating por motion API** (ActivityRecognition / `CMMotionActivity`) | provider Activity separado | GPS **apagado** en stationary; el acelerómetro (bajo consumo) decide cuándo encenderlo |
| **`stopTimeout`** (debounce moving→stationary) | nada (transición abrupta) | evita apagar GPS en un semáforo; solo entra a stationary tras N min quieto confirmados |

**Back-compat:** `locationProvider` (Raw/DistanceFilter/Activity) se mantiene en la ISA como hint, pero internamente rutea al motor unificado. Se entrega como **minor sobre 2.x** (un consumidor → libre de evolucionar la semántica interna).

**Por qué es prioritario y no solo "otra mejora":**
1. **Ataca el pain point #1** directamente (background reliability) — es lo que más duele al negocio.
2. **El Neural Engine depende de esto.** Un hard-brake o phone-usage-jitter calculado sobre fixes de un provider que despierta tarde = falsos positivos. Arreglar el AOP **mejora la precisión del scoring**, no solo la batería. Los dos moats se refuerzan.
3. **Es el tier ISP/AOP del SoC** (`ARCHITECTURE.md`): debe quedar sólido *antes* de que el Neural Engine compute encima. Va antes o en paralelo a las fases de dominio/UMA.

**Reparto de trabajo por die:**

| Die | Trabajo | Esfuerzo |
|---|---|---|
| **Android** | Reemplazar `AlarmManager` polling por **geofence nativa de stationary** (reusa `GeofencingClient`, ya en el repo) + unificar 3 providers en 1 máquina de estados | **alto** — es el gap real |
| **iOS** | Backstop ya existe (`CLCircularRegion` + significant changes + `CMMotionActivity`); solo **consolidar** 3 providers → 1 estado | bajo |
| **Web** | Nada en el motor. A lo sumo throttle de `watchPosition` por distancia en foreground | n/a |

### El die web no tiene AOP

En el modelo SoC, iOS y Android son chips **con isla always-on** (AOP / E-cores): computan con el CPU principal dormido. **El web die no tiene esa isla** — solo ejecuta con la pestaña en foreground (`navigator.geolocation.watchPosition`, *"tracking runs only while the page is alive"*). Por eso la paridad de web **no es del motor de movimiento** (imposible en el sandbox del navegador) **sino del contrato/datos** (la ISA). El web die debe degradar con honestidad: reportar `backgroundTracking: false` vía feature-detection (ligado al `getCapabilities()` propuesto) en vez de fingir un motor que no puede existir.

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
