package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.PublicHoliday;

import java.util.Locale;

/** French and English text of the activity report PDF. */
enum ActivityReportLabel {
    DEFAULT_TITLE("Compte rendu d'activité", "Activity report"),
    CONSULTANT("Consultant", "Consultant"),
    CLIENT("Client", "Client"),
    INTERMEDIARY("ESN / intermédiaire", "Agency / intermediary"),
    MISSION("Mission", "Assignment"),
    PERIOD("Période", "Period"),
    PERIOD_RANGE("du %s au %s", "from %s to %s"),
    DATE("Date", "Date"),
    DAY("Jour", "Day"),
    WORKED("Travaillé", "Worked"),
    LOCATION("Lieu", "Location"),
    REMARK("Observation", "Remark"),
    ONSITE("Sur site", "On site"),
    REMOTE("Télétravail", "Remote"),
    ABSENCE("Absence", "Absence"),
    HALF_DAY_ABSENCE("Absence (demi-journée)", "Absence (half day)"),
    HOLIDAY("Jour férié : %s", "Public holiday: %s"),
    GRID_DAY("Jour", "Day"),
    GRID_ABSENCE("Absence", "Absence"),
    GRID_ONSITE_CODE("S", "O"),
    GRID_REMOTE_CODE("T", "R"),
    GRID_HOLIDAY_CODE("F", "H"),
    GRID_LEGEND("%s : sur site · %s : télétravail · %s : jour férié · 0,5 : demi-journée",
        "%s: on site · %s: remote · %s: public holiday · 0.5: half day"),
    HOLIDAYS_OF_MONTH("Jours fériés : %s", "Public holidays: %s"),
    WORKED_TOTAL("Jours travaillés : %s", "Days worked: %s"),
    WORKED_SPLIT("dont %s sur site et %s en télétravail", "of which %s on site and %s remote"),
    ABSENCE_TOTAL("Absences : %s", "Absences: %s"),
    BUSINESS_DAYS("Jours ouvrés du mois : %s", "Business days in the month: %s"),
    COMMENT("Commentaire du consultant", "Consultant's comment"),
    CONSULTANT_SIGNATURE("Le consultant", "Consultant"),
    CLIENT_SIGNATURE("Validation du client", "Client approval"),
    SIGNATURE_HINT("Nom, date et signature", "Name, date and signature"),
    VALIDATED_BY("Validé électroniquement par %s", "Approved electronically by %s"),
    REJECTED_BY("Refusé par %s", "Rejected by %s"),
    DECIDED_AT("le %s à %s (heure de Paris)", "on %s at %s (Paris time)"),
    REASON("Motif : %s", "Reason: %s"),
    VALIDATOR_COMMENT("Commentaire : %s", "Comment: %s"),
    STATUS_DRAFT("Brouillon", "Draft"),
    STATUS_SUBMITTED("En attente de validation", "Awaiting approval"),
    STATUS_VALIDATED("Validé", "Approved"),
    STATUS_REJECTED("Refusé", "Rejected");

    private final String french;
    private final String english;

    ActivityReportLabel(String french, String english) {
        this.french = french;
        this.english = english;
    }

    String in(DocumentLanguage language) {
        return language == DocumentLanguage.EN ? english : french;
    }

    String format(DocumentLanguage language, Object... values) {
        return String.format(locale(language), in(language), values);
    }

    static Locale locale(DocumentLanguage language) {
        return language == DocumentLanguage.EN ? Locale.UK : Locale.FRANCE;
    }

    static ActivityReportLabel of(ActivityReportStatus status) {
        return switch (status) {
            case DRAFT -> STATUS_DRAFT;
            case SUBMITTED -> STATUS_SUBMITTED;
            case VALIDATED -> STATUS_VALIDATED;
            case REJECTED -> STATUS_REJECTED;
        };
    }

    static String holidayName(PublicHoliday holiday, DocumentLanguage language) {
        boolean english = language == DocumentLanguage.EN;
        return switch (holiday) {
            case NEW_YEAR -> english ? "New Year's Day" : "Jour de l'an";
            case EASTER_MONDAY -> english ? "Easter Monday" : "Lundi de Pâques";
            case LABOUR_DAY -> english ? "Labour Day" : "Fête du Travail";
            case VICTORY_DAY -> english ? "Victory in Europe Day" : "Victoire 1945";
            case ASCENSION -> english ? "Ascension Day" : "Ascension";
            case WHIT_MONDAY -> english ? "Whit Monday" : "Lundi de Pentecôte";
            case BASTILLE_DAY -> english ? "Bastille Day" : "Fête nationale";
            case ASSUMPTION -> english ? "Assumption Day" : "Assomption";
            case ALL_SAINTS -> english ? "All Saints' Day" : "Toussaint";
            case ARMISTICE -> english ? "Armistice Day" : "Armistice 1918";
            case CHRISTMAS -> english ? "Christmas Day" : "Noël";
        };
    }
}
