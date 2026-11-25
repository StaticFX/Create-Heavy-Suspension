package de.staticfx.heavysuspension.data;

public record VehicleLoadDistribution(
        /**
         * each of the entries show how much percentage load of the total load sits on that wheel
         */
        double[] loadFractions,
        /**
         * global spring scale
         */
        double springScale) {
}
