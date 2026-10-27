package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.data.CpeRange;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.isBlank;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/** A recorded version read against the CPE dictionary: {@code 17.6} from {@code 17.6-alpine}, {@code 29.9.2} from {@code 5:29.9.2-1~ubuntu.24.04~noble}. */
public final class CpeVersion {

  /** An epoch, a leading v, then digits and dots — the product's version, without variant, revision or build. */
  private static final Pattern COMPARABLE = Pattern.compile("^(?:\\d+:)?[vV]?(\\d+(?:\\.\\d+)*)");

  private static final Pattern TOKEN = Pattern.compile("\\d+|[A-Za-z]+");

  private CpeVersion() {
  }

  /** Null for a version without a number, such as a moving tag or a digest. */
  @Nullable
  public static String normalize(@Nonnull final String recorded) {
    final Matcher matcher = COMPARABLE.matcher(recorded.trim());
    return matcher.find() ? matcher.group(1) : null;
  }

  /** An entry with neither version nor bounds would claim every version ever released and is left out. */
  public static boolean affects(@Nonnull final CpeRange range, @Nonnull final String version) {
    if (isSpecific(range.version())) {
      return compare(version, range.version()) == 0;
    }
    if (isBlank(range.startIncluding()) && isBlank(range.startExcluding())
        && isBlank(range.endIncluding()) && isBlank(range.endExcluding())) {
      return false;
    }
    return (isBlank(range.startIncluding()) || compare(version, range.startIncluding()) >= 0)
           && (isBlank(range.startExcluding()) || compare(version, range.startExcluding()) > 0)
           && (isBlank(range.endIncluding()) || compare(version, range.endIncluding()) <= 0)
           && (isBlank(range.endExcluding()) || compare(version, range.endExcluding()) < 0);
  }

  /** A missing number counts as zero, {@code 17.6} = {@code 17.6.0}; letters make a pre-release, {@code 3.0.0-rc1} before {@code 3.0.0}. */
  public static int compare(@Nonnull final String first, @Nonnull final String second) {
    final List<String> left = tokens(first);
    final List<String> right = tokens(second);
    for (int index = 0; index < Math.max(left.size(), right.size()); index++) {
      final String leftToken = index < left.size() ? left.get(index) : null;
      final String rightToken = index < right.size() ? right.get(index) : null;
      final int order = compareTokens(leftToken, rightToken);
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }

  private static int compareTokens(@Nullable final String left, @Nullable final String right) {
    if (isNumber(left) && isNumber(right)) {
      return new BigInteger(left).compareTo(new BigInteger(right));
    }
    // A number against nothing counts as zero; letters against nothing are a pre-release and come first.
    if (isNull(left)) {
      return isNumber(right) ? BigInteger.ZERO.compareTo(new BigInteger(right)) : 1;
    }
    if (isNull(right)) {
      return isNumber(left) ? new BigInteger(left).compareTo(BigInteger.ZERO) : -1;
    }
    if (isNumber(left) != isNumber(right)) {
      // 1.2 is a release and 1.rc2 a candidate before it.
      return isNumber(left) ? 1 : -1;
    }
    return left.compareToIgnoreCase(right);
  }

  private static boolean isNumber(@Nullable final String token) {
    return nonBlank(token) && Character.isDigit(token.charAt(0));
  }

  private static boolean isSpecific(@Nullable final String version) {
    return nonBlank(version) && !"*".equals(version) && !"-".equals(version);
  }

  @Nonnull
  private static List<String> tokens(@Nonnull final String version) {
    final List<String> tokens = new ArrayList<>();
    final Matcher matcher = TOKEN.matcher(version);
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return tokens;
  }
}
