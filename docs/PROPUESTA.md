# REDXela — Propuesta de diseño

Documento base de las decisiones de diseño. Servirá de punto de partida para el manual técnico.
Fecha de entrega del proyecto: **martes 27 de octubre de 2026**.

## 1. Decisiones de stack

| Capa | Decisión | Motivo |
|---|---|---|
| Backend | Java 21, Maven, **Javalin 6.7**, Jackson, JUnit 5 + AssertJ | API muy pequeña (REST + 1 WebSocket); los únicos hilos son los propios, lo que facilita explicar la concurrencia. |
| Frontend | **React + TypeScript + Vite**, **Atomic Design**, CSS Modules, Vitest | UI con muchos paneles en vivo; el tipado evita errores en el contrato JSON. |
| Tiempo real | WebSocket: el servidor empuja un *snapshot* completo cada ~300 ms | Más simple y robusto que enviar diferencias; no depende del orden de los mensajes. |
| Persistencia | JSON para clientes; todo lo demás en memoria | La persistencia no es el foco del curso. |
| Idioma | Código en **inglés**, comentarios y documentación en **español**, textos de UI en español | Acordado con el estudiante. |

**Regla de arquitectura:** el núcleo de la simulación es Java plano (sin anotaciones de framework).
Solo el paquete `api` conoce Javalin. Así el benchmark corre sin servidor y los tests no levantan contexto.

## 2. Reloj simulado

Los tiempos objetivo (1, 10, 30, 60, 120 min) exigen un `SimClock` con factor de aceleración
(1 s real = 1 min simulado por defecto, ajustable ×1 … ×600) y pausa. Todo el sistema consulta ese
reloj; ningún componente usa `System.currentTimeMillis()` para decisiones de la simulación.

## 3. Niveles de servicio y políticas de atención

El tiempo objetivo funciona como deadline: `deadline = llegada + objetivo del nivel`.

| Política | Criterio | Qué demuestra |
|---|---|---|
| FIFO | Orden de llegada | Línea base |
| Prioridad estricta | Nivel, desempate por llegada | Inanición del nivel 5 |
| Prioridad con envejecimiento | `nivel − ⌊espera / T⌋` | Evita la inanición |
| EDF | Deadline más próximo | La tabla del enunciado ya son deadlines |
| SJF por artículos | Menos unidades primero | Libera estaciones rápido, castiga pedidos grandes |

Cada política es un `Comparator`, intercambiable en caliente (patrón Strategy), sin preemption.

- **Decisión tardía:** el despachador obtiene primero un encargado libre y *después* elige el mejor pedido de la cola; así un Exprés que llega mientras tanto no queda detrás de uno ya elegido.
- **Colas de los pools:** cada pool ordena a sus esperas con el mismo comparador; sin esto un Exprés esperaría detrás de un Económico dentro de la estación.
- **Benchmark:** mismo generador con semilla fija, reloj acelerado, una corrida por política. Métricas: espera promedio por nivel, % que cumple su objetivo, espera máxima, throughput y utilización de recursos.

## 4. Tipos de mercancía

La receta es la lista ordenada de recursos que se adquieren incrementalmente y se retienen mientras se trabaja
(origen del *hold-and-wait*). El encargado de bodega se asigna al admitir el pedido.
Trabajo por etapa = `unidades × tiempo por unidad` (configurable).

| Tipo | Recursos (tras el encargado) |
|---|---|
| Estándar | EMP → ESC → CAR |
| Frágil | QC → EMP → ESC → CAR |
| Pesado | MON → EMP → ESC → CAR |
| Perecedero | QC → ESC → CAR |
| Voluminoso | MON → QC → EMP → ESC → CAR |

MON = montacargas, QC = control de calidad, EMP = estación de empaque, ESC = escáner, CAR = área de carga.
Pesado y Voluminoso se disputan MON y EMP (escenario de contención exigido).

El tipo pertenece al producto. Un pedido multi-línea usa la **unión** de las recetas de sus líneas, ordenada
con el orden global; como todas las recetas son subsecuencias de ese orden, la unión queda bien definida.

## 5. Recepción → Inventario

- Buffer acotado productor/consumidor implementado a mano (`ReentrantLock` + dos `Condition`), capacidad ~10.
- Si se llena, Recepción espera con contrapresión (timeout y reintento visibles): sin pérdida de datos ni espera indefinida.
- Inventario consume en su propio hilo, más lento que Recepción. Su estado se protege con `ReentrantReadWriteLock`
  y solo se toca por su API (`reserve`, `consume`, `release`).
- Reservas atómicas de todo o nada: evitan sobreventa y eliminan el *hold-and-wait* sobre el stock.
- Al procesar una llegada, Inventario emite `StockUpdated(product)`; el planificador mueve los pedidos de
  "esperando stock" a "listos" sin tocar las estructuras de Inventario.

## 6. Almacenamiento

Asignación **no contigua**, análoga a la paginación.

- 4 pasillos × 6 posiciones × 5 niveles = 120 ubicaciones de 100 unidades de volumen.
- Bitmap de ubicaciones libres; cada lote guarda su lista de ubicaciones (asignación indexada).
- Fragmentación interna solo en la última ubicación de cada lote.
- Invariante a probar: una ubicación nunca pertenece a dos lotes.

## 7. Deadlock

Escenario natural y realista entre áreas: **Recepción** pide CAR → ESC y **Despacho** pide ESC → CAR.
Dos camiones ocupan las 2 áreas de carga y un despacho sostiene el escáner.

- **Modos:** sin prevención (demo), orden global de recursos (rompe la espera circular) y, opcional, todo-o-nada (rompe *hold-and-wait*).
- **Detección:** hilo detector cada ~500 ms sobre un snapshot consistente. Como los pools tienen varias unidades,
  se usa el algoritmo de detección con matrices Asignación/Solicitud/Disponible, no un simple grafo de espera.
- **Interfaz:** recursos involucrados, quién retiene y quién espera qué, las 4 condiciones de Coffman y una explicación.
- **Resolución manual:** el usuario elige qué operación continúa; la otra recibe `OperationPreemptedException`,
  libera todo y vuelve a la lista de espera (`retries++`, conserva su hora de llegada).

## 8. Arquitectura

```
 React (Vite) <── WebSocket (snapshot) ── api (Javalin) ── REST ──> React
                                              │
     ┌───────────┬──────────────┬─────────────┴──┬────────────┐
    sim       scheduling      resources       inventory      stats
  SimClock    Policies        ResourcePool    Inventory      Metrics
  Generators  WaitingQueue    Registry        Warehouse      Benchmark
              Dispatcher      Detector        ReceptionBuffer
```

| Hilo | Cantidad | Mecanismo clave |
|---|---|---|
| Despachador | 1 | Semáforo de encargados + cola de prioridad |
| Operación de pedido | ≤ 10 | `ResourcePool` (`ReentrantLock` + `Condition`) |
| Operación de recepción | pocas | Pools + productor del buffer |
| Consumidor de Inventario | 1 | Buffer acotado + `ReadWriteLock` |
| Generadores | 2 | `SimClock` |
| Detector de deadlock | 1 | Snapshot bajo el lock del gestor |
| Broadcaster de snapshots | 1 | Lecturas con locks breves |

Los pools son monitores propios y no `Semaphore` porque hay que saber quién retiene y quién espera,
conceder por prioridad y poder expulsar a quien espera.

## 9. Frontend: Atomic Design

```
src/
  components/
    atoms/       Badge, ProgressBar          (sin lógica de negocio)
    molecules/   ResourceCard                (átomos combinados)
    organisms/   ResourcePanel               (secciones completas)
    templates/   DashboardTemplate           (distribución, sin datos)
  pages/         DashboardPage               (conecta datos con presentación)
  hooks/         useSimulationStream         (WebSocket)
  services/      socketUrl, clientes REST
  types/         contrato con el backend
  constants/ utils/ styles/
```

Reglas: un átomo no importa moléculas; los componentes de presentación no hacen fetch ni abren sockets
(eso es de hooks/páginas); la lógica reutilizable vive en funciones puras de `utils/` con tests;
colores y espaciados solo desde `styles/tokens.css`.

## 10. Cronograma

| Semana | Entregable |
|---|---|
| 1–7 oct | Dominio, pools, planificador, tests de consola |
| 8–14 oct | Inventario, almacén, recepción, detector y resolución de deadlock |
| 15–21 oct | UI completa, benchmark, estadísticas |
| 22–26 oct | Pulido, tests, manual técnico y manual de usuario |
