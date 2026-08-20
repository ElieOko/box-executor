# AppBox — Mise en place d'un environnement hôte

Ce document explique **ce que vous devez mettre en place** pour que certaines applications tournent réellement **dans** AppBox Runtime, sans barres système visibles et avec retour contrôlé.

## Les 3 niveaux de déploiement

### Niveau 1 — AppBox seul (téléphone personnel)

**Ce que vous avez aujourd'hui sans configuration extra :**

- Ajout d'apps déjà installées via le sélecteur AppBox
- Lancement encadré via `AppBoxSessionActivity` + VirtualDisplay
- Lock Task basique (`startLockTask`) — épinglage d'écran
- Mode immersif avec re-masquage des barres au geste

**Limites :**

- Le geste **glisser depuis le bord** peut encore faire apparaître brièvement la barre de navigation Android (comportement OS)
- Pas de sandbox OS complet : les apps ne sont pas isolées au niveau kernel
- Pas de contrôle total sans Device Owner

**À activer manuellement dans AppBox (onglet Proc.) :**

1. **Statistiques d'utilisation** — suivi du premier plan (`UsageStatsManager`)
2. **Afficher par-dessus les autres apps** — bouton overlay de retour si une app sort de la box

---

### Niveau 2 — Device Owner (appareil dédié / entreprise)

**Recommandé pour un vrai poste kiosque AppBox.**

#### Prérequis

- Appareil **non provisionné** ou **factory reset**
- ADB activé
- AppBox Runtime installé (`com.appbox.runtime`)

#### Provisionner AppBox en Device Owner

```bash
adb shell dpm set-device-owner com.appbox.runtime/.admin.AppBoxDeviceAdminReceiver
```

#### Autoriser les apps dans le Lock Task

Après avoir ajouté vos apps dans AppBox (ex. `com.monapp.metier`, `com.monapp.stock`) :

```bash
adb shell dpm set-lock-task-packages com.appbox.runtime \
  com.appbox.runtime com.monapp.metier com.monapp.stock
```

AppBox synchronise aussi cette liste automatiquement si Device Owner est actif.

#### Ce que Device Owner débloque

| Fonction | Sans DO | Avec Device Owner |
|----------|---------|-------------------|
| Lock Task sans message « épingler » | Partiel | Complet |
| Masquer barres système au geste | Non fiable | `LOCK_TASK_FEATURE_NONE` |
| Whitelist apps autorisées | Non | Oui |
| Contrôle permissions centralisé | Limité | Étendu (MDM-like) |

---

### Niveau 3 — Apps métier intégrées au SDK

Pour profiter des **services communs** (auth, stockage, event bus, réseau proxifié), vos apps doivent intégrer le module `:sdk` :

```kotlin
implementation(project(":sdk")) // ou AAR publié

val client = AppBoxClient.connect(context).getOrThrow()
client.storage.put("orders", "id", "123")
```

Sans SDK, une app est **gérée et lancée** par AppBox mais **n'utilise pas** l'écosystème de services.

---

## Flux recommandé pour vos apps métier

```
1. Installer l'APK métier sur l'appareil
2. Ouvrir AppBox → Ajouter l'app depuis le téléphone
3. (Device Owner) Whitelist lock task via ADB ou sync auto
4. (Optionnel) Intégrer le SDK AppBox dans l'APK
5. Lancer depuis l'accueil AppBox → exécution dans la box
```

---

## Sortir de l'environnement AppBox

Un bouton **Quitter AppBox** (icône vectorielle en haut à droite) permet de :

1. Arrêter le Lock Task (`stopLockTask`)
2. Masquer l'overlay de retour
3. Rendre les barres système Android (mode téléphone normal)

---

## Overlay de retour (app sort de la box)

Si une application hébergée passe au premier plan **en dehors** de la session AppBox :

- `ReturnOverlayService` affiche un bouton flottant (icône `ic_exit_appbox`)
- Un tap ramène à AppBox Runtime

**Permission requise :** Afficher par-dessus les autres applications.

---

## Pourquoi le geste haut/bas affiche encore la navigation ?

Android **réserve** ce geste au système sur les appareils non Device Owner. AppBox :

1. Utilise `IMMERSIVE` + re-masquage immédiat si les barres apparaissent
2. Active `Lock Task Mode` pour bloquer la sortie
3. Avec **Device Owner**, désactive les features d'échappement du lock task

Sans Device Owner, **aucune app** ne peut garantir à 100 % l'absence de barres au geste — c'est une limitation de sécurité Android.

---

## Checklist déploiement production

- [ ] Appareil dédié factory reset
- [ ] `dpm set-device-owner com.appbox.runtime/.admin.AppBoxDeviceAdminReceiver`
- [ ] Apps métier installées et ajoutées dans AppBox
- [ ] Lock task packages configurés
- [ ] Permission usage stats accordée
- [ ] Permission overlay accordée
- [ ] SDK intégré dans les apps métier (si services communs requis)
- [ ] Test de sortie via bouton Quitter AppBox
