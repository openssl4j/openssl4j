package de.sfuhrm.openssl.jni;

import java.nio.ByteBuffer;

/**
 * SHA-384 message digest adapter to the OpenSSL SHA-384 functions.
 * @author Stephan Fuhrmann
 */
public class SHA384Native extends OpenSSLMessageDigestNative {
    public SHA384Native() {
        super("SHA384");
    }
}
