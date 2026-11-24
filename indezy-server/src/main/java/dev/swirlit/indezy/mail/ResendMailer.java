package dev.swirlit.indezy.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends email through Resend's API ({@code POST /emails}) with the account's key. Each email carries its idempotency
 * key, so a retry or a second replica sending the same email within Resend's 24 hours sends it once. An accepted email
 * is accepted for delivery, not delivered: Resend's logs show bounces.
 */
@Component
public class ResendMailer {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MailProperties properties;
    private final HttpClient http;

    @Autowired
    public ResendMailer(MailProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    ResendMailer(MailProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public boolean configured() {
        return properties.configured();
    }

    /** Sends {@code email}; the id Resend gave it. */
    public String send(Email email) throws InterruptedException {
        if (!configured()) {
            throw new MailException("No Resend key is set");
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("from", from(properties.from(), email.senderName()));
        if (email.replyTo() != null) {
            body.putArray("reply_to").add(email.replyTo());
        }
        body.putArray("to").add(email.to());
        body.put("subject", email.subject());
        body.put("html", email.html());
        body.put("text", email.text());
        ObjectNode headers = body.putObject("headers");
        email.headers().forEach(headers::put);
        if (!email.attachments().isEmpty()) {
            var files = body.putArray("attachments");
            email.attachments().forEach(file -> files.addObject().put("filename", file.fileName())
                .put("content", java.util.Base64.getEncoder().encodeToString(file.content())).put("content_type", file.contentType()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.resendUrl() + "/emails"))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + properties.resendApiKey())
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", email.idempotencyKey())
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MailException("Resend could not be reached: " + e.getMessage(), e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Resend's error names the problem ("validation_error", "rate_limit_exceeded"), never the email's content.
            throw new MailException("Resend answered " + response.statusCode() + ": " + errorName(response.body()));
        }
        String id = JSON.readTree(response.body()).path("id").asString("");
        if (id.isBlank()) {
            throw new MailException("Resend answered without an email id");
        }
        return id;
    }

    /**
     * A received email's text, for an answer that came back to a reply address: its sender as the {@code From:} header
     * gives it, its subject, and its text (the plain part, else the HTML part without its tags).
     */
    public record Received(String from, String subject, String text) {
    }

    /** Reads the received email {@code emailId} ({@code GET /emails/receiving/{id}}). */
    public Received received(String emailId) throws InterruptedException {
        if (!configured() || emailId == null || !emailId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new MailException("No received email to read");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.resendUrl() + "/emails/receiving/" + emailId))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + properties.readingKey())
            .GET()
            .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MailException("Resend could not be reached: " + e.getMessage(), e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MailException("Resend answered " + response.statusCode() + ": " + errorName(response.body()));
        }
        JsonNode email = JSON.readTree(response.body());
        String text = email.path("text").asString("");
        if (text.isBlank()) {
            text = email.path("html").asString("").replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</p>", "\n").replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ")
                .replaceAll("[ \t]+", " ").strip();
        }
        String from = email.path("headers").path("from").asString(email.path("from").asString(""));
        return new Received(from, email.path("subject").asString(""), text);
    }

    /** "Alice Martin via Indezy <noreply@...>" from "Indezy <noreply@...>" for an email sent for a member. */
    static String from(String sender, String memberName) {
        String name = memberName == null ? "" : memberName.replaceAll("[\\p{Cntrl}\"<>\\\\,;]", " ").replaceAll("\\s+", " ").strip();
        if (name.isEmpty()) {
            return sender;
        }
        int open = sender.indexOf('<');
        String address = open < 0 ? sender.strip() : sender.substring(open + 1, sender.indexOf('>', open)).strip();
        return "\"" + name + " via Indezy\" <" + address + ">";
    }

    private static String errorName(String body) {
        try {
            JsonNode error = JSON.readTree(body);
            return error.path("name").asString(error.path("message").asString("unknown error"));
        } catch (RuntimeException e) {
            return "unreadable answer";
        }
    }
}
