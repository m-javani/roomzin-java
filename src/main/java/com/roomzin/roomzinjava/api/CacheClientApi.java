package com.roomzin.roomzinjava.api;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.util.List;

public interface CacheClientApi extends AutoCloseable {
    Codecs getCodecs() throws RoomzinException;

    void setProp(SetPropPayload p) throws RoomzinException;

    List<String> searchProp(SearchPropPayload p) throws RoomzinException;

    List<PropertyAvail> searchAvail(SearchAvailPayload p) throws RoomzinException;

    void setRoomPkg(SetRoomPkgPayload p) throws RoomzinException;

    short setRoomAvl(UpdRoomAvlPayload p) throws RoomzinException;

    short incRoomAvl(UpdRoomAvlPayload p) throws RoomzinException;

    short decRoomAvl(UpdRoomAvlPayload p) throws RoomzinException;

    boolean propExist(String propertyId) throws RoomzinException;

    boolean propRoomExist(PropRoomExistPayload p) throws RoomzinException;

    List<String> propRoomList(String propertyId) throws RoomzinException;

    List<String> propRoomDateList(PropRoomDateListPayload p) throws RoomzinException;

    void delProp(String propertyId) throws RoomzinException;

    void delSegment(String segment) throws RoomzinException;

    void delPropDay(DelPropDayRequest p) throws RoomzinException;

    void delPropRoom(DelPropRoomPayload p) throws RoomzinException;

    void delRoomDay(DelRoomDayRequest p) throws RoomzinException;

    GetRoomDayResult getPropRoomDay(GetRoomDayRequest p) throws RoomzinException;

    List<SegmentInfo> getSegments() throws RoomzinException;

    void close() throws Exception;
}