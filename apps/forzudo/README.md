# El Forzudo · Fogón

<p align="center"><img src="fastlane/metadata/android/en-US/images/icon.png" width="140" alt="El Forzudo"></p>

Registro de entrenos de gimnasio. Sin anuncios, sin rastreo, gratis de verdad. Su personaje es una bolita con los bracitos levantados. Hasta la versión 1.2.1 se llamó **Mi Gym** (`io.github.fogonapps.migym`).

- Descargar: [fogonapps/el-forzudo](https://github.com/fogonapps/el-forzudo/releases/latest) (con [Obtainium](https://github.com/ImranR98/Obtainium) llegan solas las actualizaciones: pegar `https://github.com/fogonapps/el-forzudo`).
- Novedades de cada versión: en las [versiones de El Forzudo](https://github.com/fogonapps/el-forzudo/releases).

## Qué hace

- **Entreno en vivo:**
  - lo que hiciste la vez anterior;
  - metas automáticas ("sube el peso hoy", "busca +1 rep");
  - descanso con aviso y vibración;
  - récords marcados con la chispa;
  - aviso cuando llevas 3 sesiones sin progresar, con alternativas;
  - calentamiento sugerido (barra sola, 40, 60 y 80 %) y qué discos poner a cada lado;
  - cancelar o terminar en cualquier momento, también desde la barra del entreno en curso.
- **Historial:** cada entreno se abre para ver todas sus series y corregirlas (peso, repeticiones, agregar o quitar series, fecha), o borrarlo.
- **6 programas listos** (de 3 a 6 días) y **editor de rutinas** (series, repeticiones, descanso, orden, cambiar o quitar ejercicios).
- **65 ejercicios** con técnica y enlace a videos; 57 con lámina de inicio y final.
- **Rangos de fuerza** de Hierro a Diamante, por ejercicio, por músculo y general, con mapa del cuerpo. La tabla es propia, calibrada con OpenPowerlifting (ver [`ESTANDARES.md`](ESTANDARES.md)); las dominadas cuentan por repeticiones. También las series por músculo en la semana.
- **Constancia:**
  - racha de semanas;
  - semanas del mes;
  - mapa anual de días entrenados.
- **Medallas:** 16, por entrenos, semanas seguidas, toneladas levantadas y rangos; las nuevas aparecen al terminar un entreno.
- **Peso corporal** con gráfica. La Cucha (la app de comida de Fogón) lo lee para ajustar tus calorías.
- **Widget** con la racha de semanas y los entrenos de esta semana.
- **Tus datos son tuyos:**
  - se guardan en el teléfono;
  - exportar e importar en JSON;
  - sincronización opcional con un Gist secreto de tu propia cuenta de GitHub;
  - el respaldo de todas las apps de Fogón en un solo archivo.
- **Apariencia:** vidrio sobre una luz suave, una sola letra y el número grande de puntos. Viene en Grafito y al final del tutorial ("¡Dale vida!") se elige otro tema: el de la app, Claro o Contraste; en cada uno se eligen el papel y las dos tintas (las letras se ajustan solas para que se lean), el vidrio y la luz. El personaje de la app cambia de cara según el momento. Además, tamaño de letra, menos movimiento y más contraste.
- **En español e inglés:** sigue el idioma del teléfono, y se puede cambiar en Perfil → Idioma.
- **Reportar un fallo o sugerir algo** desde Perfil: abre GitHub o el correo con el mensaje listo.

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Internet | Para Fogón Social, si entras con tu cuenta (Tu gente, lo que compartes y la copia de tu cuaderno, cifrada con tu contraseña), y para la sincronización con tu Gist, si la activas. Sin las dos, la app no se conecta a nada. |
| Notificaciones | El aviso de "descanso terminado". Se pide al empezar el primer entreno. |
| Alarmas exactas | Que ese aviso llegue a tiempo. Si lo niegas, se usa una alarma aproximada. |
| Vibración | Al marcar una serie y al terminar el descanso. |
| Datos de las apps de Fogón | Que otra app de Fogón (firmada con la misma llave) pueda guardar o recuperar sus datos en el respaldo de todo Fogón. Ninguna otra app puede usarlo. |

- **Sin copias automáticas a la nube de Google:** solo se copian los entrenos al pasar los datos a un teléfono nuevo.
- **El token de GitHub va cifrado** con una llave del Keystore de Android y nunca sale del teléfono.

## Preguntas frecuentes

También están dentro de la app (Perfil → Preguntas frecuentes, en `ui/Pantallas.kt`). Si cambia una, se cambia en los dos lados.


**¿Por qué los dibujos de los ejercicios solo tienen figuras de hombre?**
Los dibujos salen de Everkinetic, un banco de ilustraciones de ejercicios con licencia libre, y todas sus figuras son de hombre. No encontramos ilustraciones libres con figuras de mujer. Preferimos no poner algo a medias. El ejercicio y el músculo que trabaja son los mismos para todas las personas. Si alguien quiere dibujar las versiones de mujer con licencia libre, son bienvenidas.

**¿Por qué algunos ejercicios no tienen dibujo?**
Usamos solo dibujos con licencia clara, y para esos ejercicios todavía no hay uno. Van a llegar en una actualización.

**¿Qué significan los colores?**
Cada grupo muscular tiene su color, el mismo en toda la app: la mancha del dibujo marca el músculo que trabaja. La sombra del dibujo dice la zona: rosa para el tren superior, amarillo para los brazos y azul para las piernas.

**¿Por qué no hay fotos?**
Las fotos que usaba la app venían de un banco que se presentaba como libre, pero en realidad eran fotos sacadas de internet sin permiso de sus dueños. Por eso se cambiaron por dibujos con licencia clara.

**¿De dónde salen los rangos de fuerza?**
De una tabla propia de Fogón, calibrada con resultados reales de competencias de powerlifting (OpenPowerlifting, de dominio público). Están explicados en [`ESTANDARES.md`](ESTANDARES.md).

**¿Mis datos salen del teléfono?**
No. Todo se guarda en el teléfono. Solo si conectas tu propio GitHub, los entrenos se copian a un archivo privado de tu cuenta.

**¿Por qué antes se llamaba Mi Gym?**
Hasta la versión 1.2.1 se llamó así. Desde la 1.3 se llama El Forzudo.

## Contacto

Fallos o ideas: desde la app (Perfil → Reportar un fallo o sugerir algo), en [GitHub](https://github.com/fogonapps/fogon/issues) o a **fogonapps@gmail.com**.

## Créditos

- Estándares de fuerza: modelo de Fogón con datos de [OpenPowerlifting](https://www.openpowerlifting.org) (dominio público).
- Dibujos de los ejercicios: [Everkinetic](https://github.com/everkinetic/data) (CC BY-SA 4.0; aperturas y press de hombro en máquina desde [Wikimedia Commons](https://commons.wikimedia.org/wiki/Category:Weight_training_diagrams), CC BY-SA 3.0), pasados a lámina riso por Fogón; las láminas también son CC BY-SA 4.0.
- Modelo muscular: [react-body-highlighter](https://github.com/giavinh79/react-body-highlighter) (MIT, © 2020 GV79), que tomó el dibujo de [react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter) (MIT, © 2022 ELABBASSI Hicham).
- Letras: Bricolage Grotesque y Doto (SIL Open Font License).
- Licencia del código: GPL-3.0 o posterior.
