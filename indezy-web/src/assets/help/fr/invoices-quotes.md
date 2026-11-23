**Devis** et **Factures** couvrent la facturation d'un freelance : le devis d'une prestation au forfait, la facture mensuelle d'une mission en régie à partir du CRA, les avoirs, le suivi des paiements et l'export pour votre expert-comptable.

## Compléter votre identité de facturation

Une facture française doit porter des mentions précises. Renseignez-les une fois dans **Profil → Facturation**, carte **Identité de facturation** :

1. **Dénomination** et **Forme juridique** (micro-entreprise, EI, EURL, SASU, SARL, SAS…).
2. **SIRET**, et pour une société le **Capital social** et l'**Immatriculation**.
3. Le **Numéro de TVA** et le **Taux de TVA** (20 % par défaut), ou **Exonéré de TVA (franchise en base, art. 293 B du CGI)**, coché par défaut.
4. L'**Adresse de facturation**, l'**IBAN** et le **BIC**.
5. Le **Délai de paiement (jours)** (30 par défaut, 60 au plus) et le **Préfixe des numéros de facture** (`F` par défaut, soit F2026-0001).
6. Au besoin un **Logo** (PNG ou JPEG, 2 Mo au plus), à gauche, centré ou à droite, petit, moyen ou grand, une **Mise en page** (**Simple**, **Moderne** avec un bandeau de votre couleur d'accent en haut, ou **Classique** avec une police à empattements, des filets et les coordonnées bancaires encadrées), une **Police** (sans ou avec empattements, ou la vôtre avec **Envoyer ma police** : un .ttf TrueType dont la licence permet l'incorporation, avec son fichier gras si vous l'avez), un **Papier en-tête** (un PDF d'une ou deux pages A4 sur lequel vos documents sont dessinés, ses polices incorporées, avec la **Place de son en-tête** et la **Place de son pied de page** en millimètres pour que le document ne les couvre jamais), une **Ligne sous votre nom** (une accroche), une ligne de **Pied de page** (votre site, votre téléphone) au pied de chaque page, et une **Couleur d'accent** pour le titre, l'en-tête du tableau et les filets de vos factures et devis (une couleur trop pâle pour être lue ou imprimée est refusée) ; **Aperçu** montre toutes les pages d'une facture avec ces réglages avant de les enregistrer, et **Aperçu d'un devis** celles d'un devis ; une fois affiché, l'aperçu se redessine de lui-même un instant après chaque changement. Cochez **Les devis ont leur propre couleur et mise en page** pour donner aux devis une autre mise en page et une autre couleur d'accent, le reste partagé, et vérifiez-les avec **Aperçu d'un devis**. **En-tête et pied des pages** ajoute au pied de page une zone centrale et une zone droite, et un en-tête aux pages après la première, chacun un texte avec des champs comme `{{logo}}` (votre logo, en petit), `{{page}}`, `{{pages}}`, `{{document.number}}` ou `{{seller.siret}}` (cliquez-en un dans la liste dessous pour l'insérer là où était votre curseur) ; laissez le pied droit vide pour y garder le numéro de page. **Ordre du corps** règle l'ordre des lignes et totaux, des mentions, des notes et du cadre de signature des devis : glissez chacun par sa poignée, ou utilisez les flèches à côté ; décochez **Imprimé** pour retirer le cadre de signature de vos devis (les autres blocs sont obligatoires : ils se déplacent mais ne se retirent pas). Le tableau des lignes peut remplir son en-tête de votre couleur d'accent (**En-tête rempli de la couleur d'accent**) et griser une ligne sur deux (**Lignes grisées**), quelle que soit la mise en page. Les mentions et les notes prennent aussi leur propre **Taille**, leur **Alignement**, leur **Police** (celle de vos documents, sans ou à empattements), leur **Couleur** (celle du texte, votre couleur d'accent ou le gris), le **Gras**, un cadre (**Encadré**) et un fond gris (**Grisé**). Un document émis garde le logo, le pied de page et la couleur qu'il avait. Les **Notes de départ d'une nouvelle facture** et **d'un nouveau devis** remplissent les notes de chaque nouveau brouillon.
7. Le **Taux des pénalités de retard (par an)** de vos conditions, par exemple 12,15 %. La loi demande au moins trois fois le taux d'intérêt légal ; vide, les factures indiquent le taux de la BCE majoré de 10 points, sans chiffre.

Vous pouvez écrire des brouillons à tout moment, mais émettre une facture demande un SIRET, une adresse, un IBAN et, si la TVA s'applique, un numéro de TVA. Un IBAN, un SIRET, un numéro de TVA ou un BIC mal formé est refusé.

> **Astuce :** en portage salarial, c'est la société de portage qui facture vos clients : Indezy n'émet pas de factures pour vous.

## La liste des factures

**Factures** liste vos factures avec leur statut et totalise ce qui est **En attente de paiement** et **En retard**. Le filtre **Afficher** montre **Toutes**, **Brouillons**, **En attente de paiement**, **En retard** ou **Payées**.

![La page Factures avec les totaux en attente et en retard, et la liste des factures](/assets/help/screens/invoices-fr.jpg)

Une facture passe par ces statuts : **Brouillon**, **Émise**, **En retard** une fois l'échéance passée, **Payée**, ou **Annulée par avoir**.

## Facturer un CRA

1. Sur la page **Factures**, cliquez sur **Depuis un CRA** (ou sur **Créer la facture** dans la carte CRA de la mission).
2. Choisissez le mois parmi ceux qui ont des jours travaillés et ne sont pas encore facturés.
3. Cliquez sur **Créer la facture**.

Le brouillon est adressé à l'ESN de la mission, sinon à son client, dans la langue de son modèle de CRA, avec les jours au TJM de la mission. Un mois refusé ne se facture pas, et un mois ne se facture qu'une fois. Voir [CRA et bons de commande](/help/activity-reports).

## Créer une facture à la main

1. Cliquez sur **Nouvelle facture**.
2. Choisissez le **Client**, au besoin la **Mission**, la période (**Prestation du**, **Prestation au**) et la **Langue**.
3. Pour chaque ligne, saisissez la **Désignation**, la **Quantité**, le **Prix unitaire** et la **TVA**. Cliquez sur **Ajouter une ligne** pour en ajouter (50 au plus).
4. Ajoutez au besoin des **Notes (imprimées sur la facture)**, puis enregistrez.

Une ligne à quantité négative retranche un montant (une remise, un acompte déjà facturé). Une facture au total négatif ne s'émet pas : c'est le rôle d'un avoir.

## Émettre, encaisser, annuler

1. Relisez le brouillon : son PDF indique qu'il ne s'agit pas d'une facture.
2. Ouvrez le menu de la facture et cliquez sur **Émettre**, puis choisissez la **Date d'émission** (jamais après aujourd'hui, jamais avant la dernière facture).
3. La facture reçoit le numéro suivant de l'année, sans trou, et sa date d'échéance. Elle ne peut plus être modifiée ni supprimée.
4. Quand le client paie, cliquez sur **Marquer payée** et indiquez **Payée le**. **Marquer non payée** corrige une erreur.
5. Ou cliquez sur **Rapprocher mon relevé bancaire** et choisissez le CSV exporté par votre banque : chaque crédit est rapproché de la facture qu'il paie, par son numéro dans le libellé, par le nom du client avec le même montant, ou par le montant seul quand une seule facture l'attend. Décochez un rapprochement erroné, puis cliquez sur **Marquer N facture(s) payée(s)** : chacune est marquée payée le jour de son crédit. Rien n'est enregistré avant, et le relevé n'est pas conservé.

Pour annuler tout ou partie d'une facture émise, cliquez sur **Créer un avoir** : Indezy prépare un avoir avec les mêmes lignes, à réduire pour un avoir partiel, puis à émettre. Entièrement créditée, la facture passe **Annulée par avoir**.

## Facturer dans une autre devise

Pour un client à l'étranger, choisissez la **Devise** dans le brouillon : euro, dollar américain, livre sterling ou franc suisse. Dans une autre devise que l'euro, indiquez la **Valeur de 1 USD en euros** (ou GBP, CHF) à la date d'émission, celle de votre banque ou de la BCE : la facture ne peut pas être émise sans elle.

- Les lignes et les totaux sont dans cette devise, avec le total en euros dessous.
- Le PDF indique le taux et les montants en euros, TVA comprise, comme le demande la loi.
- Les totaux en attente, en retard et par client sont comptés en euros, comme l'export comptable, avec chaque montant dans la devise à côté.
- Un avoir garde la devise et le taux de sa facture.

## Les retards de paiement

- Une facture émise passe **En retard** après son échéance. Elle apparaît dans **Factures en retard de paiement** du [tableau de bord](/help/dashboard) et dans le résumé hebdomadaire.
- Le PDF porte les pénalités de retard prévues par la loi, à votre taux si vous en indiquez un.
- Dans la liste, une facture en retard dit ce qui lui est dû en plus de son montant : les pénalités au taux qu'elle indique, par jour de retard, et l'indemnité de recouvrement de 40 €, par exemple « 12 jour(s) de retard : pénalités 2,00 € et indemnité de recouvrement 40,00 € ».
- La page de chaque client, et celle de ses opportunités, dit comment il paie d'après ses factures payées, par exemple « Paie en 52 jours en moyenne, avec 12 jours de retard ».
- **Chiffre d'affaires par client, 12 derniers mois** répartit votre chiffre d'affaires et vous avertit quand un client en représente la moitié ou plus, un risque de requalification en salariat.

## Factur-X

Les factures et avoirs émis se téléchargent avec **Télécharger le PDF** au format Factur-X : un PDF/A-3 qui embarque les mêmes données en XML (profil EN 16931). Les brouillons restent de simples PDF. L'envoi par une plateforme de dématérialisation agréée n'est pas encore disponible.

**Télécharger en Word**, dans le menu d'une facture ou d'un devis, donne le même document en fichier Word (.docx) avec votre style, à relire, adapter ou archiver. La facture que vous envoyez reste le PDF : c'est elle qui porte son numéro et son contenu figés.

Les **Modèles Word**, dans Profil → Facturation, permettent à **Télécharger en Word** de remplir votre propre mise en page :

1. Dans Word, mettez en page une facture (ou un devis) comme vous le souhaitez et tapez des champs là où vont les valeurs, comme `{{invoice.number}}`, `{{client.name}}` ou `{{totals.toPay}}` ; **Champs à copier** les liste tous.
2. Pour les lignes, mettez `{{#lines}}` au début d'une ligne de tableau et `{{/lines}}` à sa fin, avec `{{line.description}}`, `{{line.quantity}}`, `{{line.unitPrice}}` et `{{line.amount}}` dans ses cellules : la ligne est répétée pour chaque ligne de la facture.
3. Cliquez sur **Envoyer un modèle** sous **Factures et avoirs** ou **Devis**. Indezy montre les champs trouvés, ceux qu'il ne connaît pas, et les mentions absentes de votre modèle : elles sont ajoutées à la fin, pour que rien de ce qu'exige la loi ne manque.

Tous les champs, avec un exemple, sont dans [Modèles de documents](/help/document-templates). **Aperçu** télécharge votre modèle rempli avec votre dernière facture ou votre dernier devis (un exemple avant le premier). **Revenir à la mise en page intégrée** retire le modèle. Le PDF garde la mise en page intégrée.

## L'export pour le comptable

1. Sur la page **Factures**, cliquez sur **Exporter pour le comptable**.
2. Choisissez l'année.
3. Choisissez **Écritures comptables (FEC)**, le fichier qu'importent les logiciels comptables français (journal des ventes `VE`, clients en `411000`, prestations en `706000`, TVA en `445710`, encaissements en banque `512000`), ou **Registre des factures (tableur)**.

L'export reprend les factures et avoirs émis de l'année.

## Les devis

**Devis** prépare une prestation au forfait : un audit, une formation, un développement.

1. Cliquez sur **Nouveau devis**.
2. Choisissez le client, au besoin la mission, la langue, la **Durée de validité** (30 jours par défaut, 365 au plus), un éventuel **Acompte à la commande** (1 à 100 %), des notes et les lignes.
3. Cliquez sur **Envoyer** et choisissez la **Date du devis**. Le devis reçoit son propre numéro (D2026-0001) et se fige. Envoyez ensuite son PDF au client : Indezy ne l'envoie pas lui-même.
4. Selon la réponse, cliquez sur **Accepté par le client** ou **Refusé par le client**. Sans réponse, il passe **Expiré** à la fin de sa validité.

Le PDF porte la TVA ou son exonération, la validité, l'acompte, le délai de paiement, les pénalités de retard et une case « Bon pour accord » pour la signature du client. Vos **Conditions générales de vente**, écrites dans **Profil → Facturation** ou ajoutées là en PDF, suivent sur leurs propres pages ; un devis envoyé garde celles avec lesquelles il est parti.

Pour un devis accepté :

- **Facturer l'acompte** prépare une facture d'acompte de la part prévue ;
- **Facturer le solde** prépare, une fois l'acompte émis, la facture finale avec les lignes du devis, moins les acomptes déjà facturés.

La page totalise les devis envoyés en attente de réponse et la part des devis acceptés.

## Les dépenses

**Dépenses**, dans le menu, enregistre vos frais professionnels : la date, l'objet, une catégorie, le montant TTC et la TVA qu'il comprend, éventuellement une mission. La page totalise l'année par mois et par catégorie, et dit ce que vos frais changent selon votre statut, indiqué dans **Profil → Facturation** :

- en **micro-entreprise**, rien : l'abattement forfaitaire sur votre chiffre d'affaires les compte déjà, ils vous coûtent donc leur montant entier ;
- en **EI au réel** ou en **société** (EURL, SASU, SARL, SAS), ils réduisent votre bénéfice imposable (hors la TVA récupérée si vous facturez la TVA) : ils ne vous coûtent que la part que vous auriez gardée, à votre taux de réversion ;
- en **portage**, la société de portage vous les rembourse sur votre chiffre d'affaires avant charges, ce qui vous coûte la même part.
