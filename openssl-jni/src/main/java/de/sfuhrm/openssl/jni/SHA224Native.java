package de.sfuhrm.openssl.jni;

import java.nio.ByteBuffer;

/**
 * SHA-224 message digest adapter to the OpenSSL SHA-224 functions.
 * @author Stephan Fuhrmann
 */
public class SHA224Native extends AbstractNative {

    @Override
    protected int digestLength() {
        return 28;
    }

    protected native void nativeInit(ByteBuffer context);
}
