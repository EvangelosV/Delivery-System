package com.example.customerapp.model;

public class StoreItem {
    private final String title;
    private final String category;
    private final String priceRange; // $, $$ or $$$
    private final int stars; // 0‑5
    private final String logoPath; // Add this new field

    public StoreItem(String title, String category, String priceRange, int stars, String logoPath) {
        this.title = title;
        this.category = category;
        this.priceRange = priceRange;
        this.stars = stars;
        this.logoPath = logoPath;
    }

    public String getTitle()     { return title;     }
    public String getCategory()  { return category;  }
    public String getPriceRange(){ return priceRange;}
    public int    getStars()     { return stars;     }
    public String getLogoPath()  { return logoPath;  } // Add this new getter
}