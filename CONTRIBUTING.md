# Contribuir

Clona con `git clone --recurse-submodules <url>` y consulta README y docs/testing.md.
Mantén cambios pequeños y explica problema, comportamiento y validación en cada MR.
Ejecuta `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` antes de proponer cambios.
No incluyas modelos, claves, grabaciones personales, volcados de accesibilidad ni datos de otras aplicaciones.
Para nuevas dependencias, documenta versión, origen, licencia y justificación en THIRD_PARTY_NOTICES.md.
No cambies la referencia del submódulo sin revisar su diff y ejecutar las pruebas nativas.

Las pruebas de inserción deben cubrir conservación del texto, campos protegidos, cambio de destino y resultado ambiguo.
Los motores deben ejecutar inferencia real; los simuladores solo pueden existir en pruebas identificadas.
No presentes un resultado de emulador como validación de hardware físico. Adjunta versión Android, ABI y modelo exacto a los resultados.
