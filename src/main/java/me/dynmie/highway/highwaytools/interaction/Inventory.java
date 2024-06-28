package me.dynmie.highway.highwaytools.interaction;

/**
 * @author dynmie
 */
public class Inventory {

    private static int waitTicks = 0;

    public static int getWaitTicks() {
        return waitTicks;
    }

    public static void setWaitTicks(int waitTicks) {
        Inventory.waitTicks = waitTicks;
    }

    public static void increaseWaitTicks(int ticks) {
        waitTicks += ticks;
    }

    public static void decreaseWaitTicks(int ticks) {
        waitTicks -= ticks;
    }

}
