package com.zosh.config;

public class JwtConstant {
  public static final String SECRET_KEY = requireSecret();
  public static final String JWT_HEADER = "Authorization";

  private static String requireSecret() {
    String value = System.getenv("JWT_SECRET");
    if (value == null
        || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32
        || value.startsWith("replace-with"))
      throw new IllegalStateException(
          "Set JWT_SECRET to a random value of at least 32 bytes (see .env.example)");
    return value;
  }
}
