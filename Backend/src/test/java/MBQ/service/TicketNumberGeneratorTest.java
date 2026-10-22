package com.MBQ.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TicketNumberGeneratorTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "OPD,               OPD",
            "Emergency,         EME",
            "Maternity,         MAT",
            "ENT,               ENT",
            "Ob,                OB",
            "'  x-ray 2 ',      XRA",
            "'123',             DEP"
    })
    void prefixFor(String departmentName, String expected) {
        assertThat(TicketNumberGenerator.prefixFor(departmentName)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} #{1} -> {2}")
    @CsvSource({
            "OPD, 1,    OPD-001",
            "OPD, 42,   OPD-042",
            "EME, 999,  EME-999",
            "EME, 1234, EME-1234"
    })
    void format(String prefix, long sequence, String expected) {
        assertThat(TicketNumberGenerator.format(prefix, sequence)).isEqualTo(expected);
    }
}
