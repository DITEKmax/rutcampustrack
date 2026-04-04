package ru.rutcampustrack.attendance.geofence;

/**
 * Utility class for geographic distance calculations using the Haversine formula.
 * Package-private — used only within the geofence domain.
 * Per D-09, D-10: pure math, no Spring dependencies.
 */
class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {}

    /**
     * Calculates the great-circle distance in meters between two geographic points
     * using the Haversine formula.
     *
     * @param lat1 latitude of point 1 in degrees
     * @param lng1 longitude of point 1 in degrees
     * @param lat2 latitude of point 2 in degrees
     * @param lng2 longitude of point 2 in degrees
     * @return distance in meters
     */
    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Determines whether a point is within a given radius from a center point.
     *
     * @param lat         latitude of the point to check
     * @param lng         longitude of the point to check
     * @param centerLat   latitude of the center
     * @param centerLng   longitude of the center
     * @param radiusMeters radius in meters
     * @return true if the point is within the radius
     */
    static boolean isWithinRadius(double lat, double lng,
                                   double centerLat, double centerLng,
                                   double radiusMeters) {
        return distanceMeters(lat, lng, centerLat, centerLng) <= radiusMeters;
    }
}
