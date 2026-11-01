package one.epicsoft.vc.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The parts of {@code pkg:type/namespace/name@version?qualifiers#subpath} the link search reads. */
public record PackageUrl(@Nonnull String type, @Nonnull List<String> segments, @Nonnull Map<String, String> qualifiers) {

  @Nullable
  public static PackageUrl parse(@Nonnull final String purl) {
    final String value = purl.trim();
    if (!value.regionMatches(true, 0, "pkg:", 0, 4)) {
      return null;
    }
    String rest = value.substring(4).replaceFirst("#.*$", "");
    final Map<String, String> qualifiers = new LinkedHashMap<>();
    final int query = rest.indexOf('?');
    if (query >= 0) {
      Arrays.stream(rest.substring(query + 1).split("&"))
            .map(pair -> pair.split("=", 2))
            .filter(pair -> pair.length == 2)
            .forEach(pair -> qualifiers.put(pair[0].toLowerCase(Locale.ROOT), decode(pair[1])));
      rest = rest.substring(0, query);
    }
    rest = rest.replaceFirst("@[^/]*$", "");
    final List<String> parts = Arrays.stream(rest.split("/")).filter(part -> !part.isBlank()).map(PackageUrl::decode).toList();
    return parts.size() < 2 ? null : new PackageUrl(parts.getFirst().toLowerCase(Locale.ROOT), parts.subList(1, parts.size()), qualifiers);
  }

  public boolean hasNamespace() {
    return this.segments.size() >= 2;
  }

  @Nonnull
  public String path() {
    return String.join("/", this.segments);
  }

  @Nonnull
  private static String decode(@Nonnull final String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
