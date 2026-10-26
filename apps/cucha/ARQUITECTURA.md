# La Cucha por dentro

Lo común a todas las apps (lenguajes, patrón, compilar) está en [`docs/ARQUITECTURA.md`](../../docs/ARQUITECTURA.md). Aquí va lo propio de La Cucha.

## 1. En una frase

Un cuaderno de comidas en Kotlin + Compose. Guarda lo anotado en un JSON en el teléfono, trae una base de alimentos y recetas en `assets/datos/`, lee el peso de El Forzudo por el proveedor de Fogón y solo usa internet para Open Food Facts si la persona lo activa.

## 2. Mapa de archivos

Código en `src/main/java/io/github/fogonapps/cucha/`:

| Archivo | Qué hace |
|---------|----------|
| `CuchaApp.kt` | El almacén (DataStore) y la `Logica` con la base (se lee de `assets/datos/` la primera vez que hace falta). |
| `MainActivity.kt` | La única pantalla de Android: tema, respaldo e importar recetas con el selector de archivos, el escáner (`ScanContract` de zxing-android-embedded), abrir Anotar desde el widget, leer El Forzudo al volver a la app y guardar al salir. |
| `CuchaViewModel.kt` | El estado, la navegación (pestaña + pila de pantallas, `Pantalla`) y todas las acciones. Todo cambio pasa por `cambiar { }`. |
| `Datos.kt` | `DatosCucha`: el proveedor de datos para el respaldo de todo Fogón, y `leerRespaldoCucha`. |
| `Widget.kt` | `WidgetCalorias` (Glance): las calorías que quedan, la proteína y el botón Anotar. |
| `datos/Modelo.kt` | Las clases de datos (`Estado`, `Alimento`, `Receta`, `Anotado`, `Producto`, `Perfil`, `Metas`…) y `JsonCucha`. |
| `datos/Logica.kt` | `Base` (alimentos y recetas de assets), `normal()` para buscar sin tildes, `Comible` (alimento, receta o producto) y `Logica`: metas, valores, buscar, menú, mercado, racha y promedio. Sin Android: se prueba en la JVM. |
| `datos/Externos.kt` | `leerForzudo` (el peso y los entrenos del JSON de El Forzudo) y `OpenFoodFacts` / `leerProductoOff`. |
| `datos/Almacen.kt` | Guardar el estado en disco. |
| `ui/App.kt` | El armazón: barra flotante con el botón de anotar, pestañas, pila de pantallas (`PilaAnimada`), avisos y confirmaciones. `Acciones` son las cosas que solo hace la actividad. |
| `ui/Tutorial.kt` | Bienvenida, datos (`DatosPersona`), metas (calculadas o propias) y "¡Dale vida!". |
| `ui/Hoy.kt` | Pestaña Hoy: anillo, macros, El Forzudo, agua y las cinco comidas. También `nombreComida`, `nombreDia`, `BotonIcono` y `Pildora`. |
| `ui/Anotar.kt` | Buscar (`Anotar`), cuánto se comió (`Cantidad`, también corrige y borra), de dónde salen los valores (`Fuente`) y el producto propio (`EditarProducto`). |
| `ui/Recetas.kt` | Pestaña Recetas, ver una receta y crearla o corregirla. |
| `ui/Plan.kt` | Pestaña Menú (semana A/B) y la lista de mercado. |
| `ui/Progreso.kt` | Racha, promedio, barras de 14 días y la curva del peso (dibujadas con `Canvas`, animadas). |
| `ui/Perfil.kt` | Perfil, metas a mano, idioma, Acerca de y Licencias. |
| `ui/Comunes.kt` | Piezas: vidrio, filas, campos, selector, `Anillo`, `BarraMacro`, `cifra()` y `numero()`. |
| `ui/Iconos.kt` | Los íconos propios (olla, calendario, gráfica, lupa, código, plato…). |

Otros lugares:

| Lugar | Qué hay |
|-------|---------|
| `src/main/assets/datos/alimentos.json` | La base: 397 alimentos con nombre en español e inglés, grupo, número FDC, descripción de USDA, valores por 100 g y porciones. **Se genera** (ver GUIA). |
| `src/main/assets/datos/recetas.json` | Las 55 recetas de ejemplo, escritas a mano. |
| `herramientas/alimentos/` | `lista.py` (qué alimentos, de qué número FDC y con qué porciones) y `generar.py` (saca los valores de los CSV de USDA). |
| `src/test/` | `LogicaTest` (base, metas, buscar, recetas, mercado, racha, El Forzudo, Open Food Facts) y `TraduccionTest`. |

## 3. Los datos

Todo lo de la persona es un `Estado` en `files/datastore/estado.json`. Fechas `"AAAA-MM-DD"`.

| Campo | Qué es |
|-------|--------|
| `listo`, `nombre`, `perfil`, `metas` | Si pasó el tutorial, su nombre, los datos para calcular (sexo, edad, altura, peso, actividad, objetivo y ritmo) y las metas (`manual` si las puso a mano). |
| `dias` | Fecha → `Dia`: lo anotado (`Anotado`: comida, de dónde salió en `ref` — `a:`, `r:` o `p:` —, cantidad, detalle y **sus valores ya calculados**) y el agua en ml. Guardar los valores hace que el historial no cambie si después se corrige la base o una receta. |
| `recetas`, `productos` | Las recetas propias (una de ejemplo corregida se guarda aquí con el mismo id y tapa a la original) y los productos propios o de Open Food Facts. |
| `plan`, `alternar` | El menú: `"A|1|almuerzo"` → id de receta (1 = lunes); con `alternar`, las semanas impares del año son B. |
| `mercadoHecho`, `mercadoExtra` | Lo tachado en la lista de mercado y lo agregado a mano. |
| `pesos` | El peso anotado aquí, solo si no está El Forzudo. |
| `codigoBarras`, `favoritos` | Si se busca en Open Food Facts, y los favoritos. |

- **La Apariencia y el idioma** van en preferencias aparte, como en todas las apps.

## 4. Cómo fluye todo

Anotar una arepa:

1. El botón del centro abre `Anotar` en la comida que toca por la hora (`comidaAhora`).
2. `Logica.buscar` busca "arepa" en recetas, productos y la base, sin tildes, también en plural ("huevos" encuentra "huevo"), por el nombre en el otro idioma y por sus otros nombres (`Alimento.otros`).
3. `Cantidad` muestra lo que aporta según la porción y la comida elegidas; `vm.anotar` guarda un `Anotado` con sus valores. Si vino de la búsqueda, vuelve a ella en blanco para sumar otro ingrediente (huevos, después champiñones): arriba, `LlevasEn` muestra lo que va en esa comida con su total, "Listo" (vuelve a Hoy) y "Guardar como receta" (abre `EditarReceta` con esos alimentos y sus gramos). Si vino de otro lado (el menú de Hoy, corregir), vuelve a Hoy.
4. 300 ms después se guarda en disco y se actualiza el widget.

## 5. La lógica (`datos/Logica.kt`)

- **Metas:** Mifflin-St Jeor (10·peso + 6,25·altura − 5·edad + 5 en hombres, −161 en mujeres) por el factor de actividad (1,2 a 1,9), más o menos `ritmo × 7700 / 7` kcal al día para bajar o subir. Proteína 1,8 g/kg (2,0 al bajar), grasa el 25 % de las calorías, carbohidratos el resto, agua 35 ml/kg. Redondeado a 10 kcal. Son guías generales (lo dice el tutorial y el aviso de salud).
- **Valores:** alimentos y productos por gramos (`por100 × g / 100`); recetas por porciones (suma de ingredientes ÷ porciones, o `porPorcion` si se pusieron a mano).
- **Mercado:** por cada día de la semana, la receta de cada comida del menú aporta una porción de sus ingredientes; se suman por alimento y se ordenan por grupo.
- **Racha:** días seguidos con algo anotado hasta hoy (o hasta ayer, si hoy aún no hay nada).

## 6. Funciones especiales

| Función | Cómo | Permiso |
|---------|------|---------|
| El Forzudo | `RespaldoFogon.leerDe` (core) lee el respaldo de El Forzudo por su proveedor protegido con el permiso de firma; `leerForzudo` saca los pesos y los entrenos. Se lee al volver a la app; si el peso cambió y las metas son automáticas, se recalculan. | Permiso de firma `io.github.fogonapps.permiso.DATOS` |
| Código de barras | `ScanContract` abre el lector; `OpenFoodFacts.buscar` pide `api/v2/product/<código>.json` (con `User-Agent` propio, como pide Open Food Facts) y `leerProductoOff` lo convierte. Siempre se revisa antes de guardar (`EditarProducto`). | `INTERNET`, cámara (la pide el lector al usarlo) |
| Respaldo de todo Fogón | `DatosCucha` da y restaura el `Estado`; si la app está abierta, el ViewModel lo toma al verlo cambiar en el disco. | Permiso de firma |
| Widget | Glance (`WidgetCalorias`), se actualiza al guardar y cada hora. | Ninguno |

## 7. Pruebas y herramientas

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:cucha:testDebugUnitTest :apps:cucha:lintDebug
```

`LogicaTest` lee los JSON reales de `assets/datos/`: si la base o las recetas cambian, la prueba revisa que los ids sean únicos, que las calorías cuadren con los macros y que cada ingrediente exista.

## 8. Trampas conocidas

- **El lector de códigos (zxing-android-embedded 4.3.0) viene apaisado:** el manifiesto lo deja girar (`tools:replace` de `screenOrientation`). La biblioteca no se actualiza desde 2021; si un día falla, se cambia por ZXing core con CameraX.
- **Los valores de las recetas son con alimentos cocidos de USDA** (la pechuga asada, la papa cocida…), aunque la receta empiece con crudos: es aproximado, y así lo dice la app.
- **Las porciones caseras son pesos típicos,** no medidos: quien pesa la comida usa gramos.
