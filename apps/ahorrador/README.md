# El Ahorrador · Fogón

<p align="center"><img src="../../identidad/png/el-ahorrador.png" width="160" alt="El Ahorrador"></p>

Gastos, presupuesto y ahorro. Sin anuncios, sin rastreo, sin internet y sin pedirte las claves del banco. La app y su personaje se llaman igual: **El Ahorrador**.

- Cómo está hecha por dentro: [`ARQUITECTURA.md`](ARQUITECTURA.md).
- Cómo cambiar cosas y sacar versiones nuevas: [`GUIA.md`](GUIA.md).
- Descargar: [fogonapps/el-ahorrador](https://github.com/fogonapps/el-ahorrador/releases/latest) (con [Obtainium](https://github.com/ImranR98/Obtainium) llegan solas las actualizaciones: pegar `https://github.com/fogonapps/el-ahorrador`). F-Droid, más adelante.

## Qué hace

- **Anotar en segundos:** teclado grande propio, categorías con su color, "igual que el último" y fecha. Gasto, ingreso, pasar plata entre cuentas o ahorrar para una meta. Se corrige o se borra tocando el movimiento.
- **Cuentas:** efectivo, billetera digital, banco y tarjeta de crédito. Lo que tienes en total, menos lo que debes en tarjetas.
- **Tarjeta de crédito:**
  - cuánto debes, cupo disponible, día de corte y de pago;
  - pago mínimo e intereses si pagas solo el mínimo (aproximados: el valor real está en el extracto);
  - compras a cuotas, con cuántas van y cuánto falta;
  - "Pagar la tarjeta" es pasarle plata desde otra cuenta, no otro gasto.
- **Cuatro formas de organizarse** (se elige en el tutorial y se cambia en Perfil):
  - **Sobres:** la plata de tus cuentas se reparte en sobres por categoría; lo que se pasa se cubre con otro sobre; lo que sobra vuelve a estar por asignar. Cada sobre se puede editar (nombre, grupo, color e ícono) y se pueden crear nuevos;
  - **Regla 50/30/20:** necesidades, gustos y ahorro, con el reparto que quieras;
  - **Kakeibo:** cuatro grupos, una meta de ahorro del mes y el cierre con sus cuatro preguntas;
  - **Solo anotar.**
- **Resumen del mes:** cuánto entró, salió y guardaste, en qué se fue (una dona por categoría) y cómo van los últimos meses.
- **Extracto del banco:** importa el CSV que descargas del banco; adivina las columnas y la categoría de cada movimiento, salta lo que ya estaba y te deja revisar antes de anotar.
- **Reto de ahorro de 52 semanas** y **recordatorio diario** opcional (solo si ese día no anotaste nada).
- **Widget** con lo que tienes, lo que salió este mes y un botón para anotar.
- **Metas de ahorro:** con fecha te dice cuánto guardar cada quincena; sin fecha, a este paso en qué mes llegas.
- **Pagos fijos** (arriendo, servicios, celular), con su calendario del mes: aviso a las 9 de la mañana cuando vencen en 3 días o menos, y si quieres se anotan solos el día del pago.
- **Me deben y debo:** préstamos entre personas y créditos, con abonos.
- **4×1000:** si lo activas, la app anota sola el impuesto al sacar plata de una cuenta de banco.
- **Candado:** pide la huella o el bloqueo del teléfono al abrir (Android 10 o más, y solo si el teléfono tiene bloqueo).
- **Tus datos son tuyos:** respaldo y recuperación en JSON, exportar los movimientos en CSV para una hoja de cálculo y el respaldo de todas las apps de Fogón en un solo archivo.
- **Apariencia:** vidrio sobre una luz suave, una sola letra y el número grande de puntos. Viene en Grafito y al final del tutorial ("¡Dale vida!") se elige otro tema: el de la app, Claro o Contraste; en cada uno se eligen el papel y las dos tintas (las letras se ajustan solas para que se lean), el vidrio y la luz. El personaje toma las tintas del tema. Además, tamaño de letra, menos movimiento y más contraste.
- **En español e inglés:** sigue el idioma del teléfono, y se puede cambiar en Perfil → Idioma.
- **Reportar un fallo o sugerir algo** desde Perfil: abre GitHub o el correo con el mensaje listo.

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Notificaciones | Avisar de los pagos fijos que están por vencer y el recordatorio diario, si lo activas. Se pide al crear el primer pago fijo o al encender el recordatorio. |
| Huella o bloqueo del teléfono | El candado, si lo activas. |
| Arrancar con el teléfono | Volver a programar el aviso diario después de reiniciar. |
| Datos de las apps de Fogón | Que otra app de Fogón (firmada con la misma llave) pueda guardar o recuperar sus datos en el respaldo de todo Fogón. Ninguna otra app puede usarlo. |

- **Sin internet:** la app no tiene el permiso, así que no puede mandar nada a ningún lado.
- **Sin copias automáticas a la nube de Google:** solo se copian los datos al pasar a un teléfono nuevo.

## Por qué no se conecta al banco

En Colombia, las finanzas abiertas (Decreto 368 de 2026) obligan solo a entidades vigiladas por la Superintendencia Financiera. Una app libre, sin servidor y sin internet no puede conectarse, y así tampoco nadie tiene tus claves. En cambio, la app importa el extracto que descargas del banco (CSV), sin salir del teléfono.

## Compilar

Requiere JDK 21 y el SDK de Android (`local.properties` con `sdk.dir`).

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:ahorrador:assembleDebug
```

El APK queda en `apps/ahorrador/build/outputs/apk/debug/`.

Para la versión firmada y cómo llega al teléfono, ver [`docs/PUBLICAR.md`](../../docs/PUBLICAR.md).

## Versiones

| Versión | Fecha | Qué cambió |
|---------|-------|------------|
| 1.0 | 2026-10-21 | Primera versión: anotar con teclado propio, cuentas, tarjeta de crédito (mínimo, intereses, cuotas), sobres, 50/30/20, kakeibo con cierre del mes, metas, pagos fijos con aviso, me deben y debo, 4×1000, candado, respaldo JSON y CSV, español e inglés. Estilo de noche con luces verde y amarilla. Probada en el emulador (Android 16, en inglés y en español): tutorial, anotar un gasto, sobre asignado, tarjeta, meta con abono, pago fijo con su notificación, candado con PIN y sin bloqueo en el teléfono, cambio de idioma sin reiniciar, 0 cierres inesperados. 12 pruebas de la lógica y la traducción. Reproducible: un clon limpio compilado sin llave da el mismo APK que el firmado (`apksigcopier compare`). |
| 1.1 | 2026-10-21 | **Apariencia** (Perfil → Opciones → Apariencia), la misma de El Forzudo: cinco estilos con todos sus colores cambiables, transparencia, textura, esquinas y comodidad (tamaño de letra, menos movimiento, más contraste). En Rebelde la tarjeta grande es una caja negra; en Riso, una tinta plana. Personaje con borde de calcomanía. Probada en el emulador en los cinco estilos, y la versión firmada instalada encima de la 1.0. Reproducible desde la etiqueta pública `ahorrador-v1.1`. |
| 1.2 | 2026-10-23 | **Sistema riso**, el mismo de El Forzudo 1.8 (ver `docs/IDENTIDAD.md` → Sistema riso): papel y dos tintas con la segunda corrida, íconos y categorías gordos y en dos tintas, trama de puntos en lugar de orbes y degradados, la tarjeta grande como un bloque de tinta, fichas de categoría en manchas de tinta, la pestaña activa y el botón de anotar en manchas. Apariencia: Chicle reemplaza a Aura; papel, tintas, corrimiento y adorno; los colores cambiados en la 1.1 vuelven a los del estilo. Probada en el emulador en los cinco estilos (tutorial e Inicio). Reproducible desde la etiqueta pública `ahorrador-v1.2`. |
| 1.3 | 2026-10-24 | **Viene en Neutro**, igual que El Forzudo 1.9, y el tutorial termina en **"¡Dale vida!"** para elegir estilo (paso 4 de 4). Quien ya había elegido un estilo lo conserva. Probada en el emulador desde una instalación limpia. Reproducible desde la etiqueta pública `ahorrador-v1.3`. |
| 1.4 | 2026-10-24 | **Vidrio y puntos**, como El Forzudo 1.10: temas Grafito (el que viene), Vino (rojo y ámbar), Claro y Contraste; el saldo en puntos sobre su halo, acciones en vidrio, la meta más cercana con el personaje teñido y barra flotante. Fichas de sobres y cuentas iguales: un círculo teñido de su color con el ícono de línea. **Editar y crear sobres** (nombre, grupo, color e ícono) y 9 íconos nuevos: mascota, ejercicio, café, ropa, internet, música, juegos, regalo y un avión para viajes. **Ilustraciones 3D propias** (hechas en Blender) teñidas con el tema: las monedas en la meta de Inicio y el sobre en Sobres. Probada en el emulador en los cuatro temas. Reproducible desde la etiqueta pública `ahorrador-v1.4`. |
| 1.4.1 | 2026-10-24 | Sale el circulito con el personaje de la cabecera del Inicio. Probada en el emulador; reproducible desde la etiqueta pública `ahorrador-v1.4.1`. |
| 1.5 | 2026-10-24 | **Categorías editables en 50/30/20 y kakeibo** ("Tus categorías", con el mismo editor de los sobres). Los montos grandes de las tarjetas se achican para no cortarse. Datos de prueba (`herramientas/datos-prueba.py`), capturas de F-Droid nuevas y receta de F-Droid. **Movimiento** como en iPhone. **Dona del mes** (en qué se fue la plata) y **los últimos meses** comparados. **Calendario de pagos** fijos. **Importar el extracto del banco** (CSV): adivina las columnas y la categoría, salta lo repetido y deja revisar antes de anotar. **Reto de ahorro de 52 semanas.** **Recordatorio diario** opcional (solo si ese día no anotaste nada). **Widget** con lo que tienes y un botón para anotar. **Respaldo de todo Fogón** en un solo archivo. Créditos de las letras corregidos. Los títulos largos de las pantallas terminan en "…" en vez de cortarse, y la receta de F-Droid usa la categoría `Finance Manager` (`Money` ya no existe). Probada en el emulador. Publicada el 25-10-2026; reproducible desde la etiqueta pública `ahorrador-v1.5`. |
| 1.6 | 2026-10-25 | **El muñeco de Fogón** como personaje (lo que pidió Angel): el mismo muñeco en las cuatro apps, hecho en Blender (`identidad/muneco/`), con la cabeza de estrella en el color de la app y el cuerpo en semitono, que toma el color de las letras del tema. El ícono no cambia. Probada en el emulador (tutorial en Grafito y en Claro). |

## Preguntas frecuentes

También están dentro de la app (Perfil → Preguntas frecuentes, arreglos `preguntas` y `respuestas` de `strings.xml`). Si cambia una, se cambia en los dos lados.

**¿Se puede conectar con mi banco?**
No. En Colombia las finanzas abiertas solo obligan a entidades vigiladas, y una app libre sin servidor no puede conectarse. Además, así nadie tiene tus claves. Anotar es rápido con el teclado de la app.

**¿Dónde quedan mis datos?**
Solo en tu teléfono. La app no tiene permiso de internet. Guarda respaldos desde Perfil → Respaldo.

**¿Qué sistema elijo?**
Si quieres control, Sobres. Si quieres algo simple, la regla 50/30/20. Si te gusta escribir y pensar el mes, el kakeibo. Si solo quieres ver en qué se va, Solo anotar. Lo puedes cambiar cuando quieras.

**¿Cómo anoto la tarjeta de crédito?**
Las compras con la tarjeta son gastos desde la cuenta de la tarjeta (con sus cuotas). Pagar la tarjeta es pasar plata del banco a la tarjeta: no es otro gasto, ya lo contaste al comprar.

**¿Por qué el pago mínimo no cuadra con mi extracto?**
Porque cada banco lo calcula a su manera. La app lo aproxima con el porcentaje que pongas en la cuenta; el valor real está en tu extracto.

**¿Qué es el 4×1000?**
El gravamen a los movimientos financieros: 4 pesos por cada 1.000 que salen de una cuenta de banco. Si lo activas en Perfil, la app lo anota sola. Muchas cuentas de ahorro tienen una exenta.

**¿En qué se diferencia ahorrar de pasar plata?**
Ahorrar es guardar para una meta: la plata sale de lo disponible del mes y se suma a la meta. Pasar es mover plata entre tus cuentas, sin que cambie nada.

## Pendiente

- Probarla en el teléfono real.
- Para versiones siguientes: leer en el teléfono los avisos del banco para anotar solo, importar el extracto, widget para anotar rápido y sincronización opcional como en El Forzudo.

## Contacto

Fallos o ideas: desde la app (Perfil → Reportar un fallo o sugerir algo), en [GitHub](https://github.com/fogonapps/fogon/issues) o a **fogonapps@gmail.com**.

## Créditos

- Ideas de presupuesto de uso libre: el método de sobres, la regla 50/30/20 y el kakeibo.
- Letras: Bricolage Grotesque y Doto (SIL Open Font License).
- Licencia del código: GPL-3.0 o posterior.
