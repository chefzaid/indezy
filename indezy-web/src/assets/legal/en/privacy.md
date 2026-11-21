Last updated: 20 November 2026

This English version is a translation provided for convenience. The French version is the reference version and prevails in case of conflict.

This policy explains which personal data the Indezy service (https://indezy.swirlit.dev, the "Service") processes, why, on what basis, for how long, with whom it is shared and how to exercise your rights. It is written in accordance with Regulation (EU) 2016/679 (GDPR) and French law no. 78-17 of 6 January 1978 on information technology, data files and civil liberties.

It applies to the Service's users, to the members of its network and to people who receive a link or an email sent from the Service (a client approving an activity report, a person booking a call, the author of a recommendation, a contact invited to the network).

## Controller

The controller is Zaid Chefchaouni, publisher of the Service under the Swirlit brand (the "Publisher"), whose address appears in the [legal notice](/legal/notice): [to be completed].

Contact for any question about your data: zaid@swirlit.dev.

The Publisher has not appointed a data protection officer. [to be verified]

## Who decides about which data

The Service processes two kinds of data, with two different roles.

- **Your own data** (your account, your profile, your Workspace, your activity on the network): the Publisher is its controller.
- **Third-party data you record** (your contacts, people at your clients and ESNs, clients who are individuals, people booking a call, the approvers of your activity reports, the authors of the recommendations you ask for): you are its controller, and the Publisher acts as your processor, under section 14 of the [terms of use](/legal/terms). It is up to you to have a legal basis and to inform those people.

As an exception, the Publisher is controller for two uses it makes of that data for the network: keeping a fingerprint of invited addresses (see "Invitations to the network") and suggesting members who are in your contacts (see "Member network").

## The data we process

### Account and sign-in

- **Data:** email address, first and last name; in production, sign-in goes through the Swirlit sign-in service (Keycloak, run by the Publisher on its own platform), and your password is never sent to Indezy; a two-factor authentication secret for a local account; open sessions (browser and system, when opened and last used, with no IP address); API tokens (only their fingerprint is kept); the account's activity log (sign-ins, refused sign-ins, security changes, exports, imports, contact data requests, administrator actions).
- **Purposes:** creating and securing your account, letting you sign in and manage your sessions.
- **Legal basis:** performance of the contract (article 6(1)(b) GDPR); for the activity log, the legitimate interest of the Publisher and users in account security (article 6(1)(f)).
- **Retention:** for the life of the account; ended sessions, 30 days; the activity log, one year.

### Profile and Workspace

- **Data:** identity and contact details, address and city, company and title, presentation, links (website, LinkedIn, GitHub), photo, languages, skills, target rate, availability, commute preferences; billing identity (legal name, legal form, SIRET, VAT number, address, IBAN and BIC); opportunities, interview steps, journal notes, clients, sources, seasons, templates; activity reports, purchase orders, quotes, invoices and credit notes; uploaded documents (attachments, URSSAF certificates, registration extracts, insurance, bank details, identity documents); trainings; booking page; webhooks and their deliveries; preferences (language, theme, delays, contact retention).
- **Purposes:** providing the Service's functions: opportunity tracking, invoicing, documents, dashboard, yearly review, calendar.
- **Legal basis:** performance of the contract.
- **Retention:** until you delete it or until the account is deleted; webhook deliveries, 30 days.

Identity and bank documents are sensitive: upload only those you need. Files imported to bring data in are used only to create the records chosen and are not kept as such.

### Offer feed and searches

- **Data:** saved searches and their email settings, offers saved, hidden, opened or imported, when an offer was opened from an email, dismissed suggestions, excluded publishers and sites.
- **Purposes:** showing relevant offers, computing how well they match your profile, emailing you the new offers of your searches.
- **Legal basis:** performance of the contract.
- **Retention:** until you delete them or the account is deleted. A search's emails stop by themselves after 60 days without an offer opened from them.

### Emails

- **Data:** the recipient's email address, the email's subject and content.
- **Emails sent:** notice of account deletion; new offers of saved searches; weekly digest (off by default); calls booked through your booking link; unread network messages and notifications; at your request, recommendation requests and network invitations sent to your contacts.
- **Purposes:** telling you about your account and about the events you chose to follow.
- **Legal basis:** performance of the contract; for emails sent to your contacts, the Publisher acts on your behalf.
- **Retention:** the Publisher does not keep the content of emails sent; their sending provider keeps a technical record of them [to be verified: retention period at Resend].

Emails contain no tracking pixel and no click-tracking redirect. Each optional email has an unsubscribe link, and each kind is set in **Profile → Notifications** and **Profile → Network**.

### Member network

- **Data:** handle (@name) and member card (name, and as you choose photo, headline, skills, city, availability, languages, remote work); "About" section; posts, drafts, scheduled posts, comments, reactions, reposts, saves, poll votes; files (images are re-encoded without their metadata, such as place, camera and date; documents are scanned by an antivirus; their text is read on upload for the confidentiality check and not kept); messages, reactions, and, if both parties turn them on, read receipts and the typing indicator; follows, follow requests, blocks, mutes; communities; events and attendance; ideas and support for them; notifications; views of your page; impressions of your posts; posts set aside ("Not interested"); verification of your SIRET.
- **Purposes:** running the network you chose to join, suggesting members to follow and showing you the statistics of your posts and page.
- **Legal basis:** performance of the contract.
- **Retention:** while you are a member and for the life of the account; content you delete is deleted with what depends on it (reactions, comments, files); page views, 90 days; files never attached to a post or a message, one day.

**Visibility.** The network is visible only to signed-in members. Your card shows only the fields you leave visible; with a private card, your posts are read only by the followers you approve; a blocked member does not see your content. Members whose page you open see that you viewed it, unless you turn on private browsing.

**Suggestions.** To suggest members to follow, the Service compares members' sign-in addresses with the addresses other members recorded in their contacts, as well as the skills, city and follows visible on cards. The contact concerned is not told and nothing else is disclosed. This suggestion relies on the legitimate interest of the Publisher and members in making connections easier. You can object to it at any time in **Profile → Privacy** (**Suggestions by my contacts**): you are then no longer suggested to those who have your address in their contacts, and your contacts are no longer suggested to you. Suggestions of members who went through the same ESNs apply only between members who both turned them on.

**SIRET verification.** If you ask for it, only your SIRET is sent to the public company directory to check that the establishment is active and in your name.

### Moderation and abuse prevention

- **Data:** reports (reason, details, the text of the content as it was when reported), moderators' decisions and notes, sanctions, content held by the spam filter.
- **Purposes:** keeping the network safe, handling reports, fighting spam, keeping a record of decisions.
- **Legal basis:** the legitimate interest of the Publisher and members in a safe network; compliance with the Publisher's legal obligations as a hosting provider (article 6(1)(c)).
- **Retention:** decided reports are kept as a record of decisions until the account of their author or of the person targeted is deleted; sanctions appear in the account's activity log for one year.

The spam filter scores each post and comment. Held content is published or rejected only after review by an administrator: no decision is based solely on automated processing.

### Invitations to the network

- **Data:** the invited address is kept only as a fingerprint (SHA-256), with the date and the inviting member; if the person chooses "Never invite me again", the fingerprint of their address is added to an opt-out list.
- **Purposes:** sending only one invitation per contact, telling the inviting member when the person joins the network, and permanently respecting a refusal to be invited.
- **Legal basis:** the Publisher's legitimate interest in limiting invitations and respecting objections.
- **Retention:** invitations, until the inviting member's account is deleted; the opt-out list, without time limit, for as long as needed to respect the refusal.

A fingerprint does not reveal the address, but remains pseudonymised data.

### Public profile and recommendations

- **Public profile:** if you turn it on, a page readable by anyone with the link shows your name, headline, city, presentation, skills, languages, links, photo, availability and the recommendations you approve. Your email, phone, address and Workspace never appear on it. Search engines are asked not to index it (robots.txt file). Legal basis: performance of the contract, at your request; you may turn it off at any time.
- **Recommendations:** the author of a recommendation gives their name, their text and, if they wish, their title, and agrees to be named with their text. Legal basis: their consent (article 6(1)(a)), which they may withdraw by contacting the freelancer or zaid@swirlit.dev. Retention: until the recommendation or the freelancer's account is deleted. A recommendation shows only once the freelancer approves it.

### Pages opened without an account

Some pages open without an account, from a link sent by a user: approving an activity report, shared documents, booking a call, a recommendation, an invitation, unsubscribing.

- **Activity report approval:** the name typed by the approver, the decision, the reason or comment, date and time.
- **Booking a call:** the name, email and, where given, company and message entered by the person booking. The Service sends them no email.
- **Shared documents:** how many times the link was opened and when last, with no IP address.

This data is processed on behalf of the user who sent the link, who is its controller. To limit abuse, the IP address of a person booking a call is used only, in memory, to count their bookings in the hour; it is never recorded in the database.

### The user's contacts and clients

- **Data:** name, email, phone, company, position, notes, exchanges (date, channel, summary), links between contacts, org charts; for clients, address, SIREN, VAT number, notes and rating; the legal basis chosen for each contact.
- **Role:** the user is controller; the Publisher is processor.
- **Retention:** set by the user. They may set a period (1, 2, 3 or 5 years) after which a contact with no exchange or change is anonymised every night (identity, notes, exchange summaries and link notes erased).

If you are among a user's contacts, send your requests to that user: they can download what they hold about you or erase you. If you write to the Publisher, it will pass your request on to the user concerned or help them answer it.

### Offers collected from job boards

- **Data:** offers are collected from public job boards. They may incidentally contain the name of a person mentioned in their text; the application contact details sites publish in dedicated fields (a recruiter's email or phone) are not collected.
- **Purposes:** building the offer feed and market indicators.
- **Legal basis:** the legitimate interest of the Publisher and users in an overview of published missions.
- **Retention:** 90 days after publication or until they expire, unless a user saved, applied to or imported them into their Workspace.

Anyone named in an offer may object to this processing by writing to zaid@swirlit.dev.

### Security and technical logs

- **Data:** application logs, which hold no password, token, email or typed content; temporary in-memory counters against repeated sign-in attempts (15 minutes) and abuse; the result of the antivirus scan of uploaded documents; infrastructure access logs, which may contain the IP address, date and address requested [to be completed: exact content and retention period of access logs].
- **Purposes:** keeping the Service secure and working, diagnosing incidents.
- **Legal basis:** the Publisher's legitimate interest in securing the Service.
- **Retention:** [to be completed: retention period of technical logs].

Where the law requires it, some identification data may have to be kept longer to answer requests from the authorities. [to be verified: retention obligations applying to the Publisher as a hosting provider under the LCEN and decree no. 2021-1362 of 20 October 2021]

### Administration and support

- **Data:** administrators can search for an account by email or name, disable or enable it, and read aggregated usage statistics; email exchanges with the Publisher.
- **Purposes:** administering the Service, answering your requests.
- **Legal basis:** the Publisher's legitimate interest; performance of the contract.
- **Retention:** support exchanges, for as long as needed to handle the request, then [to be completed: period].

## Public services and third parties queried

Some functions query outside services, which receive only the data strictly needed:

- **French government Geo API (geo.api.gouv.fr):** the city and postal code of an offer or of your profile, to locate offers; never a person's address nor your account data.
- **IGN address search (Géoplateforme):** your address and your clients' addresses, without any name, to estimate commute times and place opportunities on the map. The coordinates obtained are kept in an address cache, not linked to any account.
- **Company directory (recherche-entreprises.api.gouv.fr) and BODACC (DILA):** the words typed in a new client's name, its SIREN, or your SIRET when your card is verified.
- **OpenStreetMap map tiles (tile.openstreetmap.org):** when you open the opportunity map, your browser loads the map tiles from the OpenStreetMap Foundation, which receives your IP address and the areas shown. Places are computed on the server and not sent to it.
- **Public job boards:** they are read by the server and receive no user data.
- **Link previews:** to show the preview of the first link of a post or a message, the server reads the linked page; that site receives a request from the server, not from your browser.

These services act under their own responsibility. The Service does not use Google Maps in production.

## Recipients

Your data is accessible:

- to the Publisher and the administrators it appoints, within the limits of their tasks;
- to other members of the network, for what you publish there, according to your visibility settings;
- to anyone with the link to your public profile, if you turn it on;
- to the recipients of the links you share (activity report, documents, booking page, recommendation);
- to the tools you connect through API tokens or webhooks, under your responsibility;
- to the authorities, on a request based on the law.

The Publisher's sub-processors:

| Sub-processor | Role | Location |
| --- | --- | --- |
| OVH SAS (OVHcloud) | hosting on dedicated servers | France |
| Resend | sending transactional emails | United States |

The sign-in service (Keycloak), the database, the antivirus (ClamAV) and the backups run on the Publisher's platform, on those same servers, with no third party involved.

Your data is never sold, rented or used for advertising.

## Transfers outside the European Union

Data is hosted in France. Two transfers take place outside the European Union:

- **Resend (United States)** receives the address, subject and content of the emails the Service sends. This transfer is governed by [to be verified: the EU–US Data Privacy Framework and/or the European Commission's Standard Contractual Clauses].
- **The OpenStreetMap Foundation (United Kingdom)** receives your IP address when you open the map; the United Kingdom benefits from an adequacy decision of the European Commission.

You can obtain a copy of the applicable safeguards by writing to zaid@swirlit.dev.

## Account deletion

From **Profile → Account → Delete my account**:

1. the account is put on hold for 30 days: every session ends, your card, posts and public profile disappear, and an email tells you the date of deletion;
2. during those 30 days, you can restore the account by signing in;
3. at the end of the 30 days, all the data of the account and its Workspace is permanently deleted: opportunities, clients, contacts, documents, invoices, quotes, activity reports, settings, network presence (card, posts with their comments and reactions, comments, reactions, follows, files).

What remains after deletion:

- the messages you sent and their files, which stay readable by their recipients and show as coming from a "former member", in those recipients' legitimate interest in keeping their conversations; you may ask for their erasure at zaid@swirlit.dev, and each request will be considered;
- the communities you created, and the ideas you suggested, attributed to a "former member";
- the fingerprints of addresses that declined every invitation;
- your identity in the Swirlit sign-in service, which is not deleted automatically: ask for its deletion at zaid@swirlit.dev;
- data in backups, until they are overwritten [to be completed: backup retention period].

Remember to export your data, in particular your invoices, which you must keep, before deleting your account.

## Your rights

You have the following rights over your data:

- **Access:** obtain confirmation that your data is processed and receive a copy.
- **Rectification:** correct your data, directly in your profile or on request.
- **Erasure:** delete your content at any time, or delete your account as described above.
- **Portability:** **Profile → Account → Export my data** downloads your data as a structured JSON file, part of which can be imported into another Indezy account.
- **Objection:** object to processing based on legitimate interest, on grounds relating to your particular situation; you can also turn off each kind of email and notification.
- **Restriction:** ask for processing to be temporarily frozen, in particular while a challenge is examined.
- **Withdrawal of consent:** at any time, where processing relies on your consent, without affecting processing already carried out.
- **Instructions after death:** set instructions on the retention, erasure and communication of your data after your death (article 85 of the French Data Protection Act).

To exercise these rights, write to zaid@swirlit.dev. You will receive an answer within one month, which may be extended by two months for a complex request; you will then be told. Proof of identity may be asked for only where there is reasonable doubt about your identity.

You may lodge a complaint with the French data protection authority, the Commission nationale de l'informatique et des libertés (CNIL), 3 place de Fontenoy, TSA 80715, 75334 Paris Cedex 07, France, or at https://www.cnil.fr.

## Whether data is required

The email address and name are needed to create the account. Some functions require additional data (for example the SIRET, the address and the IBAN to issue an invoice). Other data is optional.

## Automated decisions

The Service takes no decision producing legal effects concerning you or similarly significantly affecting you based solely on automated processing. How offers match your profile, suggestions and indicators are mere aids; content held by the spam filter is reviewed by an administrator.

## Cookies and local storage

The Service uses only the cookies and local storage needed for it to work or for settings you choose. Under article 82 of the French Data Protection Act, they do not require your consent. The Service uses **no advertising tracker, no audience measurement tool and no social network plug-in**. Its fonts are served by the Service itself.

| Name or kind | Purpose | Duration |
| --- | --- | --- |
| `_oauth2_proxy` cookie | keeping your sign-in session | [to be completed] |
| Cookies of the Swirlit sign-in service (Keycloak) | authentication | [to be completed: names and durations] |
| Local storage: session token and account information | keeping you signed in to the application | until you sign out |
| Local storage: language, theme, folded menu, dashboard view, selected season, offer feed panels, recent network searches | remembering your display preferences | until cleared in the browser |
| Session storage: sign-in attempt | avoiding a redirect loop when signing in | until the tab is closed |
| Browser cache (installable application) | reading the latest pages of your Workspace offline | 7 days, cleared when you sign out |

You can delete this data from your browser settings; you will then be signed out and your display preferences reset.

## Security

The Publisher implements technical and organisational measures appropriate to the risks, including:

- encrypted connections (HTTPS);
- hosting on dedicated servers in France, on a platform run by the Publisher;
- centralised authentication, optional two-factor authentication, lock-out after repeated sign-in attempts;
- strict separation of Workspaces, every request being checked on the server;
- unguessable shared links, limited in time and revocable, of which only a fingerprint is kept;
- antivirus scanning of documents uploaded to the network, images re-encoded without their metadata;
- logs stripped of passwords, tokens, email addresses and typed content.

In the event of a data breach likely to result in a risk to your rights, the Publisher notifies the CNIL and, where the law requires it, the people concerned.

## Minors

The Service is reserved for adult professionals and is not intended for minors.

## Browser extension

Indezy's browser extension, if you install it, keeps only the address of your Indezy instance. It asks for access to no site: it reads only the address of the tab or link you use it on, and sends it to the Service only when you click its button.

## Changes to this policy

This policy may change to reflect changes in the Service or the regulations. The date of the last update appears at the top of the page; in the event of a material change, you are told by email or in the Service.

## Language

This policy is written in French. The English version is a translation provided for information; in case of conflict, the French version prevails.
