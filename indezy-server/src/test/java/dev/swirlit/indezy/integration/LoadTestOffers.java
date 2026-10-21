package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.ScrapedOffer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Offers written the way the job boards write them, for the search load test: the same seed always gives the same
 * offers. Titles name a role and a skill, descriptions run from one to three thousand characters, two thirds state a
 * rate, and about one mission in eight is published again on another site, so the copy groups are realistic too.
 */
final class LoadTestOffers {

    /** One offer as a site published it. */
    record Collected(OfferSite site, ScrapedOffer offer) {
    }

    /** A city the offers are in, with its coordinates. */
    private record City(String name, String postalCode, double latitude, double longitude) {
    }

    /** Where every load-test offer's identifier starts. */
    static final String EXTERNAL_ID_PREFIX = "load-";

    static final List<String> SKILLS = List.of("Java", "Spring Boot", "Kotlin", "Angular", "React", "TypeScript", "Node.js",
        "Python", "Django", "Go", "Rust", "C#", ".NET", "PHP", "Symfony", "Laravel", "Kubernetes", "Docker", "Terraform",
        "Ansible", "AWS", "Azure", "GCP", "Kafka", "Spark", "Airflow", "dbt", "Snowflake", "PostgreSQL", "Oracle", "MongoDB",
        "Elasticsearch", "Jenkins", "GitLab CI", "SAP", "Salesforce", "ServiceNow", "Power BI", "Tableau", "COBOL", "Scala",
        "Swift", "Flutter", "Linux", "Selenium", "Cypress", "Figma", "Jira", "Scrum", "Vue.js");
    private static final List<String> ROLES = List.of("Développeur %s", "Développeur %s senior", "Lead développeur %s",
        "Architecte %s", "Ingénieur DevOps %s", "Data engineer %s", "Consultant %s", "Expert %s", "Tech lead %s",
        "Ingénieur QA %s", "Développeur full stack %s", "Ingénieur cloud %s");
    private static final List<String> OTHER_ROLES = List.of("Chef de projet MOA", "Product owner", "Scrum master",
        "Business analyst", "Directeur de programme", "PMO", "Responsable sécurité SI", "Coach agile", "Administrateur systèmes",
        "Ingénieur réseau");
    private static final List<City> CITIES = List.of(new City("Paris", "75008", 48.8722, 2.3125),
        new City("Paris", "75013", 48.8322, 2.3561), new City("Puteaux", "92800", 48.8846, 2.2389),
        new City("Issy-les-Moulineaux", "92130", 48.8245, 2.2743), new City("Saint-Denis", "93200", 48.9362, 2.3574),
        new City("Massy", "91300", 48.7309, 2.2713), new City("Versailles", "78000", 48.8049, 2.1204),
        new City("Lyon", "69002", 45.7485, 4.8274), new City("Marseille", "13001", 43.2999, 5.3841),
        new City("Toulouse", "31000", 43.6045, 1.4440), new City("Nantes", "44000", 47.2184, -1.5536),
        new City("Lille", "59000", 50.6292, 3.0573), new City("Bordeaux", "33000", 44.8378, -0.5792),
        new City("Strasbourg", "67000", 48.5734, 7.7521), new City("Rennes", "35000", 48.1173, -1.6778),
        new City("Montpellier", "34000", 43.6108, 3.8767), new City("Nice", "06000", 43.7102, 7.2620),
        new City("Grenoble", "38000", 45.1885, 5.7245), new City("Valbonne", "06560", 43.6410, 7.0089),
        new City("Niort", "79000", 46.3237, -0.4588), new City("Aix-en-Provence", "13100", 43.5297, 5.4474),
        new City("Rouen", "76000", 49.4432, 1.0999), new City("Tours", "37000", 47.3941, 0.6848),
        new City("Dijon", "21000", 47.3220, 5.0415), new City("Clermont-Ferrand", "63000", 45.7772, 3.0870),
        new City("Brest", "29200", 48.3904, -4.4861), new City("Orléans", "45000", 47.9030, 1.9093),
        new City("Caen", "14000", 49.1829, -0.3707), new City("Metz", "57000", 49.1193, 6.1757),
        new City("Angers", "49000", 47.4784, -0.5632));
    private static final List<String> SECTORS = List.of("bancaire", "de l'assurance", "de l'énergie", "du luxe",
        "de la grande distribution", "des télécoms", "public", "de la santé", "de l'automobile", "du transport", "aéronautique",
        "des médias", "industriel", "du e-commerce");
    private static final List<String> PROJECTS = List.of("la refonte de son système d'information",
        "la migration de ses applications vers le cloud", "la modernisation de son socle applicatif",
        "la création d'une plateforme de données", "l'industrialisation de ses déploiements",
        "le lancement d'une nouvelle offre digitale", "la sécurisation de ses environnements de production",
        "le remplacement de son progiciel de gestion", "la mise en place d'une architecture orientée événements",
        "l'amélioration de la performance de ses services en ligne");
    private static final List<String> SENTENCES = List.of(
        "Dans le cadre de %3$s, notre client du secteur %2$s recherche un consultant %1$s.",
        "Vous rejoindrez une équipe de huit personnes en charge de %3$s.",
        "Vous participerez à la conception et au développement des nouvelles fonctionnalités en %1$s.",
        "Une bonne maîtrise de %1$s et de %4$s est indispensable.",
        "La connaissance du secteur %2$s serait un plus apprécié.",
        "Vous serez garant de la qualité du code, des revues et de la couverture des tests.",
        "La mission démarre dès que possible pour une durée de %5$d mois renouvelables.",
        "Le poste est basé à %6$s avec des déplacements ponctuels sur les autres sites du groupe.",
        "Vous travaillerez en méthode agile, en lien direct avec le product owner et les équipes métier.",
        "Vous accompagnerez les développeurs juniors et partagerez les bonnes pratiques autour de %1$s.",
        "L'environnement technique comprend %1$s, %4$s et %7$s.",
        "Vous assurerez le maintien en conditions opérationnelles des applications existantes.",
        "Vous rédigerez la documentation technique et animerez des ateliers avec les utilisateurs.",
        "Une expérience significative sur des projets similaires d'au moins cinq ans est attendue.",
        "Vous contribuerez à l'automatisation des chaînes d'intégration et de déploiement continus.",
        "Le client attache une grande importance à la sécurité et à la performance des applications.",
        "Vous interviendrez sur l'analyse des besoins, le chiffrage et la planification des évolutions.",
        "Un bon niveau d'anglais est nécessaire pour échanger avec les équipes internationales.",
        "Vous serez force de proposition sur les choix d'architecture et les outils de supervision.",
        "La rémunération dépend de votre expérience et de votre niveau d'expertise sur %1$s.",
        "Le télétravail est possible selon l'organisation de l'équipe, après une période d'intégration.",
        "Vous participerez aux rituels agiles : daily, sprint planning, démonstrations et rétrospectives.",
        "Vous travaillerez sur des volumes importants, plusieurs millions d'opérations par jour.",
        "Les tests automatisés reposent sur %7$s et sont exécutés à chaque modification.");
    /** What sites add to a title: rarely the same twice for one publisher. */
    private static final List<String> TITLE_ENDS = List.of("", "", " H/F", " (H/F)", " - Banque", " - Assurance",
        " - Énergie", " - Retail", " - Télécoms", " - Secteur public", " confirmé", " senior", " - Plateforme data",
        " - Migration cloud", " - Refonte SI", " - Paiements", " - Mobile", " - Back-end", " - Front-end", " - API",
        " - Run et build", " - Temps partiel", " - Freelance", " - Mission longue");
    private static final List<String> OPENINGS = List.of("Notre client du secteur %s renforce ses équipes à %s.",
        "Pour un acteur majeur du secteur %s basé à %s, nous recherchons un profil expérimenté.",
        "Mission en régie chez un client du secteur %s, à %s.",
        "Un grand compte du secteur %s de la région de %s lance un nouveau projet.",
        "Nous accompagnons une entreprise du secteur %s à %s dans la transformation de son SI.");
    private static final List<String> PUBLISHER_STARTS = List.of("Nexa", "Orbi", "Valt", "Kéo", "Synap", "Arca", "Lumi",
        "Terra", "Vertu", "Hexa", "Novi", "Quar", "Cyré", "Iko", "Opti", "Zéphy", "Mira", "Solu", "Agil", "Bleu");
    private static final List<String> PUBLISHER_ENDS = List.of(" Conseil", " Digital", " Consulting", " Group", " Partners",
        " IT", " Tech", " Solutions", " Ingénierie", " Services");
    /** How far back offers are published: the feed keeps 90 days. */
    private static final int PUBLISHED_WITHIN_DAYS = 85;

    private LoadTestOffers() {
    }

    /** {@code count} offers published before {@code now}, the same ones for the same seed. */
    static List<Collected> generate(int count, long seed, Instant now) {
        Random random = new Random(seed);
        List<Collected> offers = new ArrayList<>(count);
        while (offers.size() < count) {
            Collected offer = offer(offers.size(), random, now);
            offers.add(offer);
            // About one mission in eight is published again, hours later, by a site of its own.
            if (offers.size() < count && random.nextInt(8) == 0) {
                offers.add(copy(offers.size(), offer, random));
            }
        }
        return offers;
    }

    private static Collected offer(int index, Random random, Instant now) {
        OfferSite site = pick(random, List.of(OfferSite.values()));
        List<String> skills = skills(random);
        String role = random.nextInt(6) == 0 ? pick(random, OTHER_ROLES) : String.format(pick(random, ROLES), skills.get(0));
        String title = role + pick(random, TITLE_ENDS);
        City city = pick(random, CITIES);
        ScrapedOffer.ScrapedOfferBuilder builder = ScrapedOffer.builder()
            .externalId(EXTERNAL_ID_PREFIX + index)
            .url(site.getHomeUrl() + "/mission/" + EXTERNAL_ID_PREFIX + index)
            .title(title)
            .company(random.nextInt(10) == 0 ? null : pick(random, PUBLISHER_STARTS) + pick(random, PUBLISHER_ENDS))
            .description(description(index, random, skills, city))
            .city(city.name())
            .postalCode(city.postalCode())
            .latitude(city.latitude())
            .longitude(city.longitude())
            .skills(skills)
            .publishedAt(now.minus(Duration.ofMinutes(random.nextInt(PUBLISHED_WITHIN_DAYS * 24 * 60))));
        rate(builder, random);
        work(builder, random);
        if (random.nextInt(10) < 7) {
            builder.durationMonths(3 + random.nextInt(22));
        }
        if (random.nextInt(10) < 7) {
            builder.seniority(pick(random, List.of(Seniority.values())));
        }
        if (random.nextInt(4) == 0) {
            builder.expiresAt(now.plus(Duration.ofDays(1 + random.nextInt(40))));
        }
        return new Collected(site, builder.build());
    }

    /** The mission published again by another site: same title, publisher and text, its own link. */
    private static Collected copy(int index, Collected original, Random random) {
        OfferSite site;
        do {
            site = pick(random, List.of(OfferSite.values()));
        } while (site == original.site());
        ScrapedOffer offer = original.offer().toBuilder()
            .externalId(EXTERNAL_ID_PREFIX + index)
            .url(site.getHomeUrl() + "/mission/" + EXTERNAL_ID_PREFIX + index)
            .publishedAt(original.offer().getPublishedAt().plus(Duration.ofHours(1 + random.nextInt(48))))
            .build();
        return new Collected(site, offer);
    }

    private static List<String> skills(Random random) {
        Set<String> skills = new LinkedHashSet<>();
        int wanted = 3 + random.nextInt(6);
        while (skills.size() < wanted) {
            // The first skills of the list are the most asked for, as on the job boards.
            int index = (int) Math.min(SKILLS.size() - 1, Math.abs(random.nextGaussian()) * SKILLS.size() / 2.5);
            skills.add(SKILLS.get(index));
        }
        return new ArrayList<>(skills);
    }

    /** Its opening names the client and a reference, as offers do: two missions rarely open alike. */
    private static String description(int index, Random random, List<String> skills, City city) {
        StringBuilder text = new StringBuilder("Réf. ").append(index).append(". ")
            .append(String.format(pick(random, OPENINGS), pick(random, SECTORS), city.name()));
        int sentences = 10 + random.nextInt(18);
        for (int i = 0; i < sentences; i++) {
            text.append(i % 4 == 3 ? "\n\n" : " ");
            text.append(String.format(pick(random, SENTENCES), skills.get(0), pick(random, SECTORS), pick(random, PROJECTS),
                skills.get(1), 3 + random.nextInt(22), city.name(), skills.get(skills.size() - 1)));
        }
        return text.toString();
    }

    private static void rate(ScrapedOffer.ScrapedOfferBuilder builder, Random random) {
        if (random.nextInt(3) == 0) {
            return;
        }
        int rate = 350 + 10 * random.nextInt(60);
        if (random.nextBoolean()) {
            builder.rateMin(rate).rateMax(rate + 50 + 10 * random.nextInt(15));
        } else {
            builder.rateMax(rate);
        }
    }

    private static void work(ScrapedOffer.ScrapedOfferBuilder builder, Random random) {
        int mode = random.nextInt(20);
        if (mode < 6) {
            builder.workMode(WorkMode.REMOTE);
        } else if (mode < 15) {
            builder.workMode(WorkMode.HYBRID).remoteDaysPerWeek(1 + random.nextInt(4));
        } else if (mode < 19) {
            builder.workMode(WorkMode.ONSITE);
        }
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
