package io.github.rfc2616.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public final class HttpStatus {

    static Map<Integer, String> statusMap = new HashMap<>();

    static {
        final InputStream inputStream = HttpStatus.class.getResourceAsStream("/http_status");

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = null;

            while ((line = reader.readLine()) != null) {
                final int statusCode = Integer.parseInt(line.substring(0, 3));
                final String description = line.substring(3).trim();

                statusMap.put(statusCode, description);
            }

        } catch (IOException e) { /***/ }

    }
    
    private final int code;
    private final String description;

    private HttpStatus(final int code, final String description) {
        this.code = code;
        this.description = description;
    }

    public static HttpStatus valueOf(final int code) {
        final String description = statusMap.get(code);
        if(description == null) {
            return null;
        }

        return new HttpStatus(code, description);
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
    
}
