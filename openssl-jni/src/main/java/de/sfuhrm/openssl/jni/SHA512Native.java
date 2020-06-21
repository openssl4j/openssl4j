package de.sfuhrm.openssl.jni;

import java.nio.ByteBuffer;

/**
 * SHA-512 message digest adapter to the OpenSSL SHA-512 functions.
 * @author Stephan Fuhrmann
 */
public class SHA512Native extends AbstractNative {
    protected native void nativeInit(ByteBuffer context);
}
