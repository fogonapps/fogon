# Publicar una app de Fogón

Cómo se compila la versión firmada de una app y cómo llega al teléfono. Hoy aplica a El Forzudo; las próximas apps siguen lo mismo.

## La llave de firma

- **Está fuera del repo:** `~/.fogon/firma/`, con `fogon-firma.p12` (PKCS12, alias `fogon`) y `clave.txt`, ambos en `chmod 600`.
- **Certificado:** CN=Fogon, SHA-256 `ce5406882e361cf18e3ece18fa348769e34d8fa6cc8dd53f74a77289a993d8e5`.
- **Una sola llave firma todas las apps de Fogón.** Android solo deja instalar una actualización encima si viene firmada con la misma llave.
- **Si se pierde, ninguna app se puede volver a actualizar:** habría que desinstalar y perder los datos. Por eso entra en el backup de restic, y conviene guardar una copia aparte en KeePass.
- **Para usar otra carpeta**, pasar `-PfogonFirma=<carpeta>` a Gradle.
- **Si la llave no está**, `build.gradle.kts` igual compila la versión `release`, pero sin firma (no se puede instalar).

## Compilar

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:assembleRelease
```

Antes, subir `versionCode` (+1) y `versionName` en `apps/<app>/build.gradle.kts`.

El APK queda en `apps/forzudo/build/outputs/apk/release/`. Para comprobar la firma:

```
~/Android/Sdk/build-tools/37.0.0/apksigner verify --print-certs <apk>
```

## Probar antes de entregar

1. En el emulador `fogon-prueba`, instalar la versión firmada encima de la anterior. Así se prueba que la actualización no borra datos.
2. Recorrer el flujo completo: la lista de El Forzudo está en `apps/forzudo/README.md` → Versiones.
3. Confirmar que no hubo cierres inesperados en `adb logcat -b crash`.

## Llevarla al teléfono

- **Por cable (lo más directo):** con la depuración USB activada en el teléfono, `adb install -r <apk>`. Actualiza encima sin perder datos.
- **Por Syncthing:** copiar el APK como `~/Fogon/<App>-<versión>.apk`. La carpeta "Fogón" (id `fogon-apps`) solo envía desde el PC y está compartida con el S24. En el teléfono se abre el APK y se instala.

## Publicar en GitHub

**Público desde el 21-10-2026:** [github.com/fogonapps/fogon](https://github.com/fogonapps/fogon). La organización `fogonapps` la creó Angel; él es admin.

El repo de trabajo (`~/Projects/fogon`, rama `main`) **no se sube**: sus commits viejos tienen las fotos que no son libres y la tabla de strengthlevel.com (ver `LEGAL.md` §6). Lo público sale de la rama `publico`, que empezó con un solo commit limpio y no comparte historial con `main`. El remoto `publico` está configurado para subir solo esa rama (`publico` → `main` en GitHub), así que `git push publico` nunca sube `main`.

Los commits públicos van firmados con el correo privado de GitHub de Angel, `257926308+1015Angel@users.noreply.github.com`, para no mostrar su Gmail.

**Subir una versión nueva:**
1. Revisar lo que cambió desde la última vez (`git diff publico main --stat`): sin secretos, datos personales ni material sin licencia libre.
2. Pasar a `publico` el estado de `main`:
   ```
   git checkout publico
   git read-tree -u --reset main
   git -c user.email=257926308+1015Angel@users.noreply.github.com commit -m "El Forzudo <versión>"
   git push publico
   git checkout main
   ```
3. Crear la versión con el APK firmado: `gh release create forzudo-v<versión> <apk> --repo fogonapps/fogon --target main --title "El Forzudo <versión>"`, con notas que incluyan el SHA-256 del APK y el del certificado. La etiqueta lleva la app delante porque en el repo va a haber varias apps.

**Obtainium (en el teléfono):** Añadir aplicación → URL origen `https://github.com/fogonapps/fogon` → en "Filtrar por título de versión" poner `El Forzudo`, para que siga solo a esta app cuando haya otras en el repo.

F-Droid va después de GitHub, con sus propios requisitos (ver `TECNOLOGIA.md`). Se revisan contra su documentación cuando llegue el momento.
