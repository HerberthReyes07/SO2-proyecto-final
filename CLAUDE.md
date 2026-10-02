# REDXela — Proyecto final de Sistemas Operativos 2

Simulador de un centro de distribución: planificación, sincronización, semáforos/monitores, prevención y
detección de deadlock, comunicación entre procesos y administración de almacenamiento.
Enunciado: `reference/Proyecto Final.md`. Diseño acordado: `docs/PROPUESTA.md`. Entrega: 27-oct-2026.

## Estructura
- `backend/` — Java 21 + Maven + Javalin 6. Paquete raíz `com.redxela`.
- `frontend/` — React + TypeScript + Vite (Atomic Design). El build se copia a `backend/src/main/resources/static`.

## Comandos
- Backend: `cd backend && mvn test` · `mvn package` (JAR único: `target/redxela.jar`)
- Frontend: `cd frontend && npm run dev` (proxy a :7070) · `npm test` · `npm run lint` · `npx tsc -b` · `npm run build`
- Producción local: `npm run build` y luego `java -jar backend/target/redxela.jar` → http://localhost:7070

## Convenciones (obligatorias)
- **Código en inglés; comentarios y Javadoc en español.** Textos visibles al usuario en español.
- Código limpio y modular, sin código espagueti: clases pequeñas con una responsabilidad, métodos cortos,
  nombres que revelan intención, sin números mágicos (constantes con nombre o configuración).
- Todo comentario debe explicar el *por qué* (sobre todo decisiones de sincronización); no repetir el *qué*.
- Manejo explícito de excepciones; nunca `catch` vacío. Las interrupciones se propagan o restauran el flag.
- **Backend:** el núcleo (`domain`, `resources`, `processing`, `orders`, `catalog`, `customers`, `scheduling`,
  `inventory`, `sim`, `stats`) es Java plano; solo `api` conoce Javalin. `ApplicationComponents` es la raíz de
  composición (lo único que conoce a todos). Dependencias hacia el dominio, nunca al revés. Estado compartido:
  o es inmutable, o se protege con un lock documentado. Tests de concurrencia deterministas (latches, esperas con timeout),
  corridos varias veces para detectar intermitencias.
- **Frontend:** Atomic Design (`atoms` → `molecules` → `organisms` → `templates` → `pages`). Un átomo no importa
  moléculas ni superiores. Los componentes de presentación no hacen fetch ni abren sockets (eso va en
  `hooks/`/`services/`). Lógica reutilizable en funciones puras de `utils/`. Estilos con CSS Modules y solo
  tokens de `styles/tokens.css`. Cada componente en su carpeta con `index.ts`, estilos y test.
- Tests con cada funcionalidad nueva. No marcar algo como terminado sin ejecutar los tests.
