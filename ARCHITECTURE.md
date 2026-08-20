# AppBox Runtime — Architecture

AppBox Runtime est un **environnement d'exécution sécurisé** pour applications Android métier. Ce n'est ni un launcher ni un MDM : c'est un **hôte runtime** qui fournit des services communs, contrôle les permissions et orchestre la communication inter-applications.

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────┐
│                     AppBox Runtime (Host)                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ App Mgr  │ │ Auth Svc │ │ Storage  │ │  Event Bus    │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ Network  │ │ Notif.   │ │ Lifecycle│ │ Remote Monitor│  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
│                         RuntimeService (Binder IPC)          │
└──────────────────────────┬──────────────────────────────────┘
                           │ SDK (AIDL-like Binder)
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   ┌──────────┐     ┌──────────┐     ┌──────────┐
   │ App Métier│     │ App Métier│     │ App Métier│
   │    A     │     │    B     │     │    C     │
   └──────────┘     └──────────┘     └──────────┘
```

## Modules Gradle

| Module | Rôle |
|--------|------|
| `:core` | Modèles de données, contrats d'interface, bus d'événements, sécurité |
| `:sdk` | Bibliothèque client pour les applications métier |
| `:app` | Application hôte runtime avec services et UI d'administration |

## Services communs

### Authentification (`AuthService`)
- Sessions tokenisées avec expiration
- Contrôle d'accès par permission `AUTH_READ` / `AUTH_WRITE`
- Prêt pour intégration OAuth/SSO externe

### Stockage (`StorageService`)
- Stockage namespacé par application
- Isolation des données par propriétaire
- Support chiffrement (flag `encrypted`)

### Réseau (`NetworkService`)
- Proxy contrôlé avec liste blanche de domaines
- Journalisation des requêtes pour le monitoring
- Permission `NETWORK_ACCESS` requise

### Notifications (`NotificationService`)
- Canal unifié AppBox
- Historique consultable par app

### Event Bus (`InProcessEventBus`)
- Publication/souscription par topics
- Routage ciblé (`targetPackage`) ou broadcast
- Communication sécurisée via permissions `EVENTS_PUBLISH` / `EVENTS_SUBSCRIBE`

## Gestion des applications

### AppRegistry
- Enregistrement automatique à la connexion SDK
- Vérification de signature SHA-256
- Liste blanche d'applications de confiance

### LifecycleManager
- États : `REGISTERED` → `ACTIVE` → `SUSPENDED` → `STOPPED`
- Heartbeat périodique (timeout 60s)
- Suspension automatique en cas de déconnexion

### PermissionManager
- 11 permissions runtime granulaires
- Attribution/révocation par application
- Journalisation des changements

## Intégration SDK (apps métier)

```kotlin
// Dans une application métier
val client = AppBoxClient.connect(context).getOrThrow()

// Authentification
val session = client.auth.authenticate(
    mapOf("user" to "john", "password" to "secret")
).getOrThrow()

// Stockage partagé
client.storage.put("orders", "last-id", "12345")

// Communication inter-apps
client.events.subscribe(setOf("orders.*", "inventory.updated"))
client.events.publish(AppBoxEvent(
    id = UUID.randomUUID().toString(),
    topic = "orders.created",
    sourcePackage = context.packageName,
    targetPackage = "com.example.inventory",
    payload = """{"orderId": "12345"}""",
))

// Réseau proxifié
client.network.request("GET", "https://api.appbox.local/v1/status")
```

## Sécurité

1. **Vérification de signature** — Seules les apps signées et enregistrées peuvent se connecter
2. **Permissions runtime** — Chaque service vérifie les permissions avant exécution
3. **Isolation réseau** — Liste blanche de domaines autorisés
4. **Isolation stockage** — Namespaces et propriété par package
5. **IPC sécurisé** — Communication via Binder avec sérialisation JSON

## Extension future : serveur central

Le module `RemoteMonitorStub` prépare l'intégration d'un backend central :

```kotlin
interface RemoteMonitorContract {
    suspend fun report(event: RemoteMonitorEvent)
    fun isConnected(): Boolean
}
```

Fonctionnalités prévues :
- Monitoring temps réel des apps et événements
- Mises à jour OTA des APK métier
- Politique de permissions centralisée
- Tableau de bord web d'administration

## Limitations Android connues

- **Pas de sandbox OS natif** sans Device Owner / Work Profile
- Les apps métier doivent **intégrer le SDK** volontairement
- L'installation d'APK tiers nécessite `REQUEST_INSTALL_PACKAGES`
- Le contrôle des permissions système Android reste limité sans privilèges device admin

## Prochaines étapes

1. Module d'installation APK via `PackageInstaller`
2. Chiffrement AES du stockage partagé
3. Plugin Gradle pour validation SDK à la compilation
4. Serveur central (API REST + WebSocket)
5. Support Work Profile pour isolation renforcée
