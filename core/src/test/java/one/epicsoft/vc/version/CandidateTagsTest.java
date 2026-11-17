package one.epicsoft.vc.version;

import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a check keeps of the versions its source offered. */
class CandidateTagsTest {

  @Test
  void keepsTheLeadingPackageEvenWhenTheIndexListsOtherPackagesFirst() {
    // An APT index orders by package name: hundreds of versions of a package the component does not use come first.
    final List<VersionCandidate> index = new ArrayList<>();
    IntStream.range(0, 250).forEach(i -> index.add(VersionCandidate.ofPackage("cagent", "0.%d.0-1~ubuntu.24.04~noble".formatted(i))));
    index.add(VersionCandidate.ofPackage("containerd.io", "2.4.2-4~ubuntu.24.04~noble"));
    index.add(VersionCandidate.ofPackage("docker-ce", "5:29.9.3-1~ubuntu.24.04~noble"));
    index.add(VersionCandidate.ofPackage("docker-ce", "5:29.10.0-1~ubuntu.24.04~noble"));

    final List<VersionCandidate> stored = CandidateTags.parse(
        CandidateTags.serialize(index, List.of(this.entry("containerd.io", 2, false), this.entry("docker-ce", 1, true))));

    assertEquals(List.of("5:29.10.0-1~ubuntu.24.04~noble", "5:29.9.3-1~ubuntu.24.04~noble", "2.4.2-4~ubuntu.24.04~noble"),
                 stored.stream().map(VersionCandidate::tag).toList());
    assertEquals("docker-ce", stored.getFirst().packageName());
  }

  @Test
  void keepsTheNewestVersionsOfEveryPackageWithinTheLimit() {
    final List<VersionCandidate> index = new ArrayList<>();
    IntStream.range(0, 150).forEach(i -> index.add(VersionCandidate.ofPackage("docker-ce", "5:29.%d.0-1~ubuntu.24.04~noble".formatted(i))));
    IntStream.range(0, 150).forEach(i -> index.add(VersionCandidate.ofPackage("containerd.io", "2.%d.0-1~ubuntu.24.04~noble".formatted(i))));

    final List<VersionCandidate> stored = CandidateTags.parse(
        CandidateTags.serialize(index, List.of(this.entry("docker-ce", 1, true), this.entry("containerd.io", 2, false))));

    assertEquals(200, stored.size());
    assertEquals("5:29.149.0-1~ubuntu.24.04~noble", stored.getFirst().tag());
    assertTrue(stored.stream().anyMatch(candidate -> "2.149.0-1~ubuntu.24.04~noble".equals(candidate.tag())));
  }

  @Test
  void keepsTheOrderOfTheSourceForAComponentWithoutPackages() {
    final List<VersionCandidate> tags = List.of(VersionCandidate.of("17.2"), VersionCandidate.of("17.4"));

    assertEquals(List.of("17.2", "17.4"),
                 CandidateTags.parse(CandidateTags.serialize(tags, List.of())).stream().map(VersionCandidate::tag).toList());
  }

  private ComponentPackageEntity entry(final String name, final int sortOrder, final boolean leading) {
    final ComponentPackageEntity entry = new ComponentPackageEntity();
    entry.setName(name);
    entry.setSortOrder(sortOrder);
    entry.setLeadingPackage(leading);
    return entry;
  }
}
