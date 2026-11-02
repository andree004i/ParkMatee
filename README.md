# ParkMate

App Android nativa (Kotlin + Jetpack Compose) per gestire le soste dei veicoli: registrazione veicoli, sessioni di parcheggio libere/orarie/a ticket, cronologia, statistiche, mappa, posizioni salvate con geofence e notifiche in background. Tutti i dati sono salvati localmente (Room).

## Architettura
- MVVM + Repository, stato esposto con `StateFlow`
- DI manuale: `AppContainer` (in `ParkMateApp`) costruisce DB, repository e scheduler; i ViewModel usano `ViewModelProvider.Factory`
- Background: WorkManager (promemoria periodici), AlarmManager (preavviso e scadenza ticket), `BootReceiver` (ripristino dopo riavvio), GeofencingClient

## Configurazione
Aggiungere in `local.properties`: `MAPS_API_KEY=<chiave Google Maps>`.
