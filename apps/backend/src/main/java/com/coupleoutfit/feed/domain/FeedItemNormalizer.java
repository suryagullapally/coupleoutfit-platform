package com.coupleoutfit.feed.domain;

import com.coupleoutfit.affiliate.domain.FeedField;
import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.catalogue.domain.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/** Deterministic generic CSV mappings; unknown fashion attributes remain absent. */
public final class FeedItemNormalizer {
    public NormalizedAffiliateItem normalize(RawAffiliateFeedRow row,FeedMarket market) {
        if(!row.validShape())throw new FeedException("FEED_ITEM_INVALID");
        var v=row.values();
        String id=text(v,FeedField.RETAILER_PRODUCT_ID,200,true),title=text(v,FeedField.TITLE,300,true),brand=text(v,FeedField.BRAND,160,false);
        String currency=value(v,FeedField.CURRENCY).toUpperCase(java.util.Locale.ROOT);
        if(!currency.isEmpty() && !currency.equals(market.context().currencyCode()))throw new FeedException("FEED_CURRENCY_MISMATCH");
        Money price=null;String amount=value(v,FeedField.PRICE);
        if(!amount.isEmpty()) {
            if(!currency.equals(market.context().currencyCode()))throw new FeedException("FEED_CURRENCY_MISMATCH");
            try {
                if(!amount.matches("[0-9]+(?:\\.[0-9]+)?"))throw new NumberFormatException();
                price=new Money(new BigDecimal(amount).movePointRight(market.minorUnitDigits()).longValueExact(),currency);
            }catch(IllegalArgumentException | ArithmeticException e){throw new FeedException("FEED_PRICE_INVALID");}
        }
        ProductTarget target=switch(token(value(v,FeedField.TARGET))) {
            case "MEN","MENS","MEN'S","MALE","MENSWEAR"->ProductTarget.MENSWEAR;
            case "WOMEN","WOMENS","WOMEN'S","FEMALE","WOMENSWEAR"->ProductTarget.WOMENSWEAR;
            default->throw new FeedException("FEED_TARGET_UNSUPPORTED");
        };
        String category=token(value(v,FeedField.CATEGORY));
        category=switch(category) {
            case "DRESSES"->"DRESS";case "SHIRTS"->"SHIRT";case "T_SHIRTS","TSHIRT","TSHIRTS"->"T_SHIRT";
            case "TOPS"->"TOP";case "JACKETS"->"JACKET";case "COATS"->"COAT";case "SKIRTS"->"SKIRT";
            case "SUITS"->"SUIT";case "SHOES"->"FOOTWEAR";case "JUMPSUITS"->"JUMPSUIT";case "ACCESSORIES"->"ACCESSORY";default->category;
        };
        ProductCategory normalizedCategory;
        try {normalizedCategory=ProductCategory.valueOf(category);if(normalizedCategory==ProductCategory.OTHER)throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){throw new FeedException("FEED_CATEGORY_UNSUPPORTED");}
        var attributes=new LinkedHashMap<String,List<String>>();
        attribute(attributes,"primaryColors",value(v,FeedField.COLOR),ClothingVocabulary.COLORS,Map.of("NAVY_BLUE","NAVY","GRAY","GREY"));
        attribute(attributes,"pattern",value(v,FeedField.PATTERN),names(Pattern.values()),Map.of("PLAIN","SOLID","STRIPES","STRIPED"));
        attribute(attributes,"materialAppearance",value(v,FeedField.MATERIAL),ClothingVocabulary.MATERIALS,Map.of("COTTON","COTTON_LIKE","LINEN","LINEN_LIKE","WOOL","WOOL_LIKE","SATIN","SATIN_LIKE","LEATHER","LEATHER_LIKE"));
        attribute(attributes,"fit",value(v,FeedField.FIT),names(Fit.values()),Map.of());
        attribute(attributes,"formality",value(v,FeedField.FORMALITY),names(Formality.values()),Map.of());
        attribute(attributes,"seasonality",value(v,FeedField.SEASONALITY),ClothingVocabulary.SEASONS,Map.of("FALL","AUTUMN"));
        attribute(attributes,"styleTags",value(v,FeedField.STYLE_TAGS),ClothingVocabulary.STYLES,Map.of());
        AvailabilityStatus availability=switch(token(value(v,FeedField.AVAILABILITY))) {
            case "IN_STOCK","AVAILABLE","INSTOCK"->AvailabilityStatus.IN_STOCK;
            case "OUT_OF_STOCK","UNAVAILABLE","OUTOFSTOCK","SOLD_OUT"->AvailabilityStatus.OUT_OF_STOCK;
            default->AvailabilityStatus.UNKNOWN;
        };
        return new NormalizedAffiliateItem(id,title,brand,normalizedCategory,target,new ProductAttributes(attributes),
                url(value(v,FeedField.PRODUCT_URL),true,market.domain()),url(value(v,FeedField.AFFILIATE_URL),false,null),
                url(value(v,FeedField.IMAGE_URL),false,null),price,availability);
    }
    private static String value(Map<FeedField,String> values,FeedField field){return values.getOrDefault(field,"").strip();}
    private static String text(Map<FeedField,String> values,FeedField field,int max,boolean required) {
        String value=value(values,field);if(value.isEmpty()){if(required)throw new FeedException("FEED_ITEM_INVALID");return null;}
        if(value.length()>max || value.chars().anyMatch(Character::isISOControl))throw new FeedException("FEED_ITEM_INVALID");return value;
    }
    private static String token(String value){return value.strip().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');}
    private static Set<String> names(Enum<?>[] values){var result=new HashSet<String>();for(var value:values)result.add(value.name());return result;}
    private static void attribute(Map<String,List<String>> out,String field,String raw,Set<String> known,Map<String,String> aliases) {
        var values=new TreeSet<String>();
        for(String part:raw.split("[/|;,]")) {String value=token(part);value=aliases.getOrDefault(value,value);if(known.contains(value) && !value.equals("UNKNOWN"))values.add(value);}
        if(!values.isEmpty())out.put(field,List.copyOf(values));
    }
    private static String url(String raw,boolean required,String retailerHost) {
        if(raw.isEmpty()){if(required)throw new FeedException("FEED_URL_INVALID");return null;}
        try {
            if(raw.length()>4096)throw new URISyntaxException("","length");
            var uri=new URI(raw);String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if(!Set.of("https","http").contains(scheme) || uri.getHost()==null || uri.getRawUserInfo()!=null)throw new URISyntaxException("","authority");
            String host=uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if(retailerHost!=null && !host.equals(retailerHost) && !host.equals("www."+retailerHost))throw new URISyntaxException("","retailer");
            if(retailerHost!=null && uri.getPort()!=-1 && uri.getPort()!=(scheme.equals("https")?443:80))throw new URISyntaxException("","port");
            // Preserve encoded query/path bytes; never decode tracking data or fetch the URL.
            String port=uri.getPort()==-1?"":":"+uri.getPort();
            return scheme+"://"+host+port+(uri.getRawPath().isEmpty()?"/":uri.normalize().getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
        }catch(URISyntaxException e){throw new FeedException("FEED_URL_INVALID");}
    }
}
