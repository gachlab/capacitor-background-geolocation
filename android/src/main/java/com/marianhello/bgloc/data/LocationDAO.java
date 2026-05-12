package com.marianhello.bgloc.data;

import java.util.Collection;

public interface LocationDAO {
    Collection<BackgroundLocation> getAllLocations();
    Collection<BackgroundLocation> getValidLocations();
    Collection<BackgroundLocation> getValidLocationsAndDelete();
    BackgroundLocation getLocationById(long id);
    BackgroundLocation getFirstUnpostedLocation();
    BackgroundLocation getNextUnpostedLocation(long fromId);
    long getUnpostedLocationsCount();
    long getLocationsForSyncCount(long millisSinceLastBatch);
    long persistLocation(BackgroundLocation location);
    long persistLocation(BackgroundLocation location, int maxRows);
    long persistLocationForSync(BackgroundLocation location, int maxRows);
    void updateLocationForSync(long locationId);
    void deleteLocationById(long locationId);
    BackgroundLocation deleteFirstUnpostedLocation();
    int deleteAllLocations();
    int deleteUnpostedLocations();
    /**
     * Delete (mark as deleted) all locations that are pending sync to syncUrl.
     * Same effect as discarding the pending sync queue without sending to server.
     */
    int deletePendingSyncLocations();
}
