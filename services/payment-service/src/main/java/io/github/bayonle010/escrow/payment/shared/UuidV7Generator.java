package io.github.bayonle010.escrow.payment.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public final class UuidV7Generator {

    private static final long TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL;
    private static final long RANDOM_12_BIT_MASK = 0xFFFL;
    private static final long RANDOM_62_BIT_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final long VERSION_7 = 0x7000L;
    private static final long RFC_4122_VARIANT = 0x8000_0000_0000_0000L;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UuidV7Generator(Clock clock) {
        this.clock = clock;
    }

    public UUID generate() {
        long timestamp = clock.millis() & TIMESTAMP_MASK;
        long mostSignificantBits = (timestamp << 16)
                | VERSION_7
                | (random.nextLong() & RANDOM_12_BIT_MASK);
        long leastSignificantBits = RFC_4122_VARIANT
                | (random.nextLong() & RANDOM_62_BIT_MASK);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
