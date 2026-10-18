package com.example.samgyupsalorderingsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetMealTest {

    @Test
    public void testSetMealAInclusions() {
        SetMeal setA = SetMeal.SET_A;
        assertEquals("SET MEAL A", setA.getCode());
        assertFalse("Set A should not include cheese", setA.hasCheese());
        assertFalse("Set A should not include beef", setA.hasBeef());
    }

    @Test
    public void testSetMealBInclusions() {
        SetMeal setB = SetMeal.SET_B;
        assertEquals("SET MEAL B", setB.getCode());
        assertTrue("Set B should include cheese", setB.hasCheese());
        assertFalse("Set B should not include beef", setB.hasBeef());
    }

    @Test
    public void testSetMealCInclusions() {
        SetMeal setC = SetMeal.SET_C;
        assertEquals("SET MEAL C", setC.getCode());
        assertTrue("Set C should include cheese", setC.hasCheese());
        assertTrue("Set C should include beef", setC.hasBeef());
    }

    @Test
    public void testFromStringParsing() {
        assertEquals(SetMeal.SET_A, SetMeal.fromString("SET_A"));
        assertEquals(SetMeal.SET_A, SetMeal.fromString("SET MEAL A"));
        assertEquals(SetMeal.SET_B, SetMeal.fromString("SET_B"));
        assertEquals(SetMeal.SET_B, SetMeal.fromString("SET MEAL B"));
        assertEquals(SetMeal.SET_C, SetMeal.fromString("SET_C"));
        assertEquals(SetMeal.SET_C, SetMeal.fromString("SET MEAL C"));
        assertEquals(SetMeal.SET_A, SetMeal.fromString(null));
        assertEquals(SetMeal.SET_A, SetMeal.fromString("INVALID"));
    }

    @Test
    public void testDowngradeRules() {
        // Set A is a downgrade from Set B and Set C
        assertTrue(SetMeal.SET_A.isDowngradeFrom(SetMeal.SET_B));
        assertTrue(SetMeal.SET_A.isDowngradeFrom(SetMeal.SET_C));

        // Set B is a downgrade from Set C
        assertTrue(SetMeal.SET_B.isDowngradeFrom(SetMeal.SET_C));

        // Upgrades are NOT downgrades
        assertFalse(SetMeal.SET_B.isDowngradeFrom(SetMeal.SET_A));
        assertFalse(SetMeal.SET_C.isDowngradeFrom(SetMeal.SET_A));
        assertFalse(SetMeal.SET_C.isDowngradeFrom(SetMeal.SET_B));

        // Same set is NOT a downgrade
        assertFalse(SetMeal.SET_A.isDowngradeFrom(SetMeal.SET_A));
        assertFalse(SetMeal.SET_B.isDowngradeFrom(SetMeal.SET_B));
        assertFalse(SetMeal.SET_C.isDowngradeFrom(SetMeal.SET_C));
    }

    @Test
    public void testInitialStarterItems() {
        java.util.List<String> itemsA = SetMeal.SET_A.getInitialStarterItems(2);
        assertTrue(itemsA.contains("2x Plain Pork Belly (Samgyupsal)"));
        assertTrue(itemsA.contains("2x Spicy Pork (Yangnyeom Bulgogi)"));
        assertTrue(itemsA.contains("2x Sweet Soy Marinated Pork"));
        assertFalse(itemsA.contains("1x Cheese Dip"));

        java.util.List<String> itemsB = SetMeal.SET_B.getInitialStarterItems(2);
        assertTrue(itemsB.contains("1x Cheese Dip"));
        assertFalse(itemsB.contains("2x Plain Beef Samgyup (Woosamgyup)"));

        java.util.List<String> itemsC = SetMeal.SET_C.getInitialStarterItems(3);
        assertTrue(itemsC.contains("3x Plain Beef Samgyup (Woosamgyup)"));
        assertTrue(itemsC.contains("3x Sweet Marinated Beef (Bulgogi)"));
        assertTrue(itemsC.contains("1x Cheese Dip"));
    }
}
