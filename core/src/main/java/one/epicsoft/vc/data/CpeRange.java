package one.epicsoft.vc.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/** A single version, or a range of up to two bounds; {@code version} is {@code *} or {@code -} where a range applies. */
public record CpeRange(@Nonnull String cve,
                       @Nullable String version,
                       @Nullable String startIncluding,
                       @Nullable String startExcluding,
                       @Nullable String endIncluding,
                       @Nullable String endExcluding,
                       @Nullable BigDecimal score,
                       @Nullable String severity,
                       boolean knownExploited,
                       @Nullable String summary) {
}
