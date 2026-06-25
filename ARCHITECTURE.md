# Architecture — @gachlab/capacitor-background-geolocation

**Modelo mental:** un SoC de clase Apple Silicon construido sobre un **ISA RISC-V
propio**. Son dos ejes distintos, y conviene no mezclarlos:

- **Diseño del SoC → Apple Silicon.** Bloques especializados (IP blocks),
  interconexión estandarizada (ports), una cola de sync que actúa como caché
  write-behind, cores heterogéneos, y un fabric compartido (backend + SSE) que une
  múltiples dispositivos. La mentalidad perf/watt → presupuesto de batería.
- **Propiedad del ISA → RISC-V, no ARM.** Apple Silicon *licencia* el ISA de ARM y
  solo diseña los cores. Nosotros **poseemos** el ISA (`definitions.ts`): no se
  licencia, se puede **extender** con instrucciones de dominio, y —con un solo
  consumidor— se es **libre de romperlo** para mejorarlo. Eso es el modelo RISC-V,
  no el de ARM (que prohíbe extender y bloquea por compatibilidad).

Este documento es el plano oficial; el plan de ejecución vive en
[`ROADMAP.md`](./ROADMAP.md).

> **Por qué esta doble analogía:** el modelo chiplet de Apple Silicon escala y da
> performance porque (1) el trabajo caro se diseña una vez y se reusa, (2) la
> interconexión estandarizada deja intercambiar bloques sin rediseñar el sistema, y
> (3) el caché se replica en cada frontera para minimizar viajes al tier más lento
> (en móvil: disco y red → batería). Y el modelo RISC-V encaja porque nuestro valor
> **no** está en la base (geolocalización genérica, que cualquiera tiene) sino en
> una **extensión propietaria** —la inteligencia de conductor— que montamos sobre
> esa base. RISC-V es justo eso: un ISA base abierto + extensiones de dominio que el
> diseñador define.

---

## Posicionamiento: appliance vertical, no plataforma horizontal

Antes de los diagramas, el norte estratégico que decide en qué juego competimos.

> **Transistorsoft es una plataforma horizontal** — un motor de localización
> genérico, best-in-class, para que *tú* construyas encima. **Nosotros somos un
> appliance vertical** — un aparato de inteligencia de conductor que *resulta* que
> necesita un motor de localización. Son categorías distintas.

El error más caro sería medirnos como "un Transistorsoft peor": en *su* juego (motor
genérico + madurez de mil apps + hardening de OEMs por una década) no les ganamos, y
no hace falta. La división de labores del modelo RISC-V lo hace explícito:

- **Base ISA (commodity):** ser pragmáticos hasta lo aburrido. Igualar o tomar
  prestados patrones probados — adoptamos el *modelo* de Transistorsoft, no su código.
  Nada de Not-Invented-Here aquí.
- **X-extension (el moat):** concentrar toda la originalidad en la inteligencia de
  conductor, donde Transistorsoft no compite.

**El principio que gobierna las prioridades:** *el moat vale exactamente lo que vale
el tier más débil.* Un trip-score calculado sobre fixes de un provider que despierta
tarde no es un score confiable — es basura bien presentada. Por eso "la base es
commodity" **no** excusa una base débil: la confiabilidad de captura (hoy floja en el
AOP de Android) es prerequisito de que el scoring sea creíble. El marco *sube* la
prioridad de cerrar ese tier, no la baja.

**Ventajas estructurales reales** (no aspiracionales): poseemos el ISA → cero costo de
licencia (Transistorsoft la exige para release builds) y control end-to-end del
contrato de datos con el backend propio; inteligencia nativa on-device; web real;
priority-sync de seguridad. **Donde aún perdemos:** madurez/hardening — se gana con
años, no se diseña.

**Una costura a preservar a propósito:** cuando el refactor `domain/` + UMA desacople
el Neural Engine del tier de captura, aparece la opción de alimentar la X-extension
desde *cualquier* fuente de localización (incluso un motor de terceros). No es la
decisión actual —seguimos construyendo lo nuestro— pero diseñar esa costura ahora es
optionality barata; descubrirla tarde es carísima.

---

## Punto de partida — actual vs. target (bottom-up)

Antes del plano target, el estado real hoy. El síntoma que motiva toda la
migración: **el único substrato compartido entre bloques de cómputo es SQLite
(disco)** — `LocationService`, `BGFacade`, `BackgroundSync`, `PostLocationTask`,
`BootReceiver` tocan los DAO directo. No hay buffer en RAM, y el orquestador real
es un hub gordo (`LocationService` ~568 líneas Android · `BGFacade` ~677 iOS) que
cablea providers + detectores + sync a mano.

```
ACTUAL (bottom-up)                          TARGET (bottom-up)
─────────────────────                       ─────────────────────
ISA definitions.ts  (✔ ya sólida)           ISA definitions.ts  (libre de mejorar*)
        ▲                                            ▲
Bridge Plugin.kt/.swift                      domain/ PURO (1 fuente, 3 dies)
 + web.ts (monolito aparte)                         ▲   ports = UltraFusion
        ▲                                    ┌───────┴───────────────────┐
╔═══════╧═══════════════╗                    │ ISP · Neural · Geofence ·  │
║ HUB GORDO             ║                    │ event-sink (P/E lanes)     │
║ LocationService/Facade║                    │   ╔════════════════════╗   │
║ cablea TODO a mano    ║                    │   ║ UMA / L2  (RAM)     ║◀──┼─ pieza
╚═╦══╦══╦══╦══╦══════════╝                    │   ║ zero-copy entre     ║   │  nueva
  ▼  ▼  ▼  ▼  ▼  (bloques aislados)          │   ║ bloques             ║   │
╔════════════════════════╗                   │   ╚═════════╦══════════╝   │
║ SQLite/DAO = bus de     ║  ◀── disco en     └─────────────╫──────────────┘
║ facto entre bloques     ║      cada salto    Memory ctrl → SQLite  [eager/durable]
╚════════════════════════╝                          ▲            ║ write-behind
        ▲                                     OS APIs            ▼ a la RED (batch)
OS location APIs                                          Fabric: backend + SSE
```

\* Ver "Frontera de la ISA" abajo: con un solo consumidor (drivers-web), romper
para una mejor interfaz es aceptable en este paso.

### Delta exacto

| | Actual | Target Apple Silicon |
|---|---|---|
| Comunicación entre bloques | vía SQLite (disco) | **UMA en RAM** (zero-copy) |
| Orquestación | hub gordo cablea todo | bloques pares contra UMA + ports |
| Escritura a disco | en cada salto entre bloques | **eager/durable** una vez (modelo Transistorsoft) |
| Write-behind | — | en la **capa de red** (event-sink), no en disco |
| Dominio | embebido en hub/detectores | **`domain/` puro**, 1 fuente para los 3 dies |
| web.ts | monolito aislado | 3er die sobre el mismo `domain/` |
| ISA | ✔ limpia | mejorable (un consumidor), no congelada |

### El ISA: RISC-V propio (`definitions.ts`)

El contrato se gobierna con el modelo RISC-V, no ARM. Tres consecuencias que de otro
modo parecerían decisiones sueltas y aquí caen de un solo principio:

**1. Base ISA + extensión propietaria (la división que importa).**

| | Base ISA (RV-base) | Extensión "X" propietaria |
|---|---|---|
| Qué | geolocalización genérica | **inteligencia de conductor** |
| Métodos/eventos | location · geofence · sync · permisos · diagnostics | `getTripScore` · `tripEnd.score` · `hardBrake` · `rapidAcceleration` · `sharpTurn` · `possibleCrash` · `phoneUsageWhileDriving` · `idleStart/End` |
| Quién más lo tiene | cualquiera (Transistorsoft, etc.) | **solo nosotros** = el moat (Neural Engine) |
| Disciplina | mantener estándar-ish; podría interoperar con engines probados | libre de innovar agresivo |

La base se mantiene limpia y convencional (incluso podría montarse sobre, o aprender
de, un engine de captura probado sin fricción); la **extensión** es donde se concentra
la diferenciación. Tratar ambas igual diluye las dos.

**2. Libre de romper (no congelado).** `definitions.ts` tiene **un solo consumidor**
hoy: la app principal vía `drivers-web`, superficie mínima (location/error + 6
métodos). RISC-V no tiene el lock-in de compatibilidad de ARM: romper para una mejor
interfaz es aceptable si (1) se actualizan los integration tests en el mismo PR y (2)
se actualiza `drivers-web` en lockstep. La red es **tests + superficie consumida
documentada**, no la inmutabilidad. Con consumidores externos, esta regla se endurece
a "aditiva, nunca rompe".

**3. `getCapabilities()` = el registro `misa`.** En RISC-V cada *hart* implementa el
base + el subset de extensiones que soporte, y lo **declara** vía el registro `misa`.
Nuestros tres dies son harts heterogéneos: el web die implementa la base pero **no** la
extensión de tracking-en-background (no tiene AOP / isla always-on). No es un die roto
— es un hart que declara honestamente qué extensiones implementa. Por eso el consumidor
**pregunta capacidades** (`getCapabilities()`) en vez de hardcodear `if (platform)`:
es leer el `misa` del hart. Esto convierte cada "no-op silencioso por plataforma" en
feature-detection explícita.

---

## Nivel 1 — El "chip": un dispositivo = un SoC

```
╔══════════════════════════════════════════════════════════════════════╗
║  ISA / CONTRATO (RISC-V propio)   definitions.ts                       ║
║  base (geoloc genérica) + X-extension (driver-intelligence)            ║
║  — extensible · libre de romper (1 consumidor) · harts declaran misa — ║
╠══════════════════════════════════════════════════════════════════════╣
║  DOMAIN  (diseño lógico, puro, sin side-effects)                       ║
║  Position · Journey · Trip · TrackingConfig · TripConfig · GeoEvent    ║
╠════════════════ die Android (Kotlin)  ║  die iOS (Swift) ══════════════╣
║                                                                        ║
║   ┌─ ISP ───────┐  ┌─ NEURAL ENGINE ──────┐  ┌─ AOP (always-on) ───┐  ║
║   │ Provider/*  │  │ SensorFusionDetector │  │ LocationService /   │  ║
║   │ Raw·DistFil │  │ DrivingEventsDetector│  │ BGBackgroundTaskMgr │  ║
║   │ ·Activity   │  │ ScoreCalculator·Trip │  │ (recolecta+persiste │  ║
║   └──────┬──────┘  └──────────┬───────────┘  │  con CPU dormido)   │  ║
║      L1: last-fix      L1: Trip en curso     └──────────┬──────────┘  ║
║         │                     │                         │             ║
║   ┌─────┴─────────────────────┴─────────────────────────┴─────────┐  ║
║   │  L2 / UMA  — buffer compartido de posiciones (zero-copy)       │  ║
║   └───────────────────────────┬────────────────────────────────────┘  ║
║                               │                                        ║
║   ┌─ GEOFENCE coproc ─┐   ┌───┴── SLC LOCAL ──────────────────────┐  ║
║   │ GeofenceManager   │   │ write-back buffer → flush por lotes   │  ║
║   └───────────────────┘   └───────────────┬───────────────────────┘  ║
║                                            │                          ║
║   ┌─ MEMORY CONTROLLER (ports) ────────────┴──────────────────────┐  ║
║   │ ConfigRepository · PositionRepository  →  SQLite (DAO/*)       │  ║
║   └────────────────────────────────────────────────────────────────┘  ║
║                                                                        ║
║   ┌─ MODEM + P/E CORES ───────────────────────────────────────────┐  ║
║   │   event-sink chiplet  (cola + sync, write-behind cache)        │  ║
║   │   ┌─ P-core lane ──────────────┐  ┌─ E-core lane ───────────┐  │  ║
║   │   │ PrioritySyncManager        │  │ BackgroundSync          │  │  ║
║   │   │ crash/sos → POST inmediato │  │ batch diferido + RDP    │  │  ║
║   │   │ (bypassa el caché)         │  │ (evicción comprimida)   │  │  ║
║   │   └────────────────────────────┘  └─────────────────────────┘  │  ║
║   └──────────────────────────────────┬────────────────────────────┘  ║
╚══════════════════════════════════════╪═══════════════════════════════╝
                                        │  UltraFusion (interconexión)
```

---

## Nivel 2 — El "board": ecosistema

```
        device-die        device-die        device-die
        (driver app)      (driver app)      (dispatch UI)
            │                 │                 │
            └────────┬────────┴────────┬────────┘
                     ▼                 ▲
        ╔════════════╧═════════════════╧════════════════════════╗
        ║   FABRIC:  backend drivers-web                         ║
        ║   ── escritura: ingest de cola (write-behind landing)  ║
        ║   ── SLC del sistema: read cache (config · geofences · ║
        ║       rutas) servido a N dies                          ║
        ║   ── SSE  = BUS DE COHERENCIA (snoop / invalidación) ──╫──► invalida
        ║   ── DB / cold storage = main memory                   ║    cachés
        ╚═══════════════════════════════════════════════════════╝
```

---

## Nivel 3 — Tabla de equivalencias (la "hoja de specs")

| Bloque Apple Silicon | Rol | Componente real | Caché |
|---|---|---|---|
| **ISA** (RISC-V propio) | contrato propio: base + X-ext, extensible, libre de romper (1 consumidor) | `definitions.ts` | — |
| **Registro `misa`** | un hart declara qué extensiones implementa | `getCapabilities()` (v2.1, las 3 superficies) | — |
| **Diseño lógico** | dominio puro | `domain/` (v2.0) | — |
| **ISP** | captura de sensores | `Provider/*` (Raw, DistanceFilter, Activity) → **target: motor de movimiento unificado** † | L1: last-fix |
| **Neural Engine** (X-extension) | inferencia = el moat propietario | `SensorFusionDetector` · `DrivingEventsDetector` · `ScoreCalculator` · `TripScore` | L1: Trip en curso |
| **AOP** | siempre-encendido, bajo consumo | `LocationService` / `BGBackgroundTaskManager` → **target: sleep en geofence nativa, no AlarmManager** † | — |
| **UMA / L2** | memoria compartida | buffer de posiciones (a introducir) | L2 |
| **Geofence coproc** | coprocesador dedicado | `GeofenceManager` | — |
| **Memory controller** | acceso a almacenamiento | ports → `Persistence/DAO` + SQLite | SLC local (write-back) |
| **Modem + P/E cores** | I/O heterogéneo | `event-sink` (`PrioritySyncManager` + `BackgroundSync`) | **write-behind** |
| **UltraFusion** | interconexión die-to-die | ports `LocationPublisher` / `SyncPublisher` | — |
| **Fabric + SLC sistema** | bus + último caché compartido | backend `drivers-web` + read cache | SLC sistema (TTL) |
| **Snoop bus (MESI)** | coherencia | **SSE** | invalidación |
| **Binning / SKUs** | variantes por config | `GachConfigMapper` (flotilla vs rideshare) | — |

> † **Tier ISP/AOP — aprendizajes de Transistorsoft (gap por-die, no uniforme).**
> El target adopta el *modelo de operación* de Transistorsoft (no su código): una
> máquina de estados moving/stationary con geofence nativa como backstop (cero CPU
> de app) + gating por motion API + `stopTimeout`. Estado real verificado:
> **Android** = el AOP estacionario hace polling por `AlarmManager` (Doze lo
> throttlea → **pain point #1**) → gap real, reemplazar por geofence nativa;
> **iOS** = ya duerme sobre `CLCircularRegion` + significant changes + `CMMotionActivity`
> → backstop ya existe, solo falta consolidar 3 providers → 1;
> **web** = **no tiene AOP** (isla always-on), corre solo en foreground → la paridad
> es del contrato/datos, no del motor (Transistorsoft tampoco targetea web).
> No es solo batería: limpia la entrada del Neural Engine (fixes ruidosos = falsos
> positivos en scoring). Plan por-die en [`ROADMAP.md`](./ROADMAP.md) § "Motor de
> movimiento unificado". Lo que **no** adoptamos: su breadth genérica (headless JS,
> SDK de propósito general) — nuestro diferenciador es el Neural Engine, que ellos
> no tienen.

---

## La jerarquía de caché

El caché no es una caja; es un principio replicado en cada frontera. La regla,
igual que en silicio: **minimizar viajes al siguiente tier más lento-y-grande.**
La jerarquía por costo/latencia:

```
RAM del bloque → buffer compartido → SQLite → red/backend → DB
   (L1)             (L2/UMA)        (SLC local)  (write-behind)  (main mem)
```

| Nivel | En el sistema | Política | Win |
|---|---|---|---|
| **L1** | estado caliente en RAM por bloque (Trip en curso, last-fix) | volátil | latencia cero para scoring |
| **L2 / UMA** | buffer de posiciones recientes compartido entre scoring, geofence y sync | write-through al buffer | evita re-serializar = batería |
| **SLC local** | buffer en memoria que se flushea por lotes a SQLite | write-back | menos I/O de disco = batería |
| **event-sink** | la cola offline **es** un caché write-behind hacia el backend | write-behind + batch | sobrevive sin red, absorbe picos |
| **SLC sistema** | read cache en backend: config, geofences, rutas | read cache con TTL | una lectura de DB sirve a N dies |

**Tres decisiones que esto fuerza:**

0. **El disco es eager, no write-behind.** Lección del líder de batería
   (Transistorsoft): cada fix se persiste a SQLite *de inmediato* — el disco es
   durable, no se difiere. El ahorro real de batería **no** viene de retrasar
   escrituras a disco (I/O de disco es barato), sino de batchear la **red**: un
   HTTP POST consume mucha más energía/segundo que el GPS. Por eso el UMA/L2 es
   un **caché de lectura/compartición** entre bloques (zero-copy, evita
   re-serializar), no un write-behind a disco. El write-behind real vive un tier
   más abajo, en la red.

1. **La cola de sync es un caché, no "una cola con retry".** Su política de
   evicción es decisión de caché: batch grande en WiFi/cargando; write-through
   inmediato para `possibleCrash`/`sos` (priority sync = la línea que *bypassa*
   el caché por criticidad). **Flush-on-motion-transition** (también de
   Transistorsoft): vaciar la cola al pasar a *stationary* (antes de que el SO
   duerma el proceso) y al empezar a *moving* (reportar el cambio de estado ya).
   El Path Compression (RDP) del roadmap es *compresión en la evicción* — reduce
   lo que se escribe al tier lento.

2. **SSE es el bus de coherencia.** Cuando el backend cambia una geofence o config,
   un evento SSE invalida el caché de las apps-die — el equivalente software de un
   snoop bus MESI.

---

## Coherencia y el límite del modelo

El caché introduce el problema más difícil del silicio: **coherencia**. Mientras
una posición tenga **un solo escritor** (el dispositivo que la genera), no hay
problema y los cachés son seguros. El día que dos dispositivos escriban el mismo
viaje, se necesita un protocolo de invalidación — y SSE es donde se construiría.
Por eso **multi-device** está marcado out-of-scope en el roadmap: es el problema de
coherencia entre dies, justo lo que UltraFusion resuelve en hardware.

El win de esta arquitectura es **arquitectónico/organizacional** (evolución
independiente, testeo aislado, presupuesto de batería), no el yield económico que
motiva los chiplets en silicio. El presupuesto de batería es el equivalente real al
*power/thermal envelope*: ahí es donde la mentalidad Apple Silicon da decisiones
concretas.

---

## Cómo aterriza en la estructura target (Clean Architecture v2.0)

La estructura de carpetas del roadmap **ya es** el plano de chiplets — solo hay que
nombrarlo así:

```
domain/      → diseño lógico (ISA interna)            [sin caché]
journey/     → use cases = los cores de cómputo       [L1/L2]
buffer/      → PositionBuffer (UMA/L2): caché de       [L2, RAM]
             compartición zero-copy entre bloques
config/      → ConfigRepository (port)                [read cache]
sync/        → event-sink chiplet: LocationPublisher  [write-behind a la red]
             + SyncPublisher (ports = UltraFusion)
adapters/    → PHY ligado a tecnología:
             Sql*Repository (memory controller) ·
             Http/WsLocationPublisher (modem) ·
             WorkManagerSyncPublisher (AOP)
```

**La regla que une todo:** un corte de chiplet es correcto si deja *fabricar el
bloque una vez y fusionarlo en varios productos sin tocar el dominio*. Por eso
`sync/` (event-sink) es el primer chiplet a extraer — pero **después** de P1 (tests
verdes), porque un bloque reusable propaga sus bugs a cada producto que lo fusione.
Es la misma disciplina de silicio: se verifica el IP block antes de hacerlo reusable.
