# Vidrio y puntos

El sistema visual de las apps desde el 24-10-2026 (El Forzudo 1.10, El Ahorrador 1.4). Las reglas están en [`docs/IDENTIDAD.md`](../../docs/IDENTIDAD.md) → ADN visual y Vidrio y puntos.

| Carpeta | Qué hay |
|---------|---------|
| `lienzo/` | Las pantallas del estudio que aprobó Angel (`*.dc.html`, del lienzo https://claude.ai/artifact/MDA52AGbTrQ1iaLDSfHQRy, página "Vidrio y puntos (estudio 3)") y `generar.py`, que las arma con los colores de cada tema. Usa direcciones `/_blob/…` del lienzo: sirve para regenerarlas allá, no para abrirlas sueltas. |
| `personajes/` | El Forzudo y El Ahorrador en las tintas de cada tema (SVG y PNG), hechos desde `identidad/personajes/` cambiando sus colores. |
| `ilustraciones/` | `generar.py` modela y renderiza en Blender (Cycles) el trofeo, la mancuerna, la moneda, la pila de monedas y el sobre, en gris sobre fondo transparente; los PNG quedan en `png/`. En las apps van como `ilustracion_*.webp` (384 px, en gris) y se tiñen con `Ilustracion`. Uso: `blender --background --factory-startup --python identidad/vidrio/ilustraciones/generar.py -- <carpeta> [nombre…]`. |

Las letras están en `identidad/fuentes/` (Bricolage Grotesque y Doto, licencia OFL). En la app van fijas en un grosor: Bricolage en 400, 500, 600 y 700, y Doto en negrita con los puntos redondos (`ROND` 100), solo con los caracteres latinos.
