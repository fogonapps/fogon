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

## Publicar en GitHub (primera vez)

El repo de trabajo (`~/Projects/fogon`) **no se sube tal cual**: sus commits viejos tienen las fotos que no son libres y la tabla de strengthlevel.com (ver `LEGAL.md` §6). El repo público empieza con un historial nuevo, hecho desde la versión actual.

1. **Angel crea la organización** `fogonapps` (gratis) en github.com/account/organizations/new. GitHub no deja crearla por API. El nombre estaba libre el 20-10-2026.
2. **Revisar la copia que se va a publicar.** `git archive HEAD` en una carpeta vacía, y buscar ahí: secretos (tokens, llaves, `clave.txt`), datos personales (correo, rutas `/home/...`), fotos y la tabla vieja. Revisión hecha el 20-10-2026 sobre `ff34d0f`: limpia (268 archivos, 9,6 MB).
3. **Crear el repo público** `fogonapps/fogon` con un solo commit desde esa copia, y subirlo.
4. **Primera versión:** `gh release create forzudo-v<versión> <apk> --repo fogonapps/fogon`. El nombre de la etiqueta lleva la app delante porque en el repo va a haber varias apps.
5. **Obtainium en el S24:** agregar `github.com/fogonapps/fogon`. Como el repo tendrá varias apps, se filtra por la etiqueta (`forzudo-v`) para que solo siga a El Forzudo. Comprobarlo en el teléfono cuando exista la primera versión.
6. **Después,** el repo de trabajo sigue en local y cada versión nueva se sube encima del repo público (ya sin historial viejo).

F-Droid va después de GitHub, con sus propios requisitos (ver `TECNOLOGIA.md`). Se revisan contra su documentación cuando llegue el momento.
