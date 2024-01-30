package de.sfuhrm.openssl4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;

/**
 * Test base.
 *
 * @author Stephan Fuhrmann
 */
public class BaseTest {

  Formatter formatter;
  Charset ascii;

  @BeforeEach
  public void before() throws IOException {
    NativeLoader.loadAll();

    formatter = Formatter.getInstance();
    ascii = StandardCharsets.US_ASCII;
  }
}
