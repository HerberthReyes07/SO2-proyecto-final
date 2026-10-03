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

## 3. Niveles de servicio, lista de espera y políticas de atención

El tiempo objetivo funciona como deadline: `deadline = llegada + objetivo del nivel`.

| Política | Clave de orden (menor = primero) | Qué demuestra |
|---|---|---|
| FIFO | número de llegada | Línea base |
| Prioridad estricta | nivel, luego llegada | Inanición del nivel 5 |
| Prioridad con envejecimiento | `llegada + nivel × 10 min` | Evita la inanición |
| EDF | `llegada + objetivo del nivel` | La tabla del enunciado ya son deadlines |
| SJF por artículos | total de artículos, luego llegada | Libera estaciones rápido, castiga pedidos grandes |

Cada política es un `Comparator<QueuedOrder>`, intercambiable en caliente (patrón Strategy), sin preemption.
Cuando dos pedidos empatan, siempre desempata el número de llegada.

**Todas las claves son independientes del tiempo actual.** El envejecimiento se suele escribir como
`nivel − espera/T`, pero eso depende de "ahora" y el orden cambiaría *mientras* se ordena (los algoritmos de
ordenamiento pueden fallar con comparadores inestables). Como "ahora" aparece igual a ambos lados de la
comparación, se cancela: `nivelA − (ahora − llegadaA)/T < nivelB − (ahora − llegadaB)/T ⇔ llegadaA + nivelA·T <
llegadaB + nivelB·T`. Así el envejecimiento es una clave fija por pedido.

### Lista de espera y despachador

- `WaitingList`: lista (no cola de prioridad) protegida por un `ReentrantLock` con una `Condition notEmpty`.
  Elegir al mejor es un recorrido O(n) con la política vigente, lo que hace inmediato el cambio de política en
  caliente; una cola de prioridad tendría que reconstruirse.
- `Dispatcher` (un hilo): **decisión tardía**. Espera a que haya algún pedido y algún encargado libre y *solo
  entonces* elige al mejor. Si eligiera antes, un Exprés que llega mientras no hay encargado quedaría detrás de uno
  ya elegido. Esperar capacidad no ocupa recursos (`ResourcePool.awaitAvailableUnit()`, que no toma la unidad).
- El despachador es el único que asigna encargados, así que entre "hay uno libre" y "lo tomo" nadie puede
  quitárselo. Los encargados limitan los pedidos en proceso a 10.
- Cada pool de recursos ordena a sus esperas con la misma política (`OperationOrdering`), de modo que un Exprés
  tampoco espera detrás de un Económico dentro de una estación.
- Si el procesador está cerrado, el despachador devuelve el encargado y el pedido a la lista y se detiene
  (en vez de girar sin parar).
- El snapshot envía solo los primeros 50 pedidos en espera más el total, para que no crezca sin medida.

**Benchmark (paso 8):** mismo generador con semilla fija, reloj acelerado, una corrida por política. Métricas:
espera promedio por nivel, % que cumple su objetivo, espera máxima, throughput y utilización de recursos.

### Generador automático de pedidos

Se separa **qué** llega de **cuándo** llega:

- `WorkloadGenerator` decide, de forma **determinista a partir de una semilla**, qué pedido llega (nivel según la
  mezcla 5/15/35/25/20 %, cliente de ese nivel, 1–3 productos distintos, cantidades 1 + exponencial con tope 30) y
  cuánto "trabajo" falta hasta la próxima llegada. No sabe de hilos ni de relojes, así que el benchmark (paso 8)
  puede correr cada política con exactamente la misma carga.
- `OrderGenerator` (un hilo) decide el momento. Las llegadas son un proceso de **Poisson**: el tiempo entre pedidos
  es exponencial. Para que un cambio de intensidad se note de inmediato (con intensidad baja el intervalo ya
  sorteado puede ser de horas simuladas) la espera se hace en tajadas de 10 s simulados, cada una consume del
  trabajo sorteado a la intensidad vigente (*time-rescaling* de un proceso de Poisson no homogéneo).
- Espera con `SimClock.sleep`, así que respeta la pausa y la velocidad. Mientras está deshabilitado duerme en una
  `Condition`; la configuración se protege con un `ReentrantLock`.
- Intensidad en pedidos por minuto simulado, entre 0,05 y 20. Medido con las duraciones y la mezcla por defecto,
  el sistema despacha unos 2 pedidos/min como máximo (lo limitan los 10 encargados): "Alta" (1,5) queda cerca del
  límite y "Saturación" (3) lo supera, de modo que la lista de espera crece.
- Las semillas por defecto son pequeñas porque JavaScript solo representa sin pérdida enteros hasta 2^53.

### Estadísticas

- Cada pedido despachado produce un `CompletedOrder` (espera en la lista, tiempo en proceso, nivel). Se entrega a
  `SimulationStatistics` mediante un *listener* del procesador, así `processing` no depende de `stats`.
- **Espera** = desde que llega hasta que se le asigna encargado. **Proceso** = desde que se le asigna encargado hasta
  que termina (incluye esperas por recursos). Un pedido está **a tiempo** si su espera es ≤ el objetivo de su nivel.
- Por nivel: despachados, espera promedio y máxima (la máxima revela inanición), proceso promedio y % a tiempo;
  además totales y rendimiento (pedidos por minuto simulado).
- Un `ReentrantLock` protege los contadores juntos para que un snapshot nunca vea, por ejemplo, la cantidad ya
  incrementada pero la suma todavía sin actualizar.
- **Ocupación de recursos:** cada `ResourcePool` integra "unidades ocupadas × tiempo" en cada concesión y liberación
  (exacto, no por muestreo) y lo mide con el reloj simulado, de modo que una pausa no cuenta como tiempo ocioso.
  "Reiniciar estadísticas" reinicia los contadores y la ocupación a la vez.

## 4. Tipos de mercancía y flujo de un pedido

Todo pedido recorre cuatro **etapas** en orden: picking → inspección → empaque → despacho. Cada recurso pertenece
a una etapa (`ResourceType.stage()`), y el orden de las etapas coincide con el orden global de recursos.

| Etapa | Recursos |
|---|---|
| Picking | Encargado de bodega (siempre) + montacargas (si la mercancía lo requiere) |
| Inspección | Estación de control de calidad |
| Empaque | Estación de empaque |
| Despacho | Escáner + área de carga |

La **receta** de un tipo de mercancía es la lista de recursos que usa (sin contar al encargado):

| Tipo | Recursos |
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

### Modelo de retención: traspaso entre etapas (`OrderWorkflow`)

1. El **encargado** se retiene durante todo el pedido (admisión).
2. Dentro de una etapa, los recursos se piden en orden global y se retienen mientras se espera el siguiente
   (*hold-and-wait*, una de las cuatro condiciones de Coffman).
3. Al pasar de etapa se adquieren primero los recursos de la siguiente y **solo entonces** se sueltan los de la
   anterior (traspaso). La mercancía no puede quedar "en el aire": el montacargas no se libera hasta que la
   estación de empaque aceptó el pedido.
4. Pase lo que pase (interrupción, expulsión, error) un `finally` libera todo lo retenido.

Entre pedidos nunca hay espera circular, porque ninguno pide un recurso de rank menor al que ya retiene.
El deadlock de la demo ocurre entre áreas (Recepción vs Despacho, ver sección 7).

**Trabajo por etapa** = `unidades × tiempo por unidad de la etapa` (`ProcessingTimes`, configurable). Valores por
defecto (segundos simulados por unidad): picking 8, inspección 15, empaque 20, despacho 5. A ×60, un pedido
estándar de 10 unidades tarda ~5,5 s reales.

**Estado observable (`OrderExecution`):** un registro inmutable en un campo `volatile`. Solo el hilo del pedido
lo reemplaza; el hilo que arma los snapshots lee una foto coherente sin locks. El progreso se calcula a partir del
reloj simulado, así que se congela al pausar.

## 5. Recepción → Inventario

Son dos áreas independientes que **solo se comunican por mensajes**: ninguna toca las estructuras de la otra.

### Recepción (`ReceptionService`)

Cada camión que llega es una operación (un hilo) que toma el escáner y un andén de carga, descarga y registra la
mercadería (1 s simulado por unidad), los suelta y recién entonces informa a Inventario con un `ReceptionMessage`.
La interfaz permite registrar varios camiones iguales a la vez, para provocar una ráfaga.

- **Suelta los recursos antes de reportar.** Si los retuviera mientras espera espacio en el buffer, un Inventario
  lento dejaría el único escáner ocupado por camiones que ya terminaron de descargar y paralizaría los despachos.
  Así un buffer lleno solo hace esperar al camión que reporta. Hay un test que lo comprueba y una mutación que lo rompe.
- **Orden de adquisición:** escáner → andén, el orden global, igual que los pedidos. El orden inverso (andén →
  escáner), que provoca el deadlock de la demo, se introduce en el paso 7 junto con su detección y resolución.
- **Prioridad en los recursos:** los camiones van antes que los pedidos en las filas de los pools (`OperationOrdering`).
  La recepción es finita y desbloquea el flujo de stock; si fuera al revés, bajo carga los camiones podrían no llegar
  a atenderse nunca.
- Límites: hasta 1.000 unidades por camión, 50 camiones por solicitud y 100 pendientes en total (una ráfaga no crea
  hilos sin límite).

### El buffer (`ReceptionBuffer`)

Productor/consumidor acotado (capacidad 10) implementado a mano con un `ReentrantLock` y dos `Condition`.

- **Sin pérdida de datos:** si está lleno, el productor *espera*; el mensaje nunca se descarta.
- **Sin bloqueo indefinido:** el consumidor siempre progresa y no depende de nada que el productor retenga (los
  camiones ya soltaron sus recursos), así que el espacio siempre termina liberándose. La espera es interrumpible.
- **Contrapresión visible:** el buffer cuenta los productores bloqueados y la interfaz lo muestra.
- **Dos condiciones:** `notFull` la esperan los productores y `notEmpty` el consumidor; con una sola, cada `signal`
  podría despertar a alguien del otro grupo. Al interrumpirse un hilo que ya había sido señalado, pasa la señal al
  siguiente para no perder el despertar.

### Inventario (`Inventory`, `InventoryWorker`)

- Un hilo propio retira los mensajes a su ritmo (20 s simulados fijos + 1 s por unidad, deliberadamente más lento
  que la llegada de camiones para que se vea llenarse el buffer), suma el stock y **avisa** que cambió el stock de
  ese producto.
- El estado se protege con un `ReentrantReadWriteLock`: las consultas (snapshots, varias por segundo) comparten el
  lock de lectura; las modificaciones, raras, toman el de escritura.
- **Reserva de todo o nada:** un pedido reserva todas sus líneas en una operación atómica, o ninguna. Verificar y
  descontar ocurren bajo el mismo lock, sin hueco entre ambos: no hay sobreventa y ningún pedido retiene stock
  parcial esperando el resto (eliminaría el *hold-and-wait* sobre el inventario).
- Ciclo del stock: `disponible → reservado` al admitir el pedido; al despacharlo las unidades **salen**; si el
  pedido se aborta **vuelven** a disponible (`StockHandling`, un listener del ciclo de vida del pedido).
- Existencias iniciales: 300 unidades de cada producto.

### Pedidos que esperan stock

- El despachador solo admite pedidos **elegibles** (hay stock para todas sus líneas); a uno al que le falta se lo
  salta y pasa adelante el siguiente mejor según la política. Mientras espera no retiene nada.
- Cuando Inventario procesa una llegada avisa al planificador (`stockChanged`), que despierta al despachador para
  que reevalúe la lista. El planificador nunca toca las estructuras de Inventario.
- "Esperando stock" **no es un estado guardado**: se deduce del inventario al armar el snapshot (qué productos y
  cuántas unidades faltan), así nunca queda desactualizado.
- Con la política vigente un pedido grande sin stock puede ser postergado por pedidos chicos que sí tienen; es el
  mismo fenómeno de inanición que SJF, y se verá en el comparador (paso 8).

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
| Despachador | 1 | `WaitingList` (`Lock` + `Condition`) + `ResourcePool.awaitAvailableUnit` |
| Operación de pedido | ≤ 10 en proceso (uno por encargado) | `ResourcePool` (`ReentrantLock` + `Condition`) |
| Camión de recepción | uno por camión (tope 100) | Pools + productor del buffer |
| Consumidor de Inventario | 1 | Buffer acotado (`Lock` + 2 `Condition`) + `ReadWriteLock` |
| Generador de pedidos | 1 | `SimClock` + `Condition` de configuración |
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
