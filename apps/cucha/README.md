# La Cucha · Fogón

<p align="center"><img src="../../identidad/png/la-cucha.png" width="160" alt="La Cucha"></p>

Lo que comes: calorías, proteína, tus recetas y el menú de la semana. Sin anuncios, sin rastreo, y sin internet salvo para buscar un código de barras si tú lo activas. La Cucha es como se le dice con cariño en Antioquia a la señora de la casa, la que sabe cocinar.

- Cómo está hecha por dentro: [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Cómo cambiar cosas y sacar versiones nuevas: [`GUIA.md`](GUIA.md).
- Descargar: [fogonapps/la-cucha](https://github.com/fogonapps/la-cucha/releases/latest) (con [Obtainium](https://github.com/ImranR98/Obtainium) llegan solas las actualizaciones: pegar `https://github.com/fogonapps/la-cucha`). F-Droid, más adelante.

## Qué hace

- **Hoy:** las calorías que quedan en un anillo, la proteína, los carbohidratos y la grasa, el agua (un vaso por toque) y las cinco comidas: desayuno, media mañana, almuerzo, algo y cena. Se puede mirar y anotar en días anteriores.
- **Anotar:** buscar entre 397 alimentos (arepa, fríjoles, patacón, bocadillo, panela, champiñones, pandebono…), las recetas y los productos propios, sin importar tildes ni plurales y por otros nombres (guineo, palomitas). Un plato se arma sumando ingredientes seguidos (huevos, y después champiñones) y se puede guardar como receta para repetirlo con un toque. Cada alimento trae porciones caseras (una arepa mediana, una taza, un cucharón) o se anota en gramos. Favoritos, lo último anotado y "lo de ayer" para repetir una comida.
- **Valores de USDA FoodData Central:** cada alimento dice de qué alimento de USDA salen sus valores, y avisa cuando es el más parecido (panela, kumis, cuajada…).
- **Código de barras (opcional):** se activa en Perfil. Escanea el empaque y busca el producto en Open Food Facts; si no está o no hay internet, se escriben los datos de la tabla nutricional. Lo escaneado queda guardado en el teléfono.
- **Recetas:** 55 de ejemplo (ajiaco, sancocho, bandeja paisa, changua, calentado, huevos con champiñones, aguapanela, wraps, bowls…), cada una con sus calorías y macros por porción calculados de sus ingredientes. Se crean, se corrigen y se importan recetas propias (JSON).
- **Menú de la semana:** una receta por comida y día, con semana A y semana B que se turnan si quieres. Lo del menú aparece en Hoy para anotarlo con un toque.
- **Lista de mercado:** sale sola del menú (esta semana o la próxima), sumando los ingredientes; se tacha, se le agrega lo que falte y se comparte.
- **Metas:** calculadas con tus datos (fórmula de Mifflin-St Jeor, tu actividad y si quieres bajar, mantener o subir), o puestas a mano si tienes un plan de nutricionista.
- **Progreso:** días seguidos anotando, promedio de la semana, las calorías de los últimos 14 días contra la meta y la curva del peso.
- **Conectada con El Forzudo** desde el principio: si está instalado, La Cucha toma de allá el peso (y recalcula las metas) y muestra cuándo entrenaste. Sin internet: las dos apps se hablan dentro del teléfono.
- **Widget:** las calorías que quedan hoy, la proteína y un botón para anotar.
- **Tus datos son tuyos:** respaldo en JSON, y el respaldo de todo Fogón en un solo archivo.
- **Apariencia** como en todas las apps de Fogón: Grafito (la que viene), Cocina (naranja y verde), Claro y Contraste.
- **En español e inglés.**

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Internet | Solo para buscar un código de barras en Open Food Facts, si activas esa opción en Perfil. Apagada, la app no se conecta a nada. |
| Cámara | La pide el lector de códigos de barras la primera vez que escaneas. Sin ella la app funciona igual. |
| Datos de las apps de Fogón | Leer el peso y los entrenos de El Forzudo, y que otra app de Fogón (firmada con la misma llave) guarde o recupere los datos de La Cucha en el respaldo de todo Fogón. Ninguna otra app puede usarlo. |

- **Sin copias automáticas a la nube de Google:** solo se copian los datos al pasar a un teléfono nuevo.
- **Lo que se manda a Open Food Facts** es solo el número del código de barras.

## Compilar

Requiere JDK 21 y el SDK de Android (`local.properties` con `sdk.dir`).

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:cucha:assembleDebug
```

El APK queda en `apps/cucha/build/outputs/apk/debug/`. Para la versión firmada, ver [`docs/PUBLICAR.md`](../../docs/PUBLICAR.md).

## Versiones

| Versión | Fecha | Qué cambió |
|---------|-------|------------|
| 1.0 | 2026-10-24 | Primera versión: Hoy con anillo de calorías, macros, agua y cinco comidas; anotar con 185 alimentos de USDA y porciones caseras; código de barras opcional con Open Food Facts; 16 recetas colombianas; menú de la semana A/B; lista de mercado; metas calculadas o a mano; progreso; conexión con El Forzudo; widget; respaldo; español e inglés. Probada en el emulador (Android 16): tutorial, anotar un alimento y un producto propio, agua, recetas, menú, mercado, progreso con el peso de El Forzudo, escáner (cámara), widget, importar recetas y cambio de idioma. 10 pruebas de la lógica y la traducción. El 25-10-2026: ficha de F-Droid (textos, ícono y 7 capturas por idioma), datos de prueba (`herramientas/datos-prueba.py`), receta de F-Droid y los títulos largos (como "Ajiaco (chicken and potato soup)") en dos líneas en vez de cortarse. Publicada el 25-10-2026; reproducible desde la etiqueta pública `cucha-v1.0`. |
| 1.1 | 2026-10-25 | **Base de 397 alimentos** (antes 185), con las porciones medidas por USDA cuando las trae (huevo grande 50 g, champiñón mediano 18 g…): más carnes, pescados, huevos de varias formas, verduras, frutas, salsas y condimentos, comidas hechas, bebidas y snacks. **55 recetas** (antes 16), entre ellas huevos con champiñones, omelet, bandeja paisa, aguapanela, arroz chino, tacos, wraps y bowls. **Buscar en plural y por otros nombres** ("huevos", "palomitas", "guineo"). **Armar un plato:** después de anotar, la búsqueda queda abierta para sumar otro ingrediente, con lo que llevas en esa comida arriba, "Listo" y "Guardar como receta". Las flechas del día quedan quietas en una píldora (antes la de adelante se salía de la pantalla con nombres largos). Al volver de un alimento sin anotarlo, la búsqueda sigue escrita. Probada en el emulador. |

## Créditos

- Valores de los alimentos: [USDA FoodData Central](https://fdc.nal.usda.gov/) (SR Legacy 2018 y FNDDS 2024), de dominio público (CC0 1.0).
- Productos escaneados: [Open Food Facts](https://world.openfoodfacts.org/), ODbL / DbCL.
- Lector de códigos de barras: ZXing y zxing-android-embedded, Apache 2.0.
- Recetas de ejemplo escritas para La Cucha.
