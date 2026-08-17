package com.roomzin.roomzinjava.api;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.util.List;

/**
 * Unified API interface for Roomzin Java SDK.
 * All methods require a segment parameter for routing in cluster mode.
 * In standalone mode, the segment is ignored but still required for API
 * compatibility.
 */
public interface RoomzinApi extends AutoCloseable {

    // Codecs
    Codecs getCodecs() throws RoomzinException;

    // Write commands
    void setProp(String segment, SetPropPayload p) throws RoomzinException;

    void setRoomPkg(String segment, SetRoomPkgPayload p) throws RoomzinException;

    short setRoomAvl(String segment, UpdRoomAvlPayload p) throws RoomzinException;

    short incRoomAvl(String segment, UpdRoomAvlPayload p) throws RoomzinException;

    short decRoomAvl(String segment, UpdRoomAvlPayload p) throws RoomzinException;

    void delProp(String segment, String propertyId) throws RoomzinException;

    void delSegment(String segment) throws RoomzinException;

    void delPropDay(String segment, DelPropDayRequest p) throws RoomzinException;

    void delPropRoom(String segment, DelPropRoomPayload p) throws RoomzinException;

    void delRoomDay(String segment, DelRoomDayRequest p) throws RoomzinException;

    // Read commands
    List<String> searchProp(String segment, SearchPropPayload p) throws RoomzinException;

    List<PropertyAvail> searchAvail(String segment, SearchAvailPayload p) throws RoomzinException;

    boolean propExist(String segment, String propertyId) throws RoomzinException;

    boolean propRoomExist(String segment, PropRoomExistPayload p) throws RoomzinException;

    List<String> propRoomList(String segment, String propertyId) throws RoomzinException;

    List<String> propRoomDateList(String segment, PropRoomDateListPayload p) throws RoomzinException;

    GetRoomDayResult getPropRoomDay(String segment, GetRoomDayRequest p) throws RoomzinException;

    void close() throws Exception;
}