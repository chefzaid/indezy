-- PostgreSQL sample data loaded only by the explicit "seed" profile.
-- Run it through `mask db-reset`; normal application startup never executes this file.

TRUNCATE TABLE
    project_documents,
    project_notes,
    interview_steps,
    contacts,
    projects,
    sources,
    clients,
    user_sessions,
    user_security_questions,
    user_skills,
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
    0.15, 0.24, '/cv/john-doe.pdf',
    '$2a$10$XgbOojgg.CTmnSP8gwpOT.aikY7bnfM4cgCrQhgJOh5UAY1lOpC9S',
    CURRENT_TIMESTAMP - INTERVAL '2 years', CURRENT_TIMESTAMP, 0
);

INSERT INTO users (
    first_name, last_name, email, phone, birth_date, address, city, avatar, bio,
    company, position, website, linkedin, github, timezone, currency, password_hash,
    last_password_change, theme, language_preference, date_format, time_format,
    default_view, items_per_page, auto_save, email_notifications, push_notifications,
    project_updates, client_messages, system_alerts, weekly_reports, marketing_emails,
    two_factor_enabled, created_at, updated_at, version
) VALUES (
    'John', 'Doe', 'john.doe@example.com', '+33 6 12 34 56 78', '1990-04-17',
    '18 rue du Sentier', 'Paris', 'avatar.jpg',
    'Freelance Java and TypeScript engineer focused on business applications.',
    'Doe Consulting', 'Senior full-stack engineer', 'https://john-doe.example',
    'https://linkedin.com/in/john-doe', 'https://github.com/john-doe',
    'Europe/Paris', 'EUR',
    '$2a$10$XgbOojgg.CTmnSP8gwpOT.aikY7bnfM4cgCrQhgJOh5UAY1lOpC9S',
    CURRENT_TIMESTAMP - INTERVAL '90 days', 'dark', 'fr', 'dd/MM/yyyy', '24h',
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

INSERT INTO user_sessions (
    session_id, device, browser, location, ip_address, last_active, is_current,
    user_id, created_at, updated_at, version
) VALUES (
    'sample-session', 'MacBook Pro', 'Chrome', 'Paris, France', '127.0.0.1',
    CURRENT_TIMESTAMP, true, 1, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP, 0
);

INSERT INTO user_security_questions (
    question, answer_hash, user_id, created_at, updated_at, version
) VALUES (
    'What was the name of your first project?',
    '$2a$10$T5QYJvK7.oMHSH7P1JXh3.9iNQTSzMuB2lGiEw1yWg6V3C5P5K1nS',
    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

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
        (ARRAY['IDENTIFIED', 'APPLIED', 'INTERVIEW', 'OFFER', 'WON', 'LOST'])[1 + ((n - 1) % 6)] AS status,
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
    1 + ((n - 1) % 18),
    CASE WHEN n % 3 <> 0 THEN 19 + ((n - 1) % 6) ELSE NULL END,
    1 + ((n - 1) % 10),
    CURRENT_TIMESTAMP - ((n % 180) * INTERVAL '1 day'),
    CURRENT_TIMESTAMP - ((n % 45) * INTERVAL '1 day'),
    0
FROM generated;

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

ANALYZE;
