package dev.swirlit.indezy.service.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Deletes everything an account holds once its 30 days on hold are over (see docs/security.md#data-privacy): its
 * workspace (opportunities, clients, contacts, documents, invoices, quotes, activity reports, settings), its network
 * presence (card, posts with everything under them, comments, reactions, follows, notifications, files) and the account
 * itself. The messages it sent stay with the members it wrote to, from a former member. Each table is named in
 * {@link #PURGED} or {@link #SHARED}, which a test checks against the schema, so a new table is never forgotten.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountPurge {

    /** The tables that hold an account's data, deleted in this order (children before what they point to). */
    static final List<String> PURGED = List.of(
        "network_post_tags", "network_post_versions", "network_post_impressions", "network_post_dismissals", "network_likes", "network_comment_likes",
        "network_mission_takes", "network_poll_votes", "network_bookmarks", "network_notifications", "network_comments",
        "network_file_contents", "network_files", "network_held", "network_reviews", "push_subscriptions", "ai_usage", "network_posts", "network_follows",
        "network_follow_requests", "network_restrictions", "network_tag_follows", "network_drafts", "network_reports",
        "network_message_reactions", "network_conversation_members", "support_attachments", "support_requests", "network_page_views", "network_community_members", "network_idea_votes",
        "network_meetup_attendees", "network_meetups", "network_invitations", "network_members",
        "webhook_events", "webhook_deliveries", "webhooks", "activity_report_days", "interview_step_checklist",
        "interview_steps", "scheduled_contact_emails", "prospection_campaigns", "contact_interactions", "contact_relations", "project_attachments", "project_notes", "project_chat_messages",
        "purchase_orders", "project_referrals", "project_contract_checks", "project_documents", "job_offer_states", "offer_ai_verdicts", "autopilot_drafts", "invoice_lines", "invoices",
        "invoice_counters", "activity_reports", "quote_lines", "quotes", "quote_counters", "expenses", "testimonials", "projects",
        "pipeline_template_steps", "pipeline_templates", "contacts", "clients", "sources", "seasons", "email_templates",
        "offer_board_visits", "offer_publisher_blocks", "offer_site_exclusions", "offer_suggestion_dismissals",
        "saved_offer_searches", "trainings", "profile_experiences", "profile_educations", "profile_cvs", "booking_windows", "bookings", "booking_pages", "compliance_pack_documents",
        "compliance_packs", "compliance_documents", "document_logos", "document_terms_files", "document_font_files", "document_templates", "document_letterheads",
        "freelances", "api_token_scopes", "api_tokens", "account_sessions",
        "account_events", "user_skills", "user_skill_details", "user_languages", "users");

    /**
     * Tables that hold no account's data: the shared offer feed and its indexes, places, flags; the conversations and
     * messages, kept for the other side (a conversation goes with its last member); and the communities,
     * kept for their members when their creator goes (the administrators then name their moderators); and the ideas of the
     * ideas board, kept for those who voted for them, their author forgotten; and the addresses that declined the network's
     * invitations, kept as hashes only.
     */
    static final Set<String> SHARED = Set.of("job_offers", "job_offer_skills", "job_offer_changes", "offer_appearances",
        "offer_mentions", "offer_rate_benchmarks", "offer_rate_history", "offer_site_statuses", "offer_skips", "offer_terms",
        "geo_places", "feature_flags", "network_conversations", "network_messages", "network_communities", "network_ideas",
        "network_invitation_opt_outs", "help_feedback_days");

    private static final String POSTS = "(SELECT id FROM network_posts WHERE author_id = ?)";
    private static final String COMMENTS = "(SELECT id FROM network_comments WHERE author_id = ? OR post_id IN " + POSTS + ")";
    private static final String PROJECTS = "(SELECT id FROM projects WHERE freelance_id = ?)";
    private static final String CONTACTS = "(SELECT id FROM contacts WHERE freelance_id = ?)";

    private final JdbcTemplate jdbc;

    /** The tables deleted with an account, in their order. */
    public static List<String> purgedTables() {
        return PURGED;
    }

    /** The tables that hold no account's data. */
    public static Set<String> sharedTables() {
        return SHARED;
    }

    /** Deletes the account and everything it holds; answers how many rows went. */
    public int purge(Long userId) {
        String email = jdbc.query("SELECT email FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, userId);
        if (email == null) {
            return 0;
        }
        Long freelanceId = jdbc.query("SELECT id FROM freelances WHERE LOWER(email) = LOWER(?)", rs -> rs.next() ? rs.getLong(1) : null, email);
        int rows = network(userId);
        if (freelanceId != null) {
            rows += workspace(freelanceId);
        }
        rows += account(userId);
        log.info("Purged account {} after its time on hold: {} rows", userId, rows);
        return rows;
    }

    private int network(Long u) {
        int rows = 0;
        // Others' posts no longer answer or pin the account's comments.
        rows += jdbc.update("UPDATE network_posts SET answer_comment_id = NULL WHERE answer_comment_id IN " + COMMENTS, u, u);
        rows += jdbc.update("UPDATE network_posts SET pinned_comment_id = NULL WHERE pinned_comment_id IN " + COMMENTS, u, u);
        for (String table : List.of("network_post_tags", "network_post_versions", "network_likes",
            "network_mission_takes", "network_poll_votes", "network_bookmarks", "network_notifications", "network_post_impressions",
            "network_post_dismissals")) {
            rows += jdbc.update("DELETE FROM " + table + " WHERE post_id IN " + POSTS, u);
        }
        rows += jdbc.update("DELETE FROM network_comment_likes WHERE post_id IN " + POSTS + " OR comment_id IN " + COMMENTS +
            " OR comment_id IN (SELECT id FROM network_comments WHERE parent_id IN " + COMMENTS + ")", u, u, u, u, u);
        // Replies first: they point to the comments they answer.
        rows += jdbc.update("DELETE FROM network_comments WHERE parent_id IN " + COMMENTS, u, u);
        rows += jdbc.update("DELETE FROM network_comments WHERE author_id = ? OR post_id IN " + POSTS, u, u);
        // Files of the posts and of what waits for review; those of the messages sent stay with them.
        String files = "(SELECT id FROM network_files WHERE post_id IN " + POSTS +
            " OR held_id IN (SELECT id FROM network_held WHERE author_id = ?) OR (owner_id = ? AND message_id IS NULL))";
        rows += jdbc.update("DELETE FROM network_file_contents WHERE file_id IN " + files, u, u, u);
        rows += jdbc.update("DELETE FROM network_files WHERE id IN " + files, u, u, u);
        rows += jdbc.update("DELETE FROM network_held WHERE author_id = ?", u);
        rows += jdbc.update("DELETE FROM network_reviews WHERE author_id = ?", u);
        rows += jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", u);
        rows += jdbc.update("DELETE FROM ai_usage WHERE user_id = ?", u);
        rows += jdbc.update("DELETE FROM support_attachments WHERE request_id IN (SELECT id FROM support_requests WHERE user_id = ?)", u);
        rows += jdbc.update("DELETE FROM support_requests WHERE user_id = ?", u);
        rows += jdbc.update("DELETE FROM network_posts WHERE author_id = ?", u);
        for (String table : List.of("network_likes", "network_comment_likes", "network_mission_takes",
            "network_poll_votes", "network_bookmarks", "network_tag_follows", "network_message_reactions",
            "network_conversation_members", "network_community_members", "network_post_dismissals", "network_idea_votes", "network_meetup_attendees",
            "network_members")) {
            rows += jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", u);
        }
        // A conversation whose last member this was goes, with its messages, their reactions and files: kept for no one.
        String orphans = "(SELECT c.id FROM network_conversations c WHERE NOT EXISTS " +
            "(SELECT 1 FROM network_conversation_members m WHERE m.conversation_id = c.id))";
        String orphanMessages = "(SELECT id FROM network_messages WHERE conversation_id IN " + orphans + ")";
        rows += jdbc.update("DELETE FROM network_message_reactions WHERE message_id IN " + orphanMessages);
        rows += jdbc.update("DELETE FROM network_file_contents WHERE file_id IN (SELECT id FROM network_files WHERE message_id IN " +
            orphanMessages + ")");
        rows += jdbc.update("DELETE FROM network_files WHERE message_id IN " + orphanMessages);
        rows += jdbc.update("DELETE FROM network_messages WHERE conversation_id IN " + orphans);
        rows += jdbc.update("DELETE FROM network_conversations WHERE id IN " + orphans);
        // The events the account organizes go, with their answers and what told of them.
        String meetups = "(SELECT id FROM network_meetups WHERE organizer_id = ?)";
        rows += jdbc.update("DELETE FROM network_meetup_attendees WHERE meetup_id IN " + meetups, u);
        rows += jdbc.update("DELETE FROM network_notifications WHERE meetup_id IN " + meetups, u);
        rows += jdbc.update("DELETE FROM network_meetups WHERE organizer_id = ?", u);
        rows += jdbc.update("DELETE FROM network_invitations WHERE inviter_id = ?", u);
        rows += jdbc.update("DELETE FROM network_notifications WHERE recipient_id = ? OR actor_id = ?", u, u);
        // The ideas proposed stay for those who voted for them, from a former member.
        rows += jdbc.update("UPDATE network_ideas SET author_id = NULL WHERE author_id = ?", u);
        rows += jdbc.update("DELETE FROM network_follows WHERE follower_id = ? OR followed_id = ?", u, u);
        rows += jdbc.update("DELETE FROM network_follow_requests WHERE member_id = ? OR requester_id = ?", u, u);
        rows += jdbc.update("DELETE FROM network_restrictions WHERE owner_id = ? OR target_id = ?", u, u);
        rows += jdbc.update("DELETE FROM network_drafts WHERE author_id = ?", u);
        rows += jdbc.update("DELETE FROM network_page_views WHERE viewer_id = ? OR member_id = ?", u, u);
        rows += jdbc.update("DELETE FROM network_post_impressions WHERE viewer_id = ?", u);
        rows += jdbc.update("DELETE FROM network_reports WHERE reporter_id = ? OR target_user_id = ?", u, u);
        return rows;
    }

    private int workspace(Long f) {
        int rows = 0;
        rows += jdbc.update("DELETE FROM webhook_events WHERE webhook_id IN (SELECT id FROM webhooks WHERE freelance_id = ?)", f);
        rows += jdbc.update("DELETE FROM webhook_deliveries WHERE webhook_id IN (SELECT id FROM webhooks WHERE freelance_id = ?)", f);
        rows += jdbc.update("DELETE FROM webhooks WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM activity_report_days WHERE report_id IN (SELECT id FROM activity_reports WHERE project_id IN " +
            PROJECTS + ")", f);
        rows += jdbc.update("DELETE FROM interview_step_checklist WHERE step_id IN (SELECT id FROM interview_steps WHERE project_id IN " +
            PROJECTS + ")", f);
        rows += jdbc.update("DELETE FROM interview_steps WHERE project_id IN " + PROJECTS, f);
        rows += jdbc.update("DELETE FROM scheduled_contact_emails WHERE freelance_id = ? OR contact_id IN " + CONTACTS, f, f);
        rows += jdbc.update("DELETE FROM prospection_campaigns WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM contact_interactions WHERE contact_id IN " + CONTACTS + " OR project_id IN " + PROJECTS, f, f);
        rows += jdbc.update("DELETE FROM contact_relations WHERE first_contact_id IN " + CONTACTS + " OR second_contact_id IN " + CONTACTS, f, f);
        for (String table : List.of("project_attachments", "project_notes", "project_chat_messages", "project_contract_checks", "project_documents", "project_referrals")) {
            rows += jdbc.update("DELETE FROM " + table + " WHERE project_id IN " + PROJECTS, f);
        }
        rows += jdbc.update("UPDATE purchase_orders SET amends_id = NULL WHERE project_id IN " + PROJECTS, f);
        rows += jdbc.update("DELETE FROM purchase_orders WHERE project_id IN " + PROJECTS, f);
        rows += jdbc.update("DELETE FROM job_offer_states WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM offer_ai_verdicts WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM autopilot_drafts WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM invoice_lines WHERE invoice_id IN (SELECT id FROM invoices WHERE freelance_id = ?)", f);
        rows += jdbc.update("UPDATE invoices SET credited_invoice_id = NULL WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM invoices WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM invoice_counters WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM activity_reports WHERE project_id IN " + PROJECTS, f);
        rows += jdbc.update("DELETE FROM quote_lines WHERE quote_id IN (SELECT id FROM quotes WHERE freelance_id = ?)", f);
        rows += jdbc.update("DELETE FROM quotes WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM quote_counters WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM expenses WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM testimonials WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM projects WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM pipeline_template_steps WHERE template_id IN (SELECT id FROM pipeline_templates WHERE freelance_id = ?)", f);
        rows += jdbc.update("DELETE FROM pipeline_templates WHERE freelance_id = ?", f);
        rows += jdbc.update("UPDATE contacts SET manager_id = NULL WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM contacts WHERE freelance_id = ?", f);
        rows += jdbc.update("DELETE FROM booking_windows WHERE page_id IN (SELECT id FROM booking_pages WHERE freelance_id = ?)", f);
        rows += jdbc.update("DELETE FROM compliance_pack_documents WHERE pack_id IN (SELECT id FROM compliance_packs WHERE freelance_id = ?)", f);
        for (String table : List.of("clients", "sources", "seasons", "email_templates", "offer_board_visits", "offer_publisher_blocks",
            "offer_site_exclusions", "offer_suggestion_dismissals", "saved_offer_searches", "trainings", "profile_experiences", "profile_educations", "profile_cvs", "bookings", "booking_pages",
            "compliance_packs", "compliance_documents", "document_logos", "document_terms_files", "document_font_files", "document_templates", "document_letterheads")) {
            rows += jdbc.update("DELETE FROM " + table + " WHERE freelance_id = ?", f);
        }
        rows += jdbc.update("DELETE FROM freelances WHERE id = ?", f);
        return rows;
    }

    private int account(Long u) {
        int rows = jdbc.update("DELETE FROM api_token_scopes WHERE token_id IN (SELECT id FROM api_tokens WHERE user_id = ?)", u);
        for (String table : List.of("api_tokens", "account_sessions", "account_events", "user_skills", "user_skill_details", "user_languages")) {
            rows += jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", u);
        }
        return rows + jdbc.update("DELETE FROM users WHERE id = ?", u);
    }
}
