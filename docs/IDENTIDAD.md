# Identidad de Fogón

> **Regla de oro:** cada app de Fogón tiene que reconocerse de un vistazo, aunque no se vea el nombre. La identidad no es decoración, es parte de la app.

La identidad se inspira en el punk, el Y2K, la impresión riso y los fanzines: tinta corrida, figuras líquidas hechas "a mano", colores que no coinciden del todo y grano de papel. Es rebelde, pero sin atacar a nadie.

Lienzo de diseño original: https://claude.ai/artifact/DTDCyymJ6YJFRS69Anp2b8 (privado). Su código está archivado en `identidad/lienzo/`.

## Lo más importante: el ADN visual

**Esta sección manda sobre todo lo demás.** Angel la pidió el 21-10-2026: una app de Fogón no puede verse genérica. Antes de diseñar o cambiar una pantalla, se revisa que tenga al menos tres de estas marcas.

Salen de las referencias que eligió Angel (pósters y apps, de otros autores; por eso no se guardan en el repo, solo se describen):

| Referencia | Qué tomamos |
|------------|-------------|
| Póster de burbuja sobre rojo, con foto en blanco y negro y semitono | Letras gigantes de burbuja (Bagel Fat One) recortadas por el borde, grano de fotocopia, figura recortada encima. |
| Portada de cómic "BOOM!" en rojo, negro y blanco | Rayos que salen de un punto, cajas negras con texto, tipografía gigante que se sale de la pantalla. |
| Póster deconstruido con líneas rojas finas | Líneas finas que cruzan la pantalla desde la figura; letras en ángulo. |
| Pósters de orbes de color difuminados, con etiquetas de catálogo | Orbes cromáticos en lugar de una luz en la esquina; etiquetas técnicas pequeñas ("FZ — 012", "SEM 43", "21 — 10 — 2026"). |
| App de salud oscura con medidor de marcas y figura 3D | Medidores con marcas finas (descanso), números grandes en Anton, filas de vidrio con ícono en círculo, botón blanco en píldora. |
| App oscura con acento lima, días en píldoras y trofeo 3D | Días de la semana en píldoras con punto, el día de hoy con contorno de acento, tarjetas con un objeto que se sale. |
| App de bienestar con panel blanco y foto con estela | Contraste entre un panel claro y el fondo oscuro; formas suaves que brillan dentro de las tarjetas. |

**Las marcas del ADN:**

1. **Dos tintas, la segunda corrida.** Todo se "imprime" como una riso: botones, tarjetas, números e íconos llevan detrás la segunda tinta un poco movida. Es el mismo truco de los personajes. Ver [Sistema riso](#sistema-riso-la-interfaz-de-las-apps).
2. **Un número o una palabra gigante** por pantalla (la racha, el rango, el monto), en Anton o Bagel, impreso en las dos tintas y tan grande que se recorta.
3. **Íconos líquidos** como los personajes: gordos, llenos y de puntas redondas. Nunca íconos de línea fina.
4. **Trama de puntos en lugar de degradados.** Ningún degradado ni orbe difuminado en la interfaz.
5. **La figura como calcomanía:** todo personaje o muñequito lleva un borde de papel. Nunca una figura del mismo color que lo de detrás.
6. **Etiquetas técnicas** pequeñas en mayúsculas con espaciado (código, semana, fecha), como un catálogo, y **sellos de goma** para los logros.
7. **Grano** (de papel o de fotocopia), aunque sea muy suave, y **las láminas siempre sobre papel**.
8. **Bordes de tinta y manchas:** tarjetas y botones con borde de tinta; la pestaña activa y el perfil en una mancha torcida, no en un círculo perfecto.

**El 22-10-2026 Angel rechazó en el teléfono la primera versión** (El Forzudo 1.7, El Ahorrador 1.1): "diseño muy pobre, íconos sin estilizar, degradados feos, el estilo no va con el personaje… quiero todo muy estilizado y personalizable y sobre todo nada genérico". La lección: fondo oscuro con orbes difuminados, vidrio e íconos de línea fina es la moda de cualquier app, no Fogón. De ahí salió el sistema riso.

Diseño (lienzo privado): https://claude.ai/artifact/MDA52AGbTrQ1iaLDSfHQRy, página "Sistema riso (nuevo)". La página "Primera propuesta" es la versión rechazada.

## Archivos

| Ruta | Qué es |
|------|--------|
| `identidad/logo/fogon-logotipo.svg` | Logotipo "fogón" en tinta, con la chispa como tilde |
| `identidad/logo/fogon-logotipo-claro.svg` | El mismo, en papel, para fondos oscuros |
| `identidad/logo/chispa.svg` | La chispa sola |
| `identidad/personajes/*.svg` | Los personajes a dos tintas |
| `identidad/personajes/una-tinta/*.svg` | Los personajes en negro (íconos temáticos de Android, notificaciones, sellos) |
| `identidad/png/*.png` | Todo lo anterior en PNG con fondo transparente (1024 px, logotipo de 1600 px) |
| `identidad/fuentes/` | Bagel Fat One y Anton, con su licencia OFL |
| `identidad/lienzo/` | Código del lienzo de diseño |

El borde de tinta y lo líquido de las figuras se hacen con filtros SVG (`feTurbulence`, `feDisplacementMap` y un desenfoque con umbral tipo *metaball*). Los navegadores, WebView y `rsvg` los muestran bien. Para Android nativo se usan los PNG.

## Logotipo

- La palabra **"fogón"** va en Bagel Fat One con borde de tinta corrida.
- **La tilde de la ó es la chispa roja** (#E5332A), una estrella de cuatro puntas estilo Y2K.
- Puede llevar una mancha de tinta riso detrás, en amarillo por defecto.
- Nunca se escribe "Del Fogón" ni se acompaña de ollas, comida o condimentos (ver `LEGAL.md`).

## La chispa

Es la firma de Fogón y aparece en todas las apps:
- en cada personaje;
- como marca de logro, racha o elemento destacado dentro de las apps;
- en los afiches, como estrellas sueltas.

## Personajes

Cada app es **alguien**: una figura líquida, con la cabeza suelta, impresa en **dos tintas riso que no coinciden del todo**. La segunda tinta va debajo, corrida unos 7 px a la derecha y 6 px hacia abajo. Todos llevan la chispa roja.

| App | Personaje | Tinta principal | Segunda tinta | Estado |
|-----|-----------|-----------------|---------------|--------|
| Fogón (la app central) | **La Minga**: cuatro personas tomadas de la mano | Rojo `#E5332A` | Amarillo `#FFC93C` | Diseñado |
| El Forzudo (antes Mi Gym) | **El Forzudo**: levanta la barra sobre la cabeza | Azul `#2D5BFF` | Rosa `#FF4FA3` | Diseñado |
| Gastos | **El Ahorrador**: empuja una moneda gigante, como Sísifo | Verde `#1FB45A` | Amarillo `#FFC93C` | Diseñado |
| Hábitos y día a día | **La Corredora**: corre detrás de la chispa | Naranja `#FF7A1A` | Azul `#2D5BFF` | Diseñado |
| Alimentación | *(personaje nuevo, por diseñar)* | — | — | Pendiente |
| En reserva | **El Monje** (meditando) y **La Escritora** (lápiz como antorcha) | Violeta / Rosa | Naranja / Amarillo | Sin app |

Reglas:
- Un personaje por app, que no cambia nunca. Es su ícono, su pantalla de arranque y su mascota.
- Los cuerpos se arman con trazos gruesos de punta redonda y se funden con el filtro "goo". La cabeza siempre va separada del cuerpo.
- Los objetos (la moneda, el lápiz) van en la segunda tinta y con transparencia (`multiply`).
- **Dentro de las apps, los personajes y muñequitos son calcomanías:** borde de papel y sombra de tinta corrida, para que se lean sobre cualquier fondo y en cualquier estilo (21-10-2026: el muñeco azul se perdía sobre la luz azul). Los `personaje_*.webp` de `core/identidad` y los `munieco_tut_*.webp` de El Forzudo ya lo traen; los originales sin borde siguen en `identidad/png/`. El borde se hace con un script de PIL que agranda el canal alfa (ver `apps/forzudo/GUIA.md` → Colores, letras, ícono y muñequitos).

## Dibujos de ejercicios: la lámina riso

Las apps que muestran ejercicios usan **láminas riso**: una ilustración en línea de tinta sobre papel con grano, con la silueta corrida en el color de la zona del cuerpo (rosa tren superior, amarillo brazos, azul piernas), el músculo que trabaja con su color propio y el número del paso (1 inicio, 2 final) en Anton con la chispa. Por dentro, la figura es del mismo papel del fondo.

Los colores de cada grupo muscular, las fuentes de los dibujos y cómo se generan están en `apps/forzudo/herramientas/laminas/README.md`. **Solo figuras de hombre**, por decisión de Angel (18-10-2026): no hay ilustraciones libres de mujer y las generadas con IA no quedaron al nivel. Está explicado en las preguntas frecuentes de la app.

## Colores (tintas riso)

Son las tintas de los personajes, el logotipo y los dibujos, y de ellas salen las luces de cada app. Las versiones "para texto" sirven sobre papel (láminas, tarjetas de papel).

| Tinta | Para formas | Para texto sobre papel (≥ 4,5:1) |
|-------|-------------|----------------------------------|
| Tinta (negro) | `#141210` | `#141210` (15,2:1) |
| Papel | `#EFE7DA` | — |
| Rojo chispa | `#E5332A` | `#C92118` |
| Amarillo | `#FFC93C` | `#835F00` |
| Rosa chicle | `#FF4FA3` | `#C90060` |
| Azul riso | `#2D5BFF` | `#2051FF` |
| Verde | `#1FB45A` | `#14753A` |
| Violeta | `#7B4DFF` | `#6C39FF` |
| Naranja | `#FF7A1A` | `#AB4800` |

Sobre papel, **ninguna tinta pura llega a 4,5:1** para texto pequeño. Por eso cada tinta tiene su versión "para texto". Sobre fondo de tinta (modo oscuro), el amarillo, el rosa, el verde y el naranja sí sirven como texto, porque pasan de 6:1.

## Sistema riso: la interfaz de las apps

Desde el 23-10-2026 (El Forzudo 1.8, El Ahorrador 1.2) **toda la interfaz se imprime como una riso**: un papel y dos tintas, con la segunda corrida. Lo que fija la identidad es la forma de imprimir; lo que cambia de un estilo a otro (y lo que la persona puede cambiar) son el papel, las tintas y cuánto se corren. Así se puede personalizar todo sin dejar de ser Fogón.

Antes: hasta el 22-10-2026 las apps usaron un "estilo de noche" de luces difuminadas y vidrio (lienzo https://claude.ai/artifact/9ed1LykdvoFCgPFyWf6dBx); Angel lo rechazó por genérico.

### Las tintas de cada app

**Cada app imprime con sus tintas; la tipografía es la misma en todas.** En el código, `Paleta.<App>`.

| App | Noche: primera / segunda tinta, papel | Riso: primera / segunda tinta (sobre papel `#EFE7DA`) |
|-----|---------------------------------------|-------------------------------------------------------|
| El Forzudo | Lima `#C8F560` / Violeta `#7B4DFF`, `#0C0A12` | Violeta `#6C39FF` / Rosa `#FF4FA3` |
| El Ahorrador | Amarillo `#FFC93C` / Verde `#1FB45A`, `#0D0C0A` | Verde `#14753A` / Amarillo `#FFC93C` |
| La Minga | Coral `#FF5A4F` / Amarillo `#FFC93C`, `#120A08` | Rojo `#C92118` / Amarillo `#FFC93C` |
| La Corredora (hábitos) | Naranja `#FF7A1A` / Rosa `#FF4FA3` | por crear |
| El Monje (sueño) | Cian claro `#7FD8FF` / Azul `#2D5BFF` | por crear |

Dos apps no comparten primera tinta en Noche. En la `Paleta`: `tinta` y `segunda` son las de Noche; `textoEnPapel` y `segundaTinta`, las de Riso.

### Apariencia: seis estilos que la persona elige

Cada app tiene **Perfil → Apariencia**, igual en todas (`PantallaApariencia` en `core/identidad`). Angel lo pidió así: "que cada estilo se le pueda cambiar los colores al fondo, a las letras…, a todo en general, pero que cada uno tenga uno por defecto", y luego "que cada estilo tenga sus cosas pero que sigan un estilo para no perder la identidad de fogón".

**Las apps vienen en Neutro** (24-10-2026, pedido de Angel: "que el aspecto por defecto sea muy neutro en las dos apps", con Tailscale como referencia): papel casi blanco, tarjetas blancas con borde gris fino, botones casi negros, la letra del teléfono y nada de adornos. El color lo pone la persona: el último paso del tutorial, **"¡Dale vida!"** (`PasoDaleVida`), muestra los seis estilos (se ven al momento) y "Más opciones" abre el resto de la Apariencia. Los personajes y la chispa siguen ahí, con sus tintas: son lo que mantiene la identidad en Neutro.

| Estilo | Papel | Primera tinta | Segunda tinta | Corrimiento | Adorno | Grano | Esquinas |
|--------|-------|---------------|---------------|-------------|--------|-------|----------|
| **Neutro** (por defecto) | Casi blanco `#F6F5F3`, tarjetas blancas | Casi negro `#1F1E1D` | Gris `#DCD8D5` (bordes de 1 dp) | 0 | Nada | Nada | Redondas |
| **Noche** | El negro de la app | La de la app (neón) | La de la app | 3 dp | Trama de puntos | Suave | Redondas |
| **Riso** | Crema `#EFE7DA` | La de la app para papel | La segunda de la app | 3 dp | Trama de puntos | Fuerte | Redondas |
| **Rebelde** | Rojo `#F03E33` | Negro | Crema | 4 dp | Rayos de cómic | Fuerte | Rectas |
| **Chicle** (Y2K, reemplazó a Aura) | Rosa `#FFE1EE` | Azul `#2D5BFF` | Amarillo flúor `#FFB020` | 3 dp | Trama y destellos | Suave | Redondas |
| **Contraste** | Negro | Blanco | La primera tinta de Noche de la app | 0 (sin corrimiento) | Nada | Nada | Redondas |

- **Arriba, lo rápido:** papel, primera tinta y segunda tinta en muestras (más "otro color"), y el corrimiento de la tinta (0 a 6 dp). Luego el adorno (trama, rayos, destellos o nada).
- **Todos los colores, en cada estilo por separado:** papel, papel de las tarjetas, letras, letras suaves, primera tinta, letras sobre la primera tinta, segunda tinta, números grandes y bordes. También la textura, las esquinas y la transparencia de las tarjetas (0 % por defecto). "Volver a como viene" deja el estilo como estaba. Si los colores se leen mal, la pantalla avisa.
- **Lo que se acomoda solo:** si la persona cambia la primera tinta, el número gigante la sigue (en Riso y Chicle) y las letras sobre ella se eligen para que se lean; si la tinta no se lee como texto sobre el papel, el acento pasa al color de las letras. Los bordes siguen a las letras.
- **Comodidad, para todos los estilos:** tamaño de las letras (4 tamaños, sobre el que ya tenga el teléfono), menos movimiento (sin animaciones) y más contraste (bordes más gruesos y todas las letras del color principal).
- **Lo que no cambia:** la forma de imprimir (dos tintas, íconos líquidos, trama, grano, bordes de tinta), las letras (Bagel Fat One, Anton y Roboto; en Neutro, solo la del teléfono), los personajes y la chispa. Las láminas siempre van sobre papel.
- **Cada estilo se lee:** la prueba `AparienciaTest` comprueba que los seis estilos pasen 4,5:1 en todas las apps (letras, letras suaves, tarjetas, la primera tinta y el acento) y que cambiar la primera tinta no deje nada ilegible.
- **Al pasar a este sistema** (preferencias versión 2), los colores que la persona había cambiado en la versión anterior vuelven a los del estilo, porque ya no significan lo mismo; el estilo elegido y la comodidad se quedan. Quien tenía Aura queda en Chicle.

### Tipografía (igual en todas las apps)

En el código las pantallas no nombran la letra: usan `FogonTema.actual.titulos` (Bagel Fat One) y `FogonTema.actual.cifras` (Anton). En el estilo Neutro las dos son la letra del teléfono en grosor medio (`Sencilla`), y `anchoCifra` dice cuánto mide una mayúscula para que las palabras gigantes quepan.

| Uso | Fuente | Ejemplos |
|-----|--------|----------|
| Títulos de pantalla, nombres de tarjetas (rutina, cuenta, meta), botones grandes | **Bagel Fat One** | "Rangos", "Torso A", "Guardar gasto" |
| Números grandes y encabezados de sección, en mayúsculas | **Anton** | "$ 842.300", "84 kg", "MIS RUTINAS" |
| Todo lo demás | **Roboto** (la letra del sistema) | fechas, explicaciones, listas |

Las dos primeras van incluidas en `identidad/fuentes/` (licencia OFL), así las apps no las descargan de internet.

### Reglas de la interfaz

En el código todo sale de `FogonTema.actual` (`core/identidad`): nunca un color fijo en una pantalla.

1. **Fondo:** el papel del estilo, su adorno impreso y el grano: `Modifier.fondoFogon(f)`. El adorno es una trama de puntos de la segunda tinta (gordos al centro, finos al borde, como un semitono), rayos de cómic en la primera tinta o destellos de cuatro puntas.
2. **Tarjetas:** papel de tarjeta con borde de tinta de 2 dp y la segunda tinta corrida detrás (`tarjetaFogon()`; en listas y campos, `tarjetaFogon(sombra = false)` para que no haya ruido). **Una tarjeta destacada por pantalla** (el disponible para gastar, el saldo) es un bloque lleno de la primera tinta con la segunda bien corrida detrás (`tarjetaDestacada()`), y lo de encima va en `sobreTinta1`.
3. **Botones en píldora** (`FormaPildora`) con borde de tinta: el principal de la primera tinta con la segunda corrida detrás (`BotonRiso`); el secundario, de papel de tarjeta (`BotonSecundario`). `Modifier.brillo()` ya no es un brillo: es la segunda tinta corrida (`f.desfase`).
4. **Números y palabras gigantes:** `NumeroRiso(…)` los imprime dos veces, la segunda tinta corrida detrás (el corrimiento crece con la letra).
5. **Íconos:** `IconoRiso(dibujo, …)` con un `Dibujo` de 24×24 hecho de rellenos, trazos gordos (2,6 a 3,6) y huecos que dejan ver el papel. La segunda tinta va corrida detrás (`f.desfaseIcono`, menos que en las tarjetas). Sobre una tinta llena (botón, pestaña activa) el ícono va en una sola tinta (`corrida = null`). Los comunes están en `IconosFogon`; cada app suma los suyos en su `Iconos`.
6. **Navegación:** la pestaña activa lleva una mancha de la primera tinta (`FormaMancha`) con el ícono encima en una sola tinta; las demás, el ícono en dos tintas. La barra es una tarjeta impresa.
7. **Sin degradados ni orbes:** donde haría falta un degradado va trama de puntos o un bloque lleno. Las líneas finas (divisores, rejillas) van en la tinta de las letras al 22 %.
8. **Lo riso va sobre papel:** las láminas y cualquier dibujo riso van sobre una tarjeta de papel `#EFE7DA`.
9. **El personaje aparece** en el arranque, en las pantallas vacías, en los logros y en "Acerca de", como calcomanía. **La chispa** marca los logros (récords, rachas y metas) y la mejor racha va en un **sello de goma**.
10. **"Acerca de" igual en todas:** el logotipo, el personaje, "Hecha en el Fogón · Sin anuncios · Sin rastreo · Código abierto", el enlace al código y los créditos.
11. **Ícono en Android:** el personaje a dos tintas sobre una ficha de papel. Android exige fondo en los íconos. Para el ícono temático se usa la versión de una tinta.
12. **Accesibilidad:** texto con contraste de 4,5:1 o más en todos los estilos (`AparienciaTest`), botones de 48 dp o más, y nunca un color como única señal (los días cumplidos llevan además un chulo).

## Voz

- Se habla de **tú**, en español sencillo, directo, con algo de actitud.
- Nada de exagerar ni de hablar mal de otras apps.
- Frases de la casa:
  - *Tu atención vale más que un anuncio.*
  - *Apps hechas en casa. Sin anuncios, sin trampas.*
  - *Gratis de verdad.*
  - *Hecho con IA, hecho por la gente.*
  - *Siempre alcanza para uno más.*

## Nombres

- **Cada app lleva el nombre de su personaje** (decidido el 18-10-2026): El Forzudo (gimnasio), El Ahorrador (gastos), La Corredora (hábitos), El Monje (sueño). En tiendas y README: **"El Forzudo · Fogón"**.
- Nunca el nombre de otra marca ni "alternativa a…" en el nombre o en el ícono.
- **Identificador de paquete:** `io.github.fogonapps.<app>`, basado en la organización de GitHub `fogonapps`. La organización existe desde el 21-10-2026 (github.com/fogonapps). Una vez publicada una app, su identificador no se puede cambiar.
