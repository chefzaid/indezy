package dev.swirlit.indezy.scraping;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Web addresses as the sites write them, made fit for a request: a sitemap, a link or a redirect may hold characters no
 * URI may ({@code https://ouidesk.com/mission/Développeur-Java-Angular-"H-F"-e53b…}), which are percent-encoded. An
 * address that is a valid URI already is kept as written, so what readers take from it (an offer's identifier) does not
 * change.
 */
public final class WebAddresses {

    /** The printable ASCII characters no part of a URI may hold. */
    private static final String ILLEGAL = "\"<>\\^`{|}";
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private WebAddresses() {
    }

    /** {@code address} as a URI, the characters no URI may hold encoded; malformed when even so it is no URI. */
    public static URI uri(String address) throws MalformedURLException {
        try {
            return new URI(encoded(address));
        } catch (URISyntaxException e) {
            MalformedURLException malformed = new MalformedURLException(e.getMessage());
            malformed.initCause(e);
            throw malformed;
        }
    }

    /**
     * {@code address} as written when it is a valid URI, otherwise with the characters no URI may hold (quotes, spaces,
     * braces, a {@code %} that starts no escape…) percent-encoded in UTF-8. Accented letters stay: a URI may hold them,
     * and the HTTP client encodes them when it sends.
     */
    public static String encoded(String address) {
        try {
            return new URI(address).toString();
        } catch (URISyntaxException e) {
            return encode(address);
        }
    }

    private static String encode(String address) {
        StringBuilder encoded = new StringBuilder(address.length());
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (illegal(address, i)) {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    encoded.append('%').append(HEX.toHexDigits(b));
                }
            } else {
                encoded.append(c);
            }
        }
        return encoded.toString();
    }

    /** Whether the character at {@code index} may stand in no URI, as {@link URI} reads them. */
    private static boolean illegal(String address, int index) {
        char c = address.charAt(index);
        if (c == '%') {
            return index + 2 >= address.length() ||
                !HexFormat.isHexDigit(address.charAt(index + 1)) || !HexFormat.isHexDigit(address.charAt(index + 2));
        }
        return ILLEGAL.indexOf(c) >= 0 || Character.isISOControl(c) || Character.isSpaceChar(c);
    }
}
