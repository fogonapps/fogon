# Revisión de pantallas

Solo para pruebas (no va en ninguna APK). Cada app tiene `RecorridoPantallasTest`: abre todas sus pestañas y
pantallas (las encuentra solas con `pantallasDe`) en Robolectric, con gráficos nativos y una pantalla muy alta, y
`revisar` marca:

- **pegados**: dos botones uno encima del otro con menos de 4 dp entre ellos;
- **encimados**: dos cosas tocables que se tapan (más de 2 dp);
- **chico**: algo tocable de menos de 32 dp;
- **sin nombre**: algo tocable sin texto ni descripción para el lector de pantalla;
- **falla**: una pantalla que se cae al abrirse.

Corre con `./fogon probar <app>`. Una pantalla nueva entra sola al recorrido; si necesita datos para abrirse, se
agregan al estado de ejemplo de la prueba.

## Antes de publicar, a mano (dos cuentas, servidor local)

Obligatorio en **cada** versión, aunque el cambio sea chico: las pruebas automáticas no inician sesión, y lo social
solo falla de verdad con dos cuentas reales. Se marca cada paso; lo que falle se arregla antes de publicar.

**Montarlo:** una copia del servidor con el correo impreso en la consola, corriendo en el puerto 8080; en las apps de
prueba, `ClienteSocial.SERVIDOR` → `http://10.0.2.2:8080` y `usesCleartextTraffic="true"` (**revertir antes del
commit**). Una cuenta (con equipo y MFA) en El Cajón y otra en otra app, en el mismo emulador, amigas entre sí.

1. **Inicio:** «De tu gente» aparece al abrir (lo guardado) y sin entrar a «Para ti»; «Te podría gustar» llega.
2. **Para ti:** la tarjeta de gustos no se mueve mientras carga; «Esta semana en tu red» y «Gente con tus gustos».
3. **Historias:** subir una (texto, foto); verla desde la otra cuenta; **responderla** (no se cierra; llega al chat);
   me gusta, silenciar; el nombre y los botones se leen enteros.
4. **Mensajes:** en las dos direcciones, «Visto», una foto (con girar), una reacción, una **nota de voz de más de
   30 s**; el aviso de un mensaje abre ese chat.
5. **Perfil ajeno:** foto, banner, fondo y marco, «te sigue», cifras sin chocar con el banner, «Mensaje», canción
   (suena, se silencia y se calla con las historias), menú ⋯ (copiar, compartir, bloquear con confirmación, reportar).
   En «Lo tuyo», tocar una película, serie o artista abre su ficha.
6. **Perfil propio:** sin colores de la app sobre el fondo de la tienda; @ una sola vez; «Editar perfil» con la tienda
   y «Quién lo vería» (cambiar una parte y ver que el servidor la tiene).
7. **Tienda:** chispas del día, comprar y ponerse un fondo; se ve desde la otra cuenta.
8. **Listas en común:** crear con la otra cuenta, agregar, votar, quitar; se llega desde Listas aunque no haya listas propias.
9. **Moderación** (cuenta del equipo): reportar una historia desde la otra cuenta; se ve en la cola (foto o texto);
   advertir; la historia se retira, llega el correo y la cola queda vacía. Un reporte propio no ofrece botones.
10. **Cumpleaños de hoy:** el aviso en los mensajes de la otra cuenta.
