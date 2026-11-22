Le pipeline suit chaque opportunité du premier contact à la signature, puis la mission une fois gagnée. Vous le voyez en tableau Kanban sur le tableau de bord, ou en liste dans **Opportunités**.

## Les étapes du pipeline

Chaque opportunité est à l'une de ces six étapes, une colonne chacune :

| Étape | Ce qu'elle veut dire |
| --- | --- |
| **Contact** | Premier échange avec le recruteur |
| **Tests & Entretiens** | Tests et entretiens en cours |
| **Offre** | Une proposition est sur la table |
| **Abandonné** | Refusé par le recruteur |
| **Désisté** | Refusé par vous (consultant) |
| **Accepté** | Mission acceptée |

## Le tableau Kanban

1. Ouvrez **Tableau de bord**.
2. Dans **Mode d'affichage**, choisissez **Kanban**.

![Le tableau Kanban avec ses six colonnes, du Contact à Accepté](/assets/screens/kanban-fr-light.jpg)

- **Faites glisser une carte** vers une autre colonne pour changer son étape. Vers **Abandonné** ou **Désisté**, Indezy demande une **Raison du refus (facultatif)**, affichée ensuite sur la carte.
- Dans une colonne, l'ordre des cartes est le vôtre ; **Épingler comme favori** (l'étoile) place une carte en tête.
- Le bouton **Ajout rapide d'une carte** d'une colonne crée une opportunité à partir d'un poste, d'un client et d'un TJM.
- Chaque carte compte les jours sans activité. Après le **Délai d’inactivité (jours)** (14 par défaut, réglable dans **Profil → Préférences**), elle est mise en évidence.
- **Doublon possible** signale deux cartes qui partagent le même client et le même poste.

> **Astuce :** cochez plusieurs cartes pour ouvrir une barre d'actions : **Déplacer vers**, **Source**, **Ajouter un tag**, **Archiver** ou **Supprimer**, sur 200 opportunités au plus.

## La liste des opportunités

**Opportunités** liste toutes vos opportunités et missions, avec pour chacune le lieu, la distance et le trajet depuis votre adresse.

![La liste des opportunités avec ses filtres et ses cartes](/assets/help/screens/projects-fr.jpg)

1. Cliquez sur **Nouvelle mission** pour en créer une, ou sur **Depuis le lien d'une offre** pour partir d'une annonce (voir [Offres](/help/offers)).
2. Filtrez par mots, TJM, mode de travail, technologies, étape, saison, dates, durée, client ou **Archive**. Les filtres restent dans l'adresse de la page.
3. **Exporter le récapitulatif** télécharge en CSV les opportunités qui démarrent cette année.
4. **Carte** affiche les opportunités sur une carte, autour de votre domicile.

## Archiver une opportunité

1. Sur la page d'une opportunité, cliquez sur **Archiver**.
2. Elle quitte le tableau et les rappels, mais garde son étape pour les statistiques.
3. Retrouvez-la dans **Archives**, puis cliquez sur **Restaurer** pour la remettre dans sa colonne.

Dans **Profil → Préférences**, **Archiver chaque nuit les opportunités inactives** archive chaque nuit celles que le tableau met en évidence. Il est activé pour les nouveaux comptes ; désactivez-le si vous préférez archiver vous-même.

## Les saisons de recherche

Une saison est une période de recherche avec son propre pipeline et son tableau de bord.

1. Dans le sélecteur **Saison** du tableau de bord, choisissez **Nouvelle saison**.
2. Donnez un **Nom de la saison**, un **Début**, une **Fin** (vide tant qu'elle dure), un **TJM visé** et un **Objectif**.
3. Cliquez sur **Démarrer la saison**.

Les opportunités sans saison créées pendant ses dates la rejoignent, et chaque nouvelle opportunité rejoint la saison en cours. Le sélecteur limite le tableau à une saison ou **Toutes les saisons**. **Clôturer la saison** la termine ; la supprimer garde ses opportunités.

## La page d'une opportunité

La page d'une opportunité réunit tout ce qui la concerne. **Changer l'étape du pipeline** la déplace sans revenir au tableau.

![La page d'une opportunité avec ses étapes de recrutement et son journal](/assets/help/screens/project-fr.jpg)

- Sa stack technique s'affiche en tags, avec leur correspondance à votre profil.
- **Documents et liens** garde jusqu'à 20 fichiers (fiche de poste, contrat, test), de 50 Mo chacun : cliquez sur **Joindre un fichier**.
- **Copier le lien du projet** copie une adresse qui ne s'ouvre que pour votre compte.

## L'aide de l'IA

- Sur une nouvelle opportunité, **Remplir depuis une annonce collée (IA)** lit une annonce que vous collez (un e-mail, une page de job board, un message LinkedIn) et remplit le poste, l'entreprise, le TJM, la stack, le lieu, les jours de télétravail, la date de début et la durée. Vérifiez-les, puis enregistrez : le client est créé si vous ne l'avez pas encore. **Depuis un PDF** met d'abord le texte d'une annonce enregistrée en PDF dans la zone, pour que vous le vérifiiez.
- Sur le formulaire d'opportunité, **Suggérer un TJM (IA)** sous le TJM donne le TJM à demander, la fourchette qu'un client accepterait et pourquoi, à partir de ce que dit le formulaire, de vos compétences et de votre objectif de TJM, et de la médiane du marché pour la compétence principale. **Utiliser** le met dans le champ.
- Sur la page d'une opportunité, **Évaluer cette opportunité (IA)** donne un score d'adéquation, ce qui convient et ce qui peut ne pas convenir, ce qu'il faut vérifier sur l'équipe et le management, les questions à poser en entretien, et ce qu'il faut penser du TJM face à votre objectif et au marché, avec un TJM à demander. Dites ce qui compte pour vous pour une réponse plus précise.
- Sur la page d'une opportunité, **Demander à l'IA sur cette opportunité** ouvre une conversation pour préparer un entretien, la négociation ou ce qu'il faut retenir une fois terminée. L'IA connaît l'opportunité, ses notes, ses étapes et son journal, et la conversation reste avec l'opportunité jusqu'à ce que vous l'effaciez.
- Sur la page d'une opportunité, **Dossier adapté (IA)** télécharge votre dossier de compétences reformulé pour elle, nominatif ou anonymisé, en français ou en anglais : la présentation réécrite pour la mission, les compétences qu'elle demande en premier et la description de chaque mission mettant en avant ce qui compte. L'IA ne fait que reformuler ce que votre dossier dit déjà, et ne voit jamais votre nom, vos coordonnées ni vos clients.
- Dans **Écrire un e-mail**, **Améliorer (IA)** corrige l'orthographe et la grammaire, ou rend le message plus formel, plus chaleureux ou plus court, en gardant ses `{{variables}}` ; **Annuler** remet votre texte.
- Sur la page d'une opportunité, **Préparer la négociation (IA)** propose une contre-proposition, le TJM en dessous duquel refuser et les arguments pour l'échange, avec la marge de l'ESN quand vous avez saisi le TJM client.
- Sur la page d'une opportunité, **Rédiger un message (IA)** écrit une candidature, une relance, un remerciement après un entretien ou un refus poli, en français ou en anglais, à partir de l'opportunité et de quelques mots sur ce que vous voulez dire. Modifiez-le à votre guise, puis **Copiez**-le dans votre e-mail.

Chaque compte a un forfait IA mensuel ; **Profil → Préférences → IA** l'affiche, et accepte votre propre clé Anthropic pour aller au-delà. L'IA ne voit que ce que vous avez saisi et l'opportunité concernée.

## Les étapes de recrutement

1. Dans **Étapes de recrutement**, cliquez sur **Ajouter une étape**.
2. Donnez un **Intitulé de l'étape**, une date et une heure, et des notes.
3. Changez son statut depuis sa pastille : **À planifier**, **Planifiée**, **En attente de retour**, **Validée**, **Échouée** ou **Annulée**.
4. Dans **Préparation**, ajoutez les points à préparer, ou **Ajouter les points suggérés**, et cochez-les au fil de l'eau.

Le menu **Modèles** propose **Enregistrer ces étapes comme modèle** pour une ESN, un client ou **Tout client ou ESN**, puis applique un modèle à une autre opportunité. Les étapes planifiées apparaissent dans le [calendrier](/help/calendar).

## Le journal et les e-mails

- **Journal** liste vos notes et vos échanges, du plus récent au plus ancien. Écrivez une **Nouvelle note** en Markdown, au besoin à partir de **Insérer un modèle** (compte-rendu d'appel, d'entretien, résumé de négociation).
- **Noter un échange** enregistre un échange avec un contact du client ou de son ESN.
- **Écrire un e-mail** choisit un **Modèle** (relance, proposition de TJM, annonce de disponibilité, révision du TJM au renouvellement) et un destinataire, puis prépare l'objet et le message. **Ouvrir dans ma messagerie**, **Copier**, ou **Envoyer depuis Indezy** pour l'envoyer en votre nom et savoir quand il est ouvert si vous le demandez. Vos propres modèles se gèrent dans **Profil → E-mails**.

## Une fois la mission acceptée

- **Points du contrat** liste ce qu'il faut vérifier avant de signer : délais de paiement, préavis, non-sollicitation, propriété intellectuelle, responsabilité et assurance, révision du TJM, lieu de travail. Cochez chaque point et notez ce que prévoit le contrat.
- La page affiche aussi les CRA et les bons de commande : voir [CRA et bons de commande](/help/activity-reports).

## Non-sollicitation et cooptation

Sur une opportunité avec une ESN, renseignez **Non-sollicitation (mois)** (de 1 à 36). Une fois cette mission gagnée, toute autre opportunité pour le même client final, en direct ou via une autre ESN, affiche un avertissement et un marteau sur sa carte, jusqu'à la fin de la clause.

La carte **Cooptation** note qui vous a recommandé (**Je verse une prime à qui m’a recommandé**) ou qui vous avez recommandé (**Je reçois une prime pour avoir recommandé**), par nom ou par @identifiant du réseau. Choisissez la prime (**Sans prime**, **Montant fixe**, **Par jour travaillé** ou **Part de la mission**) et son état : **Promise**, **À régler** ou **Réglée**.

## Les sources

**Sources** dit d'où viennent vos opportunités : site d'emploi, réseau social, e-mail, appel ou SMS, avec un lien, une **Popularité**, une **Utilité** et des notes. Votre espace démarre avec les sites lus par le fil d'offres et LinkedIn. Cliquez sur **Nouvelle source** pour en ajouter une. Les sources alimentent les filtres et le ROI par source du [tableau de bord](/help/dashboard).
