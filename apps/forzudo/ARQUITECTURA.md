# El Forzudo por dentro

Lo común a todas las apps (lenguajes, patrón, compilar) está en [`docs/ARQUITECTURA.md`](../../docs/ARQUITECTURA.md). Aquí va lo propio de El Forzudo.

## 1. En una frase

Un cuaderno de entrenos de gimnasio en Kotlin + Compose. Guarda todo en un JSON en el teléfono, calcula rachas, metas y rangos de fuerza, y si la persona quiere, sincroniza con un Gist privado de su GitHub.

Es la reescritura de una versión web anterior (repo `1015Angel/My-gym`, un solo `index.html`). La lógica se tradujo función por función y se comprobó contra la web: dan los mismos resultados. Hasta la versión 1.2.1 se llamó **Mi Gym**.

## 2. Mapa de archivos

Código en `src/main/java/io/github/fogonapps/forzudo/`:

| Archivo | Qué hace |
|---------|----------|
| `ForzudoApp.kt` | Arranca una sola vez por proceso: crea el almacén (DataStore), la caja fuerte del token, la biblioteca de datos fijos y el canal de notificaciones. |
| `MainActivity.kt` | La única pantalla de Android. Monta el tema, conecta exportar/importar con el selector de archivos, pide el permiso de notificaciones al empezar el primer entreno, guarda al salir y sincroniza al volver. |
| `Textos.kt` | Pasa los textos de `strings.xml` que usa la lógica al `Vocabulario`. |
| `GymViewModel.kt` | El cerebro. Tiene el estado, la navegación (pestaña + pila de pantallas), el entreno en vivo, el descanso y la sincronización. Todo cambio pasa por `cambiar { }`. |
| `Avisos.kt` | La notificación de "descanso terminado": alarma del sistema (`AlarmManager`) y un `BroadcastReceiver` que la muestra aunque la app esté cerrada. |
| `datos/Modelo.kt` | Las clases de datos (`Estado`, `Sesion`, `Serie`, `Rutina`, …) y la configuración del JSON (`JsonGym`). |
| `datos/Almacen.kt` | Guardar el estado en disco (`SerializadorEstado`, `crearAlmacen`) y la `CajaFuerte` del token cifrado. |
| `datos/Biblioteca.kt` | Carga los datos fijos de `assets/datos/` y los tiene a mano (ejercicios por id, programas, músculos y qué ejercicios tienen lámina). |
| `datos/Vocabulario.kt` | Las palabras de la lógica (meses, unidades, consejos). Por defecto en español, como la web; la app lo llena con el idioma en uso. |
| `datos/Logica.kt` | Todos los cálculos (ver sección 5). Clase `Logica(biblioteca)`, sin Android: se prueba en la JVM. |
| `datos/Herramientas.kt` | Calentamiento (`calentamiento`), discos por lado (`discos`) y medallas (`Medalla`, `medallas`): cálculos puros, probados en `HerramientasTest`. |
| `datos/Gist.kt` | `ClienteGist`: habla con la API de GitHub para buscar, crear, leer y escribir el Gist. |
| `ui/App.kt` | El armazón: barra de pestañas, la pantalla de arriba de la pila (también Apariencia, que es `PantallaApariencia` de `core/identidad`), la barra del entreno en curso, la del descanso con su medidor (`MedidorDescanso`), avisos y tutorial. |
| `ui/Pestanas.kt` | Las 4 pestañas: Inicio (cabecera con la racha gigante, semana, historial), Entrenar (rutinas), Rangos (el rango general gigante, mapa del cuerpo, rangos por músculo) y Perfil (peso, datos, sincronización, Apariencia, ajustes). Inicio y Rangos ponen su propia cabecera; las otras dos llevan el título que pone `App.kt`. |
| `ui/Pantallas.kt` | Las pantallas que se abren encima: entreno en vivo, resumen, detalle de ejercicio, músculo, programas, editor de rutinas, cambiar ejercicio, lámina en grande, Acerca de, Licencias y Preguntas frecuentes. |
| `ui/Comunes.kt` | Piezas de interfaz propias de esta app: secciones, filas, gráficas, insignias de rango, mapa del cuerpo, mapa anual, campos de número y fecha. |
| `ui/Medallas.kt` | Las 16 medallas en Perfil (`SeccionMedallas`) y las nuevas al terminar un entreno (`MedallasNuevas`). |
| `ui/Tutorial.kt` | Los 6 pasos del primer arranque y, al final, "¡Dale vida!" (`PasoDaleVida` de `core/identidad`) para elegir el estilo. |
| `ui/Iconos.kt` | Íconos de contorno de tinta y cuerpo de la segunda tinta (`Dibujo`, se pintan con `IconoRiso`), también los de los grupos musculares. Los comunes salen de `IconosFogon`. |

Otros lugares:

| Lugar | Qué hay |
|-------|---------|
| `src/main/assets/datos/` | Datos fijos en JSON: `ejercicios.json` (65 ejercicios), `programas.json` (6 programas), `estandares.json` (tabla de fuerza), `rangos.json` (Hierro → Diamante), `musculos.json`, `modelo-cuerpo.json` (polígonos del cuerpo) y `laminas.json` (ejercicios con lámina). |
| `src/main/assets/laminas/` | Las láminas riso de los ejercicios (`<id>-1.webp` inicio, `<id>-2.webp` final), CC BY-SA 4.0. Un ejercicio sin lámina muestra su ícono. |
| `src/main/assets/licencias/` | Textos MIT del modelo del cuerpo. Los comunes están en `core/identidad`. |
| `src/main/res/` | Ícono, muñequitos del tutorial y de la barra, temas y reglas de respaldo. |
| `src/test/` | `EquivalenciaWebTest`: corre la lógica con datos de prueba para compararla con la web. |
| `herramientas/` | Scripts del PC (ver sección 7). `herramientas/laminas/` genera las láminas riso. |
| `ESTANDARES.md` | De dónde salen los números de los rangos de fuerza. |
| `fastlane/metadata/android/` | La ficha de la app para F-Droid: textos, ícono, capturas y novedades de cada versión. |
| `fdroid/` | Borrador de la receta de F-Droid (ver `docs/PUBLICAR.md`). |

## 3. Los datos

**Todo lo de la persona** es un objeto `Estado` guardado como JSON en `files/datastore/estado.json`, dentro del espacio privado de la app:

| Campo | Qué es |
|-------|--------|
| `v` | Versión del formato (2). Es el mismo formato de la versión web. |
| `updatedAt` | Hora del último cambio, en milisegundos. Decide qué lado gana al sincronizar. |
| `sessions` | Los entrenos terminados: fecha, rutina y, por ejercicio, la lista de series (`kg`, `reps`, `done`). |
| `body` | Los registros de peso corporal (fecha → kg). |
| `routines`, `program`, `progEdited` | El programa elegido y las rutinas, con los cambios que haya hecho la persona. |
| `active` | El entreno en curso. **No** se sube a la nube ni se exporta. |
| `deleted` | Ids de entrenos borrados, para que la sincronización no los traiga de vuelta. |
| `weekMarks` | Marcas de semana de la versión web (ya no se marcan a mano). |
| `nombre` | El nombre de la persona (nuevo; la web no lo tenía). |

- **Si el archivo se daña,** se guarda una copia `estado-dañado-<hora>.json` y se empieza de cero, sin cerrar la app.
- **El token de GitHub** no va en el `Estado`: va cifrado en las preferencias `sincronizacion`, con la llave `forzudo-token` del Keystore.
- **La Apariencia** (estilo, colores, comodidad) va en las preferencias `apariencia`, la maneja `core/identidad` y no viaja en el respaldo.
- **Preferencias de interfaz** (por ejemplo, si ya vio el tutorial): en las preferencias `ui`.

## 4. Cómo fluye todo

Ejemplo: la persona marca una serie como hecha.

1. La pantalla llama a `vm.marcar(id, i)`.
2. El ViewModel usa `cambiar { }`: copia el estado con la serie marcada y pone `updatedAt` en ahora.
3. Eso dispara tres cosas:
   - las pantallas se redibujan, porque escuchan `vm.estado`;
   - 300 ms después se guarda en disco;
   - 4 s después se sincroniza, si hay token.
4. Además arranca el descanso: se guarda la hora de fin y se programa la alarma de `Avisos`.

Al salir de la app (`onStop`) se guarda de inmediato. Al volver (`onStart`) se sincroniza.

**Sincronización** (`GymViewModel.sincronizar`):
1. Busca el Gist que tenga el archivo `mi-gym-datos.json`. Si no existe, lo crea privado.
2. Lo lee y lo une con el estado local mediante `Logica.unir`:
   - gana el lado con `updatedAt` más reciente;
   - los entrenos y pesos de los dos lados se suman;
   - los borrados se respetan;
   - el entreno en curso siempre es el del teléfono.
3. Guarda el resultado y lo sube.

Los errores se muestran en palabras simples: token vencido, sin permiso o sin conexión.

## 5. La lógica (`datos/Logica.kt`)

- **Racha:** una semana cuenta cuando se completan todas las rutinas del programa. No se puede marcar a mano.
- **Meta de cada serie** (`meta`): según lo que se hizo la vez anterior y el rango de repeticiones de la rutina, dice "sube el peso", "busca +1 rep" o "mantén".
- **1RM estimado:** fórmula de Epley, `kg × (1 + reps / 30)`. Solo series de 20 repeticiones o menos.
- **Rangos de fuerza:**
  1. El mejor 1RM de cada ejercicio se compara con 5 umbrales (`estandares.json`), escalados a tu peso corporal.
  2. Eso da un puntaje de 0 a 6 y un rango: Hierro, Bronce, Plata, Oro, Platino o Diamante, cada uno con divisiones III, II y I.
  3. El rango de un músculo es el promedio de sus 2 mejores ejercicios.
  4. El rango general es el promedio de los músculos.
  5. De dónde salen los umbrales: [`ESTANDARES.md`](ESTANDARES.md).
- **Estancamiento:** si un ejercicio lleva 3 sesiones sin subir, sugiere alternativas del mismo músculo.
- **`normalizar`:** arregla un estado recién leído (de disco, Gist o respaldo) sin borrar nada. Por ejemplo, convierte el formato viejo de semanas.

## 6. Funciones especiales

| Función | Cómo | Permiso |
|---------|------|---------|
| Aviso de fin de descanso | `AlarmManager.setExactAndAllowWhileIdle`. Si no hay permiso de alarma exacta, usa una aproximada. | `POST_NOTIFICATIONS` (se pide al empezar el primer entreno), `SCHEDULE_EXACT_ALARM`, `VIBRATE` |
| Sincronizar con el Gist | OkHttp contra `api.github.com`, con el token de la persona (permiso `gist`). | `INTERNET` (solo se usa si la persona conecta GitHub) |
| Exportar / importar | Selector de archivos del sistema (`CreateDocument` / `OpenDocument`). | Ninguno |
| Widget de la racha | Glance (`Widget.kt`: `WidgetRacha`): semanas seguidas y entrenos de esta semana, con el tema elegido. Se actualiza al guardar (`GymViewModel.guardarYa`) y cada hora. Perfil tiene un botón para ponerlo. | Ninguno |
| Respaldo de todo Fogón | `Datos.kt`: `DatosForzudo` (un `ProveedorDatosFogon`) da el respaldo (sin el entreno en curso) y lo restaura conservando el entreno en curso. Si la app está abierta, `GymViewModel` ve el cambio en el disco y lo toma (`ultimoGuardado` distingue lo que escribió ella misma). | Permiso de firma `io.github.fogonapps.permiso.DATOS` |
| Videos de técnica | Abre una búsqueda de YouTube ("<ejercicio> tecnica correcta") en el navegador. La app no reproduce nada. | Ninguno |

## 7. Pruebas y herramientas

| Qué | Cómo |
|-----|------|
| Lógica igual a la web | `herramientas/` (ver su README): se generan datos de prueba, se corre la web original y la versión Kotlin, y `comparar.py` busca diferencias. Desde la 1.2 solo difieren los rangos, a propósito, por la tabla nueva. |
| Tabla de fuerza | `herramientas/estandares.py` genera `estandares.json`. `percentiles-opl.py` saca los percentiles de OpenPowerlifting. |
| Calidad | `lintDebug`: hoy quedan 12 avisos menores conocidos y ningún error. |
| En un teléfono | Emulador `fogon-prueba` (Android 16) y el S24. La lista de lo que se prueba en cada versión está en el `README.md` → Versiones. |

## 8. Trampas conocidas

- **El archivo del Gist se sigue llamando `mi-gym-datos.json`.** Es a propósito, para encontrar los datos de la web y de Mi Gym. Si se cambia, la gente pierde la sincronización.
- **Cambiar el identificador crea una app nueva.** Por eso Mi Gym → El Forzudo necesitó exportar e importar. Con la app ya publicada, no se vuelve a cambiar.
- **`extraer.js` ya no escribe `estandares.json`.** La tabla sale solo de `estandares.py`.
- **En mancuernas se anota el peso de una.** Los umbrales con `db: 1` están pensados así. Las máquinas (`m: 1`) son aproximadas.
- **El APK tiene que salir idéntico** al compilarlo en otra máquina (F-Droid lo comprueba). No hay que meter en la compilación nada que cambie entre máquinas o entre repos: fechas, rutas, el commit (`vcsInfo.include = false`). Para comprobarlo, ver `docs/PUBLICAR.md` → F-Droid.
- **La lógica no lee `strings.xml`:** usa `Vocabulario.actual`. Si agregas una palabra a la lógica, va en el `Vocabulario`, en `Textos.kt` y en los dos `strings.xml`. Así `EquivalenciaWebTest` sigue comparando en español contra la web.
- **Al cambiar de idioma** se vuelven a armar la biblioteca y la lógica (`ForzudoApp.aplicarIdioma`) y se recrea la Activity. Las rutinas que la persona ya tiene guardadas conservan su nombre: son sus datos.
- **`proguard-rules.pro`** protege las clases `@Serializable`. Si se mueve el paquete, hay que actualizarlo; si no, la versión firmada no lee los datos (la de prueba sí, y el error no se nota hasta publicar).
- **Las fotos viejas (hasta la 1.3) no son libres y siguen en el historial de git.** El repo público se sube con un historial nuevo, sin ellas (ver `docs/PUBLICAR.md`).
- **Una lámina nueva** se agrega en `herramientas/laminas/ejercicios.py` y se regenera con `laminas.py` sin lista de ids, para que también se actualice `laminas.json`. Si falta en `laminas.json`, la app no la muestra.
