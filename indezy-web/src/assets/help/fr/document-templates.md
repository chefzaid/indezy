Un **modèle Word** met en page vos factures ou vos devis comme vous le souhaitez : vous l'écrivez dans Word, placez des champs là où vont les valeurs, et Indezy le remplit pour chaque document quand vous cliquez sur **Télécharger en Word**. Cette page liste tous les champs et leur usage.

## Envoyer un modèle

1. Dans Word, mettez en page une facture (ou un devis) et tapez les champs ci-dessous là où vont les valeurs, accolades comprises, comme `{{invoice.number}}`. Ou partez de **Télécharger un modèle de départ** : la mise en page intégrée à votre style et à votre **Ordre du corps**, chaque champ déjà en place.
2. Enregistrez-le en `.docx` (5 Mo au plus, sans macros).
3. Ouvrez **Profil → Facturation → Modèles Word** et cliquez sur **Envoyer un modèle** sous **Factures et avoirs** ou **Devis**.
4. Indezy montre les champs trouvés, ceux qu'il ne connaît pas (laissés tels quels dans le document), et les mentions qu'exige la loi absentes de votre modèle : elles sont ajoutées à la fin, pour que rien d'obligatoire ne manque.
5. Cliquez sur **Aperçu** pour le télécharger rempli avec votre dernière facture ou votre dernier devis, ou avec un exemple avant le premier.

Le PDF garde la mise en page intégrée et son style ; le fichier Word est une copie à relire, adapter ou archiver. Voir [Devis et factures](/help/invoices-quotes).

## Le document

`{{invoice.…}}` et `{{quote.…}}` valent exactement `{{document.…}}` : écrivez celui qui se lit le mieux dans votre modèle.

| Champ | Ce qu'il donne | Exemple |
|---|---|---|
| `{{document.title}}` | L'intitulé : Facture, Avoir, Facture d'acompte ou Devis | Facture |
| `{{document.number}}` | Le numéro, une fois émis ou envoyé | F2026-0042 |
| `{{document.issueDate}}` | La date d'émission | 17 novembre 2026 |
| `{{document.dueDate}}` | L'échéance d'une facture | 17 décembre 2026 |
| `{{document.period}}` | La période d'une facture | Période : du 1 novembre 2026 au 30 novembre 2026 |
| `{{document.validUntil}}` | La fin de validité d'un devis | 17 décembre 2026 |
| `{{document.draft}}` | Ce qu'un brouillon dit ne pas être, vide une fois émis | BROUILLON — ce document n'est pas une facture |

## Vous, le vendeur

| Champ | Ce qu'il donne | Exemple |
|---|---|---|
| `{{seller.name}}` | Votre nom, suivi de « EI » pour une entreprise individuelle | Alice Martin EI |
| `{{seller.details}}` | Toutes les lignes sous votre nom sur le PDF : adresse, forme et capital, SIRET, TVA | 18 rue du Sentier, 75002 Paris, SIRET : 552 100 554 00012 |
| `{{seller.address}}` | Votre adresse de facturation | 18 rue du Sentier, 75002 Paris |
| `{{seller.siret}}` | Votre SIRET | 55210055400012 |
| `{{seller.vatNumber}}` | Votre numéro de TVA, vide en franchise | FR40552100554 |
| `{{seller.iban}}` | Votre IBAN, par groupes de quatre | FR76 3000 6000 0112 3456 7890 189 |
| `{{seller.bic}}` | Votre BIC | BNPAFRPP |
| `{{logo}}` | Votre logo, à la taille choisie dans Facturation ; seul dans son paragraphe ou sa cellule | |

## Le client

| Champ | Ce qu'il donne | Exemple |
|---|---|---|
| `{{client.name}}` | Le nom du client | Banque Exemple |
| `{{client.details}}` | Son adresse, son SIREN et sa TVA, comme sur le PDF | 29 boulevard Haussmann, Paris |
| `{{client.address}}` | Son adresse | 29 boulevard Haussmann, Paris |
| `{{client.siren}}` | Son SIREN | 552120222 |
| `{{client.vatNumber}}` | Son numéro de TVA | FR27552120222 |

## Les lignes

Mettez `{{#lines}}` au début d'une ligne de tableau et `{{/lines}}` à sa fin : cette ligne est répétée pour chaque ligne du document. Entre les deux, ces champs donnent les valeurs de la ligne.

| Champ | Ce qu'il donne | Exemple |
|---|---|---|
| `{{#lines}}` | Ouvre la ligne répétée pour chaque ligne | |
| `{{line.description}}` | La désignation de la ligne | Développement Java |
| `{{line.quantity}}` | Sa quantité, en jours ou en unités | 18,5 |
| `{{line.unitPrice}}` | Son prix unitaire HT | 650,00 € |
| `{{line.vatRate}}` | Son taux de TVA | 20 % |
| `{{line.amount}}` | Son montant HT | 12 025,00 € |
| `{{/lines}}` | Ferme la ligne répétée | |

## Totaux, mentions et notes

| Champ | Ce qu'il donne | Exemple |
|---|---|---|
| `{{totals.excludingVat}}` | Le total HT | 12 025,00 € |
| `{{totals.vat}}` | La TVA, vide en franchise | 2 405,00 € |
| `{{totals.toPay}}` | Le montant à payer | 14 430,00 € |
| `{{mentions}}` | Les mentions qu'exige la loi : TVA ou franchise, délai et coordonnées de paiement, absence d'escompte, pénalités de retard et indemnité de 40 € (pour un devis, sa validité et son acompte) | Paiement à 30 jours… |
| `{{notes}}` | Les notes du document | Bon de commande 4500012345 |
| `{{acceptance}}` | Les mots d'un devis au-dessus de la signature du client | Bon pour accord — date, signature et cachet… |

## Montrer une partie selon le cas

Placez une partie entre `{{#vatExempt}}` et `{{/vatExempt}}` pour ne la montrer qu'en franchise de TVA, ou entre `{{^vatExempt}}` et `{{/vatExempt}}` pour ne la montrer qu'avec la TVA.

| Champ | Ce qu'il donne | Exemple |
|---|---|---|
| `{{#vatExempt}}` | Ouvre une partie montrée seulement sans TVA | `{{#vatExempt}}TVA non applicable{{/vatExempt}}` |
| `{{^vatExempt}}` | Ouvre une partie montrée seulement avec TVA | `{{^vatExempt}}TVA {{seller.vatNumber}}{{/vatExempt}}` |
| `{{/vatExempt}}` | Ferme la partie | |

## Comment les valeurs s'écrivent

- Dates et montants s'écrivent dans la langue du document : `17 novembre 2026` et `1 250,00 €` en français, `17 November 2026` et `€1,250.00` en anglais.
- Une valeur que le document n'a pas (l'échéance d'un devis, un numéro de TVA en franchise) ne laisse rien.
- Un champ qu'Indezy ne connaît pas reste tel quel, et il est signalé à l'envoi du modèle.
- Les valeurs sur plusieurs lignes, comme une adresse, gardent leurs retours à la ligne.

> **Astuce :** Word découpe en morceaux un champ tapé avec une mise en forme mêlée (en partie en gras, en partie non). Indezy les recolle, mais donnez à tout le champ la même mise en forme : la valeur prend celle du premier morceau de son paragraphe.

## Un formulaire PDF à la place

Vous pouvez aussi envoyer un **formulaire PDF à remplir** (fait dans Acrobat, LibreOffice ou tout éditeur de PDF) à la place d'un document Word. Nommez ses champs d'après les variables, sans accolades : un champ `number` dans un champ `invoice` (écrit `invoice.number` par la plupart des éditeurs), ou simplement `invoice_number`. Les champs d'une ligne portent son rang : `line_description_1` et `line_amount_1` pour la première ligne, `_2` pour la deuxième. Une case à cocher nommée `vatExempt` est cochée quand vous ne facturez pas de TVA.

**Télécharger en Word** donne alors un PDF : votre formulaire rempli et aplati. Les lignes au-delà des rangées du formulaire, et les mentions pour lesquelles il n'a pas de champ, vont sur une page ajoutée à la fin. Un PDF protégé, avec des scripts ou sans champ est refusé. Comme la copie Word, c'est une copie pour vous : la facture que vous envoyez reste le PDF Factur-X intégré.
