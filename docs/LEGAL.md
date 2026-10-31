# Cómo hacer esto sin problemas legales

Esto no es asesoría legal: es lo que se averiguó (octubre de 2026) y las reglas que seguimos para no molestar a nadie. Si el proyecto crece, conviene consultar a un abogado de propiedad intelectual.

## 1. Código hecho por IA y derechos de autor

- **Colombia:** la Decisión Andina 351 de 1993 dice que el autor es una *persona natural*. Según análisis jurídicos colombianos, lo que genera una IA por sí sola no tiene derecho de autor y quedaría en el dominio público. Lo que aporta una persona (decidir qué se hace, el diseño, elegir, ordenar y corregir) sí puede estar protegido.
- **Estados Unidos:** la Oficina de Derechos de Autor (informe parte 2, enero de 2025) llegó a lo mismo: dar instrucciones (*prompts*) no basta para ser autor, pero los aportes humanos sí se protegen y se evalúan caso por caso.

**Qué significa para nosotros:** la GPL funciona gracias al derecho de autor. En las partes que haya escrito la IA sin aporte humano, su fuerza es incierta. La usamos igual porque:
- protege todo lo que sí es aporte humano;
- deja clara nuestra intención: que todo siga siendo abierto;
- no le quita nada a nadie: en el peor caso, el código es todavía más libre (dominio público).

Somos honestos con esto. Cada app dice en su README que fue hecha con IA.

## 2. Que la IA no copie código ajeno

Una IA puede reproducir trozos de código existente con otra licencia. Para evitarlo:
- usamos solo bibliotecas con licencias compatibles con la GPL-3.0 (Apache-2.0, MIT, BSD y LGPL lo son) y las listamos en cada app;
- no le pedimos a la IA que "copie" o "clone" ninguna app;
- si un fragmento se parece a un proyecto conocido, se reescribe o se atribuye.

## 3. Marcas, nombres e íconos

- Nombres propios, que no se parezcan a marcas existentes. Antes de elegir uno, se busca en la tienda y en el registro de marcas de la SIC (https://sipi.sic.gov.co).
- Íconos e imágenes propios o con licencia libre (por ejemplo, Material Symbols, que es Apache-2.0).
- Nunca usar logos ni capturas de otras apps, ni decir "alternativa a [marca]" en el nombre o en el ícono.

**Nombre "Fogón" — revisado en el SIPI de la SIC (11 de octubre de 2026, búsqueda `*FOGON*`, 60 resultados):**
- **Clase 9 (software): ninguna marca.**
- **Clase 42:** ninguna vigente para software. Las tres que aparecen son "Fogón Campesino" (enseña comercial de saborizantes de Quala, del año 2000), "El Fogonazo" (caducada en 1994) y "Fogón de Leña" (restaurante, caducada en 2004).
- El resto son de comida (clases 29 y 30, sobre todo la línea **"Del Fogón" de Quala**, una marca de condimentos muy conocida) y de restaurantes (clase 43).
- **Reglas que seguimos:** usar siempre "Fogón" solo, nunca "Del Fogón"; no usar imágenes de comida, condimentos ni empaques que recuerden a Quala; presentarlo siempre como software.
- Segunda búsqueda, `*FOGÓN*` con tilde (29 resultados): tampoco hay nada vigente en las clases 9 ni 42. La más cercana es la clase 38 ("El Fogón de D'Artagnan", telecomunicaciones), que fue negada. Hay otra "DelFogón" (Quala, clase 29).
- Es una revisión básica hecha por nosotros, no un concepto de abogado. Registrar la marca propia (clases 9 y 42) es opcional y queda para más adelante.

## 4. Datos personales

- La Ley 1581 de 2012 (protección de datos en Colombia) regula a quien recolecta y trata datos de otras personas. Nuestras apps **no recolectan nada**: los datos se quedan en el teléfono del usuario. Así no somos "responsables del tratamiento" de nada y el riesgo es mínimo.
- Igual, cada app tiene una política de privacidad de una línea: "Esta app no recolecta, envía ni comparte ningún dato".

## 5. Distribución en Android (situación a octubre de 2026)

Google anunció que todas las apps instaladas en teléfonos Android certificados deberán venir de un **desarrollador verificado**, aunque se instalen por fuera de la Play Store.

- **Calendario:** empieza a aplicarse en septiembre de 2026 en Brasil, Indonesia, Singapur y Tailandia. El resto del mundo, incluida Colombia, sigue desde 2027.
- **Cuentas gratis para estudiantes y aficionados:** sin documento de identidad y sin pago, pero solo permiten compartir apps con **hasta 20 dispositivos**.
- **"Advanced flow":** permite instalar apps de desarrolladores no verificados después de activar el modo desarrollador, reiniciar y esperar 24 horas.
- **ADB:** según Google, los desarrolladores pueden seguir instalando sus propias apps en sus propios dispositivos con ADB.
- **F-Droid** considera esta medida una amenaza seria y publicó una carta abierta en contra (febrero de 2026).

**Qué hacemos:**
1. Para uso propio: instalar desde el PC con ADB. Funciona hoy y seguirá funcionando.
2. Para compartir con pocas personas: cuenta gratuita de distribución limitada (hasta 20 dispositivos).
3. Para el público: publicar en F-Droid y en GitHub Releases. Si en el futuro hiciera falta, verificar la cuenta. Esta decisión se toma cuando llegue el momento.

## 6. Lo que usamos de otros (revisión del 18 de octubre de 2026)

Que una app sea gratis no cambia nada: el derecho de autor y las licencias aplican igual.

- **Fotos de ejercicios (free-exercise-db): NO son libres.** free-exercise-db se presenta como de dominio público, pero sale de `wrkout/exercises.json`, y ese proyecto dice en su `CONTRIBUTING.md` que las fotos *"have been scrapped off the internet, therefore I do not own the copyright for these images"*. Una búsqueda inversa de imágenes apunta a bodybuilding.com como una de las fuentes (issue #305 de ese repo). El autor de free-exercise-db reconoce que no sabe de dónde vienen (su issue #2). **Regla:** antes de compartir cualquier app con fotos de ejercicios, cambiarlas por dibujos con licencia clara.
- **Dibujos de ejercicios (Everkinetic):** su repositorio (github.com/everkinetic/data) los publica con CC BY-SA 4.0, y su sitio original decía en 2012 que los liberaba con Creative Commons para cualquier uso, incluidas apps (comprobado en el Internet Archive). Nuestras láminas son modificaciones, así que también van con CC BY-SA 4.0 y con crédito a Everkinetic. Dos dibujos (aperturas y press de hombro en máquina) no están en su repositorio sino en Wikimedia Commons, subidos con el autor Everkinetic y CC BY-SA 3.0; esa licencia permite publicar una modificación con una versión posterior (§4b), así que las láminas siguen siendo CC BY-SA 4.0, con crédito a Everkinetic y a Commons. Las imágenes que trae wger aparte de las de Everkinetic son fotos subidas por usuarios, sin un origen claro: no se usan.
- **Datos de alimentos (La Cucha, 24-10-2026):** los valores salen de **USDA FoodData Central** (SR Legacy 2018 y FNDDS 2024). USDA los publica como dominio público (CC0 1.0) y pide que se le dé crédito: va en Acerca de, Licencias y en cada alimento (con su número FDC). Los nombres en español son propios; las porciones caseras salen de los pesos medidos por USDA (`food_portion.csv`, el mismo CC0) cuando los trae, y si no, son pesos típicos (25-10-2026). No se usa la tabla colombiana del ICBF porque su licencia no es clara.
- **Open Food Facts (La Cucha):** la base tiene licencia ODbL, cada producto DbCL y las fotos CC BY-SA. Solo se consulta al escanear (si la persona lo activa) y cada producto traído muestra el crédito con el enlace; no se muestran fotos ni se guarda una copia de la base. Se manda solo el número del código, con un `User-Agent` propio como pide su API.
- **Recetas de ejemplo (La Cucha):** escritas para la app con platos tradicionales (que no tienen dueño); no se copian textos de libros ni páginas. Las recetas personales de Angel no entran al repo.
- **Fuentes de El Cajón (25-10-2026):** **TMDB** (películas, series y animes): licencia gratis para uso no comercial, con clave personal e intransferible, así que cada persona pone la suya (nunca va una clave en el código); pide su logo (menos destacado que la marca de la app) y el aviso "usa TMDB y su API, pero no está respaldada, certificada ni aprobada por TMDB", que van en Acerca de y en la pantalla de la clave; los datos no se guardan más de 6 meses sin renovarlos. **Open Library** (libros): datos abiertos del Internet Archive. **MusicBrainz** (álbumes y artistas): núcleo de datos CC0; pide User-Agent con contacto y una consulta por segundo. Las **carátulas** (Cover Art Archive) y los pósteres son de sus dueños (una portada que la persona sube desde su teléfono, desde la 1.8, se queda en ese teléfono): se muestran desde su fuente y no van en el repo ni en las capturas de la tienda (las capturas usan obras sin portada). Los importadores de Letterboxd y Goodreads solo leen el archivo que la persona exporta de esas apps. **Fotos de artistas y escritores:** de Wikimedia Commons, encontradas por Wikidata (datos CC0; desde la 1.4 también los escritores, por su id de Open Library). Las **biografías breves de los artistas** (desde la 1.7) son la introducción de su artículo de Wikipedia (por Wikidata), con licencia CC BY-SA 4.0: se muestran tal cual, con el crédito "Texto de Wikipedia, con licencia CC BY-SA 4.0" en la ficha y en Licencias; no van en el repo. Las **fotos de directores** son las de TMDB, como sus pósteres, bajo la misma licencia y aviso de TMDB. Cada foto tiene su licencia libre y su autor; El Cajón guarda el crédito ("Autor · Licencia · Wikimedia Commons") con la foto y lo muestra en la ficha del artista. No se guardan en el repo ni salen en las capturas.
- **Imágenes generadas con IA:** si alguna vez se usan, se descarta cualquiera que traiga texto o marcas de agua (en una prueba salió una con la marca de un banco de imágenes, aprendida de memoria por el modelo).
- **Estándares de fuerza:** desde la versión 1.2 (Mi Gym, hoy El Forzudo) son propios, calibrados con OpenPowerlifting (dominio público). Ver `apps/forzudo/ESTANDARES.md`.
- **Textos de licencia dentro de la app:** OFL (letras), MIT (modelo muscular), Apache 2.0 (bibliotecas, también ZXing en La Cucha) y MPL 2.0 (la Public Suffix List que trae OkHttp) exigen que el texto vaya con la app. Cada app tiene la pantalla **Acerca de → Licencias**, que viene de `core/identidad` (`Licencias.kt`, textos en `assets/licencias/`). Al agregar una biblioteca, revisar su licencia en el POM y sumarla a la lista.
- **Historial de git:** los commits viejos tienen las fotos y la tabla de strengthlevel.com. Por eso el repo público (`fogonapps/fogon`, desde el 21-10-2026) empezó con un commit nuevo, sin ese historial, y nunca se sube la rama `main` del repo de trabajo (ver `PUBLICAR.md`).

## 7. Aviso de salud

Las apps de ejercicio y de comida dan sugerencias que pueden afectar la salud. Cada una lleva un aviso claro: "no es consejo médico; consulta a un profesional si tienes una lesión o una condición de salud; si sientes dolor, mareo o falta de aire, detente". Va en Acerca de y en el tutorial. La GPL ya dice que el programa viene sin garantía (secciones 15 y 16); el aviso lo explica en palabras simples.

## 8. Competencia desleal y patentes

- **Colombia, Ley 256 de 1996:** no castiga regalar algo ni hacerlo mejor. Castiga actos concretos, entre ellos confundir sobre el origen (art. 10), desacreditar con afirmaciones falsas (art. 12), compararse con datos falsos o no comprobables (art. 13) e imitar con exactitud creando confusión (art. 14). Nuestras reglas (no nombrar a nadie, no atacar, no copiar diseños ni marcas) cubren eso.
- **Patentes:** en la Comunidad Andina el software "como tal" no es invención patentable (Decisión 486, art. 15, literal e). En Estados Unidos sí hay patentes de software. Para un proyecto gratuito el riesgo es bajo, pero no es cero.

Fuentes:
- https://android-developers.googleblog.com/2026/03/android-developer-verification.html
- https://support.google.com/android-developer-console/answer/16561738
- https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- https://www.taylorwessing.com/en/insights-and-events/insights/2026/06/ai-and-assisted-programming-in-open-source
- https://www.asuntoslegales.com.co/actualidad/si-crea-una-obra-usando-inteligencia-artificial-de-quien-son-los-derechos-de-autor-4100317
- https://github.com/wrkout/exercises.json/blob/master/CONTRIBUTING.md
- https://github.com/wrkout/exercises.json/issues/305
- https://github.com/yuhonas/free-exercise-db/issues/2
- https://normas.cra.gov.co/Gestor/Docs/ley_0256_1996.htm
- https://www.wipo.int/wipolex/es/text/583541
