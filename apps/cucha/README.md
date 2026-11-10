# La Cucha · Fogón

<p align="center"><img src="fastlane/metadata/android/en-US/images/icon.png" width="140" alt="La Cucha"></p>

Lo que comes: calorías, proteína, tus recetas y el menú de la semana. Sin anuncios, sin rastreo, y sin internet salvo para buscar un código de barras si tú lo activas. La Cucha es como se le dice con cariño en Antioquia a la señora de la casa, la que sabe cocinar.

- Descargar: [fogonapps/la-cucha](https://github.com/fogonapps/la-cucha/releases/latest) (con [Obtainium](https://github.com/ImranR98/Obtainium) llegan solas las actualizaciones: pegar `https://github.com/fogonapps/la-cucha`).
- Novedades de cada versión: en las [versiones de La Cucha](https://github.com/fogonapps/la-cucha/releases).

## Qué hace

- **Hoy:** las calorías que quedan en un anillo, la proteína, los carbohidratos y la grasa, el agua (un vaso por toque) y las cinco comidas: desayuno, media mañana, almuerzo, algo y cena. Se puede mirar y anotar en días anteriores.
- **Anotar:** buscar entre 397 alimentos (arepa, fríjoles, patacón, bocadillo, panela, champiñones, pandebono…), las recetas y los productos propios, sin importar tildes ni plurales y por otros nombres (guineo, palomitas). Un plato se arma sumando ingredientes seguidos (huevos, y después champiñones) y se puede guardar como receta para repetirlo con un toque. Cada alimento trae porciones caseras (una arepa mediana, una taza, un cucharón) o se anota en gramos. Favoritos, lo último anotado y "lo de ayer" para repetir una comida. **Anotar rápido:** solo las calorías (y las macros si se saben) de algo que no está en la base.
- **Fibra y azúcar** de cada alimento (USDA), de las recetas y de los productos escaneados, con el total del día en Hoy.
- **Valores de USDA FoodData Central:** cada alimento dice de qué alimento de USDA salen sus valores, y avisa cuando es el más parecido (panela, kumis, cuajada…).
- **Código de barras (opcional):** se activa en Perfil. Escanea el empaque y busca el producto en Open Food Facts; si no está o no hay internet, se escriben los datos de la tabla nutricional. Lo escaneado queda guardado en el teléfono.
- **Recetas:** 129 de ejemplo (ajiaco, sancocho, bandeja paisa, changua, calentado, mondongo, cazuela de fríjoles, sobrebarriga, pescado con arroz de coco, tamal, huevos con champiñones, aguapanela, wraps, bowls…), cada una con sus calorías y macros por porción calculados de sus ingredientes. Se crean, se corrigen y se importan recetas propias (JSON).
- **Fotos de las recetas:** la tuya, o la figura de su tipo de plato.
- **Menú de la semana:** una receta por comida y día, con semana A y semana B que se turnan si quieres. Lo del menú aparece en Hoy para anotarlo con un toque.
- **Lista de mercado:** sale sola del menú (esta semana o la próxima), sumando los ingredientes; se tacha, se le agrega lo que falte y se comparte.
- **Metas:** calculadas con tus datos (fórmula de Mifflin-St Jeor, tu actividad y si quieres bajar, mantener o subir), o puestas a mano si tienes un plan de nutricionista.
- **Progreso:** días seguidos anotando, promedio de la semana, las calorías de los últimos 14 días contra la meta y la curva del peso.
- **Conectada con El Forzudo** desde el principio: si está instalado, La Cucha toma de allá el peso (y recalcula las metas) y muestra cuándo entrenaste. Sin internet: las dos apps se hablan dentro del teléfono.
- **Widget:** las calorías que quedan hoy, la proteína y un botón para anotar.
- **Tus datos son tuyos:** respaldo en JSON, y el respaldo de todo Fogón en un solo archivo.
- **Reportar un fallo o sugerir algo** desde Perfil: abre GitHub o el correo con el mensaje listo.
- **Apariencia** como en todas las apps de Fogón: Grafito (la que viene), Cocina (naranja y verde), Claro y Contraste.
- **En español e inglés.**

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Internet | Para buscar un código de barras en Open Food Facts, si activas esa opción, y para Fogón Social, si entras con tu cuenta (Tu gente). Sin las dos, la app no se conecta a nada. |
| Ubicación (aproximada o precisa) | Solo al tocar «Usar mi ubicación» al reseñar un restaurante: propone la dirección del lugar, que puedes corregir o quitar antes de publicar. Nunca se toma sola ni va en un plato sin restaurante. |
| Cámara | La pide el lector de códigos de barras la primera vez que escaneas. Sin ella la app funciona igual. |
| Datos de las apps de Fogón | Leer el peso y los entrenos de El Forzudo, y que otra app de Fogón (firmada con la misma llave) guarde o recupere los datos de La Cucha en el respaldo de todo Fogón. Ninguna otra app puede usarlo. |

- **Sin copias automáticas a la nube de Google:** solo se copian los datos al pasar a un teléfono nuevo.
- **Lo que se manda a Open Food Facts** es solo el número del código de barras.

## Créditos

- Valores de los alimentos: [USDA FoodData Central](https://fdc.nal.usda.gov/) (SR Legacy 2018 y FNDDS 2024), de dominio público (CC0 1.0).
- Productos escaneados: [Open Food Facts](https://world.openfoodfacts.org/), ODbL / DbCL.
- Lector de códigos de barras: ZXing y zxing-android-embedded, Apache 2.0.
- Recetas de ejemplo escritas para La Cucha.
