# Láminas de los ejercicios

Reemplazan las fotos de los ejercicios, que no son libres (ver `docs/LEGAL.md` §6). Estilo aprobado por Angel el 18-10-2026.

## El estilo: lámina riso

| Capa | Qué es |
|------|--------|
| Fondo | Papel `#EFE7DA` con grano. |
| Sombra | La silueta corrida, en el color de la zona del cuerpo: **rosa** tren superior (pecho, espalda, hombros, abdomen), **amarillo** brazos, **azul** piernas. |
| Figura | Por dentro, el mismo papel del fondo (sin trama ni relleno de color). |
| Músculo | Mancha con el color de su grupo (tabla abajo). |
| Línea | En tinta `#141210`. |
| Paso | "1" (inicio) y "2" (final) en Anton, con la sombra de la zona y la chispa roja. |

| Grupo | Color |
|-------|-------|
| Pecho | `#FF7A1A` naranja |
| Espalda | `#1FB45A` verde |
| Hombros | `#FFC93C` amarillo |
| Bíceps | `#00A0E9` cian (no rosa, porque el rosa es una sombra) |
| Tríceps | `#7B4DFF` violeta |
| Cuádriceps | `#2FC1B2` turquesa |
| Femorales y glúteos | `#E5332A` rojo |
| Pantorrillas | `#B8733A` ocre |
| Abdomen | `#A8D930` lima |

La guía de colores en la app es el mapa del cuerpo de Rangos pintado con estos colores, y siempre con el nombre al lado del color.

## De dónde salen los dibujos

[Everkinetic](https://github.com/everkinetic/data): ilustraciones de inicio y final de unos 270 ejercicios. Su repositorio las publica con **CC BY-SA 4.0**, y su sitio decía en 2012 que las liberaba con Creative Commons para cualquier uso. Por eso:
- las láminas, que son una modificación, también quedan **CC BY-SA 4.0**;
- hay que dar crédito a Everkinetic en la pantalla de Licencias y en el README.

Todas las figuras de Everkinetic son de hombre. Se probó generar versiones de mujer con IA (Stable Diffusion 1.5 + ControlNet, en el PC), pero no quedaban al nivel ni en el mismo estilo, y Angel decidió dejar solo las de hombre. Está explicado en las preguntas frecuentes del README de la app.

Cubre 55 de nuestros 65 ejercicios. Los 10 que faltan, y por qué, están al principio de `ejercicios.py`: Everkinetic no tiene un dibujo que sirva (se revisó todo el banco el 20-10-2026). Necesitan un dibujo propio con licencia libre.

## Cómo se generan

```
# 1. Bajar las ilustraciones (dist/svg del repo de Everkinetic)
git clone --depth 1 https://github.com/everkinetic/data everkinetic
# 2. Generar las láminas de los ejercicios de ejercicios.py
python3 laminas.py everkinetic/dist/svg ../../src/main/assets/laminas
```

Sin lista de ids regenera todas y escribe `assets/datos/laminas.json`, la lista que lee la app. Con una lista (`... bulgara,press-banca`) genera solo esas y no toca `laminas.json`.

Necesita `rsvg-convert` (paquete `librsvg`), Pillow y numpy.

Por cada ejercicio, `ejercicios.py` dice qué SVG usa en cada paso, dónde va la mancha del músculo y qué huecos no hay que rellenar. Las coordenadas son las del `viewBox` del SVG: para encontrarlas sirve dibujar una cuadrícula encima, como se hizo con las muestras.

## Peso

Cada lámina sale a 540 px de ancho en WebP calidad 72: unos 45 KB. Las 110 láminas suman unos 5 MB.
