# FiltroKM

Ventana flotante que lee las ofertas de viaje de Uber Driver, DiDi Conductor e inDrive y muestra
**$/km**, **$/h**, minutos y km totales (recogida + viaje), con colores según tus filtros:

- Verde: todos los filtros cumplen
- Naranjo: alguno no cumple
- Rojo: ninguno cumple

## Cómo funciona
Usa un servicio de accesibilidad para leer el texto de la oferta en pantalla (no toca ni acepta viajes)
y dibuja la tarjeta encima. Todo queda en el teléfono.

## Compilar
Cada push a `main` compila la APK con GitHub Actions y la publica en *Releases* como `FiltroKM-v1.N.apk`.
