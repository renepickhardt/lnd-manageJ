package de.cotto.lndmanagej.balances.persistence;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BalancesIdTest {
    @Test
    void test_default_constructor() {
        // required for JPA
        assertThat(new BalancesId()).isNotNull();
    }

    @Test
    void testEquals() {
        EqualsVerifier.simple().forClass(BalancesId.class).usingGetClass().verify();
    }
}
