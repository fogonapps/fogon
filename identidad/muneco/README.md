# El muñeco de Fogón

Un solo muñeco en bloques, hecho en Blender, que hace la acción de cada app. De aquí salen los personajes de las
apps, los muñequitos del tutorial de El Forzudo y **los íconos de las cuatro apps**. Por qué es así y qué se
descartó: `docs/IDENTIDAD.md` → "El muñeco".

| Archivo | Qué es |
|---------|--------|
| `muneco.py` | El modelo y las poses (se corre dentro de Blender). |
| `generar.py` | Corre Blender y escribe todo lo de las apps. |
| `png/` | Los cuatro personajes armados, en grande. |

Necesita Blender 5 (`blender --version`), Python 3 con Pillow y numpy. No hace falta abrir Blender: todo corre en
segundo plano.

## Rehacer todo

Desde la raíz del repo:

```
python3 identidad/muneco/generar.py
```

Tarda unos 15 segundos y reescribe, para las cuatro apps:

- `core/identidad/src/main/res/drawable-nodpi/personaje_<app>_cuerpo.webp`, `_cabeza.webp` y `personaje_<app>.webp`;
- `apps/forzudo/src/main/res/drawable-nodpi/munieco_tut_<paso>_cuerpo.webp` y `_cabeza.webp`;
- `apps/<app>/src/main/res/mipmap-*/ic_launcher_foreground.webp` y `ic_launcher_monochrome.webp` (el ícono);
- `apps/<app>/fastlane/metadata/android/en-US/images/icon.png` (el ícono de la tienda);
- `identidad/muneco/png/<personaje>.png`.

**Las poses que pidió Angel** (no cambiarlas sin que lo pida): El Forzudo de frente con las dos piernas iguales; El Ahorrador como el Sísifo de las portadas, sin rampa; La Cucha sin falda ni gorro; El Cajón saltando con un disco.

Después: compilar, probar en el emulador (tutorial en Grafito y en Claro, el ícono en el cajón de apps), rehacer
las capturas de la tienda que muestren al personaje (ver la `GUIA.md` de cada app) y publicar las apps.

## Ver una pose sin tocar las apps

```
blender -b -P identidad/muneco/muneco.py -- <carpeta> la-cucha el-cajon
```

Deja en `<carpeta>` `<nombre>-luz.png` (el modelo con luz, para ver las caras), `<nombre>-cuerpo.png` y
`<nombre>-cabeza.png`. Sin nombres, hace todos. Nombres: `el-forzudo`, `el-ahorrador`, `la-cucha`, `el-cajon`,
`tut-hola`, `tut-anota`, `tut-racha`, `tut-rangos`, `tut-datos`. Así se arman las propuestas para Angel antes de
cambiar nada en las apps.

## Cambiar algo

Todo está en `muneco.py`:

- **Las proporciones** (igual para todos): `HUESOS` (largo y grosor al empezar y al terminar de cada hueso),
  `HOMBRO`, `CADERA`, `CABEZA` (tamaño de la estrella) y `CARAS` (caras de cada prisma: 5).
- **Una pose:** cada personaje es una función (`el_forzudo`, `la_cucha`…) que llama a `M.armar({...})` con hacia
  dónde apunta cada hueso: `espalda`, `brazo_i`/`brazo_d`, `antebrazo_…`, `muslo_…`, `canilla_…` y `pie_…`, como
  vectores (x a la derecha, y hacia el fondo, z arriba). `"adelante": {"d": 0.12}` acerca ese lado a la cámara.
  **Nunca se agranda una pierna en el modelo:** lo que se vea más grande es por estar más cerca de la cámara.
- **Los objetos** (barra, moneda, cuchara, gorro, disco, cuesta) van en la misma función, después de `armar`,
  con las piezas facetadas `prisma`, `bloque`, `gema` y `disco`, ubicados desde `M.manos`, `M.pies` y `M.cabeza`.
  `restar` hace huecos (la chispa calada de la moneda, el hueco del disco).
- **La cámara:** al final de la parte "Escena" (`cam.data.lens` = 20 mm, cerca y un poco abajo).
- **Una app nueva:** una función con su acción en `muneco.py` y su entrada en `ACCIONES` y `COLOR`; en
  `generar.py`, en `NOMBRES`, `APP`, `COLOR` y `CABEZA_ICONO`; en la app, `@color/icono_fondo` y el personaje en
  `core/identidad` (`CAPAS` en `Componentes.kt`, `CAPAS_…` en `Widget.kt`).

En `generar.py`: el acabado (`capas(…, puntos=True)`: semitono en las apps; en los íconos, `semitono(…, 22)`, con puntos más grandes), los colores de la cabeza (`COLOR` en la app, `CABEZA_ICONO` en el ícono: el lima del Forzudo también, como pidió Angel), el fondo del ícono (`FONDO_ICONO`, blanco) y el tamaño en la zona segura
(64 de 108 dp).

## Si Blender no arranca

El 25-10-2026 una actualización de Arch dejó `opensubdiv` y `usd` pidiendo `libcudart.so.13` sin instalar CUDA, y
Blender no abría. Se arregló volviendo a las versiones anteriores de la caché de pacman (`opensubdiv`, `usd`,
`blender` y `draco`). Si vuelve a pasar: ver qué librería falta (`blender --version`), buscar en
`/var/cache/pacman/pkg/` la versión de antes y comprobar con `readelf -d` que no la pida, antes de proponerle a
Angel el `pkexec pacman -U …`. No usar librerías bajadas de otros lados (PyPI y similares).
