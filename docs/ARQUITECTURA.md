# Cómo están hechas las apps de Fogón por dentro

Este documento es para cualquiera que quiera leer, arreglar o continuar el código: una persona que sabe programar, una que está aprendiendo o una IA. Explica lo que comparten **todas** las apps. Lo propio de cada una está en `apps/<app>/ARQUITECTURA.md`.

> **Regla:** cada app trae dos archivos propios:
> - `ARQUITECTURA.md`: cómo está hecha por dentro, con las mismas secciones que el de El Forzudo (ver al final, "Plantilla").
> - `GUIA.md`: cómo cambiar cosas y sacar versiones nuevas, a partir de [`apps/forzudo/GUIA.md`](../apps/forzudo/GUIA.md).
>
> Si cambias cómo funciona algo o cómo se hace, actualizas esos archivos en el mismo commit.

## En una frase

Apps de Android nativas escritas en **Kotlin**, con la interfaz en **Jetpack Compose**, que guardan todo en el teléfono como **un archivo JSON** y no dependen de ningún servicio de Google.

## Los lenguajes y herramientas

| Pieza | Qué es | Para qué se usa aquí |
|-------|--------|----------------------|
| **Kotlin** | El lenguaje oficial de Android. Parecido a Java pero más corto y seguro con los valores nulos. | Todo el código de las apps. |
| **Jetpack Compose** | La forma moderna de dibujar pantallas en Android: cada pantalla es una función (`@Composable`) que describe lo que se ve según los datos. Cuando los datos cambian, Compose vuelve a dibujar solo lo necesario. | Todas las pantallas. No hay archivos XML de pantallas. |
| **Material 3** | La biblioteca de componentes de Google (botones, barras, diálogos). | Base de los componentes, pintada con los colores de Fogón (sin *Material You*). |
| **Gradle** (con Kotlin DSL, archivos `.kts`) | La herramienta que compila, prueba y empaqueta. | `./gradlew` en la raíz. Versiones fijadas en `gradle/libs.versions.toml`. |
| **kotlinx.serialization** | Convierte objetos de Kotlin a JSON y de vuelta. | Leer y guardar los datos, los respaldos y la sincronización. |
| **DataStore** | Guarda un archivo de forma atómica: si el teléfono se apaga a mitad de una escritura, el archivo no queda a medias. | El archivo con todos los datos de la persona. |
| **Coroutines + StateFlow** | Para hacer cosas en segundo plano (guardar, internet) sin trabar la pantalla, y para avisar a la pantalla cuando cambian los datos. | En el `ViewModel` de cada app. |
| **OkHttp** | Cliente HTTP. | Solo funciones opcionales que necesitan internet. |
| **Python y Node.js** | Scripts que corren en el PC, no en el teléfono. | Herramientas de cada app (`apps/<app>/herramientas/`): generar datos y comprobar cálculos. |

Versiones exactas y dónde están instaladas: [`TECNOLOGIA.md`](TECNOLOGIA.md).

## Cómo está organizado el repositorio

```
fogon/
├── settings.gradle.kts      ← lista de módulos (core:identidad, apps:forzudo, apps:ahorrador)
├── gradle/libs.versions.toml← versiones de TODAS las bibliotecas, en un solo lugar
├── core/identidad/          ← lo que comparten todas las apps (biblioteca de Android)
├── apps/<app>/              ← una carpeta por app (aplicación de Android)
├── identidad/               ← archivos de diseño: logo, personajes, letras, lienzo
└── docs/                    ← documentos del proyecto (este, LEGAL, IDENTIDAD, …)
```

Es un **monorepo**: todas las apps viven juntas y comparten el módulo `core/identidad`. Así, un cambio en un botón o en la pantalla de licencias llega a todas las apps a la vez.

## `core/identidad`: lo compartido

| Archivo | Qué tiene |
|---------|-----------|
| `Tintas.kt` | Las tintas riso de Fogón (`Tintas`) y la paleta de noche de cada app (`Paleta.Forzudo`, `Paleta.Ahorrador`, …): fondo, dos luces y acento. |
| `Tema.kt` | `FogonTema(paleta, apariencia) { … }`: arma los colores (`fogonDe`) con la paleta de la app y la Apariencia elegida, aplica el tamaño de letra y da las letras (Bagel Fat One y Anton). Las pantallas leen todo de `FogonTema.actual`: fondo, tarjetas (`superficie`), texto, `pildora`/`sobrePildora`, `destacada`/`sobreDestacada`, `numero`, borde, si hay sombra dura o brillo, decoración y grano. |
| `Apariencia.kt` | Los cinco estilos (`Estilo`), los colores que trae cada uno (`colorBase`), lo que cambió la persona (`AjustesEstilo`), la comodidad y `AlmacenApariencia`, que lo guarda en las preferencias `apariencia` y avisa a toda la app. También `contraste()` y `legibleSobre()`. |
| `PantallaApariencia.kt` | Perfil → Apariencia, igual en todas las apps: vista previa, estilos, los 10 colores de cada estilo con su editor (`DialogoColor`: muestras, tono, saturación, brillo y código), transparencia, textura, esquinas y comodidad. |
| `Componentes.kt` | Piezas con la identidad: `fondoFogon` (el fondo del estilo con su decoración: orbes, trama, rayos; y el grano), `tarjetaFogon`, `forma(radio)` (respeta las esquinas rectas), `resplandor`/`sombraDura`, `brillo`, `FormaRiso` (tarjetas) y `FormaPildora` (botones), `BotonRiso`, `BotonSecundario`, `Personaje`, `EstadoVacio`, `Logro`, el grano de papel (un *shader* AGSL, solo en Android 13 o superior) y `AcercaDeFogon` (con un aviso opcional al final, de salud o de plata, y su título). Las reglas del estilo: `docs/IDENTIDAD.md` → Estilo de noche. |
| `Manifiesto.kt` | El manifiesto, igual en todas las apps y sin internet. |
| `Idioma.kt` | El idioma de las apps: el del teléfono o el elegido en la app, y `Idioma.envolver(contexto)` para aplicarlo. |
| `Licencias.kt` | La pantalla de licencias. `licenciasFogon()` lista lo que llevan todas las apps; cada app suma lo suyo. Una app sin internet pasa `conRed = false`, porque la Public Suffix List solo viene con OkHttp. Los textos completos están en `assets/licencias/`. |
| `res/` | Letras, logotipo, chispa y los personajes de todas las apps. |

## El patrón de cada app

Todas siguen la misma forma. Un ejemplo completo y real es El Forzudo.

```
Pantallas (Compose)  ──lee──▶  ViewModel  ──usa──▶  Lógica (funciones puras)
       ▲                         │   ▲
       └──── StateFlow ──────────┘   │
                                     ▼
                       DataStore (archivo JSON en el teléfono)
                       + opcional: sincronización / exportar
```

1. **Un solo estado.** Todos los datos de la persona caben en un objeto (`Estado`) que se guarda como un único JSON. Es simple de respaldar, de exportar y de entender.
2. **La lógica no toca la pantalla ni el disco.** Los cálculos (rachas, metas, totales) son funciones que reciben datos y devuelven resultados, así que se pueden probar sin teléfono.
3. **El `ViewModel` manda.** Es el único que cambia el estado. Cada cambio pasa por una función `cambiar { … }` que actualiza la memoria, guarda en disco 300 ms después (para no escribir con cada tecla) y avisa a las pantallas por `StateFlow`.
4. **Navegación propia y simple.** Unas pestañas abajo y una pila de pantallas (`List<Pantalla>`): abrir es agregar a la pila y atrás es quitar. No se usa Navigation Compose.
5. **Datos fijos en `assets/datos/*.json`.** Lo que no es de la persona (ejercicios, programas, tablas) va en JSON dentro de la app, no escrito en el código. Así se puede revisar y cambiar sin tocar Kotlin.
6. **Textos en archivos de idioma.** Nada de lo que se ve en pantalla va escrito en el código: está en `res/values-es/strings.xml` (español) y `res/values/strings.xml` (inglés, el que Android usa si el teléfono está en un idioma que la app no tiene). El idioma sigue al teléfono o se fija en la app (`Idioma.kt`, en `core/identidad`). Los datos fijos se traducen aparte (`assets/datos/<idioma>/`). Cómo se hace: la `GUIA.md` de cada app → "Cambiar textos e idiomas".

## Privacidad y seguridad (en todas)

- **Sin Google Play Services, Firebase ni analíticas.** Ninguna biblioteca que rastree.
- **Internet solo si hace falta,** y explicado en el `AndroidManifest.xml` con un comentario.
- **Secretos cifrados** con una llave del Keystore de Android (AES-GCM). La llave nunca sale del hardware seguro.
- **Sin copia automática a la nube de Google** (`res/xml/reglas_respaldo.xml`). Al pasar a un teléfono nuevo sí se copian los datos.
- **Exportar e importar** con el selector de archivos del sistema: no hace falta pedir permiso de almacenamiento.

## Compilar, probar y firmar

```
# Requiere JDK 21 y el SDK de Android (local.properties con sdk.dir)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:<app>:assembleDebug        # versión de prueba
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:<app>:testDebugUnitTest    # pruebas
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:<app>:lintDebug            # revisión de calidad
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:<app>:assembleRelease      # versión firmada
```

- La llave de firma **nunca** va en el repositorio. Cómo se firma y se entrega: [`PUBLICAR.md`](PUBLICAR.md).
- La versión de publicación se encoge con R8. Por eso cada app tiene un `proguard-rules.pro` que protege las clases que se leen desde JSON.
- `dependenciesInfo { includeInApk = false }`: así el APK no lleva un bloque firmado por Google y F-Droid puede reproducir la compilación.

## Agregar una app nueva

1. Crear `apps/<app>/` copiando la estructura de `apps/forzudo/` (sin su código propio).
2. Agregar `include(":apps:<app>")` en `settings.gradle.kts`.
3. Identificador `io.github.fogonapps.<app>`. **Una vez publicada no se puede cambiar.**
4. Paleta propia en `Tintas.kt` y personaje en `core/identidad/res/` (ver [`IDENTIDAD.md`](IDENTIDAD.md)).
5. Usar `AcercaDeFogon` y `LicenciasFogon`, y agregar a la lista las licencias de lo que la app traiga de otros.
6. Escribir `README.md` (qué hace) y `ARQUITECTURA.md` (cómo está hecha).

## Plantilla de `ARQUITECTURA.md` para cada app

1. **En una frase:** qué hace la app y con qué está hecha.
2. **Mapa de archivos:** qué hay en cada archivo y carpeta.
3. **Los datos:** qué se guarda, en qué formato y dónde.
4. **Cómo fluye todo:** desde que la persona toca algo hasta que se guarda.
5. **La lógica:** los cálculos importantes, explicados.
6. **Funciones especiales:** notificaciones, internet, permisos.
7. **Pruebas y herramientas.**
8. **Trampas conocidas:** lo que no es obvio y conviene saber antes de cambiar algo.
