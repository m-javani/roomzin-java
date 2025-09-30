package com.roomzin.roomzinjava.client.cluster;

import com.roomzin.roomzinjava.api.CacheClientApi;
import com.roomzin.roomzinjava.internal.command.Commands;
import com.roomzin.roomzinjava.internal.cluster.ClusterHandler;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.util.List;

/**
 * ClusterClient implements CacheClientApi for cluster mode, routing writes to
 * the leader
 * and reads to followers using ClusterHandler.
 */
public class ClusterClient implements CacheClientApi {
    private final ClusterHandler handler;
    private Codecs codecs;

    public ClusterClient(ClusterConfig config) throws RoomzinException {
        this.handler = new ClusterHandler(config);
        this.handler.start();

        this.handler.setOnReconnectCallback(() -> {
            this.codecs = null; // Invalidate codecs on leader change
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
            ProtocolTypes.RawResult res = handler.execute(false, payload);
            return Commands.parseGetCodecsResponse(res.status, res.fields);
        } catch (Exception e) {
            return null;
        }

    }

    @Override
    public void setProp(SetPropPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetPropPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseSetPropResponse(res.status, res.fields);
    }

    @Override
    public List<String> searchProp(SearchPropPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSearchPropPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parseSearchPropResponse(res.status, res.fields);
    }

    @Override
    public List<PropertyAvail> searchAvail(SearchAvailPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSearchAvailPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parseSearchAvailResponse(getCodecsInternal(), res.status, res.fields);
    }

    @Override
    public void setRoomPkg(SetRoomPkgPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetRoomPkgPayload(getCodecsInternal(), p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseSetRoomPkgResponse(res.status, res.fields);
    }

    @Override
    public short setRoomAvl(UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildSetRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        return Commands.parseSetRoomAvlResponse(res.status, res.fields);
    }

    @Override
    public short incRoomAvl(UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildIncRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        return Commands.parseIncRoomAvlResponse(res.status, res.fields);
    }

    @Override
    public short decRoomAvl(UpdRoomAvlPayload p) throws RoomzinException {
        byte[] payload = Commands.buildDecRoomAvlPayload(p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        return Commands.parseDecRoomAvlResponse(res.status, res.fields);
    }

    @Override
    public boolean propExist(String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildPropExistPayload(propertyId);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parsePropExistResponse(res.status, res.fields);
    }

    @Override
    public boolean propRoomExist(PropRoomExistPayload p) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomExistPayload(p);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parsePropRoomExistResponse(res.status, res.fields);
    }

    @Override
    public List<String> propRoomList(String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomListPayload(propertyId);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parsePropRoomListResponse(res.status, res.fields);
    }

    @Override
    public List<String> propRoomDateList(PropRoomDateListPayload p) throws RoomzinException {
        byte[] payload = Commands.buildPropRoomDateListPayload(p);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parsePropRoomDateListResponse(res.status, res.fields);
    }

    @Override
    public void delProp(String propertyId) throws RoomzinException {
        byte[] payload = Commands.buildDelPropPayload(propertyId);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseDelPropResponse(res.status, res.fields);
    }

    @Override
    public void delSegment(String segment) throws RoomzinException {
        byte[] payload = Commands.buildDelSegmentPayload(segment);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseDelSegmentResponse(res.status, res.fields);
    }

    @Override
    public void delPropDay(DelPropDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildDelPropDayPayload(p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseDelPropDayResponse(res.status, res.fields);
    }

    @Override
    public void delPropRoom(DelPropRoomPayload p) throws RoomzinException {
        byte[] payload = Commands.buildDelPropRoomPayload(p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseDelPropRoomResponse(res.status, res.fields);
    }

    @Override
    public void delRoomDay(DelRoomDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildDelRoomDayPayload(p);
        ProtocolTypes.RawResult res = handler.execute(true, payload); // Write to leader
        Commands.parseDelRoomDayResponse(res.status, res.fields);
    }

    @Override
    public GetRoomDayResult getPropRoomDay(GetRoomDayRequest p) throws RoomzinException {
        byte[] payload = Commands.buildGetPropRoomDayPayload(p);
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parseGetPropRoomDayResponse(getCodecsInternal(), res.status, res.fields);
    }

    @Override
    public List<SegmentInfo> getSegments() throws RoomzinException {
        byte[] payload = Commands.buildGetSegmentsPayload();
        ProtocolTypes.RawResult res = handler.execute(false, payload); // Read from follower
        return Commands.parseGetSegmentsResponse(res.status, res.fields);
    }

    @Override
    public void close() throws Exception {
        handler.close();
    }
}