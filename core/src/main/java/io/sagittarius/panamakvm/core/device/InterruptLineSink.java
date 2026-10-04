package io.sagittarius.panamakvm.core.device;

/** Receives changes to a legacy interrupt line level. */
@FunctionalInterface
public interface InterruptLineSink {
    /**
     * Sets whether a legacy IRQ/GSI line is asserted.
     *
     * @param interrupt legacy IRQ/GSI number
     * @param asserted whether the line is asserted
     */
    void setLevel(int interrupt, boolean asserted);
}
