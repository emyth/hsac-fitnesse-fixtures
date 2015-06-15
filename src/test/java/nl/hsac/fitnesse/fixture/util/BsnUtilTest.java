package nl.hsac.fitnesse.fixture.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests BsnUtil.
 */
public class BsnUtilTest {
    private final BsnUtil generator = new BsnUtil();

    /**
     * Tests basic generation.
     */
    @Test
    public void testGenerate() {
        for (int i = 0; i < 100; i++) {
            String result = generator.generateBsn();
            assertEquals("Got: " + result, 10, result.length());
            assertTrue("Got: " + result, generator.testBsn(result));
        }
    }
}
