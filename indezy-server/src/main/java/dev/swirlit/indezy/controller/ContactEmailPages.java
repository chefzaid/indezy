package dev.swirlit.indezy.controller;

/** The small pages a prospecting email's objection link opens: a question with its confirm button, then the answer. */
final class ContactEmailPages {

    private ContactEmailPages() {
    }

    /** A page with this title and message, and a button posting to the same link when {@code formToken} is given. */
    static String page(String title, String message, String formToken) {
        String form = formToken == null ? "" : "<form method=\"post\" action=\"" + escape(formToken) + "\"><button type=\"submit\">" +
            "Confirmer / Confirm</button></form>";
        return "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>" + escape(title) + "</title><style>body{font-family:system-ui,sans-serif;max-width:32rem;margin:3rem auto;padding:0 1rem;" +
            "color:#1f2937}button{font:inherit;padding:.6rem 1.2rem;border:0;border-radius:6px;background:#1f2937;color:#fff;cursor:pointer}" +
            "</style></head><body><h1>" + escape(title) + "</h1><p>" + message + "</p>" + form + "</body></html>";
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
