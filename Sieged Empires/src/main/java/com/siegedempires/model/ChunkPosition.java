package com.siegedempires.model;

import java.util.Objects;

public class ChunkPosition {
    private int x;
    private int z;
    private String dimension;

    public ChunkPosition() {}

    public ChunkPosition(int x, int z, String dimension) {
        this.x = x;
        this.z = z;
        this.dimension = dimension;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkPosition that = (ChunkPosition) o;
        return x == that.x && z == that.z && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z, dimension);
    }
}