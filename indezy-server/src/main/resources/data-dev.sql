-- PostgreSQL sample data loaded only by the explicit "seed" profile.
-- Run it through `mask db-reset`; normal application startup never executes this file.

TRUNCATE TABLE
    offer_publisher_blocks,
    offer_site_exclusions,
    saved_offer_searches,
    offer_skips,
    job_offer_states,
    job_offer_skills,
    job_offers,
    offer_site_statuses,
    geo_places,
    activity_report_days,
    activity_reports,
    project_documents,
    project_notes,
    seasons,
    interview_step_checklist,
    interview_steps,
    contacts,
    projects,
    sources,
    clients,
    user_skills,
    user_skill_details,
    user_languages,
    users,
    freelances
RESTART IDENTITY CASCADE;

-- The authenticated user id and freelance id are deliberately both 1 because the
-- current frontend uses the authenticated account id as its freelance workspace id.
-- Login: john.doe@example.com / password123
INSERT INTO freelances (
    first_name, last_name, email, phone, birth_date, address, city, status,
    notice_period_in_days, availability_date, reversion_rate, income_tax_rate,
    cv_file_path, password_hash, created_at, updated_at, version
) VALUES (
    'John', 'Doe', 'john.doe@example.com', '+33 6 12 34 56 78', '1990-04-17',
    '18 rue du Sentier', 'Paris', 'FREELANCE', 30, CURRENT_DATE + 21,
    46.0, 6.0, '/cv/john-doe.pdf',
    '$2a$10$XgbOojgg.CTmnSP8gwpOT.aikY7bnfM4cgCrQhgJOh5UAY1lOpC9S',
    CURRENT_TIMESTAMP - INTERVAL '2 years', CURRENT_TIMESTAMP, 0
);

INSERT INTO users (
    first_name, last_name, email, phone, birth_date, address, city, avatar, bio,
    company, position, website, linkedin, github, timezone, currency, target_daily_rate, password_hash,
    last_password_change, theme, language_preference, date_format, time_format,
    default_view, items_per_page, auto_save, email_notifications, push_notifications,
    project_updates, client_messages, system_alerts, weekly_reports, marketing_emails,
    two_factor_enabled, created_at, updated_at, version
) VALUES (
    'John', 'Doe', 'john.doe@example.com', '+33 6 12 34 56 78', '1990-04-17',
    '18 rue du Sentier', 'Paris', NULL,
    'Freelance Java and TypeScript engineer focused on business applications.',
    'Doe Consulting', 'Senior full-stack engineer', 'https://john-doe.example',
    'https://linkedin.com/in/john-doe', 'https://github.com/john-doe',
    'Europe/Paris', 'EUR', 600,
    '$2a$10$XgbOojgg.CTmnSP8gwpOT.aikY7bnfM4cgCrQhgJOh5UAY1lOpC9S',
    CURRENT_TIMESTAMP - INTERVAL '90 days', 'light', 'fr', 'DD/MM/YYYY', '24h',
    'dashboard', 25, true, true, false, true, true, true, true, false, false,
    CURRENT_TIMESTAMP - INTERVAL '2 years', CURRENT_TIMESTAMP, 0
);

INSERT INTO user_skills (user_id, skill) VALUES
    (1, 'Java'),
    (1, 'Spring Boot'),
    (1, 'Angular'),
    (1, 'TypeScript'),
    (1, 'PostgreSQL'),
    (1, 'Docker'),
    (1, 'Kubernetes'),
    (1, 'AWS');

INSERT INTO user_languages (user_id, language) VALUES
    (1, 'French'),
    (1, 'English');

INSERT INTO sources (
    name, type, link, is_listing, popularity_rating, usefulness_rating, notes,
    freelance_id, created_at, updated_at, version
) VALUES
    ('LinkedIn', 'SOCIAL_MEDIA', 'https://linkedin.com/jobs', true, 5, 5, 'Strong network and direct recruiter contacts.', 1, CURRENT_TIMESTAMP - INTERVAL '2 years', CURRENT_TIMESTAMP, 0),
    ('Malt', 'JOB_BOARD', 'https://malt.fr', true, 4, 4, 'Good French freelance marketplace.', 1, CURRENT_TIMESTAMP - INTERVAL '22 months', CURRENT_TIMESTAMP, 0),
    ('Free-Work', 'JOB_BOARD', 'https://free-work.com', true, 4, 4, 'High volume of technology missions.', 1, CURRENT_TIMESTAMP - INTERVAL '20 months', CURRENT_TIMESTAMP, 0),
    ('APEC', 'JOB_BOARD', 'https://apec.fr', true, 3, 3, 'Useful for long-running consulting roles.', 1, CURRENT_TIMESTAMP - INTERVAL '18 months', CURRENT_TIMESTAMP, 0),
    ('Welcome to the Jungle', 'JOB_BOARD', 'https://welcometothejungle.com', true, 4, 3, 'Detailed company profiles.', 1, CURRENT_TIMESTAMP - INTERVAL '16 months', CURRENT_TIMESTAMP, 0),
    ('Comet', 'JOB_BOARD', 'https://comet.co', true, 3, 4, 'Curated assignments.', 1, CURRENT_TIMESTAMP - INTERVAL '14 months', CURRENT_TIMESTAMP, 0),
    ('Recruiter email', 'EMAIL', NULL, false, 4, 4, 'Inbound recruiter conversations.', 1, CURRENT_TIMESTAMP - INTERVAL '12 months', CURRENT_TIMESTAMP, 0),
    ('Professional network', 'CALL', NULL, false, 5, 5, 'Recommendations from former colleagues.', 1, CURRENT_TIMESTAMP - INTERVAL '10 months', CURRENT_TIMESTAMP, 0),
    ('Company career page', 'JOB_BOARD', NULL, true, 3, 4, 'Direct applications without an intermediary.', 1, CURRENT_TIMESTAMP - INTERVAL '8 months', CURRENT_TIMESTAMP, 0),
    ('Community message', 'SMS', NULL, false, 3, 3, 'Local meetup and community referrals.', 1, CURRENT_TIMESTAMP - INTERVAL '6 months', CURRENT_TIMESTAMP, 0);

-- Eighteen end clients followed by six intermediaries/ESNs.
INSERT INTO clients (
    company_name, address, city, domain, is_final, notes, rating, is_blacklisted,
    blacklist_reason, freelance_id, created_at, updated_at, version
)
SELECT
    (ARRAY[
        'Asteria Bank', 'Blue Orbit', 'Cobalt Retail', 'Delta Mobility',
        'Epsilon Health', 'Fjord Energy', 'Greenline Logistics', 'Helios Media',
        'Iris Assurance', 'Juniper Cloud', 'Kite Telecom', 'Lumen Travel',
        'Mosaic Industrie', 'Nova Public', 'Opal Marketplace', 'Pulse Gaming',
        'Quartz Data', 'Rivage Hotels', 'Atlas Consulting', 'Boreal Partners',
        'Capstone Digital', 'Dynamo Conseil', 'Elevate Services', 'Focus Tech'
    ])[n],
    format('%s avenue de l''Innovation', 10 + n),
    (ARRAY['Paris', 'Lyon', 'Bordeaux', 'Nantes', 'Lille', 'Toulouse'])[1 + ((n - 1) % 6)],
    format('https://company-%s.example', n),
    n <= 18,
    CASE WHEN n <= 18
        THEN 'End client tracked for direct and intermediary opportunities.'
        ELSE 'Intermediary used to track the client rate and commercial margin.'
    END,
    1 + ((n + 2) % 5),
    n IN (12, 22),
    CASE WHEN n = 12 THEN 'Repeatedly cancelled interviews at short notice.'
         WHEN n = 22 THEN 'Payment terms and margin were not transparent.'
         ELSE NULL
    END,
    1,
    CURRENT_TIMESTAMP - (n * INTERVAL '18 days'),
    CURRENT_TIMESTAMP - ((n % 40) * INTERVAL '1 day'),
    0
FROM generate_series(1, 24) AS series(n);

-- Two contacts per company, including deliberately old activity for reminder views.
INSERT INTO contacts (
    first_name, last_name, email, phone, notes, client_id, freelance_id,
    created_at, updated_at, version
)
SELECT
    (ARRAY['Camille', 'Alex', 'Sarah', 'Nicolas', 'Emma', 'Hugo', 'Lea', 'Thomas'])[1 + ((n - 1) % 8)],
    (ARRAY['Martin', 'Bernard', 'Dubois', 'Robert', 'Richard', 'Petit', 'Durand', 'Moreau'])[1 + ((n + 2) % 8)],
    format('contact%s@company-%s.example', n, 1 + ((n - 1) / 2)),
    format('+33 6 %s %s %s %s',
        lpad(((n * 3) % 100)::text, 2, '0'),
        lpad(((n * 5) % 100)::text, 2, '0'),
        lpad(((n * 7) % 100)::text, 2, '0'),
        lpad(((n * 11) % 100)::text, 2, '0')),
    CASE WHEN n % 2 = 0
        THEN 'Technical decision maker; prefers concise written follow-ups.'
        ELSE 'Commercial or recruitment contact; available in the morning.'
    END,
    1 + ((n - 1) / 2),
    1,
    CURRENT_TIMESTAMP - (n * INTERVAL '8 days'),
    CURRENT_TIMESTAMP - ((n % 9) * INTERVAL '17 days'),
    0
FROM generate_series(1, 48) AS series(n);

-- Ninety-six opportunities spread evenly across the whole pipeline.
WITH generated AS (
    SELECT
        n,
        (ARRAY[
            'Senior Java Engineer', 'Angular Lead', 'Cloud Architect',
            'Platform Engineer', 'Full-stack Developer', 'Data Platform Engineer',
            'Spring Boot Expert', 'Frontend Architect', 'DevOps Consultant',
            'Technical Lead', 'API Integration Engineer', 'Solution Architect'
        ])[1 + ((n - 1) % 12)] AS role,
        (ARRAY['CONTACT', 'INTERVIEW', 'OFFER', 'WON', 'LOST'])[1 + ((n - 1) % 5)] AS status,
        (ARRAY['REMOTE', 'HYBRID', 'ONSITE'])[1 + ((n - 1) % 3)] AS work_mode,
        500 + ((n * 37) % 351) AS daily_rate
    FROM generate_series(1, 96) AS series(n)
)
INSERT INTO projects (
    role, status, description, tech_stack, daily_rate, client_daily_rate,
    asked_daily_rate, offered_daily_rate, work_mode, remote_days_per_month,
    onsite_days_per_month, advantages, start_date, duration_in_months,
    order_renewal_in_months, days_per_year, link, personal_rating, notes,
    lost_reason, is_favorite, board_position, freelance_id, client_id,
    middleman_id, source_id, created_at, updated_at, version
)
SELECT
    role,
    status,
    format('Sample opportunity %s covering delivery, architecture, mentoring, and stakeholder coordination.', n),
    (ARRAY[
        'Java, Spring Boot, PostgreSQL, Docker',
        'Angular, TypeScript, RxJS, SCSS',
        'AWS, Terraform, Kubernetes, Helm',
        'Java, Kafka, Redis, PostgreSQL',
        'TypeScript, Node.js, Angular, Playwright',
        'Python, Airflow, dbt, BigQuery',
        'Spring Boot, OAuth2, REST, Testcontainers',
        'Angular, Nx, Storybook, Cypress'
    ])[1 + ((n - 1) % 8)],
    daily_rate,
    CASE WHEN n % 3 <> 0 THEN daily_rate + 80 + ((n % 4) * 20) ELSE NULL END,
    daily_rate + 50,
    daily_rate - 25,
    work_mode,
    CASE work_mode WHEN 'REMOTE' THEN 20 WHEN 'HYBRID' THEN 12 ELSE 0 END,
    CASE work_mode WHEN 'REMOTE' THEN 0 WHEN 'HYBRID' THEN 8 ELSE 20 END,
    (ARRAY[
        'Flexible hours and equipment budget.',
        'Modern stack and experienced product team.',
        'Long engagement with a renewal option.',
        'Training budget and occasional travel.'
    ])[1 + ((n - 1) % 4)],
    CURRENT_DATE + ((n % 120) - 45),
    3 + (n % 16),
    CASE WHEN n % 4 = 0 THEN 3 ELSE 6 END,
    180 + (n % 61),
    format('https://jobs.example/opportunities/%s', n),
    1 + ((n + 1) % 5),
    format('Generated scenario %s. Follow up according to the current pipeline stage.', n),
    CASE WHEN status = 'LOST'
        THEN (ARRAY[
            'RATE_TOO_LOW', 'POSITION_FILLED', 'NO_RESPONSE', 'PROFILE_MISMATCH',
            'CLIENT_CANCELED', 'ACCEPTED_OTHER_OFFER', 'OTHER'
        ])[1 + ((n - 1) % 7)]
        ELSE NULL
    END,
    n % 13 = 0,
    ((n - 1) / 6),
    1,
    -- Shift the client with each 12-role cycle so role/client pairs stay unique; opportunity 94
    -- deliberately repeats opportunity 10 (same client and role) to showcase duplicate detection.
    CASE WHEN n = 94 THEN 10 ELSE 1 + ((((n - 1) % 12) - ((n - 1) / 12) + 18) % 18) END,
    CASE WHEN n % 3 <> 0 THEN 19 + ((n - 1) % 6) ELSE NULL END,
    1 + ((n - 1) % 10),
    CURRENT_TIMESTAMP - ((n % 180) * INTERVAL '1 day'),
    CURRENT_TIMESTAMP - ((n % 45) * INTERVAL '1 day'),
    0
FROM generated;

-- Two job-hunting seasons: a closed spring search and the running one. Opportunities join the
-- season covering the day they were created; older ones stay outside any season.
INSERT INTO seasons (
    name, start_date, end_date, objective, target_daily_rate, freelance_id, created_at, updated_at, version
) VALUES
    ('Spring search', CURRENT_DATE - 180, CURRENT_DATE - 91,
     'Land a hybrid Java mission in Paris before the summer.', 620, 1,
     CURRENT_TIMESTAMP - INTERVAL '180 days', CURRENT_TIMESTAMP - INTERVAL '91 days', 0),
    ('Autumn search', CURRENT_DATE - 90, NULL,
     'Sign a remote-friendly lead role at 700 EUR/day or more.', 700, 1,
     CURRENT_TIMESTAMP - INTERVAL '90 days', CURRENT_TIMESTAMP, 0);

UPDATE projects
SET season_id = CASE WHEN created_at >= CURRENT_DATE - 90 THEN 2 ELSE 1 END
WHERE created_at >= CURRENT_DATE - 180;

INSERT INTO project_documents (project_id, document_path)
SELECT id, format('/documents/opportunity-%s-brief.pdf', id)
FROM projects
WHERE id % 3 = 0
UNION ALL
SELECT id, format('/documents/opportunity-%s-rate-card.pdf', id)
FROM projects
WHERE id % 5 = 0;

-- Every opportunity gets a short journal so timeline and search views have useful data.
INSERT INTO project_notes (content, project_id, created_at, updated_at, version)
SELECT
    CASE note_number
        WHEN 1 THEN format('Captured opportunity #%s and reviewed the role requirements.', project.id)
        WHEN 2 THEN 'Discussed availability, daily rate, and preferred working arrangement.'
        ELSE 'Sent a follow-up and recorded the next decision point.'
    END,
    project.id,
    project.created_at + (note_number * INTERVAL '2 days'),
    project.created_at + (note_number * INTERVAL '2 days'),
    0
FROM projects AS project
CROSS JOIN generate_series(1, 3) AS notes(note_number);

-- Two steps per opportunity provide validated history, failures, and upcoming reminders.
INSERT INTO interview_steps (
    title, date, status, notes, project_id, created_at, updated_at, version
)
SELECT
    CASE step_number WHEN 1 THEN 'Initial qualification' ELSE 'Technical and client interview' END,
    CURRENT_TIMESTAMP + ((step_number * 4 + (project.id % 15) - 8) * INTERVAL '1 day'),
    CASE
        WHEN step_number = 1 THEN 'VALIDATED'
        WHEN project.status = 'LOST' THEN 'FAILED'
        WHEN project.status = 'WON' THEN 'VALIDATED'
        WHEN project.status IN ('INTERVIEW', 'OFFER') THEN 'PLANNED'
        ELSE 'TO_PLAN'
    END,
    CASE step_number
        WHEN 1 THEN 'Scope, timing, and budget discussed.'
        ELSE 'Prepared technical examples and questions for the delivery team.'
    END,
    project.id,
    project.created_at + (step_number * INTERVAL '3 days'),
    CURRENT_TIMESTAMP - ((project.id % 20) * INTERVAL '1 day'),
    0
FROM projects AS project
CROSS JOIN generate_series(1, 2) AS steps(step_number);

-- Upcoming steps carry a preparation checklist, half of it ticked.
INSERT INTO interview_step_checklist (step_id, position, label, done)
SELECT step.id, item.position, item.label, item.position < 2
FROM interview_steps AS step
CROSS JOIN (VALUES
    (0, 'Reread the job description and the client''s website'),
    (1, 'Prepare two project examples matching the stack'),
    (2, 'Prepare questions about the team and the delivery process'),
    (3, 'Test the video call link and the microphone')
) AS item(position, label)
WHERE step.status = 'PLANNED';

-- Accepted missions get last month's activity report, approved by their client or ESN, and the
-- current month in progress up to yesterday. Days follow the work mode (hybrid: remote on Mondays
-- and Fridays); the fixed-date public holidays are skipped, the Easter-based ones are not.
INSERT INTO activity_reports (
    project_id, report_year, report_month, status, comment, submitted_at, validator_name, decided_at,
    decision_comment, created_at, updated_at, version
)
SELECT
    project.id,
    EXTRACT(YEAR FROM period.month_start)::int,
    EXTRACT(MONTH FROM period.month_start)::int,
    CASE WHEN period.months_ago = 1 THEN 'VALIDATED' ELSE 'DRAFT' END,
    CASE WHEN period.months_ago = 1 THEN 'Planning respected; on-site days agreed with the team lead.' END,
    CASE WHEN period.months_ago = 1 THEN date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '1 day' END,
    CASE WHEN period.months_ago = 1 THEN 'Claire Martin' END,
    CASE WHEN period.months_ago = 1 THEN date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '2 days' END,
    CASE WHEN period.months_ago = 1 THEN 'Conforme au planning.' END,
    period.month_start,
    CURRENT_TIMESTAMP,
    0
FROM projects AS project
CROSS JOIN (VALUES (1), (0)) AS offsets(months_ago)
CROSS JOIN LATERAL (
    SELECT offsets.months_ago, (date_trunc('month', CURRENT_DATE) - offsets.months_ago * INTERVAL '1 month')::date AS month_start
) AS period
WHERE project.status = 'WON';

INSERT INTO activity_report_days (report_id, day_date, day_type, half_day)
SELECT
    report.id,
    day::date,
    CASE
        WHEN project.work_mode = 'REMOTE' THEN 'REMOTE'
        WHEN project.work_mode = 'HYBRID' AND EXTRACT(ISODOW FROM day) IN (1, 5) THEN 'REMOTE'
        ELSE 'ONSITE'
    END,
    false
FROM activity_reports AS report
JOIN projects AS project ON project.id = report.project_id
CROSS JOIN LATERAL generate_series(
    make_date(report.report_year, report.report_month, 1),
    CASE WHEN report.status = 'DRAFT'
        THEN CURRENT_DATE - 1
        ELSE (make_date(report.report_year, report.report_month, 1) + INTERVAL '1 month' - INTERVAL '1 day')::date
    END,
    INTERVAL '1 day'
) AS days(day)
WHERE EXTRACT(ISODOW FROM day) < 6
  AND to_char(day, 'MM-DD') NOT IN ('01-01', '05-01', '05-08', '07-14', '08-15', '11-01', '11-11', '12-25');

-- Freelance offer feed: fictional missions in the shape the job board readers store, so the feed can be tried
-- without reading the boards (set INDEZY_OFFERS_COLLECTION_ENABLED=false to keep only these). Links open each
-- site's mission list.
INSERT INTO job_offers (
    site, external_id, url, title, company, description, rate_min, rate_max, city, postal_code, department_code,
    region_code, country_code, latitude, longitude, work_mode, remote_days_per_week, duration_months, start_date,
    start_asap, seniority, published_at, date_precision, first_seen_at, last_seen_at, expires_at, search_text,
    created_at, updated_at, version
) VALUES
('FREE_WORK', 'sample-1', 'https://www.free-work.com/fr/tech-it/jobs', 'Développeur Java Spring Boot (H/F)', 'Exemple Conseil',
 E'Contexte\nRefonte du SI de paiement d''une banque de détail.\n\n• Java 21 et Spring Boot\n• Kafka et PostgreSQL', 550, 650,
 'Paris', '75008', '75', '11', 'FR', 48.8722, 2.3125, 'HYBRID', 2, 12, CURRENT_DATE + 14, false, 'SENIOR',
 CURRENT_TIMESTAMP - INTERVAL '2 hours', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP + INTERVAL '60 days', 'developpeur java spring boot h f exemple conseil paris paris java spring boot kafka contexte refonte du si de paiement d une banque de detail',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('FREE_WORK', 'sample-2', 'https://www.free-work.com/fr/tech-it/jobs', 'Architecte cloud AWS', 'Nuage Partenaires',
 E'Migration de 40 applications vers AWS, landing zone et FinOps.', 700, 800,
 'Lyon', '69002', '69', '84', 'FR', 45.7485, 4.8270, 'ONSITE', 0, 6, NULL, true, 'EXPERT',
 CURRENT_TIMESTAMP - INTERVAL '5 hours', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP + INTERVAL '45 days', 'architecte cloud aws nuage partenaires lyon rhone aws terraform migration de 40 applications vers aws landing zone et finops',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('COLLECTIVE', 'sample-3', 'https://www.collective.work/jobs/fr', 'Data engineer Spark / Databricks', 'Données & Cie',
 E'Industrialisation des pipelines de données d''un assureur.', NULL, 600,
 'Courbevoie', '92400', '92', '11', 'FR', 48.8976, 2.2574, 'REMOTE', 5, 9, CURRENT_DATE + 30, false, 'CONFIRMED',
 CURRENT_TIMESTAMP - INTERVAL '1 day', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP + INTERVAL '30 days', 'data engineer spark databricks donnees cie courbevoie hauts de seine spark databricks python industrialisation des pipelines de donnees d un assureur',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('COLLECTIVE', 'sample-4', 'https://www.collective.work/jobs/fr', 'Product Owner paiement mobile', 'Agence Produit',
 E'Roadmap d''une application de paiement grand public.', 600, 700,
 'Nantes', '44000', '44', '52', 'FR', 47.2184, -1.5536, 'HYBRID', 3, 6, CURRENT_DATE + 21, false, 'SENIOR',
 CURRENT_TIMESTAMP - INTERVAL '2 days', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP,
 NULL, 'product owner paiement mobile agence produit nantes loire atlantique scrum jira roadmap d une application de paiement grand public',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('CHERRY_PICK', 'sample-5', 'https://app.cherry-pick.io/ext/missions', 'Développeur .NET C#', 'Cherry Pick',
 E'Back-office e-commerce, .NET 8 et Entity Framework Core.', 500, 550,
 'Paris', '75000', '75', '11', 'FR', 48.8589, 2.3470, 'HYBRID', 3, 12, CURRENT_DATE + 7, false, 'SENIOR',
 CURRENT_TIMESTAMP - INTERVAL '3 days', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP,
 NULL, 'developpeur .net c# cherry pick paris paris c# .net core back office e commerce .net 8 et entity framework core',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('LEHIBOU', 'sample-6', 'https://www.lehibou.com/recherche/annonces', 'Architecte fonctionnel télécom', NULL,
 E'Cadrage de projets transverses, catalogue commercial B2C.', 650, 650,
 'Meudon', '92190', '92', '11', 'FR', 48.8000, 2.2400, 'HYBRID', 2, 6, CURRENT_DATE + 45, false, NULL,
 CURRENT_TIMESTAMP - INTERVAL '6 hours', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '6 hours', CURRENT_TIMESTAMP,
 NULL, 'architecte fonctionnel telecom meudon hauts de seine uml cadrage de projets transverses catalogue commercial b2c',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('LEHIBOU', 'sample-7', 'https://www.lehibou.com/recherche/annonces', 'Consultant SAP FICO S/4HANA', NULL,
 E'Déploiement S/4HANA dans un groupe industriel.', NULL, NULL,
 'Toulouse', '31000', '31', '76', 'FR', 43.6045, 1.4440, 'ONSITE', 0, 18, NULL, true, 'EXPERT',
 CURRENT_TIMESTAMP - INTERVAL '8 days', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP,
 NULL, 'consultant sap fico s 4hana toulouse haute garonne sap fico deploiement s 4hana dans un groupe industriel',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('YEETS', 'sample-8', 'https://www.yeets.fr', 'Tech Lead Java Kafka', NULL,
 NULL, 720, 720,
 'Lille', NULL, '59', '32', 'FR', 50.6292, 3.0573, 'HYBRID', NULL, 12, CURRENT_DATE + 10, false, 'SENIOR',
 CURRENT_TIMESTAMP - INTERVAL '4 days', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP,
 NULL, 'tech lead java kafka lille nord java kafka',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('YEETS', 'sample-9', 'https://www.yeets.fr', 'Administrateur Splunk N2', NULL,
 NULL, 480, 480,
 'Paris', NULL, '75', '11', 'FR', 48.8589, 2.3470, 'REMOTE', 5, 6, NULL, true, 'CONFIRMED',
 CURRENT_TIMESTAMP - INTERVAL '12 days', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP,
 NULL, 'administrateur splunk n2 paris paris splunk linux',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('FREE_WORK', 'sample-10', 'https://www.free-work.com/fr/tech-it/jobs', 'Développeur Angular confirmé', 'Exemple Conseil',
 E'Refonte de l''espace client d''un énergéticien.', 450, 520,
 'Bordeaux', '33000', '33', '75', 'FR', 44.8378, -0.5792, 'HYBRID', 2, 6, CURRENT_DATE + 30, false, 'CONFIRMED',
 CURRENT_TIMESTAMP - INTERVAL '20 days', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP + INTERVAL '40 days', 'developpeur angular confirme exemple conseil bordeaux gironde angular typescript refonte de l espace client d un energeticien',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('COLLECTIVE', 'sample-11', 'https://www.collective.work/job/sample-11', 'Développeur Java Spring Boot', 'Exemple Conseil',
 E'Refonte du SI de paiement d''une banque de détail : Java 21, Spring Boot, Kafka et PostgreSQL.', NULL, 650,
 'Paris', NULL, '75', '11', 'FR', 48.8566, 2.3522, 'HYBRID', 2, 12, CURRENT_DATE + 14, false, 'SENIOR',
 CURRENT_TIMESTAMP - INTERVAL '1 hour', 'EXACT', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP,
 NULL, 'developpeur java spring boot exemple conseil paris refonte du si de paiement d une banque de detail java 21 spring boot kafka et postgresql',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- The publishers' keys, folded as JobOffer.companyKeyOf does (case, accents and punctuation aside).
UPDATE job_offers SET company_key = CASE company
    WHEN 'Exemple Conseil' THEN 'exemple conseil'
    WHEN 'Nuage Partenaires' THEN 'nuage partenaires'
    WHEN 'Agence Produit' THEN 'agence produit'
    WHEN 'Données & Cie' THEN 'donnees cie'
    WHEN 'Cherry Pick' THEN 'cherry pick'
END;

-- The first mission is also on Collective: one card, with an "Also on" link.
UPDATE job_offers SET copy_group = (SELECT MIN(id) FROM job_offers WHERE external_id IN ('sample-1', 'sample-11'))
WHERE external_id IN ('sample-1', 'sample-11');

INSERT INTO job_offer_skills (offer_id, position, skill)
SELECT offer.id, skill.position, skill.name
FROM job_offers AS offer
JOIN (VALUES
    ('sample-1', 0, 'Java'), ('sample-1', 1, 'Spring Boot'), ('sample-1', 2, 'Kafka'),
    ('sample-2', 0, 'AWS'), ('sample-2', 1, 'Terraform'),
    ('sample-3', 0, 'Spark'), ('sample-3', 1, 'Databricks'), ('sample-3', 2, 'Python'),
    ('sample-4', 0, 'Scrum'), ('sample-4', 1, 'Jira'),
    ('sample-5', 0, 'C#'), ('sample-5', 1, '.NET Core'),
    ('sample-6', 0, 'UML'),
    ('sample-7', 0, 'SAP FICO'),
    ('sample-8', 0, 'Java'), ('sample-8', 1, 'Kafka'),
    ('sample-9', 0, 'Splunk'), ('sample-9', 1, 'Linux'),
    ('sample-10', 0, 'Angular'), ('sample-10', 1, 'TypeScript'),
    ('sample-11', 0, 'Java'), ('sample-11', 1, 'Spring Boot')
) AS skill(external_id, position, name) ON skill.external_id = offer.external_id;

-- John Doe saved one mission and hid another.
INSERT INTO job_offer_states (freelance_id, offer_id, saved, hidden, seen_at, created_at, updated_at, version)
SELECT 1, offer.id, offer.external_id = 'sample-1', offer.external_id = 'sample-7', CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM job_offers AS offer
WHERE offer.external_id IN ('sample-1', 'sample-7');

-- A saved search last opened three days ago: the badge on Offers counts what came in since.
INSERT INTO saved_offer_searches (freelance_id, name, filters, last_opened_at, created_at, updated_at, version)
VALUES (1, 'Java à Paris', '{"q":"java","departments":["75"]}', CURRENT_TIMESTAMP - INTERVAL '3 days',
    CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP, 0);

-- The profile's city, already located, so distances work without the geocoding API.
INSERT INTO geo_places (query_key, found, city, department_code, region_code, latitude, longitude, resolved_at)
VALUES ('|paris', true, 'Paris', '75', '11', 48.8589, 2.3470, CURRENT_TIMESTAMP);

ANALYZE;
