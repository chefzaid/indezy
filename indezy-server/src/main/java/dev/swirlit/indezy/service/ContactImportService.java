package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContactImportResultDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses CSV or vCard payloads and imports the contained contacts under a given client,
 * skipping any whose email already belongs to the client's freelance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactImportService {

    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;

    /** A single contact parsed from an import payload, before persistence. */
    public record ParsedContact(String firstName, String lastName, String email, String phone) {
    }

    @Transactional
    public ContactImportResultDto importForClient(Long clientId, String content) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + clientId));
        Freelance freelance = client.getFreelance();
        Long freelanceId = freelance.getId();

        List<ParsedContact> parsed = parse(content);
        int imported = 0;
        int skipped = 0;
        for (ParsedContact parsedContact : parsed) {
            if (parsedContact.email() != null && !parsedContact.email().isBlank()
                    && contactRepository.existsByEmailAndFreelanceId(parsedContact.email(), freelanceId)) {
                skipped++;
                continue;
            }
            Contact contact = new Contact();
            contact.setFirstName(parsedContact.firstName());
            contact.setLastName(parsedContact.lastName());
            contact.setEmail(emptyToNull(parsedContact.email()));
            contact.setPhone(emptyToNull(parsedContact.phone()));
            contact.setClient(client);
            contact.setFreelance(freelance);
            contactRepository.save(contact);
            imported++;
        }
        log.debug("Imported {} contact(s), skipped {} duplicate(s) for client {}", imported, skipped, clientId);
        return new ContactImportResultDto(imported, skipped, parsed.size());
    }

    /** Detects the payload format and parses it into contacts with a usable first name. */
    public List<ParsedContact> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        if (content.stripLeading().toUpperCase(Locale.ROOT).startsWith("BEGIN:VCARD")) {
            return parseVcard(content);
        }
        return parseCsv(content);
    }

    private List<ParsedContact> parseCsv(String content) {
        List<ParsedContact> contacts = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        int headerIndex = firstNonBlankIndex(lines);
        if (headerIndex < 0) {
            return contacts;
        }
        List<String> headers = parseCsvLine(lines[headerIndex]);
        int firstNameCol = columnIndex(headers, "first");
        int lastNameCol = columnIndex(headers, "last");
        int nameCol = columnIndex(headers, "name");
        int emailCol = columnIndex(headers, "email");
        int phoneCol = anyColumnIndex(headers, "phone", "mobile", "tel");

        for (int i = headerIndex + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            List<String> fields = parseCsvLine(lines[i]);
            String firstName = at(fields, firstNameCol);
            String lastName = at(fields, lastNameCol);
            if (firstName.isBlank() && nameCol >= 0) {
                String[] split = splitFullName(at(fields, nameCol));
                firstName = split[0];
                lastName = split[1];
            }
            if (firstName.isBlank()) {
                continue;
            }
            contacts.add(new ParsedContact(firstName, blankToNull(lastName),
                at(fields, emailCol), at(fields, phoneCol)));
        }
        return contacts;
    }

    private List<ParsedContact> parseVcard(String content) {
        List<ParsedContact> contacts = new ArrayList<>();
        String firstName = "";
        String lastName = "";
        String email = "";
        String phone = "";
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.equals("BEGIN:VCARD")) {
                firstName = lastName = email = phone = "";
            } else if (upper.equals("END:VCARD")) {
                if (!firstName.isBlank()) {
                    contacts.add(new ParsedContact(firstName, blankToNull(lastName),
                        blankToNull(email), blankToNull(phone)));
                }
            } else if (upper.startsWith("N:")) {
                String[] parts = line.substring(2).split(";");
                lastName = parts.length > 0 ? parts[0].trim() : "";
                firstName = parts.length > 1 ? parts[1].trim() : firstName;
            } else if (upper.startsWith("FN:") && firstName.isBlank()) {
                String[] split = splitFullName(line.substring(3).trim());
                firstName = split[0];
                lastName = split[1];
            } else if (upper.startsWith("EMAIL") && email.isBlank()) {
                email = valueAfterColon(line);
            } else if (upper.startsWith("TEL") && phone.isBlank()) {
                phone = valueAfterColon(line);
            }
        }
        return contacts;
    }

    /** Splits a CSV line into fields, honouring double-quoted values and escaped quotes. */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    private int firstNonBlankIndex(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private int columnIndex(List<String> headers, String keyword) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).toLowerCase(Locale.ROOT).contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    private int anyColumnIndex(List<String> headers, String... keywords) {
        for (String keyword : keywords) {
            int index = columnIndex(headers, keyword);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    private String at(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index).trim() : "";
    }

    private String[] splitFullName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, lastSpace).trim(), trimmed.substring(lastSpace + 1).trim()};
    }

    private String valueAfterColon(String line) {
        int colon = line.indexOf(':');
        return colon >= 0 ? line.substring(colon + 1).trim() : "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
