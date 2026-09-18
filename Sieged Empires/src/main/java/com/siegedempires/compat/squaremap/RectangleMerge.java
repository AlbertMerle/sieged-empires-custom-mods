package com.siegedempires.compat.squaremap;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.Polygon;

/**
 * Merges adjacent chunk rectangles into a single polygon outline.
 * Adapted from squaremap-addons claimchunk.
 */
final class RectangleMerge {
	private RectangleMerge() {
	}

	static Polygon toPolygon(List<ClaimChunk> claims) {
		List<Shape> shapes = new ArrayList<>();
		for (ClaimChunk claim : claims) {
			int x = claim.x() << 4;
			int z = claim.z() << 4;
			List<Point> points = Arrays.asList(
					Point.of(x, z),
					Point.of(x, z + 16),
					Point.of(x + 16, z + 16),
					Point.of(x + 16, z)
			);
			shapes.add(toShape(points));
		}

		Area area = null;
		for (Shape shape : shapes) {
			if (area == null) {
				area = new Area(shape);
			} else {
				area.add(new Area(shape));
			}
		}

		if (area == null) {
			throw new IllegalStateException("No claims to merge");
		}

		return toMarker(area);
	}

	private static Shape toShape(List<Point> points) {
		Path2D path = new Path2D.Double();
		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			if (i == 0) {
				path.moveTo(point.x(), point.z());
			} else {
				path.lineTo(point.x(), point.z());
			}
		}
		path.closePath();
		return path;
	}

	private static Polygon toMarker(Shape shape) {
		List<Point> main = new ArrayList<>();
		List<List<Point>> subtract = new ArrayList<>();
		PathIterator iter = shape.getPathIterator(null, 0.0);
		double[] coords = new double[6];
		List<Point> current = main;
		while (!iter.isDone()) {
			int segment = iter.currentSegment(coords);
			switch (segment) {
				case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO ->
						current.add(Point.of(coords[0], coords[1]));
				case PathIterator.SEG_CLOSE -> {
					List<Point> hole = new ArrayList<>();
					subtract.add(hole);
					current = hole;
				}
			}
			iter.next();
		}
		subtract.removeIf(List::isEmpty);
		return Marker.polygon(main, subtract);
	}
}
