package util;

import java.util.UUID;

public class UuidUtil {

    private UuidUtil() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String generateWithoutDashes() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
