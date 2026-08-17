package com.roomzin.roomzinjava.client;

import com.roomzin.roomzinjava.api.RoomzinApi;
import com.roomzin.roomzinjava.internal.RoomzinHandler;
import com.roomzin.roomzinjava.internal.command.Commands;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.util.List;

/**
 * Unified Roomzin client for both standalone and router (cluster) modes.
 * All methods require a segment parameter for routing in cluster mode.
 * In standalone mode, the segment is ignored but still required.
 */
public class RoomzinClient implements RoomzinApi {
    private final RoomzinHandler handler;
    private Codecs codecs;

    public RoomzinClient(RoomzinConfig config) throws RoomzinException {
        this.handler = new RoomzinHandler(config);
        this.handler.setOnReconnect(() -> {
            this.codecs = null;
        });
    }

    @Override
    public Codecs getCodecs() throws RoomzinException {
        Codecs codecs = getCodecsInternal();
        if (codecs == null) {
            throw RoomzinException.of("Failed to fetch codecs");
        }
        return codecs;
    }

    private Codecs getCodecsInternal() {
        if (codecs != null) {
            return codecs;
        }
        synchronized (this) {
            if (codecs == null) {
                codecs = fetchCodecs();
            }
        }
        return codecs;
    }

    private Codecs fetchCodecs() {
        try {
            byte[] payload = Commands.buildGetCodecsPayload();
            ProtocolTypes.RawResult res = handler.execute(ProtocolTypes.CODEC_SEGMENT, false, payload);
            return Commands.parseGetCodecsResponse(res.status, res.fields);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------- WRITE COMMANDS --------------------

    @Override
    public void setProp(String segment, SetPropPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetPropPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseSetPropResponse(res.status, res.fields);
    }

    @Override
    public void setRoomPkg(String segment, SetRoomPkgPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetRoomPkgPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseSetRoomPkgResponse(res.status, res.fields);
    }

    @Override
    public short setRoomAvl(String segment, UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        return Commands.parseSetRoomAvlResponse(res.status, res.fields);
    }

    @Override
    public short incRoomAvl(String segment, UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildIncRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        return Commands.parseIncRoomAvlResponse(res.status, res.fields);
    }

    @Override
    public short decRoomAvl(String segment, UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildDecRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        return Commands.parseDecRoomAvlResponse(res.status, res.fields);
    }

    @Override
    public void delProp(String segment, String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildDelPropPayload(propertyId);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseDelPropResponse(res.status, res.fields);
    }

    @Override
    public void delSegment(String segment) throws RoomzinException {
        byte[] payload = Commands.buildDelSegmentPayload(segment);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseDelSegmentResponse(res.status, res.fields);
    }

    @Override
    public void delPropDay(String segment, DelPropDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildDelPropDayPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseDelPropDayResponse(res.status, res.fields);
    }

    @Override
    public void delPropRoom(String segment, DelPropRoomPayload p) throws RoomzinException {
        byte[] payload = Commands.buildDelPropRoomPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseDelPropRoomResponse(res.status, res.fields);
    }

    @Override
    public void delRoomDay(String segment, DelRoomDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildDelRoomDayPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, true, payload);
        Commands.parseDelRoomDayResponse(res.status, res.fields);
    }

    // -------------------- READ COMMANDS --------------------

    @Override
    public List<String> searchProp(String segment, SearchPropPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSearchPropPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parseSearchPropResponse(res.status, res.fields);
    }

    @Override
    public List<PropertyAvail> searchAvail(String segment, SearchAvailPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSearchAvailPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parseSearchAvailResponse(getCodecsInternal(), res.status, res.fields);
    }

    @Override
    public boolean propExist(String segment, String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildPropExistPayload(propertyId);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parsePropExistResponse(res.status, res.fields);
    }

    @Override
    public boolean propRoomExist(String segment, PropRoomExistPayload p) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomExistPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parsePropRoomExistResponse(res.status, res.fields);
    }

    @Override
    public List<String> propRoomList(String segment, String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomListPayload(propertyId);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parsePropRoomListResponse(res.status, res.fields);
    }

    @Override
    public List<String> propRoomDateList(String segment, PropRoomDateListPayload p) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomDateListPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parsePropRoomDateListResponse(res.status, res.fields);
    }

    @Override
    public GetRoomDayResult getPropRoomDay(String segment, GetRoomDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildGetPropRoomDayPayload(p);
        ProtocolTypes.RawResult res = handler.execute(segment, false, payload);
        return Commands.parseGetPropRoomDayResponse(getCodecsInternal(), res.status, res.fields);
    }

    @Override
    public void close() throws Exception {
        handler.close();
    }
}