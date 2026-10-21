# Identidad de Fogón

> **Regla de oro:** cada app de Fogón tiene que reconocerse de un vistazo, aunque no se vea el nombre. La identidad no es decoración, es parte de la app.

La identidad se inspira en el punk, el Y2K, la impresión riso y los fanzines: tinta corrida, figuras líquidas hechas "a mano", colores que no coinciden del todo y grano de papel. Es rebelde, pero sin atacar a nadie.

Lienzo de diseño original: https://claude.ai/artifact/DTDCyymJ6YJFRS69Anp2b8 (privado). Su código está archivado en `identidad/lienzo/`.

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

## Dibujos de ejercicios: la lámina riso

Las apps que muestran ejercicios usan **láminas riso**: una ilustración en línea de tinta sobre papel con grano, con la silueta corrida en el color de la zona del cuerpo (rosa tren superior, amarillo brazos, azul piernas), el músculo que trabaja con su color propio y el número del paso (1 inicio, 2 final) en Anton con la chispa. Por dentro, la figura es del mismo papel del fondo.

Los colores de cada grupo muscular, las fuentes de los dibujos y cómo se generan están en `apps/forzudo/herramientas/laminas/README.md`. **Solo figuras de hombre**, por decisión de Angel (18-10-2026): no hay ilustraciones libres de mujer y las generadas con IA no quedaron al nivel. Está explicado en las preguntas frecuentes de la app.

## Colores (tintas riso)

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

## Tipografía

| Uso | Fuente | Notas |
|-----|--------|-------|
| Logo, títulos de pantalla, nombres de personajes | **Bagel Fat One** | Con borde de tinta solo en títulos grandes |
| Números grandes (kg, pesos, rachas, calorías) y carteles | **Anton** | En mayúsculas; aquí la app "grita" |
| Texto para leer, botones, listas | **Letra del sistema** | Limpia, sin efectos |

Las dos fuentes van incluidas en `identidad/fuentes/` con licencia OFL, así las apps no las descargan de internet.

## Reglas para la interfaz de cada app

1. **Fondo de papel** `#EFE7DA` en modo claro y **fondo de tinta** `#141210` en modo oscuro. Nunca blanco puro ni negro puro.
2. **La tinta de la app es el acento:** botones principales, gráficas, la pestaña activa. La segunda tinta es la sombra.
3. **Sombra riso en vez de sombras difusas:** los botones y tarjetas destacadas llevan una sombra dura en la segunda tinta, corrida unos 4 px, sin desenfoque (`box-shadow: 4px 4px 0 <segunda tinta>`).
4. **El personaje aparece** en la pantalla de arranque, en las pantallas vacías ("todavía no hay nada aquí"), en los logros y en "Acerca de".
5. **La chispa marca los logros**: récords, rachas, metas cumplidas.
6. **Títulos en Bagel Fat One y cifras en Anton.** El resto, en la letra del sistema.
7. **Grano de papel** opcional en fondos grandes. Debe ser sutil y nunca va detrás de texto pequeño.
8. **Pantalla "Acerca de" igual en todas:** el logotipo, el personaje, "Hecha en el Fogón · Sin anuncios · Sin rastreo · Código abierto", el enlace al código y los créditos.
9. **Ícono en Android:** el personaje a dos tintas sobre una ficha de papel. Android exige fondo en los íconos. Para el ícono temático se usa la versión de una tinta.
10. **Accesibilidad:** el texto siempre con contraste de 4,5:1 o más (ver la tabla de colores), botones de al menos 44 px, y nunca un color como única señal.

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
- **Identificador de paquete:** `io.github.fogonapps.<app>`, basado en la organización de GitHub `fogonapps`. Ese nombre está libre a 11 de octubre de 2026, pero todavía no se ha creado. Una vez publicada una app, su identificador no se puede cambiar.
