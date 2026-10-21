# Herramientas de El Forzudo

Sirven para comprobar que la versión Kotlin hace exactamente lo mismo que la versión web original (`index.html` del repo `1015Angel/My-gym`).

| Archivo | Qué hace |
|---------|----------|
| `extraer.js` | Saca los datos (ejercicios, programas, modelo del cuerpo, fotos; ya no los estándares) del `index.html` a `src/main/assets/datos/` |
| `datos-prueba.js` | Genera un estado de prueba determinista (≈100 entrenos, pesos, marcas de semana, entreno activo y un estado "de la nube") |
| `web-salida.js` | Corre la lógica **original** de la web con DOM simulado y fecha fija (11 oct 2026) y escribe los resultados |
| `comparar.py` | Compara los resultados de la web con los de Kotlin, campo por campo |
| `estandares.py` | Genera `estandares.json` con la tabla de fuerza de Fogón (ver `../ESTANDARES.md`) |
| `percentiles-opl.py` | Saca los percentiles de OpenPowerlifting que calibran esa tabla |

```
node herramientas/datos-prueba.js src/main/assets/datos /tmp/prueba.json
node herramientas/web-salida.js /ruta/a/index.html /tmp/prueba.json > /tmp/salida-web.json
../../gradlew :apps:forzudo:testDebugUnitTest -Pprueba=/tmp/prueba.json -Psalida=/tmp/salida-kotlin.json
python3 herramientas/comparar.py /tmp/salida-web.json /tmp/salida-kotlin.json   # debe decir: diferencias: 0
```

Resultado el 11 de octubre de 2026: **0 diferencias** (rachas, semanas, metas, récords, rangos por ejercicio, músculo y general, estancamiento, alternativas, volumen, unión con la nube y formatos).
