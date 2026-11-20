Dernière mise à jour : 20 novembre 2026

La présente politique explique quelles données personnelles le service Indezy (https://indezy.swirlit.dev, le « Service ») traite, pourquoi, sur quelle base, pendant combien de temps, avec qui elles sont partagées et comment exercer vos droits. Elle est établie conformément au règlement (UE) 2016/679 (RGPD) et à la loi n° 78-17 du 6 janvier 1978 relative à l'informatique, aux fichiers et aux libertés.

Elle s'applique aux utilisateurs du Service, aux membres de son réseau et aux personnes qui reçoivent un lien ou un e-mail envoyé depuis le Service (client validant un compte rendu d'activité, personne réservant un appel, auteur d'une recommandation, contact invité au réseau).

## Responsable du traitement

Le responsable du traitement est Zaid Chefchaouni, éditeur du Service sous la marque Swirlit (l'« Éditeur »), dont l'adresse figure dans les [mentions légales](/legal/notice) : [à compléter].

Contact pour toute question relative à vos données : zaid@swirlit.dev.

L'Éditeur n'a pas désigné de délégué à la protection des données. [à vérifier]

## Qui décide de quelles données

Le Service traite deux catégories de données, avec deux rôles différents.

- **Vos propres données** (votre compte, votre profil, votre Espace de travail, votre activité sur le réseau) : l'Éditeur en est le responsable du traitement.
- **Les données de tiers que vous enregistrez** (vos contacts, les interlocuteurs de vos clients et ESN, les personnes physiques clientes, les personnes qui réservent un appel, les valideurs de vos CRA, les auteurs des recommandations que vous demandez) : vous en êtes le responsable du traitement, et l'Éditeur agit comme votre sous-traitant, dans les conditions de l'article 14 des [conditions d'utilisation](/legal/terms). Il vous appartient de disposer d'une base légale et d'informer ces personnes.

Par exception, l'Éditeur est responsable du traitement pour deux usages qu'il fait de ces données pour le réseau : la conservation d'une empreinte des adresses invitées (voir « Invitations au réseau ») et la suggestion de membres figurant dans vos contacts (voir « Réseau des membres »).

## Les données que nous traitons

### Compte et connexion

- **Données :** adresse e-mail, nom et prénom ; en production, la connexion passe par le service de connexion de Swirlit (Keycloak, exploité par l'Éditeur sur sa propre plateforme), et le mot de passe n'est jamais transmis à Indezy ; secret d'authentification à deux facteurs pour un compte local ; sessions ouvertes (navigateur et système, date d'ouverture et de dernière utilisation, sans adresse IP) ; jetons d'API (seule leur empreinte est conservée) ; journal d'activité du compte (connexions, connexions refusées, changements de sécurité, exports, imports, demandes relatives aux contacts, actions des administrateurs).
- **Finalités :** créer et sécuriser votre compte, vous permettre de vous connecter et de gérer vos sessions.
- **Base légale :** exécution du contrat (article 6.1.b du RGPD) ; pour le journal d'activité, intérêt légitime de l'Éditeur et des utilisateurs à la sécurité des comptes (article 6.1.f).
- **Conservation :** pendant la vie du compte ; les sessions terminées, 30 jours ; le journal d'activité, un an.

### Profil et Espace de travail

- **Données :** identité et coordonnées, adresse et ville, société et titre, présentation, liens (site, LinkedIn, GitHub), photo, langues, compétences, tarif cible, disponibilité, préférences de trajet ; identité de facturation (raison sociale, forme juridique, SIRET, numéro de TVA, adresse, IBAN et BIC) ; opportunités, étapes d'entretien, notes de journal, clients, sources, saisons, modèles ; comptes rendus d'activité, bons de commande, devis, factures et avoirs ; documents déposés (pièces jointes, attestations URSSAF, extraits d'immatriculation, assurance, RIB, pièces d'identité) ; formations ; page de réservation ; webhooks et leurs livraisons ; préférences (langue, thème, délais, rétention des contacts).
- **Finalités :** fournir les fonctions du Service : suivi des opportunités, facturation, documents, tableau de bord, bilan annuel, calendrier.
- **Base légale :** exécution du contrat.
- **Conservation :** jusqu'à leur suppression par vous ou jusqu'à la suppression du compte ; les livraisons de webhooks, 30 jours.

Les documents d'identité et bancaires sont sensibles : ne déposez que ceux dont vous avez besoin. Les fichiers importés pour une reprise de données servent uniquement à créer les enregistrements choisis et ne sont pas conservés en tant que tels.

### Flux d'offres et recherches

- **Données :** recherches enregistrées et leurs réglages d'e-mail, offres enregistrées, masquées, ouvertes ou importées, date d'ouverture d'une offre depuis un e-mail, suggestions écartées, éditeurs et sites exclus.
- **Finalités :** afficher les offres pertinentes, calculer leur correspondance avec votre profil, vous envoyer les nouvelles offres de vos recherches.
- **Base légale :** exécution du contrat.
- **Conservation :** jusqu'à leur suppression ou celle du compte. Les envois d'e-mails d'une recherche s'arrêtent d'eux-mêmes après 60 jours sans offre ouverte depuis ces e-mails.

### E-mails

- **Données :** adresse e-mail du destinataire, objet et contenu de l'e-mail.
- **E-mails envoyés :** avis de suppression du compte ; nouvelles offres des recherches enregistrées ; récapitulatif hebdomadaire (désactivé par défaut) ; appels réservés par votre lien de réservation ; messages et notifications non lus du réseau ; à votre demande, demandes de recommandation et invitations au réseau adressées à vos contacts.
- **Finalités :** vous informer de ce qui concerne votre compte et des événements que vous avez choisi de suivre.
- **Base légale :** exécution du contrat ; pour les e-mails adressés à vos contacts, l'Éditeur agit pour votre compte.
- **Conservation :** l'Éditeur ne conserve pas le contenu des e-mails envoyés ; leur prestataire d'envoi en conserve une trace technique [à vérifier : durée de conservation chez Resend].

Les e-mails ne contiennent ni pixel de suivi ni lien de redirection de mesure des clics. Chaque e-mail facultatif comporte un lien de désinscription, et chaque catégorie se règle dans **Profil → Notifications** et **Profil → Réseau**.

### Réseau des membres

- **Données :** identifiant (@pseudo) et carte de membre (nom, et selon vos choix photo, titre, compétences, ville, disponibilité, langues, télétravail) ; section « À propos » ; publications, brouillons, publications programmées, commentaires, réactions, partages, enregistrements, votes aux sondages ; fichiers (les images sont réencodées sans leurs métadonnées, telles que lieu, appareil et date ; les documents sont analysés par un antivirus ; leur texte est lu au dépôt pour le contrôle de confidentialité et n'est pas conservé) ; messages, réactions, et, si les deux interlocuteurs l'activent, accusés de lecture et indicateur de saisie ; abonnements, demandes d'abonnement, blocages, mises en sourdine ; communautés ; événements et participations ; idées et soutiens ; notifications ; visites de votre page ; affichages de vos publications ; publications écartées (« Pas intéressé ») ; vérification de votre SIRET.
- **Finalités :** faire fonctionner le réseau que vous avez choisi de rejoindre, vous suggérer des membres à suivre et vous montrer les statistiques de vos publications et de votre page.
- **Base légale :** exécution du contrat.
- **Conservation :** pendant votre adhésion et la vie du compte ; un contenu que vous supprimez est supprimé avec ce qui en dépend (réactions, commentaires, fichiers) ; les visites de page, 90 jours ; les fichiers jamais joints à une publication ou à un message, un jour.

**Visibilité.** Le réseau n'est visible que des membres connectés. Votre carte n'affiche que les champs que vous laissez visibles ; avec une carte privée, vos publications ne sont lues que par les abonnés que vous approuvez ; un membre bloqué ne voit pas vos contenus. Les membres dont vous ouvrez la page voient que vous l'avez consultée, sauf si vous activez la navigation privée.

**Suggestions.** Pour vous suggérer des membres à suivre, le Service compare l'adresse de connexion des membres aux adresses que les autres membres ont enregistrées dans leurs contacts, ainsi que les compétences, la ville et les abonnements visibles sur les cartes. Le contact concerné n'est pas informé et rien d'autre n'est communiqué. Cette suggestion repose sur l'intérêt légitime de l'Éditeur et des membres à faciliter les mises en relation. [à vérifier : moyen de s'opposer à cette suggestion] La suggestion de membres passés par les mêmes ESN ne s'applique qu'aux membres qui l'ont tous deux activée.

**Vérification du SIRET.** Si vous la demandez, seul votre SIRET est envoyé à l'annuaire public des entreprises pour vérifier que l'établissement est actif et à votre nom.

### Modération et prévention des abus

- **Données :** signalements (motif, précisions, texte du contenu tel qu'il était au moment du signalement), décisions et notes des modérateurs, sanctions, contenus retenus par le filtre anti-spam.
- **Finalités :** assurer la sécurité du réseau, traiter les signalements, lutter contre le spam, garder la trace des décisions.
- **Base légale :** intérêt légitime de l'Éditeur et des membres à un réseau sûr ; respect des obligations légales de l'Éditeur en tant qu'hébergeur (article 6.1.c).
- **Conservation :** les signalements tranchés sont conservés comme trace des décisions jusqu'à la suppression du compte de leur auteur ou de la personne visée ; les sanctions figurent dans le journal d'activité du compte pendant un an.

Le filtre anti-spam attribue un score à chaque publication et commentaire. Un contenu retenu n'est publié ou rejeté qu'après examen par un administrateur : aucune décision n'est prise sur le seul fondement d'un traitement automatisé.

### Invitations au réseau

- **Données :** l'adresse invitée est conservée uniquement sous forme d'empreinte (SHA-256), avec la date et le membre qui invite ; si la personne choisit « Ne plus jamais être invité », l'empreinte de son adresse est ajoutée à une liste d'opposition.
- **Finalités :** n'envoyer qu'une invitation par contact, prévenir le membre qui invite lorsque la personne rejoint le réseau, et respecter durablement le refus d'être invité.
- **Base légale :** intérêt légitime de l'Éditeur à limiter les invitations et à respecter les oppositions.
- **Conservation :** les invitations, jusqu'à la suppression du compte du membre qui invite ; la liste d'opposition, sans limite de durée, aussi longtemps que nécessaire pour respecter le refus.

Une empreinte ne permet pas de lire l'adresse, mais reste une donnée pseudonymisée.

### Profil public et recommandations

- **Profil public :** si vous l'activez, une page lisible par toute personne disposant du lien affiche votre nom, titre, ville, présentation, compétences, langues, liens, photo, disponibilité et les recommandations que vous approuvez. Votre e-mail, votre téléphone, votre adresse et votre Espace de travail n'y figurent jamais. Les moteurs de recherche sont invités à ne pas l'indexer (fichier robots.txt). Base légale : exécution du contrat, à votre demande ; vous pouvez la désactiver à tout moment.
- **Recommandations :** l'auteur d'une recommandation fournit son nom, son texte et, s'il le souhaite, son titre, et accepte d'être nommé avec son texte. Base légale : son consentement (article 6.1.a), qu'il peut retirer en s'adressant au freelance ou à zaid@swirlit.dev. Conservation : jusqu'à la suppression de la recommandation ou du compte du freelance. Une recommandation n'apparaît qu'une fois approuvée par le freelance.

### Pages ouvertes sans compte

Certaines pages s'ouvrent sans compte, à partir d'un lien envoyé par un utilisateur : validation d'un CRA, partage de documents, réservation d'un appel, recommandation, invitation, désinscription.

- **Validation d'un CRA :** nom saisi par le valideur, décision, motif ou commentaire, date et heure.
- **Réservation d'un appel :** nom, e-mail, et le cas échéant société et message saisis par la personne qui réserve. Aucun e-mail ne lui est envoyé par le Service.
- **Documents partagés :** nombre d'ouvertures du lien et date de la dernière, sans adresse IP.

Ces données sont traitées pour le compte de l'utilisateur qui a envoyé le lien, qui en est le responsable du traitement. Pour limiter les abus, l'adresse IP d'une personne qui réserve un appel sert uniquement, en mémoire, à compter ses réservations de l'heure ; elle n'est jamais enregistrée en base de données.

### Contacts et clients de l'utilisateur

- **Données :** nom, e-mail, téléphone, société, fonction, notes, échanges (date, canal, résumé), liens entre contacts, organigrammes ; pour les clients, adresse, SIREN, numéro de TVA, notes et évaluation ; base légale choisie pour chaque contact.
- **Rôle :** l'utilisateur est responsable du traitement ; l'Éditeur, sous-traitant.
- **Conservation :** fixée par l'utilisateur. Il peut définir une durée (1, 2, 3 ou 5 ans) au-delà de laquelle un contact sans échange ni changement est anonymisé chaque nuit (identité, notes, résumés d'échanges et notes de liens effacés).

Si vous figurez parmi les contacts d'un utilisateur, adressez vos demandes à cet utilisateur : il peut télécharger ce qu'il détient sur vous ou vous effacer. Si vous écrivez à l'Éditeur, il transmettra votre demande à l'utilisateur concerné ou l'aidera à y répondre.

### Offres collectées sur les sites d'emploi

- **Données :** les offres sont collectées sur des sites d'emploi publics. Elles peuvent contenir incidemment le nom d'une personne cité dans leur texte ; les coordonnées de candidature que les sites publient dans des champs dédiés (e-mail, téléphone du recruteur) ne sont pas collectées.
- **Finalités :** constituer le flux d'offres et les indicateurs de marché.
- **Base légale :** intérêt légitime de l'Éditeur et des utilisateurs à disposer d'une vue d'ensemble des missions publiées.
- **Conservation :** 90 jours après leur publication ou jusqu'à leur expiration, sauf lorsqu'un utilisateur les a enregistrées, y a postulé ou les a importées dans son Espace de travail.

Toute personne citée dans une offre peut s'opposer à ce traitement en écrivant à zaid@swirlit.dev.

### Sécurité et journaux techniques

- **Données :** journaux de l'application, qui ne contiennent ni mot de passe, ni jeton, ni e-mail, ni contenu saisi ; compteurs temporaires en mémoire contre les tentatives de connexion répétées (15 minutes) et les abus ; résultat de l'analyse antivirus des documents déposés ; journaux d'accès de l'infrastructure, qui peuvent contenir l'adresse IP, la date et l'adresse demandée [à compléter : contenu exact et durée de conservation des journaux d'accès].
- **Finalités :** assurer la sécurité et le bon fonctionnement du Service, diagnostiquer les incidents.
- **Base légale :** intérêt légitime de l'Éditeur à sécuriser le Service.
- **Conservation :** [à compléter : durée de conservation des journaux techniques].

Lorsque la loi l'impose, certaines données d'identification peuvent devoir être conservées plus longtemps pour répondre aux réquisitions des autorités. [à vérifier : obligations de conservation applicables à l'Éditeur en tant qu'hébergeur au titre de la LCEN et du décret n° 2021-1362 du 20 octobre 2021]

### Administration et support

- **Données :** les administrateurs peuvent rechercher un compte par e-mail ou par nom, le désactiver ou le réactiver, et consulter des statistiques d'usage agrégées ; échanges par e-mail avec l'Éditeur.
- **Finalités :** administrer le Service, répondre à vos demandes.
- **Base légale :** intérêt légitime de l'Éditeur ; exécution du contrat.
- **Conservation :** les échanges de support, le temps nécessaire au traitement de la demande, puis [à compléter : durée].

## Services publics et tiers interrogés

Certaines fonctions interrogent des services extérieurs, qui ne reçoivent que les données strictement nécessaires :

- **API Géo de l'État (geo.api.gouv.fr) :** ville et code postal d'une offre ou de votre profil, pour situer les offres ; jamais l'adresse d'une personne ni les données de votre compte.
- **Service de recherche d'adresses de l'IGN (Géoplateforme) :** votre adresse et celle de vos clients, sans aucun nom, pour estimer les temps de trajet et placer les opportunités sur la carte. Les coordonnées obtenues sont gardées dans un cache d'adresses, sans lien avec un compte.
- **Annuaire des entreprises (recherche-entreprises.api.gouv.fr) et BODACC (DILA) :** les mots saisis dans le nom d'un nouveau client, son SIREN, ou votre SIRET lors de la vérification de votre carte.
- **Tuiles cartographiques OpenStreetMap (tile.openstreetmap.org) :** lorsque vous affichez la carte des opportunités, votre navigateur charge les fonds de carte auprès de la Fondation OpenStreetMap, qui reçoit votre adresse IP et les zones affichées. Les lieux sont calculés sur le serveur et ne lui sont pas transmis.
- **Sites d'emploi publics :** ils sont lus par le serveur et ne reçoivent aucune donnée des utilisateurs.
- **Aperçus de liens :** pour afficher l'aperçu du premier lien d'une publication ou d'un message, le serveur lit la page liée ; le site concerné reçoit une requête du serveur, et non de votre navigateur.

Ces services agissent sous leur propre responsabilité. Le Service n'utilise pas Google Maps en production.

## Destinataires

Vos données sont accessibles :

- à l'Éditeur et aux administrateurs qu'il désigne, dans la limite de leurs missions ;
- aux autres membres du réseau, pour ce que vous y publiez, selon vos réglages de visibilité ;
- à toute personne disposant du lien de votre profil public, si vous l'activez ;
- aux destinataires des liens que vous partagez (CRA, documents, page de réservation, recommandation) ;
- aux outils que vous connectez par jetons d'API ou webhooks, sous votre responsabilité ;
- aux autorités, sur demande fondée sur la loi.

Sous-traitants de l'Éditeur :

| Sous-traitant | Rôle | Localisation |
| --- | --- | --- |
| OVH SAS (OVHcloud) | hébergement sur serveurs dédiés | France |
| Resend | envoi des e-mails transactionnels | États-Unis |

Le service de connexion (Keycloak), la base de données, l'antivirus (ClamAV) et les sauvegardes fonctionnent sur la plateforme de l'Éditeur, sur ces mêmes serveurs, sans intervention d'un tiers.

Vos données ne sont ni vendues, ni louées, ni utilisées à des fins publicitaires.

## Transferts hors de l'Union européenne

Les données sont hébergées en France. Deux transferts ont lieu hors de l'Union européenne :

- **Resend (États-Unis)** reçoit l'adresse, l'objet et le contenu des e-mails envoyés par le Service. Ce transfert est encadré par [à vérifier : le cadre de protection des données UE–États-Unis (Data Privacy Framework) et/ou les clauses contractuelles types de la Commission européenne].
- **La Fondation OpenStreetMap (Royaume-Uni)** reçoit, lorsque vous affichez la carte, votre adresse IP ; le Royaume-Uni bénéficie d'une décision d'adéquation de la Commission européenne.

Vous pouvez obtenir une copie des garanties applicables en écrivant à zaid@swirlit.dev.

## Suppression du compte

Depuis **Profil → Compte → Supprimer mon compte** :

1. le compte est mis en attente pendant 30 jours : toutes les sessions se terminent, votre carte, vos publications et votre profil public disparaissent, et un e-mail vous indique la date de suppression ;
2. pendant ces 30 jours, vous pouvez restaurer le compte en vous reconnectant ;
3. au terme des 30 jours, toutes les données du compte et de son Espace de travail sont supprimées définitivement : opportunités, clients, contacts, documents, factures, devis, CRA, réglages, présence sur le réseau (carte, publications avec leurs commentaires et réactions, commentaires, réactions, abonnements, fichiers).

Subsistent après la suppression :

- les messages que vous avez envoyés et leurs fichiers, qui restent lisibles par leurs destinataires et apparaissent comme venant d'un « ancien membre », dans l'intérêt légitime de ces destinataires à conserver leurs conversations ; vous pouvez demander leur effacement à zaid@swirlit.dev, et chaque demande sera examinée ;
- les communautés que vous avez créées, ainsi que les idées que vous avez proposées, attribuées à un « ancien membre » ;
- l'empreinte des adresses ayant refusé toute invitation ;
- votre identité dans le service de connexion de Swirlit, qui n'est pas supprimée automatiquement : demandez sa suppression à zaid@swirlit.dev ;
- les données présentes dans les sauvegardes, jusqu'à leur remplacement [à compléter : durée de conservation des sauvegardes].

Pensez à exporter vos données, et en particulier vos factures, que vous devez conserver, avant de supprimer votre compte.

## Vos droits

Vous disposez des droits suivants sur vos données :

- **Accès :** obtenir la confirmation que vos données sont traitées et en recevoir une copie.
- **Rectification :** corriger vos données, directement dans votre profil ou sur demande.
- **Effacement :** supprimer vos contenus à tout moment, ou supprimer votre compte comme décrit ci-dessus.
- **Portabilité :** **Profil → Compte → Exporter mes données** télécharge vos données dans un fichier JSON structuré, dont une partie peut être réimportée dans un autre compte Indezy.
- **Opposition :** vous opposer à un traitement fondé sur l'intérêt légitime, pour des raisons tenant à votre situation particulière ; vous pouvez aussi désactiver chaque catégorie d'e-mails et de notifications.
- **Limitation :** demander le gel temporaire d'un traitement, notamment le temps d'examiner une contestation.
- **Retrait du consentement :** à tout moment, lorsque le traitement repose sur votre consentement, sans remettre en cause les traitements déjà effectués.
- **Directives après votre décès :** définir des directives sur la conservation, l'effacement et la communication de vos données après votre décès (article 85 de la loi Informatique et Libertés).

Pour exercer ces droits, écrivez à zaid@swirlit.dev. Une réponse vous est apportée dans un délai d'un mois, qui peut être prolongé de deux mois en cas de demande complexe ; vous en êtes alors informé. Un justificatif d'identité ne peut vous être demandé qu'en cas de doute raisonnable sur votre identité.

Vous pouvez introduire une réclamation auprès de la Commission nationale de l'informatique et des libertés (CNIL), 3 place de Fontenoy, TSA 80715, 75334 Paris Cedex 07, ou sur https://www.cnil.fr.

## Caractère obligatoire des données

L'adresse e-mail et le nom sont nécessaires à la création du compte. Certaines fonctions exigent des données complémentaires (par exemple le SIRET, l'adresse et l'IBAN pour émettre une facture). Les autres données sont facultatives.

## Décisions automatisées

Le Service ne prend aucune décision produisant des effets juridiques à votre égard ou vous affectant de manière significative sur le seul fondement d'un traitement automatisé. La correspondance des offres avec votre profil, les suggestions et les indicateurs sont de simples aides ; les contenus retenus par le filtre anti-spam sont examinés par un administrateur.

## Cookies et stockage local

Le Service n'utilise que les cookies et le stockage local nécessaires à son fonctionnement ou à des réglages que vous choisissez. Conformément à l'article 82 de la loi Informatique et Libertés, ils ne nécessitent pas votre consentement. Le Service n'utilise **aucun traceur publicitaire, aucun outil de mesure d'audience et aucun module de réseau social**. Ses polices de caractères sont fournies par le Service lui-même.

| Nom ou type | Finalité | Durée |
| --- | --- | --- |
| Cookie `_oauth2_proxy` | maintenir votre session de connexion | [à compléter] |
| Cookies du service de connexion de Swirlit (Keycloak) | authentification | [à compléter : noms et durées] |
| Stockage local : jeton de session et informations du compte | vous garder connecté à l'application | jusqu'à la déconnexion |
| Stockage local : langue, thème, menu replié, vue du tableau de bord, saison choisie, panneaux du flux d'offres, recherches récentes du réseau | mémoriser vos préférences d'affichage | jusqu'à leur effacement dans le navigateur |
| Stockage de session : tentative de connexion | éviter une boucle de redirection lors de la connexion | jusqu'à la fermeture de l'onglet |
| Cache du navigateur (application installable) | consulter hors connexion les dernières pages de votre Espace de travail | 7 jours, effacé à la déconnexion |

Vous pouvez supprimer ces données depuis les réglages de votre navigateur ; vous serez alors déconnecté et vos préférences d'affichage seront réinitialisées.

## Sécurité

L'Éditeur met en œuvre des mesures techniques et organisationnelles adaptées aux risques, notamment :

- chiffrement des échanges (HTTPS) ;
- hébergement sur des serveurs dédiés en France, sur une plateforme exploitée par l'Éditeur ;
- authentification centralisée, authentification à deux facteurs possible, verrouillage après des tentatives de connexion répétées ;
- cloisonnement strict des Espaces de travail, chaque requête étant contrôlée côté serveur ;
- liens partagés imprévisibles, limités dans le temps, révocables, dont seule une empreinte est conservée ;
- analyse antivirus des documents déposés sur le réseau, réencodage des images sans leurs métadonnées ;
- journaux expurgés des mots de passe, jetons, adresses e-mail et contenus saisis.

En cas de violation de données susceptible d'engendrer un risque pour vos droits, l'Éditeur la notifie à la CNIL et, lorsque la loi l'exige, aux personnes concernées.

## Mineurs

Le Service est réservé aux professionnels majeurs et ne s'adresse pas aux mineurs.

## Extension de navigateur

L'extension de navigateur d'Indezy, si vous l'installez, ne conserve que l'adresse de votre instance Indezy. Elle ne demande l'accès à aucun site : elle ne lit que l'adresse de l'onglet ou du lien sur lequel vous l'utilisez, et ne la transmet au Service que lorsque vous cliquez sur son bouton.

## Modification de la politique

Cette politique peut être modifiée pour refléter l'évolution du Service ou de la réglementation. La date de mise à jour figure en tête de page ; en cas de modification substantielle, vous en êtes informé par e-mail ou dans le Service.

## Langue

Cette politique est rédigée en français. La version anglaise est une traduction fournie pour information ; en cas de divergence, la version française prévaut.
