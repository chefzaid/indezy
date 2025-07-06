-- Development Sample Data for Indezy Application
-- This script populates the database with realistic sample data for development and testing
-- Only executed in dev and devcontainer profiles

-- Clear existing data (in reverse dependency order)
DELETE FROM project_documents;
DELETE FROM interview_steps;
DELETE FROM contacts;
DELETE FROM projects;
DELETE FROM sources;
DELETE FROM clients;
DELETE FROM freelances;

-- Reset sequences
ALTER SEQUENCE freelances_id_seq RESTART WITH 1;
ALTER SEQUENCE clients_id_seq RESTART WITH 1;
ALTER SEQUENCE contacts_id_seq RESTART WITH 1;
ALTER SEQUENCE projects_id_seq RESTART WITH 1;
ALTER SEQUENCE sources_id_seq RESTART WITH 1;
ALTER SEQUENCE interview_steps_id_seq RESTART WITH 1;

-- Insert Freelances
INSERT INTO freelances (id, first_name, last_name, email, phone, birth_date, address, city, status, notice_period_in_days, availability_date, reversion_rate, cv_file_path, password_hash, created_at, updated_at, version) VALUES
(1, 'Jean', 'Dupont', 'jean.dupont@email.com', '+33 6 12 34 56 78', '1985-03-15', '123 Rue de la Paix', 'Paris', 'FREELANCE', 30, '2024-01-01', 0.15, '/cv/jean-dupont.pdf', '$2a$10$encrypted_password_hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Marie', 'Martin', 'marie.martin@email.com', '+33 6 98 76 54 32', '1990-07-22', '456 Avenue des Champs', 'Lyon', 'FREELANCE', 15, '2024-02-01', 0.12, '/cv/marie-martin.pdf', '$2a$10$encrypted_password_hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'Pierre', 'Durand', 'pierre.durand@email.com', '+33 6 11 22 33 44', '1988-11-08', '789 Boulevard Saint-Germain', 'Marseille', 'PORTAGE', 45, '2024-03-01', 0.18, '/cv/pierre-durand.pdf', '$2a$10$encrypted_password_hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert Sources
INSERT INTO sources (id, name, type, link, is_listing, popularity_rating, usefulness_rating, notes, freelance_id, created_at, updated_at, version) VALUES
(1, 'LinkedIn', 'SOCIAL_MEDIA', 'https://linkedin.com', true, 5, 4, 'Excellent pour le networking professionnel', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Indeed', 'JOB_BOARD', 'https://indeed.fr', true, 4, 3, 'Beaucoup d''offres mais qualité variable', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'Freelance.com', 'JOB_BOARD', 'https://freelance.com', true, 3, 4, 'Spécialisé freelance, bonnes missions', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(4, 'Recommandation directe', 'EMAIL', NULL, false, 5, 5, 'Contact direct par email suite à recommandation', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(5, 'Malt', 'JOB_BOARD', 'https://malt.fr', true, 4, 4, 'Plateforme française spécialisée freelance', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(6, 'Appel téléphonique', 'CALL', NULL, false, 3, 3, 'Contact téléphonique direct', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert Clients
INSERT INTO clients (id, company_name, address, city, domain, is_final, notes, freelance_id, created_at, updated_at, version) VALUES
(1, 'TechCorp Solutions', '123 Avenue des Champs-Élysées, 75008 Paris', 'Paris', 'https://techcorp.fr', true, 'Client principal, projets récurrents. Paiement toujours à temps.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'StartupInnovate', '45 Rue de Rivoli, 75001 Paris', 'Paris', 'https://startupinnovate.com', false, 'Startup prometteuse, budget limité mais projets intéressants.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'E-Commerce Plus', '78 Boulevard Saint-Germain, 75006 Paris', 'Paris', NULL, false, 'Nouveau client, première mission en cours.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(4, 'ConsultingPro', '12 Place Vendôme, 75001 Paris', 'Paris', 'https://consultingpro.fr', true, 'Client exigeant mais bien payeur. Projets de longue durée.', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(5, 'DigitalAgency', '34 Rue du Faubourg Saint-Antoine, 75012 Paris', 'Paris', 'https://digitalagency.com', true, 'Agence digitale, missions variées et créatives.', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(6, 'FinTech Solutions', '56 Avenue de la République, 69003 Lyon', 'Lyon', 'https://fintech-solutions.fr', false, 'Secteur financier, réglementations strictes.', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert Contacts
INSERT INTO contacts (id, first_name, last_name, email, phone, notes, client_id, freelance_id, created_at, updated_at, version) VALUES
(1, 'Marie', 'Dubois', 'marie.dubois@techcorp.fr', '+33 1 23 45 67 89', 'Directrice RH, contact principal pour les recrutements développeurs', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Pierre', 'Martin', 'p.martin@innovate.com', '+33 1 98 76 54 32', 'CTO, décideur technique. Préfère les entretiens le matin.', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'Sophie', 'Leroy', 's.leroy@ecommerce-plus.com', '+33 1 11 22 33 44', 'Chef de projet, très réactive par email', 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(4, 'Laurent', 'Moreau', 'l.moreau@consultingpro.fr', '+33 1 55 66 77 88', 'Directeur technique, expert en architecture logicielle', 4, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(5, 'Isabelle', 'Roux', 'i.roux@consultingpro.fr', '+33 1 44 55 66 77', 'Responsable RH, gère les contrats et négociations', 4, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(6, 'Thomas', 'Blanc', 't.blanc@digitalagency.com', '+33 1 77 88 99 00', 'Creative Director, focus sur l''UX/UI', 5, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(7, 'Nathalie', 'Girard', 'n.girard@fintech-solutions.fr', '+33 4 12 34 56 78', 'Lead Developer, experte en sécurité financière', 6, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert Projects
INSERT INTO projects (id, role, description, tech_stack, daily_rate, work_mode, remote_days_per_month, onsite_days_per_month, advantages, start_date, duration_in_months, order_renewal_in_months, days_per_year, link, personal_rating, notes, freelance_id, client_id, middleman_id, source_id, created_at, updated_at, version) VALUES
(1, 'Développeur Full Stack Senior', 'Développement d''une application de gestion de commandes pour e-commerce', 'Angular 18, Spring Boot 3, PostgreSQL, Docker', 650, 'HYBRID', 15, 7, 'Équipe jeune et dynamique, technologies modernes, formation continue', '2024-01-15', 6, 3, 220, 'https://techcorp.fr/careers', 5, 'Excellent projet, équipe très compétente. Client très satisfait.', 1, 1, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Architecte Logiciel', 'Refonte de l''architecture microservices existante', 'Java 21, Spring Cloud, Kubernetes, Redis', 750, 'REMOTE', 22, 0, 'Full remote, horaires flexibles, projets innovants', '2024-03-01', 12, 6, 240, NULL, 4, 'Projet complexe mais très enrichissant techniquement.', 1, 2, NULL, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'Lead Developer Frontend', 'Création d''une PWA pour la gestion de stock', 'React 18, TypeScript, PWA, Material-UI', 600, 'ONSITE', 0, 22, 'Locaux modernes, équipe expérimentée, défis techniques', '2024-02-01', 4, 2, 180, 'https://ecommerce-plus.com', 3, 'Projet intéressant mais délais serrés.', 1, 3, NULL, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(4, 'Consultant Technique Senior', 'Audit et optimisation des performances', 'Java, Spring Boot, PostgreSQL, Monitoring', 800, 'HYBRID', 10, 12, 'Missions variées, expertise reconnue, bon réseau', '2024-01-01', 18, 12, 200, NULL, 5, 'Mission de conseil très enrichissante. Client fidèle.', 2, 4, NULL, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(5, 'Développeur Creative Tech', 'Développement d''expériences interactives', 'Vue.js 3, Three.js, WebGL, Node.js', 550, 'HYBRID', 12, 10, 'Projets créatifs, liberté technique, environnement stimulant', '2024-04-01', 8, 4, 190, 'https://digitalagency.com/portfolio', 4, 'Projets très créatifs, équipe talentueuse.', 2, 5, NULL, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(6, 'Expert Sécurité FinTech', 'Implémentation de solutions de sécurité bancaire', 'Java 21, Spring Security, OAuth2, Blockchain', 900, 'ONSITE', 0, 22, 'Secteur financier, sécurité maximale, expertise pointue', '2024-05-01', 10, 6, 210, NULL, 4, 'Projet très technique, réglementations strictes mais passionnant.', 3, 6, NULL, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert Project Documents
INSERT INTO project_documents (project_id, document_path) VALUES
(1, '/documents/techcorp-contract-2024.pdf'),
(1, '/documents/techcorp-specifications.pdf'),
(2, '/documents/startup-innovate-nda.pdf'),
(2, '/documents/architecture-proposal.pdf'),
(3, '/documents/ecommerce-wireframes.pdf'),
(4, '/documents/consulting-audit-report.pdf'),
(4, '/documents/performance-optimization-plan.pdf'),
(5, '/documents/creative-brief.pdf'),
(6, '/documents/security-compliance-checklist.pdf'),
(6, '/documents/fintech-technical-specs.pdf');

-- Insert Interview Steps
INSERT INTO interview_steps (id, title, date, status, notes, project_id, created_at, updated_at, version) VALUES
-- Project 1 (TechCorp) - Completed successfully
(1, 'Entretien RH initial', '2024-01-08 10:00:00', 'VALIDATED', 'Excellent feeling avec Marie Dubois. Présentation du poste et de l''équipe.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Test technique', '2024-01-10 14:00:00', 'VALIDATED', 'Test réussi avec 95%. Exercice sur Angular et Spring Boot.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'Entretien technique avec l''équipe', '2024-01-12 15:30:00', 'VALIDATED', 'Très bon échange technique. Équipe motivée et compétente.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(4, 'Négociation finale', '2024-01-14 11:00:00', 'VALIDATED', 'Accord sur le TJM de 650€. Début le 15 janvier.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Project 2 (StartupInnovate) - Completed successfully
(5, 'Appel de pré-qualification', '2024-02-20 16:00:00', 'VALIDATED', 'Discussion avec Pierre Martin (CTO). Projet d''architecture microservices.', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(6, 'Présentation de l''architecture proposée', '2024-02-25 10:00:00', 'VALIDATED', 'Présentation de 2h sur l''architecture cible. Très bien reçue.', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(7, 'Entretien avec les développeurs', '2024-02-27 14:00:00', 'VALIDATED', 'Rencontre avec l''équipe dev. Bonne dynamique d''équipe.', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Project 3 (E-Commerce Plus) - Completed with some challenges
(8, 'Premier contact', '2024-01-25 09:00:00', 'VALIDATED', 'Appel avec Sophie Leroy. Projet PWA urgent.', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(9, 'Démonstration technique', '2024-01-28 11:00:00', 'VALIDATED', 'Demo d''une PWA similaire. Client convaincu.', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(10, 'Négociation délais et budget', '2024-01-30 16:00:00', 'VALIDATED', 'Délais serrés mais budget correct. Accepté à 600€/jour.', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Project 4 (ConsultingPro) - In progress
(11, 'Entretien de conseil', '2023-12-15 14:00:00', 'VALIDATED', 'Audit des performances avec Laurent Moreau. Expertise reconnue.', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(12, 'Présentation du plan d''action', '2023-12-20 10:00:00', 'VALIDATED', 'Plan d''optimisation approuvé. Mission longue durée.', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(13, 'Négociation contrat', '2023-12-22 15:00:00', 'VALIDATED', 'Contrat 18 mois signé. TJM 800€.', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Project 5 (DigitalAgency) - Planned
(14, 'Rencontre créative', '2024-03-25 11:00:00', 'VALIDATED', 'Excellent échange avec Thomas Blanc. Projets très créatifs.', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(15, 'Test créatif', '2024-03-28 14:00:00', 'PLANNED', 'Création d''un prototype interactif demandé.', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Project 6 (FinTech) - In progress
(16, 'Audit sécurité initial', '2024-04-20 09:00:00', 'VALIDATED', 'Évaluation des besoins sécurité avec Nathalie Girard.', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(17, 'Présentation solution technique', '2024-04-25 15:00:00', 'WAITING_FEEDBACK', 'Solution blockchain présentée. Attente validation comité.', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(18, 'Entretien final', '2024-05-02 10:00:00', 'PLANNED', 'Entretien final avec la direction technique.', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
