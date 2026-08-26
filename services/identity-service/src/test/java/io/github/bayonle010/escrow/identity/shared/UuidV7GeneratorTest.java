package io.github.bayonle010.escrow.identity.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generatesUniqueRfc4122VersionSevenIdentifiers() {
        UuidV7Generator generator = new UuidV7Generator(Clock.systemUTC());
        Set<UUID> identifiers = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            UUID identifier = generator.generate();
            identifiers.add(identifier);
            assertThat(identifier.version()).isEqualTo(7);
            assertThat(identifier.variant()).isEqualTo(2);
        }

        assertThat(identifiers).hasSize(1_000);
    }
}
