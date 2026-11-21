package dev.swirlit.indezy.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * The cryptography of web push with the JDK alone: a message encrypted for a browser's subscription (RFC 8291, the
 * {@code aes128gcm} content coding of RFC 8188, one record) and the VAPID token that signs the request (RFC 8292, ES256).
 */
public final class WebPushCrypto {

    static final int RECORD_SIZE = 4096;
    private static final int POINT_LENGTH = 65;
    private static final int COORDINATE = 32;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte LAST_RECORD = 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private WebPushCrypto() {
    }

    /** The message encrypted for the subscription: salt, record size, the sender's public key, then the ciphertext. */
    public static byte[] encrypt(byte[] plaintext, String p256dh, String auth) throws GeneralSecurityException {
        byte[] receiverKey = URL_DECODER.decode(p256dh);
        byte[] authSecret = URL_DECODER.decode(auth);
        KeyPair sender = generate();
        byte[] senderKey = point((ECPublicKey) sender.getPublic());
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[][] keyAndNonce = derive(agree((ECPrivateKey) sender.getPrivate(), publicKey(receiverKey)), authSecret, receiverKey, senderKey, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyAndNonce[0], "AES"), new GCMParameterSpec(TAG_BITS, keyAndNonce[1]));
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = LAST_RECORD;
        byte[] ciphertext = cipher.doFinal(padded);
        return ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + POINT_LENGTH + ciphertext.length).put(salt).putInt(RECORD_SIZE)
            .put((byte) POINT_LENGTH).put(senderKey).put(ciphertext).array();
    }

    /** The receiver's side, for tests: the plaintext of a message encrypted for the given private key and secret. */
    static byte[] decrypt(byte[] message, ECPrivateKey receiver, byte[] receiverKey, byte[] authSecret) throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] salt = new byte[SALT_LENGTH];
        buffer.get(salt);
        buffer.getInt();
        byte[] senderKey = new byte[buffer.get()];
        buffer.get(senderKey);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        byte[][] keyAndNonce = derive(agree(receiver, publicKey(senderKey)), authSecret, receiverKey, senderKey, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndNonce[0], "AES"), new GCMParameterSpec(TAG_BITS, keyAndNonce[1]));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (end > 0 && padded[end] == 0) {
            end--;
        }
        return Arrays.copyOf(padded, end);
    }

    /** The VAPID token for a push service's origin, valid twelve hours: ES256 over the header and the claims. */
    public static String vapidToken(String audience, String subject, long expiresAt, String privateKey, String publicKey)
            throws GeneralSecurityException {
        String header = URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = URL.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAt + ",\"sub\":\"" + subject + "\"}")
            .getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey(URL_DECODER.decode(privateKey)));
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + URL.encodeToString(signer.sign());
    }

    /** A new P-256 key pair as base64url: the public point, then the private scalar, as the VAPID keys are given. */
    public static String[] newVapidKeys() throws GeneralSecurityException {
        KeyPair pair = generate();
        byte[] scalar = unsigned(((ECPrivateKey) pair.getPrivate()).getS());
        return new String[] {URL.encodeToString(point((ECPublicKey) pair.getPublic())), URL.encodeToString(scalar)};
    }

    private static byte[][] derive(byte[] shared, byte[] authSecret, byte[] receiverKey, byte[] senderKey, byte[] salt)
            throws GeneralSecurityException {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.writeBytes("WebPush: info".getBytes(StandardCharsets.US_ASCII));
        info.write(0);
        info.writeBytes(receiverKey);
        info.writeBytes(senderKey);
        byte[] ikm = expand(hmac(authSecret, shared), info.toByteArray(), COORDINATE);
        byte[] prk = hmac(salt, ikm);
        byte[] key = expand(prk, label("Content-Encoding: aes128gcm"), KEY_LENGTH);
        byte[] nonce = expand(prk, label("Content-Encoding: nonce"), NONCE_LENGTH);
        return new byte[][] {key, nonce};
    }

    private static byte[] label(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return Arrays.copyOf(bytes, bytes.length + 1);
    }

    /** HKDF-Expand for one block: the first {@code length} bytes of HMAC(prk, info || 0x01). */
    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        byte[] input = Arrays.copyOf(info, info.length + 1);
        input[info.length] = 1;
        return Arrays.copyOf(hmac(prk, input), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] agree(ECPrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    static KeyPair generate() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    static byte[] point(ECPublicKey key) {
        byte[] point = new byte[POINT_LENGTH];
        point[0] = 4;
        System.arraycopy(unsigned(key.getW().getAffineX()), 0, point, 1, COORDINATE);
        System.arraycopy(unsigned(key.getW().getAffineY()), 0, point, 1 + COORDINATE, COORDINATE);
        return point;
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] fixed = new byte[COORDINATE];
        int length = Math.min(bytes.length, COORDINATE);
        System.arraycopy(bytes, bytes.length - length, fixed, COORDINATE - length, length);
        return fixed;
    }

    static ECPublicKey publicKey(byte[] point) throws GeneralSecurityException {
        if (point.length != POINT_LENGTH || point[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 1 + COORDINATE));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 1 + COORDINATE, POINT_LENGTH));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve()));
    }

    private static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), curve()));
    }

    private static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }
}
