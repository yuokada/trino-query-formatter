package io.github.yuokada.core.util;

/**
 * Minimal JSON utility focused on string escaping for safe embedding into JSON.
 */
public final class JsonUtil {

  private JsonUtil() {
  }

  /**
   * Escapes a Java string for JSON string literal context.
   * Converts control characters and quotes/backslashes to their escaped forms.
   *
   * @param s input string (nullable)
   * @return escaped string (never null)
   */
  public static String escape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
      }
    }
    return out.toString();
  }
}

