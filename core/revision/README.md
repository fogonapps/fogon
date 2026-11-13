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

1. Una cuenta en El Cajón y otra en otra app, en el mismo emulador.
2. Ver el perfil de la otra cuenta: foto, banner, fondo y marco de la tienda, cifras, «Mensaje», canción y silencio.
3. Mensajes en las dos direcciones, «Visto», una foto (con el paso de girar), reacción.
4. Una historia y una nota; la nota se ve en la otra app.
5. Tienda: las chispas del día, comprar y ponerse un fondo; se ve desde la otra cuenta.
6. Cumpleaños de hoy: el aviso en los mensajes de la otra cuenta.
