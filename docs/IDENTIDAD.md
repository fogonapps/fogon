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

**Las marcas del ADN (Vidrio y puntos, desde el 24-10-2026):**

1. **Un número grande de puntos** por pantalla (la racha, el saldo, el volumen), en la letra Doto, sobre un **halo de puntos** que se apaga hacia el borde: la trama riso de Fogón convertida en luz. Solo uno por pantalla.
2. **Una sola letra para todo:** Bricolage Grotesque. Nada de mezclar letras.
3. **Vidrio sobre una sola luz:** tarjetas transparentes con borde fino sobre el papel del tema, y arriba una luz suave de su color. Nada más brilla.
4. **El personaje en las tintas del tema:** el cuerpo en la primera tinta, la sombra en la segunda y la chispa roja siempre. Va en la tarjeta grande, en los vacíos y en el tutorial, una sola vez por pantalla. Nunca girado, repetido ni en un circulito de la cabecera (Angel, 24-10-2026: "innecesario, no cumple ninguna función").
5. **Íconos de línea redonda**, todos iguales, casi siempre dentro de una ficha de vidrio redonda. Ninguno relleno de negro ni con recortes blancos.
6. **Píldoras:** botones, días de la semana, la barra de pestañas flotante y los logros. La pestaña activa es una píldora llena con su nombre.
7. **Frases, no carteles:** títulos y etiquetas en minúsculas como una frase; nada en mayúsculas ni girado.
8. **La chispa** marca los logros (récords, rachas, metas cumplidas).

**Historia.** El 22-10-2026 Angel rechazó la primera versión (luces difuminadas y vidrio genérico, El Forzudo 1.7); de ahí salió el sistema riso (1.8, papel y dos tintas corridas). El 24-10-2026 encontró la 1.9 "vomitiva" (personaje y sello girados, demasiadas cosas) y luego que las combinaciones de los temas limpios "se ven mal" y las letras mezcladas; mandó referencias de apps oscuras con vidrio, números de puntos e ilustraciones (El Forzudo: lima sobre oliva; El Ahorrador: rojo vino con vidrio) y aprobó el estudio **Vidrio y puntos**: lienzo https://claude.ai/artifact/MDA52AGbTrQ1iaLDSfHQRy, página "Vidrio y puntos (estudio 3)". Lo que lo hace propio y no genérico: los números y el halo de puntos (la trama riso), el personaje teñido y una sola luz. Las páginas "Sistema riso (nuevo)" y "Primera propuesta" quedan como historia.

## Archivos

| Ruta | Qué es |
|------|--------|
| `identidad/logo/fogon-logotipo.svg` | Logotipo "fogón" en tinta, con la chispa como tilde |
| `identidad/logo/fogon-logotipo-claro.svg` | El mismo, en papel, para fondos oscuros |
| `identidad/logo/chispa.svg` | La chispa sola |
| `identidad/personajes/*.svg` | Los personajes a dos tintas |
| `identidad/personajes/una-tinta/*.svg` | Los personajes en negro (íconos temáticos de Android, notificaciones, sellos) |
| `identidad/png/*.png` | Todo lo anterior en PNG con fondo transparente (1024 px, logotipo de 1600 px) |
| `identidad/fuentes/` | Bricolage Grotesque y Doto (las de las apps), Bagel Fat One y Anton (logotipo y piezas impresas), cada una con su licencia OFL |
| `identidad/munecos/*.svg` | Los muñequitos del tutorial de El Forzudo |
| `identidad/vidrio/` | El estudio Vidrio y puntos, los personajes en cada tema y el generador de capas para teñirlos |
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
| Alimentación | **La Cucha**: moño, delantal y la cuchara de palo en alto | Naranja `#FF7A3D` | Verde `#3FBF6A` | Diseñado (`identidad/cucha/`) |
| En reserva | **El Monje** (meditando) y **La Escritora** (lápiz como antorcha) | Violeta / Rosa | Naranja / Amarillo | Sin app |

Reglas:
- Un personaje por app, que no cambia nunca. Es su ícono, su pantalla de arranque y su mascota.
- Los cuerpos se arman con trazos gruesos de punta redonda y se funden con el filtro "goo". La cabeza siempre va separada del cuerpo.
- Los objetos (la moneda, el lápiz) van en la segunda tinta y con transparencia (`multiply`).
- **Dentro de las apps, los personajes y muñequitos toman las tintas del tema** (24-10-2026): el cuerpo en la primera tinta, la sombra en la segunda y la chispa con su rojo. Van en capas (`*_t1`, `*_t2`, `*_fijo`), hechas con `identidad/vidrio/capas/generar_capas.py`; los `personaje_*.webp` sin capas (los de las apps que aún no existen) siguen como calcomanía. Antes (21-10-2026) llevaban un borde de papel para que se leyeran sobre cualquier fondo; con las tintas del tema ya no hace falta.

## Dibujos de ejercicios: las láminas

Las apps que muestran ejercicios usan **láminas** en el estilo Vidrio y puntos (desde el 25-10-2026; antes eran láminas riso sobre papel crema): papel grafito, un halo de puntos detrás de la figura en el color de la zona del cuerpo (rosa tren superior, amarillo brazos, azul piernas), la figura rellena de vidrio con la línea clara, el músculo que trabaja con su color y el número del paso (1 inicio, 2 final) en la letra de puntos con la chispa. Son oscuras en todos los temas, como una foto.

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

## Vidrio y puntos: la interfaz de las apps

Desde el 24-10-2026 (El Forzudo 1.10, El Ahorrador 1.4). El estudio, las letras y las capas de los personajes están en `identidad/vidrio/` (ver su README).

### Los temas

Cada app tiene **Perfil → Apariencia** (`PantallaApariencia`, igual en todas) y el último paso del tutorial, **"¡Dale vida!"** (`PasoDaleVida`). Hay cuatro temas; **las apps vienen en Grafito** (Angel: "por defecto el oscuro con blanco").

| Tema | Papel | Primera tinta (botones, número) | Segunda tinta (sombra, halo, metas) | Luz |
|------|-------|----------------------------------|-------------------------------------|-----|
| **Grafito** (por defecto) | `#0E0E10` | Blanco `#EDEDEF` | Gris `#5E5E66` | Blanca, muy suave |
| **El de la app** | El de la `Paleta` | El de la `Paleta` | El de la `Paleta` | `Paleta.brillo` |
| **Claro** | `#F3F2EE` | Casi negro `#19181A` | El acento de la app | El acento, suave |
| **Contraste** | Negro | Blanco | El acento de la app | Ninguna |

| App | Tema propio (`Paleta.nombreTema`) | Papel | Primera / segunda tinta | Luz |
|-----|-----------------------------------|-------|-------------------------|-----|
| El Forzudo | **Gimnasio** | `#0C0D09` | Lima `#CDEB3A` / Oliva `#5C7A12` | `#8CAA1E` |
| El Ahorrador | **Vino** | `#0D0809` | Rojo `#F2384D` / Ámbar `#F5B547` | `#AA1830` |
| La Cucha | **Cocina** | `#100B08` | Naranja `#FF8A4C` / Verde `#3FBF6A` | `#B8501E` |
| La Minga | **Brasa** | `#120A08` | Coral `#FF5A4F` / Amarillo `#FFC93C` | `#B0301F` |

- **Solo se eligen tres colores por tema:** el papel y las dos tintas (muestras u "otro color"), más qué tan transparente es el vidrio y si lleva la luz. "Volver a como viene" deja el tema como estaba.
- **Lo demás se calcula** (`Apariencia.colores`): las letras son las del tema si llegan a 7:1 sobre el papel; si no, casi negras o casi blancas (o negro y blanco puros). El vidrio es blanco con poca opacidad en los temas oscuros y blanco lechoso en los claros; en papeles de brillo medio va oscuro para no quitarle contraste a las letras. Las letras suaves se leen (4,6:1) sobre el papel y sobre el vidrio. El número grande va en la primera tinta solo si se lee (3:1).
- **Comodidad, para todos los temas:** tamaño de letra (4), menos movimiento y más contraste (bordes más gruesos y letras del color principal).
- **Cada tema se lee:** `AparienciaTest` revisa los cuatro temas en todas las apps y 67 papeles y tintas distintos, con el vidrio al mínimo y al máximo.
- **Al cambiar de sistema** (preferencias versión 4), lo de cada tema vuelve a como viene y el tema elegido pasa al más parecido: Neutro → Claro, Noche → Grafito, Riso, Bosque, Durazno, Chicle y Rebelde → el de la app.

### Letras

Una sola familia, incluida en la app (licencia OFL, en `identidad/fuentes/` y `core/identidad/src/main/res/font/`): **Bricolage Grotesque** para todo, en cuatro grosores, y **Doto** (puntos redondos, negrita) solo para el número grande de cada pantalla. En el código: `MaterialTheme.typography` (todo en Bricolage), `FogonTema.actual.puntos` para el número grande (`NumeroRiso`) y `anchoCifra` para que una palabra grande quepa. Bagel Fat One y Anton salieron de las apps; quedan en `identidad/fuentes/` para el logotipo y piezas impresas.

### Reglas de la interfaz

En el código todo sale de `FogonTema.actual` (`core/identidad`): nunca un color fijo en una pantalla.

1. **Fondo:** el papel del tema con su luz arriba: `Modifier.fondoFogon(f)`.
2. **Tarjetas de vidrio:** `tarjetaFogon()` (vidrio + borde fino de 1 dp, esquinas de 24). **Una tarjeta destacada por pantalla** puede ir llena de la primera tinta (`tarjetaDestacada()`), con lo de encima en `sobreTinta1`.
3. **El número grande:** `NumeroRiso(…)` en puntos, sobre `Modifier.haloPuntos(f.tinta2)`.
4. **Botones en píldora:** el principal lleno de la primera tinta (`BotonRiso`), el secundario de vidrio (`BotonSecundario`).
5. **Íconos:** `IconoRiso(dibujo, …)` dibuja el contorno de cada parte del `Dibujo` (línea de 1,9, puntas redondas); con `relleno` se le pone un cuerpo suave. En listas y acciones van dentro de una ficha de vidrio redonda; en El Ahorrador las fichas de categoría (`Ficha`) son un círculo teñido de su color con el ícono en ese color.
6. **Barra de pestañas:** una píldora opaca que flota (`f.barra`), con la activa llena de la primera tinta y su nombre; las demás, solo el ícono (el nombre va para los lectores de pantalla).
7. **El personaje:** `Personaje(R.drawable.personaje_…)` lo tiñe solo si tiene capas; cualquier figura en capas se dibuja con `PersonajeTenido(t1, t2, fijo, …)`. Las capas se generan con `identidad/vidrio/capas/generar_capas.py`.
8. **Una sola luz:** fuera de `fondoFogon` y del halo no hay degradados ni brillos. Las líneas finas van en `f.linea`.
9. **Ilustraciones 3D propias** (trofeo, mancuerna, moneda, pila de monedas, sobre): modeladas desde cero en Blender con `identidad/vidrio/ilustraciones/generar.py`, en gris; `Ilustracion(recurso, tinta = …)` las tiñe con la tinta del tema (a las tintas oscuras les suma gris para que no pierdan el volumen). Van en las tarjetas, al lado del texto, nunca solas ni como fondo: "Lo que toca" y los récords en El Forzudo; la meta del Inicio y la tarjeta de Sobres en El Ahorrador.
10. **Las láminas de ejercicios** son oscuras (papel grafito) en todos los temas; van tal cual, con esquinas redondeadas.
11. **"Acerca de" igual en todas:** el logotipo, el personaje, "Hecha en el Fogón · Sin anuncios · Sin rastreo · Código abierto", el enlace al código y los créditos.
12. **Movimiento** (ver abajo).
13. **Accesibilidad:** texto con 4,5:1 o más en todos los temas (`AparienciaTest`), botones de 48 dp o más, y nunca un color como única señal (los días cumplidos llevan además un chulo).

### Movimiento

Desde el 24-10-2026 (`core/identidad/.../Movimiento.kt`). La idea: que se sienta como una app de iPhone, con resortes suaves y sin adornos.

- **Resortes:** `resorte()` (rápido, sin rebote) para casi todo y `resorteAlegre()` (con un poco de rebote) para lo que celebra.
- **Pantallas:** `PilaAnimada` hace que la pantalla nueva entre desde la derecha mientras la de atrás se hunde un poco (94 %) y se apaga; al volver, al revés. `PestanaAnimada` cambia de pestaña subiendo 14 dp con un fundido.
- **Tocar:** `Modifier.rebote()` achica un poco lo que se toca y lo suelta con un resorte (botones, pestañas, píldoras, acciones). Ya lo traen `BotonRiso` y `BotonSecundario`.
- **Aparecer:** `Modifier.aparecer(n)` sube y aclara las tarjetas una tras otra (45 ms entre cada una); `aparecerConRebote()` para ilustraciones y logros (ya lo trae `Ilustracion`).
- **Números:** `NumeroRiso` usa `CifrasRodantes`: cada cifra que cambia sale hacia arriba y la nueva entra desde abajo. Las barras y los anillos se llenan con `progresoAnimado`, y las gráficas se dibujan de izquierda a derecha.
- **Menos movimiento** (Apariencia → Comodidad) apaga todo: fundidos cortos o nada.

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
