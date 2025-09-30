package com.roomzin.roomzinjava.types;

import java.util.Objects;

public class SegmentInfo {
    private final String segment;
    private final int propCount; // uint32 as int

    public SegmentInfo(String segment, int propCount) {
        this.segment = segment;
        this.propCount = propCount;
    }

    public String getSegment() {
        return segment;
    }

    public int getPropCount() {
        return propCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SegmentInfo that = (SegmentInfo) o;
        return propCount == that.propCount &&
                Objects.equals(segment, that.segment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segment, propCount);
    }
}