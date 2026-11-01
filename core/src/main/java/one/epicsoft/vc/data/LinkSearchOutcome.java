package one.epicsoft.vc.data;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.enums.LinkOrigin;
import java.util.List;
import java.util.Map;

/** An origin missing from {@code found} failed or was not asked, and keeps its stored links. */
public record LinkSearchOutcome(@Nonnull Map<LinkOrigin, List<FoundLink>> found, @Nonnull List<String> failures) {
}
