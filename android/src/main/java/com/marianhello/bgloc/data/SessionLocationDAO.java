package com.marianhello.bgloc.data;

import java.util.Collection;

/**
 * DAO for the current recording session locations.
 * Session is independent of sync: locations are kept until startSession() or clearSession().
 */
public interface SessionLocationDAO {
    /** Clear session table and set session active. Call when user starts a route. */
    void startSession();
    /** Clear session table and set session inactive. Call when route is finished and sync OK. */
    void clearSession();
    boolean isSessionActive();
    void persistSessionLocation(BackgroundLocation location);
    Collection<BackgroundLocation> getSessionLocations();
    int getSessionLocationsCount();
}
