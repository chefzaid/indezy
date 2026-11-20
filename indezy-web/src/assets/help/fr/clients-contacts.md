**Clients** et **Contacts** gardent la mémoire de vos relations : les clients finaux et les ESN avec qui vous travaillez, les personnes que vous y connaissez et chaque échange avec elles.

## Ajouter un client ou une ESN

1. Ouvrez **Clients** et cliquez sur **Nouveau client**.
2. Dans **Nom de l'entreprise**, tapez au moins trois caractères du nom ou un SIREN : Indezy propose jusqu'à cinq entreprises de l'annuaire public, chacune avec son SIREN, sa forme juridique, sa ville et son code NAF. Une entreprise fermée est marquée **fermée**.
3. Choisissez-en une : la raison sociale, le SIREN, l'adresse du siège et le numéro de TVA intracommunautaire se remplissent. Ce que l'annuaire ne donne pas reste tel que vous l'avez saisi.
4. Laissez **Client final** activé pour un client final, ou désactivez-le pour une ESN, une agence ou un autre intermédiaire.
5. Ajoutez au besoin une **Évaluation** (1 à 5), des notes, ou cochez **Sur liste noire** avec la **Raison de la mise en liste noire**.
6. Enregistrez.

![La liste des clients avec leur type, leur ville et leur nombre de missions](/assets/help/screens/clients-fr.jpg)

La section **Facturation** du formulaire garde le SIREN et le numéro de TVA imprimés sur vos factures. La section **Modèle de compte rendu d'activité (CRA)** règle le PDF des CRA de ce client : titre, **Mise en page**, **Langue du PDF**, distinction sur site et télétravail, note de bas de page (voir [CRA et bons de commande](/help/activity-reports)).

> **Astuce :** la liste se filtre au fil de la frappe par mots, type et ville. Un client encore lié à des missions ne peut pas être supprimé, et supprimer un client supprime ses contacts.

## La santé de l'entreprise

Pour un client ou une ESN qui a un SIREN, Indezy vérifie sur sa page et sur celles de ses opportunités :

- s'il est fermé selon l'annuaire des entreprises ;
- s'il fait l'objet d'une procédure collective (sauvegarde, redressement ou liquidation judiciaire) selon sa dernière annonce au BODACC des trois dernières années.

La page l'indique alors avec la date de l'annonce. Seul le SIREN est envoyé. Une entreprise sans SIREN n'est pas vérifiée.

## Les délais de paiement

Dès qu'un client a des factures payées, sa page dit comment il paie vraiment, par exemple « Paie en 52 jours en moyenne, avec 12 jours de retard (4 facture(s) payée(s)) ». La même phrase apparaît sur les opportunités qu'il paie. Voir [Devis et factures](/help/invoices-quotes).

## Ajouter et importer des contacts

Un contact appartient à un client. Il a un nom, un e-mail, un téléphone, un poste, des notes et la base légale qui justifie de garder ses coordonnées.

1. Ouvrez **Contacts** et cliquez sur **Ajouter un contact**, ou faites-le depuis la page du client.
2. Choisissez son client et remplissez ses coordonnées.
3. Enregistrez.

Pour en ajouter plusieurs d'un coup, ouvrez la page du client, cliquez sur **Importer des contacts**, collez un contenu CSV (un export LinkedIn, par exemple) ou vCard, puis cliquez sur **Importer**. Les e-mails déjà présents dans votre espace sont ignorés.

![La liste des contacts avec leur client et leur dernier échange](/assets/help/screens/contacts-fr.jpg)

## Noter les échanges

1. Sur la page d'un contact, dans **Échanges**, cliquez sur **Noter un échange**.
2. Indiquez la **Date** (aujourd'hui au plus tard), le **Canal** (**E-mail**, **Téléphone**, **LinkedIn**, **Rendez-vous** ou **Autre**) et le **Sens** : **De ma part** ou **De sa part**.
3. Pour un message de votre part, précisez la **Réponse** : **En attente de réponse**, **A répondu** ou **Sans réponse**.
4. Ajoutez au besoin un **Résumé (facultatif)** et l'opportunité concernée, puis enregistrez.

La page en déduit le **Dernier contact**, le **Taux de réponse** et le **Canal préféré**. Un message de votre part resté sans réponse au-delà du délai **Relancer après (jours)** (5 par défaut, dans **Profil → Préférences**) apparaît dans les **Relances à faire** du [tableau de bord](/help/dashboard).

## Le réseau d'un contact et l'organigramme

1. Dans le formulaire d'un contact, choisissez son manager dans **Rattaché à**, dans la même société ou une autre.
2. Sur sa page, la carte **Réseau** montre son manager, son **Équipe** et, sous **Travaille avec**, les personnes avec qui il collabore.
3. Pour lier deux personnes, choisissez la **Personne**, ajoutez au besoin comment ils travaillent ensemble, puis cliquez sur **Lier**.

Ces liens vont dans les deux sens, souvent entre un client et son ESN. L'onglet **Contacts** de la page d'un client dessine l'**Organigramme**, avec les liens de chacun vers d'autres sociétés.

## Les données personnelles de vos contacts

Vous êtes responsable des données de vos contacts ; Indezy les garde pour vous. La carte **Ses données** d'un contact propose :

- **Pourquoi vous gardez ses coordonnées** : **Intérêt légitime** (par défaut), **Consentement** ou **Contrat** ;
- **Télécharger ses données** : un fichier JSON de ce que votre espace garde sur lui ;
- **Effacer à sa demande** : le contact disparaît avec ses échanges et ses liens.

Dans **Profil → Préférences**, **Anonymiser les contacts sans échange depuis** fixe une durée de conservation : **Jamais** (par défaut), 1, 2, 3 ou 5 ans. Chaque nuit, un contact resté sans échange ni modification aussi longtemps perd son nom, son e-mail, son téléphone et ses notes ; les dates et canaux des échanges restent, pour garder vos statistiques.

> **Astuce :** la variable `{{privacyNotice}}` des [modèles d'e-mails](/help/pipeline) explique au destinataire qui garde ses coordonnées et comment les consulter, corriger ou effacer.

## Inviter un contact sur le réseau

Si vous êtes membre du [réseau](/help/network), vous pouvez inviter un contact qui a une adresse e-mail.

1. Sur la page du contact, cliquez sur **Inviter sur le réseau**.
2. Confirmez avec **Envoyer l’invitation**. Indezy lui envoie un e-mail de votre part.
3. Le bouton devient **Invité sur le réseau le …**, puis **Sa page sur le réseau** une fois qu'il est membre, ou **Ne souhaite pas être invité sur le réseau** s'il a refusé.

Chaque contact n'est invité qu'une fois, et vous envoyez au plus 20 invitations par jour. La personne peut choisir **Ne plus jamais être invité** : plus aucun membre ne pourra l'inviter.
