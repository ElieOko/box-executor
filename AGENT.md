# AppBox Super Agent — Automatisation

AppBox Runtime inclut un **Super Agent** : couche d'exécution **et** moteur d'automatisation déclaratif (style n8n).

## Capacités

| Canal | Description |
|-------|-------------|
| **Fichier d'instructions** | JSON dans `assets/instructions/default_agent.json` |
| **Event bus** | Déclenchement via topics (`agent.run`, etc.) |
| **Planification** | Alarmes exactes (WhatsApp 18h, Hacker News 8h) |
| **Voix** | Commandes FR : « ouvrir yvent », « digest hacker news », « envoyer whatsapp » |
| **UI Agent** | Visualisation du flow, exécution manuelle, micro |

## Architecture

```
InstructionFile (JSON)
       ↓
AutomationAgent ──→ WorkflowEngine (nœuds + edges)
       │                    ↓
       ├── SchedulerService  HTTP / WhatsApp / Notify / Launch / Speak
       ├── VoiceService
       └── EventBus (InProcessEventBus)
```

## Fichier d'instructions

Emplacement : `app/src/main/assets/instructions/default_agent.json`

Structure :

```json
{
  "version": 1,
  "agent": {
    "name": "AppBox Super Agent",
    "workflows": [ { "id", "name", "nodes", "edges" } ],
    "schedules": [ { "id", "workflowId", "triggerType", "hour", "minute" } ],
    "voiceCommands": [ { "phrase", "workflowId" } ],
    "eventTriggers": [ { "topic", "workflowId" } ]
  }
}
```

### Types de nœuds (workflow)

| Type | Rôle |
|------|------|
| `TRIGGER_SCHEDULE` | Entrée planifiée |
| `TRIGGER_VOICE` | Entrée vocale |
| `TRIGGER_EVENT` | Entrée event bus |
| `HTTP_FETCH` | Requête HTTP (ex. API HN) |
| `PARSE_HN_DIGEST` | Top N titres Hacker News |
| `WHATSAPP_PREPARE` | Prépare phone + message (`{{time}}`) |
| `WHATSAPP_OPEN` | Ouvre WhatsApp (wa.me) — envoi validé manuellement |
| `NOTIFY` | Notification Android |
| `LAUNCH_APP` | Lance une app (box ou système) |
| `SPEAK` | Synthèse vocale (TTS) |
| `DELAY` | Pause |
| `CONDITION` | Branchement |
| `STORE` | Persistance agent |
| `PUBLISH_EVENT` | Publie sur le bus |

Variables de template : `{{digest}}`, `{{date}}`, `{{time}}`, etc.

## Exemples configurés

### WhatsApp planifié (18h)

1. `WHATSAPP_PREPARE` — numéro + message
2. `WHATSAPP_OPEN` — ouvre la conversation
3. `NOTIFY` — rappel de valider l'envoi

> Android interdit l'envoi WhatsApp sans interaction utilisateur ou Accessibility Service. AppBox ouvre la conversation pré-remplie à l'heure exacte.

Modifier le numéro dans `default_agent.json` :

```json
"config": { "phone": "+33612345678", "message": "Votre message" }
```

### Digest Hacker News (8h)

1. `HTTP_FETCH` → API Firebase HN
2. `PARSE_HN_DIGEST` → top 5 titres
3. `NOTIFY` + `SPEAK`

### Commande vocale « ouvrir yvent »

Lance `com.yvent.app` dans la box AppBox.

## UI — onglet Agent

- Liste des workflows + bouton ▶ exécution manuelle
- **Canvas flow** (nœuds + liens pointillés, style n8n)
- Micro : push-to-talk
- Planifications et logs d'exécution

## Permissions requises

- `RECORD_AUDIO` — reconnaissance vocale
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — heure précise
- `POST_NOTIFICATIONS` — digest et confirmations
- `INTERNET` — Hacker News API

## Déclenchement via event bus

Publier depuis une app SDK ou l'hôte :

```kotlin
eventBus.publish(AppBoxEvent(
    id = UUID.randomUUID().toString(),
    topic = "agent.run",
    sourcePackage = "com.appbox.runtime",
    payload = "{}",
))
```

## Personnalisation

1. Dupliquer / éditer `default_agent.json`
2. Ajouter workflows (nodes + edges avec `positionX` / `positionY` pour l'UI)
3. Recharger via **Agent → Recharger** ou redémarrage app

## Limitations

- **WhatsApp** : pas d'envoi silencieux sans Accessibility Service (non activé par défaut pour la sécurité)
- **Voix** : nécessite Google Speech Services sur l'appareil
- **Alarmes exactes** : sur Android 12+, l'utilisateur peut devoir autoriser les alarmes exactes

## Évolutions prévues

- Éditeur visuel de flows (drag & drop)
- Nœuds Accessibility pour automatisation UI complète
- Sync instructions depuis serveur central
- Intégration SDK (`AgentApi`, `WorkflowApi`)
