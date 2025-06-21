# indezy

---

# **Product Requirements Document (PRD)**

**Title:** Indezy - Job Tracking App for Freelancers (French Market)
**Owner:** Zaid Chefchaouni | byteworks.dev
**Date:** 2025-06-15
**Version:** 1.0

---

## **1. Problem Statement**

Freelancers in the French tech market struggle to manage their job applications, track interactions, and maintain organized dashboards across various channels like job boards, consulting firms, LinkedIn, and email.

---

## **2. Objective**

Build a cross-platform (web + mobile) job tracking system tailored for freelancers in France, enabling them to:

* Organize project opportunities and applications
* Visualize project pipelines (Kanban)
* Extract insights from project data
* Automate tracking and communication using integrations and AI

---

## **3. Target Users**

* Freelancers (especially in IT/tech)
* Professionals seeking permanent contracts (CDI) as a secondary target
* French market primarily

---

## **4. Key Features**

### 4.1 Job Management

* Capture project details: role, client, consulting firm, rate, location, interview steps, sources
* Add ratings and personal notes
* Attach documents and links

### 4.2 Visualization

* **Kanban board:** drag-and-drop to manage status
* **Dashboard:** analytics on rate, domains, client types, success rates

### 4.3 Integrations

* **Job boards:** Freework, CherryPick, Malt, LinkedIn
* **Email:** Outlook, Gmail – fetch emails, send/receive templates
* **AI:** for auto-detecting job info, generating follow-ups, CV screening insights
* **Chrome Extension:** to capture jobs directly from job boards or LinkedIn

### 4.4 Filtering & Sorting

* By daily rate, location (based on distance from home), tech stack, match score
* By start date, duration, and status

---

## **5. User Interface (UI/UX)**

### 5.1 Public Site

* Homepage with banner
* Three columns to explain main features
* Top-right: **Login**, **Sign Up**
* Right-side Nav: **Home**, **Features**, **Contact**

### 5.2 Sign-Up Flow

* Fields: First Name, Last Name, Email
* Post-login: Fill freelance profile and start adding projects
* OAuth: Google, Microsoft, Facebook, GitHub

### 5.3 Logged-In Dashboard

* **Main menu:** Freelance Profile, Projects, Clients, Sources, Contacts
* **Kanban View:** All active projects, sortable by status
* **Graphs Section:** (Phase 2) Show application trends, rates evolution, source efficiency

---

## **6. Functional Requirements**

* Add/edit/delete Projects, Clients, Contacts, Sources
* Drag & drop interface for moving projects between statuses
* Upload CVs and documents
* Real-time syncing with job board APIs and email clients
* AI-based job parsing and template suggestions
* Multi-device sync (web + mobile apps)

---

## **7. Non-Functional Requirements**

* Mobile responsive
* Fast loading (<2s/page)
* GDPR compliant (for French market)
* Secure authentication and encrypted storage for user data
* Scalable for multiple users

---

## **8. Competitor Analysis**

* **Huntr.co:** Visual Kanban, email tracking, resume features
* **TealHQ.com:** Resume builder, AI, job tracker
  → *Opportunity:* focus on French market, deeper integrations (Freework, CherryPick, Malt), and freelance-specific needs

---

## **9. MVP Scope (Phase 1)**

* User registration and profile setup
* CRUD for Freelance, Client, Project, Contact, Source
* Kanban board

---

## **10. Future Enhancements (Phase 2+)**

* Sort jobs by commuting time (home address to job location)
* Email integration (view + template generation)
* Dashboard (basic graphs)
* In-app calendar with interview reminders
* AI-powered job parsing from PDF, URLs, Outlook/Gmail
* Help calculate reversion rate for better insights into revenue
* Chrome extension (basic scraping)
* Advanced analytics: daily rate evolution, client rating, conversion funnel

---

## **11. Data Model**

**Written in pseudo-code**

<details>
<summary>Expand Data Model</summary>

```
entity Freelance {
  firstName String required
  lastName String required
  email String required
  phone String
  birthDate Date
  address String
  city String
  status EmploymentStatus
  noticePeriodInDays Integer
  availabilityDate Date
  reversionRate Double
  cv File
  @OneToMany
  projects List<Project>
}

entity Client {
  companyName String required
  adress String
  city String required
  domain String
  final Boolean required
  notes String
  @OneToMany
  projects List<Project>
  @OneToMany
  contacts List<Contact>
}

entity Project {
  role String required
  description String
  techStack String
  dailyRate Integer required
  workMode WorkMode
  remoteDaysPerMonth Integer (if hybrid)
  onsiteDaysPerMonth Integer (if hybrid)
  advantages String
  startDate Date
  durationInMonths Integer
  orderRenewalInMonths Integer
  daysPerYear Integer
  documents List<File>
  link String
  source Source
  personalRating Integer
  notes String
  @ManyToOne
  middleman Client
  @ManyToOne
  client Client required
  @ManyToOne
  source Source
  @OneToMany
  steps List<InterviewStep>
}

entity InterviewStep {
  title String required
  date Date
  status StepStatus required
}

entity Contact {
  firstName String required
  lastName String
  email String
  phone String
  notes String
}

entity Source {
  name String required
  type SourceType required
  link String
  isListing Boolean
  popularityRating Integer
  usefulnessRating Integer
  notes String
}

enum EmploymentStatus {
  FREELANCE, PORTAGE, CDI
}

enum StepStatus {
  VALIDATED, FAILED, PLANNED
}

enum SourceType {
  JOB_BOARD, SOCIAL_MEDIA, EMAIL, CALL, SMS
}

enum WorkMode {
  ONSITE, REMOTE, HYBRID
}
```

</details>

---

## **12. Tech Stack**

* **Frontend:** HTML + CSS + Material + Angular
* **Backend:** Java 21 + Spring Boot + Lombok
* **Authentication:** Spring Security + JWT
* **Database:** Postgres
* **DevOps:** GitHub + Jenkins + Docker + Kubernetes
* **OS:** Linux Ubuntu 24.04
* **Dev Env:** DevContainer with all necessary apps/tools

---
