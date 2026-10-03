# REDXela

Simulador del centro de distribución REDXela (Sistemas Operativos 2). Ver `docs/PROPUESTA.md` para el diseño.

## Requisitos
Java 21, Maven 3.9+, Node 20.19+.

## Desarrollo (recarga en caliente del frontend)
```bash
cd backend && mvn exec:java -Dexec.mainClass=com.redxela.RedxelaApplication   # API en :7070
cd frontend && npm install && npm run dev                                      # UI en :5173
```

## Ejecución como una sola aplicación
```bash
cd frontend && npm install && npm run build   # copia la UI a backend/src/main/resources/static
cd ../backend && mvn package                  # genera target/redxela.jar
java -jar target/redxela.jar                  # http://localhost:7070
```

## Pruebas
```bash
cd backend && mvn test
cd frontend && npm test
```

## Solución de problemas

**`Unresolved compilation problem` / `X cannot be resolved` al correr los tests del backend.** La extensión de
Java de VS Code (`redhat.java`) y Maven comparten la carpeta `backend/target/`. Si el editor deja clases
compiladas a medias, Maven las da por buenas y los tests fallan aunque el código esté bien. Se arregla
borrando lo compilado y volviendo a correr:

```bash
rm -rf backend/target/classes backend/target/test-classes
cd backend && mvn test
```
