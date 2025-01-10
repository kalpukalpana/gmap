package org.meicode.gmap;

import com.google.android.gms.maps.model.LatLng;

public class Shop {
    private String name;
    private LatLng location;
    private String address;
    private String type;

    public Shop(String name, LatLng location, String address, String type) {
        this.name = name;
        this.location = location;
        this.address = address;
        this.type = type;
    }

    public String getName() { return name; }
    public LatLng getLocation() { return location; }
    public String getAddress() { return address; }
    public String getType() { return type; }
} 