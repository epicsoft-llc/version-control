package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.enums.DebianVersionDisplay;

import static java.util.Objects.nonNull;

/**
 * How a version is written for a reader.
 *
 * <p>Recognised by its form rather than by the component it belongs to: an epoch or a distribution only ever turns up
 * in a Debian package version, and image tags cannot carry either character. Everything else is written as it is.
 */
public final class VersionLabel {

  private VersionLabel() {
  }

  @Nonnull
  public static String of(@Nonnull final String version, @Nonnull final DebianVersionDisplay display) {
    if (DebianVersionDisplay.SHORT.equals(display)) {
      final DebianVersion debian = DebianVersion.parse(version);
      if (nonNull(debian)) {
        return debian.shortForm();
      }
    }
    return version;
  }
}
