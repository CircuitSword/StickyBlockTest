package PositionalKeys;

import java.util.Objects;

public final class StripCoord {

    public final byte hash;

    StripCoord(byte h) {
        hash=h;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StripCoord && ((StripCoord) o).hash == this.hash;
    }
}