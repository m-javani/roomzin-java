package com.roomzin.roomzinjava.client.single;

import com.roomzin.roomzinjava.api.CacheClientApi;
import com.roomzin.roomzinjava.internal.command.Commands;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.internal.single.SingleHandler;
import com.roomzin.roomzinjava.types.*;

import java.util.List;

/**
 * SingleClient implements CacheClientApi for a single-node Roomzin server,
 * routing commands through a SingleHandler.
 */
public class SingleClient implements CacheClientApi {
    private final SingleHandler handler;
    private Codecs codecs;

    /**
     * Constructs a SingleClient with the given configuration.
     * 
     * @param config The configuration for the single-node client
     * @throws RoomzinException If the handler initialization fails
     */
    public SingleClient(SingleConfig config) throws RoomzinException {
        this.handler = new SingleHandler(config);
        this.handler.setOnReconnect(() -> {
            this.codecs = null;
        });
    }

    @Override
    public Codecs getCodecs() throws RoomzinException {
        Codecs codecs = getCodecsInternal();
        if (codecs == null) {
            throw RoomzinException.of("failed to fetch codecs");
        }
        return codecs;
    }

    private Codecs getCodecsInternal() {
        if (codecs != null) {
            return codecs;
        }
        synchronized (this) {
            if (codecs == null) {
                codecs = fetchCodecs(); // Can return null
            }
        }
        return codecs;
    }

    private Codecs fetchCodecs() {
        try {
            byte[] payload = Commands.buildGetCodecsPayload();
            ProtocolTypes.RawResult res = handler.roundTrip(payload);
            return Commands.parseGetCodecsResponse(res.status, res.fields);
        } catch (Exception e) {
            return null; // Like Go, return nil on error
        }
    }

    public void setOnReconnect(Runnable onReconnect) {
        handler.setOnReconnect(() -> {
            this.codecs = null; // Clear codecs on reconnect like Go
            onReconnect.run();
        });
    }

    /**
     * Sets a property in the cache.
     * 
     * @param p The property payload
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void setProp(SetPropPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetPropPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseSetPropResponse(res.status, res.fields);
    }

    /**
     * Searches for properties matching the given criteria.
     * 
     * @param p The search payload
     * @return List of matching property IDs
     * @throws RoomzinException If the operation fails
     */
    @Override
    public List<String> searchProp(SearchPropPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSearchPropPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseSearchPropResponse(res.status, res.fields);
    }

    /**
     * Searches for available properties on specific dates.
     * 
     * @param p The availability search payload
     * @return List of available properties with their day availability
     * @throws RoomzinException If the operation fails
     */
    @Override
    public List<PropertyAvail> searchAvail(SearchAvailPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSearchAvailPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseSearchAvailResponse(getCodecsInternal(), res.status, res.fields);
    }

    /**
     * Sets a room package for a property.
     * 
     * @param p The room package payload
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void setRoomPkg(SetRoomPkgPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetRoomPkgPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseSetRoomPkgResponse(res.status, res.fields);
    }

    /**
     * Updates room availability for a specific date.
     * 
     * @param p The room availability payload
     * @return The updated availability count
     * @throws RoomzinException If the operation fails
     */
    @Override
    public short setRoomAvl(UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseSetRoomAvlResponse(res.status, res.fields);
    }

    /**
     * Increments room availability for a specific date.
     * 
     * @param p The room availability payload
     * @return The updated availability count
     * @throws RoomzinException If the operation fails
     */
    @Override
    public short incRoomAvl(UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildIncRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseIncRoomAvlResponse(res.status, res.fields);
    }

    /**
     * Decrements room availability for a specific date.
     * 
     * @param p The room availability payload
     * @return The updated availability count
     * @throws RoomzinException If the operation fails
     */
    @Override
    public short decRoomAvl(UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildDecRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseDecRoomAvlResponse(res.status, res.fields);
    }

    /**
     * Checks if a property exists.
     * 
     * @param propertyId The property ID
     * @return True if the property exists, false otherwise
     * @throws RoomzinException If the operation fails
     */
    @Override
    public boolean propExist(String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildPropExistPayload(propertyId);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parsePropExistResponse(res.status, res.fields);
    }

    /**
     * Checks if a room exists for a property.
     * 
     * @param p The property and room payload
     * @return True if the room exists, false otherwise
     * @throws RoomzinException If the operation fails
     */
    @Override
    public boolean propRoomExist(PropRoomExistPayload p) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomExistPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parsePropRoomExistResponse(res.status, res.fields);
    }

    /**
     * Lists all rooms for a property.
     * 
     * @param propertyId The property ID
     * @return List of room IDs
     * @throws RoomzinException If the operation fails
     */
    @Override
    public List<String> propRoomList(String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomListPayload(propertyId);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parsePropRoomListResponse(res.status, res.fields);
    }

    /**
     * Lists all dates with availability for a room.
     * 
     * @param p The property and room payload
     * @return List of available dates
     * @throws RoomzinException If the operation fails
     */
    @Override
    public List<String> propRoomDateList(PropRoomDateListPayload p) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomDateListPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parsePropRoomDateListResponse(res.status, res.fields);
    }

    /**
     * Deletes a property from the cache.
     * 
     * @param propertyId The property ID
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void delProp(String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildDelPropPayload(propertyId);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseDelPropResponse(res.status, res.fields);
    }

    /**
     * Deletes a segment from the cache.
     * 
     * @param segment The segment name
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void delSegment(String segment) throws RoomzinException {
        byte[] payload = Commands.buildDelSegmentPayload(segment);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseDelSegmentResponse(res.status, res.fields);
    }

    /**
     * Deletes a day's availability for a property.
     * 
     * @param p The property and date payload
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void delPropDay(DelPropDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildDelPropDayPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseDelPropDayResponse(res.status, res.fields);
    }

    /**
     * Deletes a room from a property.
     * 
     * @param p The property and room payload
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void delPropRoom(DelPropRoomPayload p) throws RoomzinException {
        byte[] payload = Commands.buildDelPropRoomPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseDelPropRoomResponse(res.status, res.fields);
    }

    /**
     * Deletes a day's availability for a room.
     * 
     * @param p The room and date payload
     * @throws RoomzinException If the operation fails
     */
    @Override
    public void delRoomDay(DelRoomDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildDelRoomDayPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        Commands.parseDelRoomDayResponse(res.status, res.fields);
    }

    /**
     * Retrieves availability details for a room on a specific date.
     * 
     * @param p The room and date request payload
     * @return The room availability result
     * @throws RoomzinException If the operation fails
     */
    @Override
    public GetRoomDayResult getPropRoomDay(GetRoomDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildGetPropRoomDayPayload(p);
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseGetPropRoomDayResponse(getCodecsInternal(), res.status, res.fields);
    }

    /**
     * Retrieves all segments in the cache.
     * 
     * @return List of segment information
     * @throws RoomzinException If the operation fails
     */
    @Override
    public List<SegmentInfo> getSegments() throws RoomzinException {
        byte[] payload = Commands.buildGetSegmentsPayload();
        ProtocolTypes.RawResult res = handler.roundTrip(payload);
        return Commands.parseGetSegmentsResponse(res.status, res.fields);
    }

    /**
     * Closes the client and underlying handler.
     * 
     * @throws Exception If closure fails
     */
    @Override
    public void close() throws Exception {
        handler.close();
    }
}