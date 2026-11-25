import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;

// Read-only: resolve a PKCS12 private-key alias using the existing certificate pin.
class ResolveV2SigningAlias {
    static String env(String name) { return System.getenv().getOrDefault(name, ""); }
    static String fingerprint(Certificate cert) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
    }
    static void fail(String reason) { System.err.println(reason); System.exit(1); }
    public static void main(String[] args) throws Exception {
        String pin = env("DPC_AIO_CONFIGURED_SIGNING_CERT_SHA256").replaceAll("[:\\s]", "").toUpperCase(Locale.ROOT);
        if (!pin.matches("[0-9A-F]{64}")) { fail("V2_CERT_PIN_INVALID"); return; }
        KeyStore store = KeyStore.getInstance("PKCS12");
        char[] password = env("DPC_AIO_RELEASE_STORE_PASSWORD").toCharArray();
        try (var in = Files.newInputStream(Path.of(args[0]))) { store.load(in, password); }
        catch (Exception e) { fail("V2_KEYSTORE_LOAD_FAILED: check PKCS12 and store password"); return; }
        finally { Arrays.fill(password, '\0'); }
        String requested = env("DPC_AIO_RELEASE_KEY_ALIAS");
        var matches = new ArrayList<String>();
        for (String alias : Collections.list(store.aliases())) {
            Certificate cert = store.getCertificate(alias);
            if (store.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class) && cert != null && fingerprint(cert).equals(pin)) matches.add(alias);
        }
        if (matches.size() != 1) { fail("V2_CERT_MATCH_NOT_UNIQUE: expected exactly one pinned private-key entry"); return; }
        String alias = matches.get(0);
        if (store.containsAlias(requested) && !alias.equalsIgnoreCase(requested)) { fail("V2_CONFIGURED_ALIAS_CERT_MISMATCH"); return; }
        if (!alias.matches("[ -~]+") || !alias.equals(alias.strip())) { fail("V2_ALIAS_FORMAT_UNSUPPORTED"); return; }
        char[] keyPassword = env("DPC_AIO_RELEASE_KEY_PASSWORD").toCharArray();
        try {
            if (!(store.getKey(alias, keyPassword) instanceof PrivateKey)) { fail("V2_PRIVATE_KEY_UNAVAILABLE"); return; }
        } catch (Exception e) { fail("V2_PRIVATE_KEY_UNAVAILABLE: check key password"); return; }
        finally { Arrays.fill(keyPassword, '\0'); }
        System.out.println(alias);
    }
}
