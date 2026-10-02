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
