/*
 * Copyright (c) 2021 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.valid;

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Implements the algorithms required to compute the <code>isValid()</code> method
 * for {@link Geometry}s.
 * See the documentation for the various geometry types for a specification of validity.
 *
 * @version 1.7
 */
public class IsValidOp
{
  private static final int MIN_SIZE_LINESTRING = 2;
  private static final int MIN_SIZE_RING = 4;

  /**
   * Tests whether a {@link Geometry} is valid.
   * @param geom the Geometry to test
   * @return true if the geometry is valid
   */
  public static boolean isValid(Geometry geom)
  {
    IsValidOp isValidOp = new IsValidOp(geom);
    return isValidOp.isValid();
  }
  
  /**
   * Checks whether a coordinate is valid for processing.
   * Coordinates are valid if their x and y ordinates are in the
   * range of the floating point representation.
   *
   * @param coord the coordinate to validate
   * @return <code>true</code> if the coordinate is valid
   */
  public static boolean isValid(Coordinate coord)
  {
    if (Double.isNaN(coord.x)) return false;
    if (Double.isInfinite(coord.x)) return false;
    if (Double.isNaN(coord.y)) return false;
    if (Double.isInfinite(coord.y)) return false;
    return true;
  }
  
  /**
   * The geometry being validated
   */
  private Geometry inputGeometry;  
  /**
   * If the following condition is TRUE JTS will validate inverted shells and exverted holes
   * (the ESRI SDE model)
   */
  private boolean isInvertedRingValid = false;
  
  private TopologyValidationError validErr;

  /**
   * Creates a new validator for a geometry.
   * 
   * @param inputGeometry the geometry to validate
   */
  public IsValidOp(Geometry inputGeometry)
  {
    this.inputGeometry = inputGeometry;
  }

  /**
   * Sets whether polygons using <b>Self-Touching Rings</b> to form
   * holes are reported as valid.
   * If this flag is set, the following Self-Touching conditions
   * are treated as being valid:
   * <ul>
   * <li>the shell ring self-touches to create a hole touching the shell
   * <li>a hole ring self-touches to create two holes touching at a point
   * </ul>
   * <p>
   * The default (following the OGC SFS standard)
   * is that this condition is <b>not</b> valid (<code>false</code>).
   * <p>
   * Self-Touching Rings which disconnect the 
   * the polygon interior are still considered to be invalid
   * (these are <b>invalid</b> under the SFS, and many other
   * spatial models as well).
   * This includes:
   * <ul>
   * <li>exverted ("bow-tie") shells which self-touch at a single point
   * <li>inverted shells with the inversion touching the shell at another point
   * <li>exverted holes with exversion touching the hole at another point
   * <li>inverted ("C-shaped") holes which self-touch at a single point causing an island to be formed
   * <li>inverted shells or exverted holes which form part of a chain of touching rings
   * (which disconnect the interior)
   * </ul>
   *
   * @param isValid states whether geometry with this condition is valid
   */
  public void setSelfTouchingRingFormingHoleValid(boolean isValid)
  {
    isInvertedRingValid = isValid;
  }

  /**
   * Tests the validity of the input geometry.
   * 
   * @return true if the geometry is valid
   */
  public boolean isValid()
  {
    return isValidGeometry(inputGeometry);
  }

  /**
   * Computes the validity of the geometry,
   * and if not valid returns the validation error for the geometry,
   * or null if the geometry is valid.
   * 
   * @return the validation error, if the geometry is invalid
   * or null if the geometry is valid
   */
  public TopologyValidationError getValidationError()
  {
    isValidGeometry(inputGeometry);
    return validErr;
  }
  
  private void logInvalid(int code, Coordinate pt) {
    validErr = new TopologyValidationError(code, pt);   
  }
  
  private boolean hasInvalidError() {
    return validErr != null;
    
  }
  
  private boolean isValidGeometry(Geometry g)
  {
    validErr = null;

    // empty geometries are always valid
    if (g.isEmpty()) return true;

    if (g instanceof Point)              return isValid( (Point) g);
    if (g instanceof MultiPoint)         return isValid( (MultiPoint) g);
    if (g instanceof LinearRing)         return isValid( (LinearRing) g);
    if (g instanceof LineString)         return isValid( (LineString) g);
    if (g instanceof Polygon)            return isValid( (Polygon) g);
    if (g instanceof MultiPolygon)       return isValid( (MultiPolygon) g);
    if (g instanceof GeometryCollection) return isValid( (GeometryCollection) g);
    
    // geometry type not known
    throw new UnsupportedOperationException(g.getClass().getName());
  }

  /**
   * Tests validity of a Point.
   */
  private boolean isValid(Point g)
  {
    checkCoordinateInvalid(g.getCoordinates());
    if (hasInvalidError()) return false;
    return true;
  }
  
  /**
   * Tests validity of a MultiPoint.
   */
  private boolean isValid(MultiPoint g)
  {
    checkCoordinateInvalid(g.getCoordinates());
    if (hasInvalidError()) return false;
    return true;
  }

  /**
   * Tests validity of a LineString.  
   * Almost anything goes for linestrings!
   */
  private boolean isValid(LineString g)
  {
    checkCoordinateInvalid(g.getCoordinates());
    if (hasInvalidError()) return false;
    checkTooFewPoints(g, MIN_SIZE_LINESTRING);
    if (hasInvalidError()) return false;
    return true;
  }
  
  /**
   * Tests validity of a LinearRing.
   */
  private boolean isValid(LinearRing g)
  {
    checkCoordinateInvalid(g.getCoordinates());
    if (hasInvalidError()) return false;
    
    checkRingNotClosed(g);
    if (hasInvalidError()) return false;

    checkRingTooFewPoints(g);
    if (hasInvalidError()) return false;

    checkSelfIntersectingRing(g);
    return validErr == null;
  }

  /**
   * Tests the validity of a polygon.
   * Sets the validErr flag.
   */
  private boolean isValid(Polygon g)
  {
    checkCoordinateInvalid(g);
    if (hasInvalidError()) return false;
    
    checkRingsNotClosed(g);
    if (hasInvalidError()) return false;

    checkRingsTooFewPoints(g);
    if (hasInvalidError()) return false;

    PolygonTopologyAnalyzer areaAnalyzer = new PolygonTopologyAnalyzer(g, isInvertedRingValid);

    checkAreaIntersections(areaAnalyzer);
    if (hasInvalidError()) return false;

    checkHolesOutsideShell(g);
    if (hasInvalidError()) return false;
    
    checkHolesNotNested(g);
    if (hasInvalidError()) return false;
    
    checkInteriorDisconnected(areaAnalyzer);
    if (hasInvalidError()) return false;
    
    return true;
  }

  /**
   * Tests validity of a MultiPolygon.
   * 
   * @param g
   * @return
   */
  private boolean isValid(MultiPolygon g)
  {
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Polygon p = (Polygon) g.getGeometryN(i);
      checkCoordinateInvalid(p);
      if (hasInvalidError()) return false;
      
      checkRingsNotClosed(p);
      if (hasInvalidError()) return false;
      checkRingsTooFewPoints(p);
      if (hasInvalidError()) return false;
    }

    PolygonTopologyAnalyzer areaAnalyzer = new PolygonTopologyAnalyzer(g, isInvertedRingValid);
    
    checkAreaIntersections(areaAnalyzer);
    if (hasInvalidError()) return false;
    
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Polygon p = (Polygon) g.getGeometryN(i);
      checkHolesOutsideShell(p);
      if (hasInvalidError()) return false;
    }
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Polygon p = (Polygon) g.getGeometryN(i);
      checkHolesNotNested(p);
      if (hasInvalidError()) return false;
    }
    checkShellsNotNested(g);
    if (hasInvalidError()) return false;
    
    checkInteriorDisconnected(areaAnalyzer);
    if (hasInvalidError()) return false;

    return true;
  }

  /**
   * Tests validity of a GeometryCollection.
   * 
   * @param gc
   * @return
   */
  private boolean isValid(GeometryCollection gc)
  {
    for (int i = 0; i < gc.getNumGeometries(); i++) {
      if (! isValidGeometry( gc.getGeometryN(i) )) 
        return false;
    }
    return true;
  }

  private void checkCoordinateInvalid(Coordinate[] coords)
  {
    for (int i = 0; i < coords.length; i++) {
      if (! isValid(coords[i])) {
        logInvalid(TopologyValidationError.INVALID_COORDINATE, coords[i]);
        return;
      }
    }
  }

  private void checkCoordinateInvalid(Polygon poly)
  {
    checkCoordinateInvalid(poly.getExteriorRing().getCoordinates());
    if (hasInvalidError()) return;
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      checkCoordinateInvalid(poly.getInteriorRingN(i).getCoordinates());
      if (hasInvalidError()) return;
    }
  }

  private void checkRingNotClosed(LinearRing ring)
  {
    if (ring.isEmpty()) return;
    if (! ring.isClosed() ) {
      Coordinate pt = ring.getNumPoints() >= 1 ? ring.getCoordinateN(0) : null;
      logInvalid( TopologyValidationError.RING_NOT_CLOSED, pt);
      return;
    }
  }
  
  private void checkRingsNotClosed(Polygon poly)
  {
    checkRingNotClosed(poly.getExteriorRing());
    if (hasInvalidError()) return;
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      checkRingNotClosed(poly.getInteriorRingN(i));
      if (hasInvalidError()) return;
    }
  }

  private void checkRingsTooFewPoints(Polygon poly)
  {
    checkRingTooFewPoints(poly.getExteriorRing());
    if (hasInvalidError()) return;
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      checkRingTooFewPoints(poly.getInteriorRingN(i));
      if (hasInvalidError()) return;
    }
  }

  private void checkRingTooFewPoints(LinearRing ring) {
    if (ring.isEmpty()) return;
    checkTooFewPoints(ring, MIN_SIZE_RING);
  }

  /**
   * Check the number of non-repeated points is at least a given size.
   * 
   * @param line
   * @param minSize
   */
  private void checkTooFewPoints(LineString line, int minSize) {
    if (! isNonRepeatedSizeAtLeast(line, minSize) ) {
      Coordinate pt = line.getNumPoints() >= 1 ? line.getCoordinateN(0) : null;
      logInvalid(TopologyValidationError.TOO_FEW_POINTS, pt);
    }
  }

  /**
   * Test if the number of non-repeated points in a line 
   * is at least a given minimum size.
   * 
   * @param line the line to test
   * @param minSize the minimum line size
   * @return true if the line has the required number of non-repeated points
   */
  private boolean isNonRepeatedSizeAtLeast(LineString line, int minSize) {
    int numPts = 0;
    Coordinate prevPt = null;
    for (int i = 0; i < line.getNumPoints(); i++) {
      if (numPts >= minSize) return true;
      Coordinate pt = line.getCoordinateN(i);
      if (prevPt == null || ! pt.equals2D(prevPt))
        numPts++;
      prevPt = pt; 
    }
    return numPts >= minSize;
  }

  private void checkAreaIntersections(PolygonTopologyAnalyzer areaAnalyzer) {
    if (areaAnalyzer.hasIntersection()) {
      logInvalid(TopologyValidationError.SELF_INTERSECTION,
                 areaAnalyzer.getIntersectionLocation());
      return;
    }
    if (areaAnalyzer.hasDoubleTouch()) {
      logInvalid(TopologyValidationError.DISCONNECTED_INTERIOR,
                 areaAnalyzer.getIntersectionLocation());
      return;
    }
    if (areaAnalyzer.isInteriorDisconnectedBySelfTouch()) {
      logInvalid(TopologyValidationError.DISCONNECTED_INTERIOR,
                 areaAnalyzer.getDisconnectionLocation());
      return;
    }

  }

  /**
   * Check whether a ring self-intersects (except at its endpoints).
   *
   * @param ring the linear ring to check
   */
  private void checkSelfIntersectingRing(LinearRing ring)
  {
    Coordinate intPt = PolygonTopologyAnalyzer.findSelfIntersection(ring);
    if (intPt != null) {
      logInvalid(TopologyValidationError.RING_SELF_INTERSECTION,
          intPt);
    }
  }
  
  /**
   * Tests that each hole is inside the polygon shell.
   * This routine assumes that the holes have previously been tested
   * to ensure that all vertices lie on the shell or on the same side of it
   * (i.e. that the hole rings do not cross the shell ring).
   * Given this, a simple point-in-polygon test of a single point in the hole can be used,
   * provided the point is chosen such that it does not lie on the shell.
   *
   * @param poly the polygon to be tested for hole inclusion
   */
  private void checkHolesOutsideShell(Polygon poly)
  {
    // skip test if no holes are present
    if (poly.getNumInteriorRing() <= 0) return;
    
    LinearRing shell = poly.getExteriorRing();
    boolean isShellEmpty = shell.isEmpty();
    PointOnGeometryLocator pir = new IndexedPointInAreaLocator(shell);
    
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      LinearRing hole = poly.getInteriorRingN(i);
      if (hole.isEmpty()) continue;
      
      Coordinate invalidPt = null;
      if (isShellEmpty) {
        invalidPt = hole.getCoordinate();
      }
      else {
        invalidPt = findHoleOutsideShellPoint(pir, hole);
      }
      if (invalidPt != null) {
        logInvalid(TopologyValidationError.HOLE_OUTSIDE_SHELL,
            invalidPt);
        return;
      }
    }
  }

  /**
   * Checks if a polygon hole lies inside its shell
   * and if not returns the point indicating this.
   * The hole is known to be wholly inside or outside the shell, 
   * so it suffices to find a single point which is interior or exterior.
   * A valid hole may only have a single point touching the shell
   * (since otherwise it creates a disconnected interior).
   * So there should be at least one point which is interior or exterior,
   * and this should be the first or second point tested.
   * 
   * @param shellLocator
   * @param hole
   * @return a hole point outside the shell, or null if valid
   */
  private Coordinate findHoleOutsideShellPoint(PointOnGeometryLocator shellLocator, LinearRing hole) {
    for (int i = 0; i < hole.getNumPoints() - 1; i++) {
      Coordinate holePt = hole.getCoordinateN(i);
      int loc = shellLocator.locate(holePt);
      if (loc== Location.BOUNDARY) continue;
      if (loc== Location.INTERIOR) return null;
      /**
       * Location is EXTERIOR, so hole is outside shell
       */
      return holePt;
    }
    return null;
  }

  /**
   * Tests if any polygon hole is nested inside another.
   * Assumes that holes do not cross (overlap),
   * This is checked earlier.
   * 
   * @param poly the polygon with holes to test
   */
  private void checkHolesNotNested(Polygon poly)
  {
    // skip test if no holes are present
    if (poly.getNumInteriorRing() <= 0) return;
    
    IndexedNestedHoleTester nestedTester = new IndexedNestedHoleTester(poly);
    if ( nestedTester.isNested() ) {
      logInvalid(TopologyValidationError.NESTED_HOLES,
                            nestedTester.getNestedPoint());
    }
  }

  /**
   * Tests that no element polygon is in the interior of another element polygon.
   * <p>
   * Preconditions:
   * <ul>
   * <li>shells do not partially overlap
   * <li>shells do not touch along an edge
   * <li>no duplicate rings exist
   * </ul>
   * These have been confirmed by the {@link PolygonTopologyAnalyzer}.
   */
  private void checkShellsNotNested(MultiPolygon mp)
  {
    for (int i = 0; i < mp.getNumGeometries(); i++) {
      Polygon p = (Polygon) mp.getGeometryN(i);
      if (p.isEmpty())
        continue;
      LinearRing shell = p.getExteriorRing();
      for (int j = 0; j < mp.getNumGeometries(); j++) {
        if (i == j) continue;
        Polygon p2 = (Polygon) mp.getGeometryN(j);
        Coordinate invalidPt = findShellSegmentInPolygon(shell, p2);
        if (invalidPt != null) {
          logInvalid(TopologyValidationError.NESTED_SHELLS,
              invalidPt);
          return;
        }
      }
    }
  }

  /**
   * Finds a point of a shell segment which lies inside a polygon, if any.
   * The shell is assume to touch the polyon only at shell vertices, 
   * and does not cross the polygon.
   * 
   * @param the shell to test
   * @param the polygon to test against
   * @return an interior segment point, or null if the shell is nested correctly
   */
  private Coordinate findShellSegmentInPolygon(LinearRing shell, Polygon poly)
  {
    LinearRing polyShell = poly.getExteriorRing();
    if (polyShell.isEmpty()) return null;
    
    //--- if envelope is not covered --> not nested
    if (! poly.getEnvelopeInternal().covers(shell.getEnvelopeInternal()))
      return null;
    
    Coordinate shell0 = shell.getCoordinateN(0);
    Coordinate shell1 = shell.getCoordinateN(1);
    
    if (! PolygonTopologyAnalyzer.isSegmentInRing(shell0, shell1, polyShell))
      return null;

    /**
     * Check if the shell is inside a hole (if there are any). 
     * If so this is valid.
     */
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      LinearRing hole = poly.getInteriorRingN(i);
      if (hole.getEnvelopeInternal().covers(shell.getEnvelopeInternal())
          && PolygonTopologyAnalyzer.isSegmentInRing(shell0, shell1, hole)) {
        return null;
      }
    }
    
    /**
     * The shell is contained in the polygon, but is not contained in a hole.
     * This is invalid.
     */
    return shell0;
  } 
 
  private void checkInteriorDisconnected(PolygonTopologyAnalyzer areaAnalyzer) {
    if (areaAnalyzer.isInteriorDisconnectedByRingCycle())
      logInvalid(TopologyValidationError.DISCONNECTED_INTERIOR,
          areaAnalyzer.getDisconnectionLocation());
  }
}
