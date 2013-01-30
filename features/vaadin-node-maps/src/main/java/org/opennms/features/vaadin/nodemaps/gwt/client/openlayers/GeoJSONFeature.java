package org.opennms.features.vaadin.nodemaps.gwt.client.openlayers;

import java.util.Map;

import com.google.gwt.core.client.JavaScriptObject;

public class GeoJSONFeature extends JavaScriptObject {
    protected GeoJSONFeature() {}
    
    public static GeoJSONFeature create(final Float longitude, final Float latitude, final Map<String,String> stringProperties, final Map<String,Integer> intProperties) {
        final GeoJSONProperties props = GeoJSONProperties.create();
        for (final Map.Entry<String,String> entry : stringProperties.entrySet()) {
            props.setString(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String,Integer> entry : intProperties.entrySet()) {
            props.setInteger(entry.getKey(), entry.getValue());
        }
        return GeoJSONFeature.create(longitude, latitude, props);
    }

    public static native GeoJSONFeature create(final Float longitude, final Float latitude, final JavaScriptObject properties) /*-{
        var props = properties || {};
        return {
            "type": "Feature",
            "properties": props,
            "geometry":{
                "type": "Point",
                "coordinates":[ latitude, longitude ]
            },
        };
    }-*/;

    public native final Float getLongitude() /*-{
        return this.geometry.coordinates[1];
    }-*/;

    public native final Float getLatitude() /*-{
        return this.geometry.coordinates[0];
    }-*/;
}
