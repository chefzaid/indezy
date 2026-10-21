package dev.swirlit.indezy;

import dev.swirlit.indezy.dto.ActivityReportLinkDto;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.dto.FreelanceDto;
import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.PasswordChangeRequestDto;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.dto.TwoFactorCodeRequest;
import dev.swirlit.indezy.dto.TwoFactorSetupDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring MVC logs request and response bodies at DEBUG through their {@code toString}, which Lombok's {@code @Data}
 * generates with every field. Every class of the dto and model packages that holds a password (or its hash), a token, a
 * two-factor secret or code, a backup code or an API key must print none of them; such a class and the classes nested in
 * it (the auth requests and responses, the account entities) must not print the email or name of the account either,
 * nor may any class holding a person's email or phone (the contact, workspace and profile bodies) print who they are.
 */
class SensitiveToStringTest {

    private static final List<String> SCANNED_PACKAGES = List.of("dev.swirlit.indezy.dto", "dev.swirlit.indezy.model");
    /** A bare {@code code} is the two-factor code of a request; a qualified one, such as a postal code, is not a secret. */
    private static final Pattern SECRET_FIELD =
        Pattern.compile("(?i)code|.*(password|secret|token|totp|otpauth|backupcode|recoverycode|apikey).*");
    private static final Set<String> IDENTITY_FIELDS = Set.of("email", "phone", "firstName", "lastName", "fullName", "address");
    /** A person's contact details: a class holding one holds personal data. */
    private static final Set<String> CONTACT_FIELDS = Set.of("email", "phone");

    @Test
    void scan_ShouldFindEveryClassHoldingASecretOrNestedInOne() {
        assertThat(sensitiveClasses()).contains(LoginRequest.class, RegisterRequest.class, PasswordChangeRequestDto.class,
            TwoFactorSetupDto.class, TwoFactorCodeRequest.class, LoginResponse.class, LoginResponse.UserInfo.class, User.class,
            Freelance.class, ActivityReportLinkDto.class, ActivityReport.class, ContactDto.class, FreelanceDto.class, UserDto.class,
            Contact.class);
    }

    @Test
    void toString_ShouldPrintNeitherTheSecretsNorTheIdentity() throws ReflectiveOperationException {
        List<String> leaks = new ArrayList<>();
        for (Class<?> type : sensitiveClasses()) {
            Object instance = ReflectionUtils.accessibleConstructor(type).newInstance();
            List<Field> fields = protectedFields(type);
            for (Field field : fields) {
                ReflectionUtils.makeAccessible(field);
                String value = sampleValue(field);
                field.set(instance, field.getType() == String.class ? value : new ArrayList<>(List.of(value)));
            }
            String printed = instance.toString();
            List<String> printedFields = fields.stream().filter(field -> printed.contains(sampleValue(field))).map(Field::getName).toList();
            if (!printedFields.isEmpty()) {
                leaks.add(type.getName() + " prints " + printedFields);
            }
        }
        assertThat(leaks).isEmpty();
    }

    private static List<Class<?>> sensitiveClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        return SCANNED_PACKAGES.stream()
            .flatMap(basePackage -> scanner.findCandidateComponents(basePackage).stream())
            .<Class<?>>map(definition -> ClassUtils.resolveClassName(definition.getBeanClassName(), null))
            .filter(type -> !protectedFields(type).isEmpty())
            .toList();
    }

    /**
     * The secret fields of the class, plus its identity fields when it or its enclosing class holds a secret, or when it
     * holds a person's contact details.
     */
    private static List<Field> protectedFields(Class<?> type) {
        boolean hidesIdentity = holdsSecrets(type) || (type.getEnclosingClass() != null && holdsSecrets(type.getEnclosingClass())) ||
            textFields(type).stream().anyMatch(field -> CONTACT_FIELDS.contains(field.getName()));
        return textFields(type).stream()
            .filter(field -> isSecret(field) || (hidesIdentity && IDENTITY_FIELDS.contains(field.getName())))
            .toList();
    }

    private static boolean holdsSecrets(Class<?> type) {
        return textFields(type).stream().anyMatch(SensitiveToStringTest::isSecret);
    }

    private static boolean isSecret(Field field) {
        return SECRET_FIELD.matcher(field.getName()).matches();
    }

    /** Instance fields that hold text or a list of it, declared or inherited; a date or flag such as lastPasswordChange holds no secret. */
    private static List<Field> textFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        ReflectionUtils.doWithFields(type, fields::add, field -> !Modifier.isStatic(field.getModifiers()) &&
            (field.getType() == String.class || field.getType().isAssignableFrom(ArrayList.class)));
        return fields;
    }

    private static String sampleValue(Field field) {
        return "sensitive-" + field.getName() + "-value";
    }
}
