package dev.swirlit.indezy.scraping;

import java.io.IOException;

/** A job board answered with an error status, or refused the request under its rules. */
public class SiteResponseException extends IOException {

    private final int status;

    public SiteResponseException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status, or 0 when Indezy did not send the request (robots.txt, foreign domain). */
    public int getStatus() {
        return status;
    }
}
