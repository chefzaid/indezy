Your account is set up in **Profile**, from your account menu at the top right. This article covers sign-in and security, notifications and emails, developer tools, your data and how the app looks.

## Sign in and secure your account

Depending on your instance, you sign in with your email and password, or with **Continue with SSO**. A password has at least 8 characters.

To change your password:

1. Open **Profile → Security**.
2. Click **Change password**, enter the **Current password**, then the **New password** twice.

A new password signs out every other session. There is no reset for a forgotten password on local accounts yet.

### Turn on two-factor authentication

1. In **Profile → Security**, under **Two-factor authentication**, click **Enable**.
2. Add the secret shown to your authenticator app.
3. Enter the 6-digit **Verification code**, then **Verify & activate**.

Each time you sign in, your app gives you the code asked for. Recovery codes do not exist yet: keep your authenticator app backed up.

## Sessions and account activity

**Where you're signed in**, in **Profile → Security**, lists each browser or app signed in: its system, how it signed in and when it was last active. **This browser** marks the one you are using.

- **Sign out** ends one session.
- **Sign out everywhere else** ends all the others.

**Account activity** lists sign-ins (and refused ones), sign-outs, password and two-factor changes, exports and imports, and administrator actions, over 12 months.

> **Tip:** a session you don't recognize? Sign it out, then change your password.

## Notifications and emails

**Profile → Notifications** holds switches saved at once:

- **Weekly digest**: **Email me a weekly digest**, every Monday morning, off by default. Pick its sections under **What the digest gives** (see [Dashboard](/help/dashboard)).
- **Booked calls**: **Email me each call booked** (see [Calendar](/help/calendar)).
- **Network messages**: **Email me the messages unread for an hour**. One email, at most every three hours, without the messages' text.
- **New offers**: **Count new offers next to Offers in the menu**.

Reminders of interviews, follow-ups and opportunities gone quiet always show on the dashboard. Each saved search sets its own emails (see [Offers](/help/offers)). Network notifications are set kind by kind in **Profile → Network** (see [Network](/help/network)). Every email has a one-click unsubscribe link.

## Privacy: all your consents

**Profile → Privacy** gathers in one place everything you agree to or refuse.

- The switches save at once: on the network, **Suggestions by my contacts**, **Suggestions by the same ESNs**, **Private card**, **Share my rates with the benchmark** (off by default), **Browse privately**, **Receive message requests**, **Read receipts**, **Typing indicator** and **Online presence**; for emails, **Weekly digest**, **Unread messages by email** and **Booked calls**.
- The settings that live in a larger form (what your card shows, each network notification, the digest's sections, saved search emails, automatic archiving, contact anonymization, the public profile, exporting and deleting the account) each have a **Change** button that opens their tab.

**Suggestions by my contacts** is on by default: you are suggested to members who have your address in their contacts, and members among your contacts are suggested to you. Off on either side, no suggestion by contacts between you.

## API tokens

A token lets a script, a spreadsheet or an automation service **read** your workspace. It never changes anything and cannot reach your account settings.

1. Open **Profile → Security → API tokens**.
2. Enter the **Token name** and choose **Expires after** (30, 90 or 365 days, or **Never**).
3. Under **It reads**, narrow it if you like: **Opportunities and interviews**, **Activity reports and purchase orders**, **Clients, contacts and sources**, **Invoices**, **Compliance documents**, **Offers**. With nothing ticked, it reads **The whole workspace**.
4. Click **Create token**, then **Copy token**: it won't be shown again.

An account holds 10 tokens at most. **Revoke** cuts access at once.

## Webhooks

A webhook tells your tools (Zapier, Make, n8n, a script) about your workspace's events: an opportunity added or moved, an activity report sent, approved or rejected, an invoice issued or paid, a call booked.

1. Open **Profile → Security → Webhooks**.
2. Enter the **HTTPS address** and tick the **Events**.
3. Click **Add the webhook**, then **Copy the secret**: it signs each delivery and is shown only once.

**Send a test** checks it arrives; **Latest deliveries** shows the last 20. A delivery that fails is tried again several times; after 20 failures in a row the webhook turns off until you click **Turn on**. A workspace has 5 webhooks at most.

## Fill in your profile from your CV or LinkedIn

**Profile → Personal information → Import my profile** reads your CV (PDF, Word .docx or .doc, or OpenDocument, 10 MB at most, read by AI) or LinkedIn's archive of your data (read without AI) into your profile's fields: name, phone, city, position, company, presentation, links, languages, skills with their years (and the level your CV states), and certifications. The file is not kept.

To get LinkedIn's archive: on LinkedIn, **Settings & Privacy → Data privacy → Get a copy of your data**; LinkedIn emails you a ZIP, usually within a day. Quicker, your profile's PDF: on your own LinkedIn profile, **More → Save to PDF**. It is read without AI too, but lists only your three top skills and your certifications without their dates.

1. Click **Choose my CV** or **Choose my LinkedIn archive or PDF**.
2. Tick what to take: each field found shows what your profile holds now; the empty ones are ticked. Skills and languages your profile lacks are added, none removed. A skill your profile already names, under another spelling ("spring-boot" for "Spring Boot") or a known alias ("JS" for JavaScript), is listed as already there, and a language your profile does not offer as left out. A profile names 50 skills at most: when more are found than it has room for, choose which to add.
3. Click **Fill in the form**, check it, then **Save**: nothing is saved before, except the certifications you ticked.

Certifications go to **Trainings and certifications** on the [Documents](/help/documents) page, with their issuer, the date obtained and the expiry; one your trainings already name is marked **Already in your trainings** and not added again. Positions and schools go to **Past positions** and **Education** under your personal information, kept off your public profile until you show them; one your profile holds already is marked **Already in your profile**. Importing again months later so ticks only what is new.

Tick **Keep … as my profile's CV** to keep the file read: it then waits under **My CV** in your personal information, to download and attach to your applications. **Keep a CV** there keeps one without importing it, and **Replace** or **Delete** changes it; it is checked for viruses first.

A scanned CV without text cannot be read. Reading a CV uses your monthly AI allowance, or your own key; LinkedIn's archive does not. When AI is not available, your CV is read by its headings (Profile, Skills, Experience, Education, Languages, Certifications) instead: check what it found, as a CV in columns or tables reads better with AI.

## Export and import your data

- **Profile → Account → Export my data** downloads all your data as JSON.
- **Profile → Account → Import data** brings an Indezy export (50 MB at most) into your workspace.

To import:

1. Click **Choose an export file**.
2. Read the preview: each record is new, already here or left out (with the reason).
3. Click **Import**: nothing is saved before.

The import brings sources, clients and ESNs, contacts, seasons, opportunities with their steps, and journal notes. Importing the same file twice adds nothing. The profile, activity reports, offers, templates and attachments are not imported.

## Delete your account

1. Open **Profile → Account**.
2. Click **Delete my account**.
3. Type your email to confirm.

At once, you are signed out everywhere, nobody can sign in, and your card, posts and public page are hidden. An email tells you the deletion date. For 30 days, signing in offers **Restore my account**, which brings it back as it was. After that, everything is deleted for good. Messages you sent stay with the members you wrote to, from a **Former member**.

> **Tip:** export your data before deleting your account if you want to keep it.

## Language and theme

- Change the language (French or English) with the globe icon in the top bar, or in **Profile → Preferences → Language**. It is saved with your account.
- Set the theme in **Profile → Preferences → Theme**: **Light**, **Dark** or **Auto**, then **Save**. The moon or sun icon in the top bar switches between light and dark.

## Use Indezy offline

On the production instance, Indezy installs as a web app. Without a connection, it shows what it last loaded of your workspace's main pages, kept seven days, and a banner tells you so. Changes need a connection. Downloads, exports, your sessions and tokens are never kept, and signing out forgets everything.
