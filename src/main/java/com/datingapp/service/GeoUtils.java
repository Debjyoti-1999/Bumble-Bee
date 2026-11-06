package com.datingapp.service;

public final class GeoUtils {

    public static final double EARTH_RADIUS_KM = 6371.0088;
    /** Length of one degree of latitude (and of longitude at the equator). */
    public static final double KM_PER_DEGREE = Math.PI * EARTH_RADIUS_KM / 180.0;

    private GeoUtils() {
    }

    /** Great-circle distance using the haversine formula. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Distance rounded to whole kilometres, never below 1, so exact positions can't be triangulated. */
    public static int roundedDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        return (int) Math.max(1, Math.round(distanceKm(lat1, lon1, lat2, lon2)));
    }
}
