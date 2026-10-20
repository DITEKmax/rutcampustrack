package ru.rutcampustrack.auth.support;

import org.springframework.data.redis.core.StringRedisTemplate;

import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.service.JwtService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;

/** Synthetic keys loaded through the same public PEM boundary as production. */
public final class TestJwtKeys {
    private TestJwtKeys() {}

    public static JwtService load(KeyPair pair, Path directory, StringRedisTemplate redis)
            throws Exception {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("private.key"),
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(
                directory.resolve("public.key"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        Files.writeString(directory.resolve("kid.txt"), "synthetic-test-kid");
        JwtService service =
                new JwtService(new JwtProperties(directory.toString(), 900, 604800), redis);
        service.init();
        return service;
    }

    private static String pem(String kind, byte[] encoded) {
        return "-----BEGIN "
                + kind
                + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END "
                + kind
                + "-----\n";
    }
}
