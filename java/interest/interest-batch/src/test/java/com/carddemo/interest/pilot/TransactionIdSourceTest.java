package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransactionIdSourceTest {

    @Test
    void INT_R08_i01_idIsParmFollowedBySixDigitSuffix() {
        TransactionIdSource ids = new TransactionIdSource("2022071800");
        assertThat(ids.idFor(1)).isEqualTo("2022071800000001");
        assertThat(ids.idFor(2)).isEqualTo("2022071800000002");
    }

    @Test
    void INT_H05_suffixWrapsAfter999999WithoutDroppingRows() {
        TransactionIdSource ids = new TransactionIdSource("2022071800");
        assertThat(ids.idFor(999_999)).isEqualTo("2022071800999999");
        assertThat(ids.idFor(1_000_000)).isEqualTo("2022071800000000");
    }

    @Test
    void INT_H02_parmIsOpaqueTextNotADate() {
        assertThat(new TransactionIdSource("ABCDEFGHIJ").idFor(7)).isEqualTo("ABCDEFGHIJ000007");
    }

    @Test
    void INT_H02_parmShorterThanPicIsHeld() {
        assertThatThrownBy(() -> new TransactionIdSource("20220718"))
                .isInstanceOf(PilotFailure.UnprovenHostOutcome.class);
    }
}
