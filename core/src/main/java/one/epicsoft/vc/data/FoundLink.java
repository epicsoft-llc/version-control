package one.epicsoft.vc.data;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.enums.LinkKind;

public record FoundLink(@Nonnull String url, @Nonnull LinkKind kind) {
}
