package one.epicsoft.vc.data;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.enums.SourceType;
import java.util.UUID;

public record LinkSearchDraft(@Nonnull UUID componentId,
                              @Nonnull SourceType sourceType,
                              @Nullable String sourceRef,
                              @Nullable String eolProduct) {
}
