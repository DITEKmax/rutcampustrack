package ru.rutcampustrack.gateway.config;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicKeyConfigTest {

    // Test 1: parsePemPublicKey correctly parses a valid PEM-encoded RSA public key
    @Test
    void parsePemPublicKey_validPem_returnsPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        byte[] encoded = keyPair.getPublic().getEncoded();
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";

        PublicKeyConfig config = new PublicKeyConfig();
        var result = config.parsePemPublicKey(pem);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(keyPair.getPublic());
    }

    // Test 2: getPublicKey throws IllegalStateException when no key has been loaded
    @Test
    void getPublicKey_whenKeyNotLoaded_throwsIllegalStateException() {
        PublicKeyConfig config = new PublicKeyConfig();

        assertThatThrownBy(config::getPublicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Public key not yet loaded");
    }
}
