package de.sfuhrm.openssl4j;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.Provider;
import java.util.HashMap;
import java.util.Map;

/**
 * JCA provider directing all calls to the system native OpenSSL library.
 *
 * @author Stephan Fuhrmann
 */
public final class OpenSSL4JProvider extends Provider {

    /** The provider name passed to JCA. */
    public static final String PROVIDER_NAME = "OpenSSL4J";

    public static final String PROVIDER_INFO = PROVIDER_NAME + " JSP/JCE";

    private OpenSSLCryptoNative crypto = new OpenSSLCryptoNative();

    private static OpenSSL4JProvider instance = null;

    /**
     * Constructor for the JCA Provider for OpenSSL JNI.
     *
     * @throws IllegalStateException
     *             if the native object file can't be loaded and the class can't be used.
     */

    public static synchronized OpenSSL4JProvider getInstance() {
        if (OpenSSL4JProvider.instance == null) {
            OpenSSL4JProvider.instance = new OpenSSL4JProvider();
        }

        return OpenSSL4JProvider.instance;
    }

    /**
     * Constructor for the JCA Provider for OpenSSL JNI.
     *
     * @throws IllegalStateException
     *             if the native object file can't be loaded and the class can't be used.
     */
    public OpenSSL4JProvider() {
        super(PROVIDER_NAME, getLibraryVersion(), "OpenSSL4J provider v" + PropertyAccessor.get("version", "unknown")
                + ", implementing " + "multiple message digest algorithms.");

        try {

            // Load.
            NativeLoader.loadAll();

            // Initialize sets of names if not already initialized.
            if (OpenSSL4JProviderUtils.openSslMessageDigestAlgorithms == null) {
                OpenSSL4JProviderUtils.openSslMessageDigestAlgorithms = OpenSSLMessageDigestNative
                        .getMessageDigestList();
            }
            if (OpenSSL4JProviderUtils.openSslCiphers == null) {
                OpenSSL4JProviderUtils.openSslCiphers = OpenSSLCipherNative.getCipherList();
            }

            Map<String, String> names = getNames(openSslMessageDigestAlgorithms);
            putAll(names);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize: " + e.getMessage(), e);
        }
    }

    private static double getLibraryVersion() {
        double result = 0.0;
        String stringVersion = PropertyAccessor.get("version", "0.0.0");
        Pattern versionPattern = Pattern.compile("(\\d+\\.\\d+).*");
        Matcher matcher = versionPattern.matcher(stringVersion);
        if (matcher.matches()) {
            result = Double.parseDouble(matcher.group(1));
        }
        return result;
    }

    /**
     * Gets the names and the aliases of all message digest algorithms.
     *
     * @return a map mapping from algorithm name / alias to algorithm class.
     */
    private static Map<String, String> getNames(Set<String> availableOpenSslAlgorithmNames) {
        Map<String, String> result = getOpenSSLHashnames(availableOpenSslAlgorithmNames);
        result.putAll(createAliases(result));
        return result;
    }

    /**
     * Creates some aliases for an input map.
     *
     * @param map
     *            a map with keys being algorithm names of the form "MessageDigest.MD5" and the keys being java class
     *            names.
     *
     * @return a map mapping from algorithm name / alias to algorithm class.
     */
    private static Map<String, String> createAliases(Map<String, String> map) {
        Map<String, String> aliases = new HashMap<>();
        Pattern pattern = Pattern.compile("([^0-9]*)-([0-9]+)");

        for (Map.Entry<String, String> entry : map.entrySet()) {
            Matcher matcher = pattern.matcher(entry.getKey());
            if (matcher.matches()) {

                // adds for MessageDigest.SHA512 an alias like MessageDigest.SHA-512
                aliases.put(matcher.group(1) + matcher.group(2), entry.getValue());
            }
        }
        aliases.put("MessageDigest.SHA", map.get("MessageDigest.SHA1"));
        return aliases;
    }

    /**
     * Name pairs mapping from SSL to Java. First one is SSL name, second one is Java name.
     */
    private static final String[] SSL_TO_JAVA_NAMES = { "MD5", "MD5", "SHA1", "SHA1", "SHA224", "SHA-224", "SHA256",
            "SHA-256", "SHA384", "SHA-384", "SHA512", "SHA-512", "SHA512-224", "SHA-512/224", "SHA512-256",
            "SHA-512/256", "SHA3-224", "SHA3-224", "SHA3-256", "SHA3-256", "SHA3-384", "SHA3-384", "SHA3-512",
            "SHA3-512", "BLAKE2b512", "BLAKE2b512", "BLAKE2s256", "BLAKE2s256", "MD4", "MD4", "RIPEMD160", "RIPEMD160",
            "SM3", "SM3", "whirlpool", "Whirlpool" };

    /**
     * Fills a map with the names of all algorithms in OpenSSL-JNA.
     *
     * @return mapping from algorithm name to class name.
     */
    private static Map<String, String> getOpenSSLHashnames(Set<String> availableOpenSslAlgos) {
        Map<String, String> map = new HashMap<>();

        for (int i = 0; i < SSL_TO_JAVA_NAMES.length; i += 2) {
            String sslName = SSL_TO_JAVA_NAMES[i];
            String javaName = SSL_TO_JAVA_NAMES[i + 1];

            // only if OpenSSL has the algorithm available, add it
            if (availableOpenSslAlgos.contains(sslName)) {
                String javaClass = MessageDigest.class.getName() + "$"
                        + (javaName.replaceAll("-", "_").replaceAll("/", "_"));
                map.put("MessageDigest." + javaName, javaClass);
            }
        }

        return map;
    }
}
