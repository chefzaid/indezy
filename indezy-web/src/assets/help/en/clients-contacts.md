**Clients** and **Contacts** keep track of your relationships: the end clients and ESNs you work with, the people you know there and every exchange with them.

## Add a client or an ESN

1. Open **Clients** and click **New client**.
2. In **Company name**, type at least three characters of the name, or a SIREN: Indezy offers up to five companies from the French public directory, each with its SIREN, legal form, town and NAF code. A closed company is marked **closed**.
3. Pick one: the legal name, SIREN, head office address and intra-EU VAT number fill in. Whatever the directory does not give stays as you typed it.
4. Leave **End client** on for an end client, or turn it off for an ESN, an agency or another intermediary.
5. Optionally add a **Rating** (1 to 5), notes, or tick **Blacklisted** with the **Blacklist reason**.
6. Save.

![The clients list with their type, city and number of projects](/assets/help/screens/clients-en.jpg)

The form's **Billing** section keeps the SIREN and VAT number printed on your invoices. The **Activity report template** section sets the PDF of this client's activity reports: title, **Layout**, **PDF language**, on site and remote split, footer note (see [Timesheets and purchase orders](/help/activity-reports)).

> **Tip:** the list filters as you type, by words, type and city. A client still linked to missions cannot be deleted, and deleting a client deletes its contacts.

## Company health check

For a client or ESN with a SIREN, Indezy checks, on its page and on its opportunities' pages:

- whether it has closed, according to the public company directory;
- whether it is under insolvency proceedings (safeguard, receivership or liquidation), according to its latest BODACC announcement of the last three years.

The page then says so, with the announcement's date. Only the SIREN is sent. A company without a SIREN is not checked.

## Payment delays

Once a client has paid invoices, its page says how it really pays, for example "Pays in 52 days on average, 12 days late (4 paid invoice(s))". The same line shows on the opportunities it pays for. See [Quotes and invoices](/help/invoices-quotes).

## Add and import contacts

A contact belongs to one client. It has a name, email, phone, position, notes and the legal basis for keeping their details.

1. Open **Contacts** and click **Add a contact**, or do it from the client's page.
2. Pick their client and fill in their details.
3. Save.

To add several at once, open the client's page, click **Import contacts**, paste CSV content (a LinkedIn export, for example) or vCard content, then click **Import**. Emails your workspace already has are skipped.

![The contacts list with their client and last exchange](/assets/help/screens/contacts-en.jpg)

## Record exchanges

1. On a contact's page, under **Exchanges**, click **Record an exchange**.
2. Enter the **Date** (today at the latest), the **Channel** (**Email**, **Phone**, **LinkedIn**, **Meeting** or **Other**) and the **Direction**: **From me** or **From them**.
3. For a message from you, set the **Answer**: **Waiting for an answer**, **Answered** or **No answer**.
4. Optionally add a **Summary (optional)** and the opportunity it was about, then save.

The page works out the **Last contact**, the **Response rate** and the **Preferred channel**. A message from you still unanswered after **Follow up after (days)** (5 by default, in **Profile → Preferences**) shows under **Follow-ups due** on the [dashboard](/help/dashboard).

## A contact's network and the org chart

1. In a contact's form, pick their manager under **Reports to**, at the same company or another.
2. On their page, the **Network** card shows their manager, their **Team** and, under **Works with**, the people they work alongside.
3. To link two people, pick the **Person**, optionally say how they work together, then click **Link**.

These links go both ways, often across a client and its ESN. The **Contacts** tab of a client's page draws the **Org chart**, with each person's links to other companies.

## Your contacts' personal data

You are responsible for your contacts' data; Indezy keeps it on your behalf. A contact's **Their data** card offers:

- **Why you keep their details**: **Legitimate interest** (the default), **Consent** or **Contract**;
- **Download their data**: a JSON file of what your workspace holds about them;
- **Erase on their request**: the contact goes, with their exchanges and links.

In **Profile → Preferences**, **Anonymize contacts without exchange for** sets a retention period: **Never**, 1, 2, 3 or 5 years (3 years for new accounts, as the CNIL advises for prospects). Every night, a contact with no exchange or change for that long loses their name, email, phone and notes; the dates and channels of their exchanges stay, so your statistics keep their shape.

> **Tip:** the `{{privacyNotice}}` variable of [email templates](/help/pipeline) tells the recipient who keeps their details and how to see, correct or erase them.

## Invite a contact to the network

If you are a member of the [network](/help/network), you can invite a contact who has an email address.

1. On the contact's page, click **Invite to the network**.
2. Confirm with **Send the invitation**. Indezy emails them on your behalf.
3. The button becomes **Invited to the network on …**, then **Their page on the network** once they join, or **Does not want to be invited to the network** if they declined.

Each contact is invited only once, and you send at most 20 invitations a day. The person can choose **Never invite me again**: no member can invite them any more.
