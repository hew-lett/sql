package main.app;



public class SummaryType {
    private final Frequency frequency;
    private final Calculation calculation;

    public SummaryType(Frequency frequency, Calculation calculation) {
        this.frequency = frequency;
        this.calculation = calculation;
    }
    public Frequency getFrequency() {
        return this.frequency;
    }
    public Calculation getCalculation() {
        return this.calculation;
    }
    public enum Frequency {
        MONTHLY, YEARLY, TOTAL
    }

    public enum Calculation {
        CHARGE, FREQ
    }
    // Getters, equals, hashCode, and other methods as required...
}
