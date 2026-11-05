package ru.rutcampustrack.schedule.constructor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** Constructor canonical JSON v1; shared wire semantics, not Java/BSON numeric representation. */
public final class ConstructorHashes {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ConstructorHashes() {}

    public static byte[] json(Object value) {
        return sha256(canonical(JSON.valueToTree(value)).getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    public static String canonical(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) {
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            var result = new StringBuilder("{");
            for (String name : names) {
                if (result.length() > 1) result.append(',');
                result.append(quote(name)).append(':').append(canonical(node.get(name)));
            }
            return result.append('}').toString();
        }
        if (node.isArray()) {
            var result = new StringBuilder("[");
            for (JsonNode value : node) {
                if (result.length() > 1) result.append(',');
                result.append(canonical(value));
            }
            return result.append(']').toString();
        }
        if (node.isNumber()) {
            if (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue()))
                throw new IllegalArgumentException("Non-finite constructor number");
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        if (node.isBoolean()) return node.booleanValue() ? "true" : "false";
        if (node.isTextual()) return quote(node.textValue());
        throw new IllegalArgumentException("Unsupported constructor JSON value");
    }

    private static String quote(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception impossible) {
            throw new IllegalArgumentException("Invalid constructor string", impossible);
        }
    }
}
