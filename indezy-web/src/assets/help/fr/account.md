Votre compte se règle dans **Profil**, depuis le menu de votre compte en haut à droite. Cet article couvre la connexion et la sécurité, les notifications et e-mails, les outils pour développeurs, vos données et l'affichage de l'application.

## Se connecter et sécuriser son compte

Selon votre instance, vous vous connectez avec votre e-mail et votre mot de passe, ou avec **Continuer avec SSO**. Un mot de passe compte au moins 8 caractères.

Pour changer de mot de passe :

1. Ouvrez **Profil → Sécurité**.
2. Cliquez sur **Changer le mot de passe**, saisissez le **Mot de passe actuel** puis le **Nouveau mot de passe** deux fois.

Un nouveau mot de passe déconnecte toutes vos autres sessions. Il n'existe pas encore de réinitialisation du mot de passe oublié pour les comptes locaux.

### Activer l'authentification à deux facteurs

1. Dans **Profil → Sécurité**, section **Authentification à deux facteurs**, cliquez sur **Activer**.
2. Ajoutez le secret affiché à votre application d'authentification.
3. Saisissez le **Code de vérification** à 6 chiffres, puis **Vérifier et activer**.

À chaque connexion, votre application vous donnera le code demandé. Les codes de secours n'existent pas encore : gardez votre application d'authentification sauvegardée.

## Sessions et activité du compte

**Où vous êtes connecté**, dans **Profil → Sécurité**, liste chaque navigateur ou application connecté : son système, la façon dont il s'est connecté et sa dernière activité. **Ce navigateur** marque celui que vous utilisez.

- **Déconnecter** ferme une session.
- **Déconnecter partout ailleurs** ferme toutes les autres.

**Activité du compte** liste les connexions (et celles refusées), les déconnexions, les changements de mot de passe et de double authentification, les exports et imports, et les actions des administrateurs, sur 12 mois.

> **Astuce :** une session que vous ne reconnaissez pas ? Déconnectez-la, puis changez votre mot de passe.

## Notifications et e-mails

**Profil → Notifications** regroupe des interrupteurs enregistrés aussitôt :

- **Résumé hebdomadaire** : **M'envoyer un résumé chaque semaine**, chaque lundi matin, désactivé par défaut. Choisissez ses sections sous **Ce que donne le résumé** (voir [Tableau de bord](/help/dashboard)).
- **Appels réservés** : **M'envoyer chaque appel réservé par e-mail** (voir [Calendrier](/help/calendar)).
- **Messages du réseau** : **M'envoyer par e-mail les messages non lus depuis une heure**. Un seul e-mail, au plus toutes les trois heures, sans le texte des messages.
- **Nouvelles offres** : **Compter les nouvelles offres à côté d'Offres dans le menu**.

Les rappels d'entretiens, de relances et d'opportunités sans nouvelles s'affichent toujours sur le tableau de bord. Chaque recherche enregistrée règle ses propres e-mails (voir [Offres](/help/offers)). Les notifications du réseau se règlent type par type dans **Profil → Réseau** (voir [Réseau](/help/network)). Chaque e-mail contient un lien de désinscription en un clic.

## Confidentialité : tous vos consentements

**Profil → Confidentialité** rassemble en un seul endroit tout ce que vous acceptez ou refusez.

- Les interrupteurs s'enregistrent aussitôt : sur le réseau, **Suggestions par mes contacts**, **Suggestions par les mêmes ESN**, **Carte privée**, **Partager mes tarifs avec le baromètre** (désactivé par défaut), **Consulter les pages en privé**, **Recevoir des demandes de message**, **Confirmations de lecture**, **Indicateur de saisie** et **Présence en ligne** ; pour les e-mails, **Résumé hebdomadaire**, **Messages non lus par e-mail** et **Appels réservés**.
- Les réglages qui vivent dans un formulaire plus large (ce que montre votre carte, chaque notification du réseau, les sections du résumé, les e-mails des recherches enregistrées, l'archivage automatique, l'anonymisation des contacts, le profil public, l'export et la suppression du compte) ont chacun un bouton **Modifier** qui ouvre leur onglet.

**Suggestions par mes contacts** est activé par défaut : vous êtes proposé aux membres qui ont votre adresse dans leurs contacts, et ceux de vos contacts vous sont proposés. Désactivé d'un côté ou de l'autre, aucune suggestion par contacts entre vous.

## Jetons d'API

Un jeton permet à un script, un tableur ou un service d'automatisation de **lire** votre espace. Il ne modifie jamais rien et n'accède pas aux réglages de votre compte.

1. Ouvrez **Profil → Sécurité → Jetons d'API**.
2. Saisissez le **Nom du jeton**, choisissez **Expire après** (30, 90 ou 365 jours, ou **Jamais**).
3. Sous **Il lit**, limitez-le si besoin : **Opportunités et entretiens**, **CRA et bons de commande**, **Clients, contacts et sources**, **Factures**, **Documents de conformité**, **Offres**. Sans choix, il lit **Tout l'espace**.
4. Cliquez sur **Créer le jeton**, puis **Copier le jeton** : il ne sera plus affiché.

Un compte garde 10 jetons au plus. **Révoquer** coupe l'accès aussitôt.

## Webhooks

Un webhook prévient vos outils (Zapier, Make, n8n, un script) des événements de votre espace : opportunité ajoutée ou déplacée, CRA envoyé, validé ou refusé, facture émise ou payée, appel réservé.

1. Ouvrez **Profil → Sécurité → Webhooks**.
2. Saisissez l'**Adresse HTTPS** et cochez les **Événements**.
3. Cliquez sur **Ajouter le webhook**, puis **Copier le secret** : il signe chaque envoi et n'est affiché qu'une fois.

**Envoyer un test** vérifie la réception ; **Derniers envois** montre les 20 derniers. Un envoi refusé est réessayé plusieurs fois ; après 20 échecs d'affilée, le webhook se désactive jusqu'à ce que vous cliquiez sur **Activer**. Un espace a 5 webhooks au plus.

## Remplir votre profil depuis votre CV ou LinkedIn

**Profil → Informations personnelles → Importer mon profil** lit votre CV (PDF, Word .docx ou .doc, ou OpenDocument, 10 Mo au plus, lu par l'IA) ou l'archive de vos données LinkedIn (lue sans IA) dans les champs de votre profil : nom, téléphone, ville, poste, société, présentation, liens, langues, compétences avec leurs années (et le niveau qu'indique votre CV), et certifications. Le fichier n'est pas conservé.

Pour obtenir l'archive LinkedIn : sur LinkedIn, **Préférences et confidentialité → Confidentialité des données → Obtenir une copie de vos données** ; LinkedIn vous envoie un ZIP par e-mail, en général sous un jour. Plus rapide, le PDF de votre profil : sur votre propre profil LinkedIn, **Plus → Enregistrer au format PDF**. Il est lu sans IA lui aussi, mais ne cite que vos trois principales compétences et vos certifications sans leurs dates.

1. Cliquez sur **Choisir mon CV** ou **Choisir mon archive ou mon PDF LinkedIn**.
2. Cochez ce que vous reprenez : chaque champ trouvé montre ce que contient votre profil ; les champs vides sont cochés. Les compétences et langues absentes de votre profil sont ajoutées, aucune retirée. Une compétence que votre profil nomme déjà, sous une autre écriture (« spring-boot » pour « Spring Boot ») ou un alias connu (« JS » pour JavaScript), est indiquée comme déjà présente, et une langue que votre profil ne propose pas comme écartée. Un profil nomme 50 compétences au plus : quand il en est trouvé plus que la place restante, choisissez lesquelles ajouter.
3. Cliquez sur **Remplir le formulaire**, vérifiez, puis **Sauvegarder** : rien n'est enregistré avant, sauf les certifications cochées.

Les certifications rejoignent **Formations et certifications** sur la page [Documents](/help/documents), avec leur organisme, leur date d'obtention et leur fin de validité ; celle que vos formations nomment déjà est marquée **Déjà dans vos formations** et n'est pas ajoutée une seconde fois. Les postes et formations rejoignent **Expériences antérieures** et **Formation** sous vos informations personnelles, hors de votre profil public tant que vous ne les affichez pas ; celui que votre profil contient déjà est marqué **Déjà dans votre profil**. Importer à nouveau quelques mois plus tard ne coche donc que ce qui est nouveau.

Cochez **Garder … comme CV de mon profil** pour garder le fichier lu : il vous attend alors sous **Mon CV** dans vos informations personnelles, à télécharger et joindre à vos candidatures. **Garder un CV** y garde un CV sans l'importer, et **Remplacer** ou **Supprimer** le change ; il est d'abord vérifié contre les virus.

Un CV scanné sans texte ne peut pas être lu. Lire un CV utilise votre quota mensuel d'IA, ou votre propre clé ; l'archive LinkedIn non. Quand l'IA n'est pas disponible, votre CV est lu d'après ses titres (Profil, Compétences, Expérience, Formation, Langues, Certifications) : vérifiez ce qu'il a trouvé, un CV en colonnes ou en tableaux se lisant mieux avec l'IA.

## Exporter et importer vos données

- **Profil → Compte → Exporter mes données** télécharge toutes vos données au format JSON.
- **Profil → Compte → Importer des données** ajoute un export Indezy (50 Mo au plus) à votre espace.

Pour importer :

1. Cliquez sur **Choisir un fichier d'export**.
2. Lisez l'aperçu : chaque élément est nouveau, déjà présent ou écarté (avec la raison).
3. Cliquez sur **Importer** : rien n'est enregistré avant.

L'import reprend les sources, clients et ESN, contacts, saisons, opportunités avec leurs étapes, et notes du journal. Importer deux fois le même fichier n'ajoute rien. Le profil, les CRA, les offres, les modèles et les pièces jointes ne sont pas importés.

## Supprimer votre compte

1. Ouvrez **Profil → Compte**.
2. Cliquez sur **Supprimer mon compte**.
3. Saisissez votre e-mail pour confirmer.

Aussitôt, vous êtes déconnecté partout, personne ne peut se connecter, et votre carte, vos publications et votre page publique sont masquées. Un e-mail vous donne la date de suppression. Pendant 30 jours, vous connecter propose **Restaurer mon compte**, qui le rend tel qu'il était. Ensuite, tout est supprimé définitivement. Les messages que vous avez envoyés restent chez leurs destinataires, signés d'un **Ancien membre**.

> **Astuce :** exportez vos données avant de supprimer votre compte si vous voulez les garder.

## Langue et thème

- La langue (français ou anglais) se change avec l'icône globe de la barre du haut, ou dans **Profil → Préférences → Langue**. Elle est enregistrée avec votre compte.
- Le thème se règle dans **Profil → Préférences → Thème** : **Clair**, **Sombre** ou **Automatique**, puis **Sauvegarder**. L'icône lune ou soleil de la barre du haut bascule entre clair et sombre.

## Utiliser Indezy hors ligne

Sur l'instance de production, Indezy s'installe comme une application web. Sans connexion, il affiche ce qu'il a chargé en dernier des principales pages de votre espace, gardé sept jours, et un bandeau vous prévient. Les modifications demandent une connexion. Les téléchargements, les exports, vos sessions et vos jetons ne sont jamais gardés, et la déconnexion efface tout.
