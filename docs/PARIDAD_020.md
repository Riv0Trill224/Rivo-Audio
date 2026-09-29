# Rivo Audio 0.2.0 · Android / iOS

Esta versión lleva a Android la biblioteca por archivos/carpetas, artistas, cola, reproducción en segundo plano, cambio entre audio y video local por coincidencia de título/artista, edición de metadatos del catálogo y carátulas, valoración, historial, Last.fm, fotografía con atribución desde MusicBrainz/Wikidata/Commons y transferencia FTP local.

En ambos sistemas se añaden letras LRC persistentes asociadas al ID de canción, edición/importación/exportación/borrado, selección manual en casos ambiguos y búsqueda automática limitada a una tentativa diaria por pista. La eliminación desactiva la reimportación automática hasta elegir una letra manualmente. Android comienza sin letras heredadas; iOS migra el catálogo y los archivos LRC junto a la música, conservando esos originales.

Ajustes comunes: fondo de carátula, animación de letras, tamaño de texto entre 20 y 40, EQ de 10/15/31 bandas y los mismos presets, velocidad de 0.5 a 2 y preamplificación de -12 a 0 dB. Diagnóstico de formato y salidas según lo que proporciona cada sistema.

## Diferencias que requieren validación

- La interfaz Android usa controles nativos; comparte estructura y colores, pero no es una réplica píxel por píxel de SwiftUI.
- Android aplica EQ mediante un procesador PCM de Media3. iOS utiliza AVAudioUnitEQ y AVAudioUnitTimePitch; las respuestas y consumo deben compararse en equipos reales.
- Android 0.2.1 incorpora la forma de onda real de 56 muestras RMS con caché e interrupción al salir; lector PCM WAV y decodificación de ventanas para formatos comprimidos.
- Se unifican controles vectoriales, menú de canción, disposición de reproducción, vista previa de tamaño de letras y navegación por líneas sincronizadas.
- Android ofrece las salidas detectadas por el sistema; no implementa un controlador USB exclusivo ni garantiza bit perfect.
- Android enlaza audio/video por título y artista normalizados exactos y solicita selección si hay varias versiones. iOS además usa duración y coincidencias aproximadas.
- Last.fm y fotografías requieren pruebas con la cuenta real/red del usuario; los tests no validan disponibilidad de imagen para todos los artistas.
- La APK es de depuración. No reutiliza una firma de producción existente: una instalación previa con otra firma puede requerir desinstalación. Conservar/exportar datos antes de hacerlo.

## Pruebas

Actions compila la APK e inicia un emulador API 35: importa un WAV local, conserva letras al recargar, abre reproducción y letras/ajustes, comprueba avance del audio, cambia EQ y elimina el LRC. Los artefactos incluyen reporte y capturas. La prueba real de DAC y batería en RG DS/teléfono queda pendiente.
