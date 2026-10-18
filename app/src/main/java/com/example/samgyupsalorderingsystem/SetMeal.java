package com.example.samgyupsalorderingsystem;

public enum SetMeal {
    SET_A("SET MEAL A", "Typical Pork", "Contains the typical pork cuts (Pork Belly, Spicy Bulgogi, Banchan, Rice, Lettuce)", false, false),
    SET_B("SET MEAL B", "Typical Pork + Unli Cheese", "Contains the typical pork + Unlimited Cheese Dip", true, false),
    SET_C("SET MEAL C", "Pork & Beef + Unli Cheese", "Contains premium beef brisket & pork cuts + Unlimited Cheese Dip", true, true);

    private final String code;
    private final String title;
    private final String description;
    private final boolean includesCheese;
    private final boolean includesBeef;

    SetMeal(String code, String title, String description, boolean includesCheese, boolean includesBeef) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.includesCheese = includesCheese;
        this.includesBeef = includesBeef;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasCheese() {
        return includesCheese;
    }

    public boolean hasBeef() {
        return includesBeef;
    }

    private static int priceSetA = 499;
    private static int priceSetB = 549;
    private static int priceSetC = 599;

    public static void updatePrices(int a, int b, int c) {
        if (a > 0) priceSetA = a;
        if (b > 0) priceSetB = b;
        if (c > 0) priceSetC = c;
    }

    public static int getPriceSetA() { return priceSetA; }
    public static int getPriceSetB() { return priceSetB; }
    public static int getPriceSetC() { return priceSetC; }

    public int getPricePerPax() {
        switch (this) {
            case SET_A: return priceSetA;
            case SET_B: return priceSetB;
            case SET_C: return priceSetC;
            default: return priceSetA;
        }
    }

    public int getRank() {
        switch (this) {
            case SET_A: return 1;
            case SET_B: return 2;
            case SET_C: return 3;
            default: return 1;
        }
    }

    public boolean isDowngradeFrom(SetMeal other) {
        if (other == null) return false;
        return this.getRank() < other.getRank();
    }

    public java.util.List<String> getInitialStarterItems(int headCount) {
        int pax = Math.max(1, headCount);
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add(pax + "x Plain Pork Belly (Samgyupsal)");
        items.add(pax + "x Spicy Pork (Yangnyeom Bulgogi)");
        items.add(pax + "x Sweet Soy Marinated Pork");
        if (hasBeef()) {
            items.add(pax + "x Plain Beef Samgyup (Woosamgyup)");
            items.add(pax + "x Sweet Marinated Beef (Bulgogi)");
        }
        if (hasCheese()) {
            items.add("1x Cheese Dip");
        }
        items.add("1x Fresh Lettuce Basket (Sangchu)");
        items.add("1x Kimchi");
        items.add(pax + "x Steamed White Rice (Bap)");
        items.add("1x Ssamjang & Sesame Oil Dip");
        return items;
    }

    public static SetMeal fromString(String val) {
        if (val == null) return SET_A;
        for (SetMeal set : values()) {
            if (set.name().equalsIgnoreCase(val) || set.code.equalsIgnoreCase(val)) {
                return set;
            }
        }
        return SET_A;
    }
}
