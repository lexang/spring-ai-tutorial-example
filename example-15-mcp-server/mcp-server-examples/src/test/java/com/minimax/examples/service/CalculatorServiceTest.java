package com.minimax.examples.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorServiceTest {

    @Test
    void rejectsNonFiniteInputsAndOverflow() {
        assertThatThrownBy(() -> new CalculatorService().basicCalculation(Double.NaN, 1, "+"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculatorService().basicCalculation(Double.MAX_VALUE, Double.MAX_VALUE, "+"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculatorService().percentageCalculation(1, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private final CalculatorService calculator = new CalculatorService();

    @Test
    void calculatesBasicOperations() {
        assertThat(this.calculator.basicCalculation(8, 2, "+").result()).isEqualTo(10);
        assertThat(this.calculator.basicCalculation(8, 2, "/").result()).isEqualTo(4);
    }

    @Test
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> this.calculator.basicCalculation(8, 0, "/"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculatesScientificValues() {
        assertThat(this.calculator.scientificCalculation("sqrt", 16).result()).isEqualTo(4);
        assertThat(this.calculator.scientificCalculation("log", 100).result()).isEqualTo(2);
    }

    @Test
    void onlyAcceptsWhitelistedComplexFormulas() {
        assertThat(this.calculator.complexCalculation(2, 3, 4, "(a+b)*c").result()).isEqualTo(20);
        assertThatThrownBy(() -> this.calculator.complexCalculation(1, 2, 3, "a; System.exit(0)"))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
