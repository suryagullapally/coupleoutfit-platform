package com.coupleoutfit.analysis.domain;

import java.util.*;

public final class ClothingVocabulary {
    private ClothingVocabulary() {}
    public static final Set<String> COLORS=Set.of("BLACK","WHITE","GREY","NAVY","BLUE","LIGHT_BLUE","RED","BURGUNDY","PINK","PURPLE","GREEN","OLIVE","YELLOW","ORANGE","BROWN","BEIGE","CREAM","GOLD","SILVER","MULTICOLOUR","UNKNOWN");
    public static final Set<String> MATERIALS=Set.of("DENIM","SATIN_LIKE","LEATHER_LIKE","KNIT","COTTON_LIKE","LINEN_LIKE","WOOL_LIKE","SHEER","VELVET_LIKE","SEQUINED","SYNTHETIC_LIKE","UNKNOWN");
    public static final Set<String> SEASONS=Set.of("SPRING","SUMMER","AUTUMN","WINTER");
    public static final Set<String> STYLES=Set.of("CLASSIC","MINIMAL","ELEGANT","EVENING","SPORTY","STREETWEAR","BOHEMIAN","ROMANTIC","EDGY","PREPPY","TAILORED","RELAXED","VINTAGE","CONTEMPORARY","UTILITY","FESTIVE","UNKNOWN");
    public static final Set<String> DETAILS=Set.of("MIDI","MAXI","MINI","SLEEVELESS","SHORT_SLEEVE","LONG_SLEEVE","COLLARED","V_NECK","CREW_NECK","HIGH_NECK","BUTTONED","ZIPPER","PLEATED","RUFFLED","LACE","EMBROIDERED","BELTED","LAYERED","MULTI_GARMENT","POCKETS","HOODED","CROPPED","WIDE_LEG","STRAIGHT_LEG","TAPERED","UNKNOWN");
    public static final Set<String> SUBCATEGORIES=Set.of("MIDI_DRESS","MAXI_DRESS","MINI_DRESS","SHIRT","T_SHIRT","BLOUSE","TROUSERS","JEANS","SUIT","JACKET","COAT","KNITWEAR","FOOTWEAR","SKIRT","SHORTS","JUMPSUIT","OTHER");
    static List<String> tokens(List<String> values, Set<String> allowed, int max) {
        if(values==null || values.size()>max || !allowed.containsAll(values) || new HashSet<>(values).size()!=values.size()) {
            throw new IllegalArgumentException("Invalid clothing tokens.");
        }
        return List.copyOf(values);
    }
}
