package one.epicsoft.vc.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;

/** {@code dataAsOf}: when the database last took in the NVD, null where it does not say. */
public record DepcheckLookup(@Nonnull List<CpeRange> ranges, @Nullable Instant dataAsOf) {
}
