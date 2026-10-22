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

1. **Un número o una palabra gigante** por pantalla (la racha, el rango, el monto), en Anton o Bagel, tan grande que se recorta.
2. **Etiquetas técnicas** pequeñas en mayúsculas con espaciado (código, semana, fecha), como un catálogo.
3. **La figura como calcomanía:** todo personaje o muñequito lleva un borde de papel y una sombra de tinta corrida, para que se lea sobre cualquier fondo. Nunca una figura del mismo color que la luz de detrás.
4. **Orbes de color difuminados**, no una mancha en la esquina.
5. **Líneas finas o rayos** que cruzan la composición.
6. **Las láminas siempre sobre papel**, como un recorte pegado.
7. **Grano** (de papel o de fotocopia), aunque sea muy suave.
8. **Botones en píldora** blanca o del acento; nunca un botón gris genérico.

Propuesta de diseño con estas marcas, los estilos y la pantalla de Apariencia: https://claude.ai/artifact/MDA52AGbTrQ1iaLDSfHQRy (privado).

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

## Estilo de noche: la interfaz de las apps

Desde el 21-10-2026 todas las apps usan el **estilo de noche** (decidido por Angel a partir de referencias de apps con luces difuminadas, vidrio y píldoras). Lo riso no desaparece: sigue en los personajes, el logotipo, la chispa, las láminas y el grano. Lo que cambia es la interfaz.

Diseños de referencia: el lienzo https://claude.ai/artifact/9ed1LykdvoFCgPFyWf6dBx, página "De noche" (El Ahorrador y El Forzudo). El estilo anterior, riso sobre papel, quedó en su página "Riso de día".

### Paleta de cada app

**Cada app tiene su propia paleta; la tipografía es la misma en todas.** Cada paleta tiene un fondo casi negro con un matiz propio, dos luces y un acento.

| App | Luz principal (`segunda`) | Segunda luz (`luz2`) | Acento (`tinta`) | Fondo | En el código |
|-----|---------------------------|----------------------|------------------|-------|--------------|
| El Forzudo | Violeta `#7B4DFF` | Azul `#2D5BFF` | Lima `#C8F560` | `#0C0A12` | `Paleta.Forzudo` |
| El Ahorrador | Verde `#1FB45A` | Amarillo `#FFC93C` | Amarillo `#FFC93C` | `#0D0C0A` | `Paleta.Ahorrador` |
| La Corredora (hábitos) | Naranja `#FF7A1A` | Rosa `#FF4FA3` | Naranja `#FF7A1A` | `#120B08` | por crear |
| El Monje (sueño) | Azul `#2D5BFF` | Cian `#00A0E9` | Cian claro `#7FD8FF` | `#070A14` | por crear |
| Alimentación (personaje pendiente) | Rosa `#FF4FA3` | Rojo `#E5332A` | Rosa claro `#FF8FC4` | `#120A0E` | provisional |
| Fogón / La Minga | Rojo `#E5332A` | Amarillo `#FFC93C` | Coral `#FF5A4F` | `#120A08` | por crear |

Dos apps no comparten acento. El texto sobre el acento va siempre en el fondo oscuro de la app (`sobreTinta`); sobre el fondo, el acento sirve también como color de texto (`textoEnTinta`), porque todos pasan de 6:1.

### Apariencia: cinco estilos que la persona elige

Desde el 21-10-2026 (El Forzudo 1.7, El Ahorrador 1.1) cada app tiene **Perfil → Apariencia**, igual en todas. Angel lo pidió así: "que cada estilo se le pueda cambiar los colores al fondo, a las letras o a los degradados, a todo en general, pero que cada uno tenga uno por defecto". Diseño: https://claude.ai/artifact/MDA52AGbTrQ1iaLDSfHQRy.

| Estilo | Cómo se ve | Fondo detrás | Tarjetas | Grano | Esquinas |
|--------|------------|--------------|----------|-------|----------|
| **Noche** (el de siempre) | El fondo casi negro de la app, sus dos luces y su acento | Orbes difuminados | Vidrio (92 % de transparencia) | Suave | Redondas |
| **Riso** | Papel, la primera tinta de la app (`textoEnPapel`) y su segunda tinta corrida (`segundaTinta`) | Círculo de trama de puntos | Blancas, sólidas, con borde y sombra dura | Fuerte | Redondas |
| **Rebelde** | Rojo `#F03E33`, negro y crema | Rayos de cómic | Crema, sólidas, con borde; la destacada es una caja negra | Fuerte | Rectas |
| **Aura** | Gris claro, letras casi negras | Orbe cromático grande con las dos luces | Vidrio claro (45 %) | Suave | Redondas |
| **Contraste** | Negro y blanco, el acento de la app | Nada | Negras con borde blanco | Nada | Redondas |

- **Todo color se puede cambiar, en cada estilo por separado:** fondo, tarjetas, letras, letras suaves, acento, letras sobre el acento, degradado 1, degradado 2, números grandes y bordes. También la transparencia (0 a 100 %), la textura (nada, suave, fuerte) y las esquinas. "Volver a como viene" deja el estilo como estaba. Si los colores elegidos se leen mal, la pantalla avisa.
- **Comodidad, para todos los estilos:** tamaño de las letras (4 tamaños, sobre el que ya tenga el teléfono), menos movimiento (sin brillos ni animaciones) y más contraste (bordes marcados y todas las letras del color principal).
- **Lo que no cambia:** las letras (Bagel Fat One, Anton y Roboto), los personajes, la chispa y el ADN visual. Las láminas siempre van sobre papel.
- **Cada estilo se lee:** la prueba `AparienciaTest` (en `core/identidad`) comprueba que los colores por defecto de los cinco estilos pasen 4,5:1 en todas las apps. Si se agrega un estilo o una app, la prueba los cubre sola.
- **Las pantallas nunca usan colores fijos:** todo sale de `FogonTema.actual` (fondo, texto, `pildora`, `sobreDestacada`, `numero`…). Un `Color.White` en una pantalla rompe los estilos claros.

### Tipografía (igual en todas las apps)

| Uso | Fuente | Ejemplos |
|-----|--------|----------|
| Títulos de pantalla, nombres de tarjetas (rutina, cuenta, meta), botones grandes | **Bagel Fat One** | "Rangos", "Torso A", "Guardar gasto" |
| Números grandes y encabezados de sección, en mayúsculas | **Anton** | "$ 842.300", "84 kg", "MIS RUTINAS" |
| Todo lo demás | **Roboto** (la letra del sistema) | fechas, explicaciones, listas |

Las dos primeras van incluidas en `identidad/fuentes/` (licencia OFL), así las apps no las descargan de internet.

### Reglas de la interfaz

1. **Fondo:** el negro de la app (nunca negro puro), con sus dos luces difuminadas, la principal arriba a la izquierda y la segunda a la derecha, más abajo. Encima, un grano muy sutil: el guiño riso. En código: `Modifier.fondoFogon(f)`.
2. **Texto blanco**; el secundario, blanco al 68 %; las líneas, blanco al 16 %.
3. **Tarjetas de vidrio:** blanco al 8 % con borde de 1 dp en blanco al 16 % y esquinas grandes (22 a 28 dp, `FormaRiso`). **Una tarjeta destacada por pantalla** (la racha, el disponible para gastar) va con degradado de las dos luces y brillo.
4. **Botones en píldora** (`FormaPildora`):
   - el principal, en el acento con texto oscuro y su brillo (`BotonRiso`);
   - el secundario, de vidrio con contorno blanco al 35 % (`BotonSecundario`);
   - las acciones rápidas, en círculos blancos con el ícono oscuro.
5. **Brillo en vez de sombra dura:** una luz suave del color, alrededor de la forma (`Modifier.brillo`). La sombra corrida riso queda para los dibujos.
6. **Navegación:** la pestaña activa va en una píldora del acento, con el ícono oscuro.
7. **Lo riso va sobre papel:** las láminas y cualquier dibujo riso van sobre una tarjeta de papel `#EFE7DA`. Ese contraste entre el papel y la noche es parte del estilo.
8. **El personaje aparece** en el arranque, en las pantallas vacías, en los logros y en "Acerca de", con un brillo de su luz. **La chispa** marca los logros: récords, rachas y metas cumplidas.
9. **"Acerca de" igual en todas:** el logotipo, el personaje, "Hecha en el Fogón · Sin anuncios · Sin rastreo · Código abierto", el enlace al código y los créditos.
10. **Ícono en Android:** el personaje a dos tintas sobre una ficha de papel. Android exige fondo en los íconos. Para el ícono temático se usa la versión de una tinta.
11. **Accesibilidad:** texto con contraste de 4,5:1 o más (blanco y acentos sobre el fondo, fondo oscuro sobre el acento), botones de 48 dp o más, y nunca un color como única señal.
12. **Siempre de noche:** las apps no cambian con el modo claro del teléfono (`FogonTema` usa oscuro por defecto). Las barras del sistema van con íconos claros.

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
