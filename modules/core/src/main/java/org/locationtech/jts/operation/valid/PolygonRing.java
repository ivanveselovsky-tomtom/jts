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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;

/**
 * A ring of a polygon being analyzed for topological validity.
 * The shell and hole rings of valid polygons touch only at discrete points.
 * The "touch" relationship induces a graph over the set of rings. 
 * The interior of a valid polygon must be connected.
 * This is the case if there is no "chain" of touching rings
 * (which would partition off part of the interior).
 * This is equivalent to the touch graph having no cycles.
 * <p>
 * Also, in a valid polygon two rings can touch only at a single location,
 * since otherwise they disconnect a portion of the interior between them.
 * This is checked as the touches relation is built
 * (so the touch relation representation for a polygon ring does not need to support
 * more than one touch location for each adjacent ring).
 * <p>
 * Thus the touch graph of a valid polygon is a forest - a set of disjoint trees.
 * <p>
 * The cycle detection algorithm works for polygon rings which also contain self-touches
 * (inverted shells and exverted holes).
 * <p>
 * Polygons with no holes do not need to be checked for
 * a connected interior (unless self-touches are allowed).
 * <p>
 * The class also records the topology at self-touch nodes,
 * to support checking if an invalid self-touch disconnects the polygon.
 *
 * @author mdavis
 *
 */
class PolygonRing {
  
  /**
   * Tests if a polygon ring represents a shell.
   * 
   * @param polyRing the ring to test (may be null)
   * @return true if the ring represents a shell
   */
  public static boolean isShell(PolygonRing polyRing) {
    if (polyRing == null) return true;
    return polyRing.isShell();
  }

  /**
   * Records a touch location between two rings,
   * and checks if the rings already touch in a different location.
   * 
   * @param ring0 a polygon ring
   * @param ring1 a polygon ring
   * @param pt the location where they touch
   * @return true if the polygons already touch
   */
  public static boolean addTouch(PolygonRing ring0, PolygonRing ring1, Coordinate pt) {
    //--- skip if either polygon does not have holes
    if (ring0 == null || ring1 == null) 
      return false;
    
    //--- only record touches within a polygon
    if (! ring0.isSamePolygon(ring1)) return false;
    
    if (! ring0.isOnlyTouch(ring1, pt)) return true;
    if (! ring1.isOnlyTouch(ring0, pt)) return true;
    
    ring0.addTouch(ring1, pt);
    ring1.addTouch(ring0, pt);
    return false;
  }
  
  /**
   * Finds a location (if any) where a chain of rings forms a cycle
   * in the ring touch graph.
   * This indicates that a set of holes disconnects the interior of a polygon.
   * 
   * @param polyRings the list of rings to check
   * @return a vertex contained in a ring cycle, or null if none is found
   */
  public static Coordinate findTouchCycleLocation(List<PolygonRing> polyRings) {
    for (PolygonRing polyRing : polyRings) {
      if (! polyRing.isInTouchSet()) {
        Coordinate touchCycleLoc = polyRing.findTouchCycleLocation();
        if (touchCycleLoc != null) return touchCycleLoc;
      }
    }
    return null;
  }

  /**
   * Finds a location of an interior self-touch in a list of rings,
   * if one exists. 
   * This indicates that a self-touch disconnects the interior of a polygon,
   * which is invalid.
   * 
   * @param polyRings the list of rings to check
   * @return the location of an interior self-touch node, or null if there are none
   */
  public static Coordinate findInteriorSelfNode(List<PolygonRing> polyRings) {
    for (PolygonRing polyRing : polyRings) {
        Coordinate interiorSelfNode = polyRing.findInteriorSelfNode();
        if (interiorSelfNode != null) {
          return interiorSelfNode;
      }
    }
    return null;
  }
  
  private int id;
  private PolygonRing shell;
  private LinearRing ring;
  
  /**
   * The root of the touch graph tree containing this ring.
   * Serves as the id for the graph partition induced by the touch relation.
   */
  private PolygonRing touchSetRoot = null;
  
  /**
   * The parent of this ring in the touch tree graph.
   */
  private PolygonRing touchTreeParent = null;
  
  // lazily created
  /**
   * The set of PolygonRingTouch links
   * for this ring. 
   * The set of all touches in the rings of a polygon
   * forms the polygon touch graph. 
   * This supports detecting touch cycles, which
   * reveal the condition of a disconnected interior.
   * <p>
   * Only a single touch is recorded between any two rings, 
   * since more than one touch between two rings 
   * indicates interior disconnection as well.
   */
  private Map<Integer, PolygonRingTouch> touches = null;
  
  /**
   * The set of self-nodes in this ring.
   * This supports checking valid ring self-touch topology.
   */
  private ArrayList<PolygonRingSelfNode> selfNodes = null;

  /**
   * Creates a ring for a polygon shell.
   * @param ring
   */
  public PolygonRing(LinearRing ring) {
    this.ring = ring;
    id = -1;
    shell = this;
  }

  /**
   * Creates a ring for a polygon hole.
   * @param ring the ring geometry
   * @param index the index of the hole
   * @param shell the parent polygon shell
   */
  public PolygonRing(LinearRing ring, int index, PolygonRing shell) {
    this.ring = ring;
    this.id = index;
    this.shell = shell;
  }
  
  public boolean isSamePolygon(PolygonRing ring) {
    return shell == ring.shell;
  }
  
  public boolean isShell() {
    return shell == this;
  }
  
  private boolean isInTouchSet() {
    return touchSetRoot != null;
  }
  
  private void setTouchSetRoot(PolygonRing ring) {
    touchSetRoot = ring;
  }

  private PolygonRing getTouchSetRoot() {
    return touchSetRoot;
  }
  
  private void setParent(PolygonRing ring) {
    touchTreeParent = ring;
  }
  
  private boolean isChildOf(PolygonRing ring) {
    return touchTreeParent == ring;
  }

  private boolean hasTouches() {
    return touches != null && ! touches.isEmpty();
  }

  private Collection<PolygonRingTouch> getTouches() {
    return touches.values();
  }

  private void addTouch(PolygonRing ring, Coordinate pt) {
    if (touches == null) {
      touches = new HashMap<Integer, PolygonRingTouch>();
    }
    PolygonRingTouch touch = touches.get(ring.id);
    if (touch == null) {
      touches.put(ring.id, new PolygonRingTouch(ring, pt));
    };
  }
  
  public void addSelfTouch(Coordinate origin, Coordinate e00, Coordinate e01, Coordinate e10, Coordinate e11) {
    if (selfNodes == null) {
      selfNodes = new ArrayList<PolygonRingSelfNode>();
    }
    selfNodes.add(new PolygonRingSelfNode(origin, e00, e01, e10, e11));
  }
  
  /**
   * Tests if this ring touches a given ring at
   * the single point specified.
   * 
   * @param ring the other PolygonRing
   * @param pt the touch point
   * @return true if the rings touch only at the given point
   */
  private boolean isOnlyTouch(PolygonRing ring, Coordinate pt) {
    //--- no touches for this ring
    if (touches == null) return true;
    //--- no touches for other ring
    PolygonRingTouch touch = touches.get(ring.id);
    if (touch == null) return true;
    //--- the rings touch - check if point is the same
    return touch.isAtLocation(pt);
  }
  
  /**
   * Detects whether the subgraph of rings linked by touch to this ring
   * contains a touch cycle.
   * If no cycles are detected, the subgraph of touching rings is a tree.
   * The subgraph is marked using this ring as the root.
   * 
   * @return a vertex in a ring cycle, or null if no cycle found
   */
  private Coordinate findTouchCycleLocation() {
    //--- the touch set including this ring is already processed
    if (isInTouchSet()) return null;
    
    //--- scan the touch set tree rooted at this ring
    // Assert: this.touchSetRoot is null
    PolygonRing root = this;
    root.setParent(root);
    root.setTouchSetRoot(root);
    
    Deque<PolygonRing> ringStack = new ArrayDeque<PolygonRing>();
    ringStack.add(root);
    
    while (! ringStack.isEmpty()) {
     PolygonRing ring = ringStack.removeFirst();
     Coordinate touchCyclePt = scanForTouchCycle(root, ring, ringStack);
      if (touchCyclePt != null) {
        return touchCyclePt;
      }
    }
    return null;
  }

  /**
   * Scans the rings touching a given ring, 
   * and checks if they are already part of its ring subgraph set.
   * If so, a ring cycle has been detected.
   * Otherwise, each touched ring is added to the current subgraph set, 
   * and queued to be scanned in turn.
   *  
   * @param root the root of the touch subgraph
   * @param ring the ring being processed
   * @param ringStack the stack of rings to scan
   * @return a vertex in a ring cycle if found, or null
   */
  private Coordinate scanForTouchCycle(PolygonRing root, PolygonRing ring, Deque<PolygonRing> ringStack) {
    if (! ring.hasTouches()) 
      return null;
    
    //-- check the touched rings
    //--- either they form a touch cycle, or they are pushed on stack for processing
    for (PolygonRingTouch touch : ring.getTouches()) {
      PolygonRing touchRing = touch.getRing();
      /**
       * There is always a link back to the touch-tree parent of the ring, 
       * so don't include it.
       * (I.e. the ring touches the parent ring which originally 
       * added this ring to the stack)
       */
      if (ring.isChildOf(touchRing))
        continue;
      
      /**
       * Test if the touched ring has already been 
       * reached via a different path in the tree.
       * This indicates a touching ring cycle has been found. 
       * This is invalid.
       */
      if (touchRing.getTouchSetRoot() == root)
        return touch.getCoordinate();

      touchRing.setParent(ring);
      touchRing.setTouchSetRoot(root);
      ringStack.add(touchRing);
    }
    return null;
  }

  /**
   * Finds the location of an invalid interior self-touch in this ring,
   * if one exists. 
   * 
   * @return the location of an interior self-touch node, or null if there are none
   */
  public Coordinate findInteriorSelfNode() {
    if (selfNodes == null) return null;
    
    /**
     * Determine if the ring interior is on the Right.
     * This is the case if the ring is a shell and is CW,
     * or is a hole and is CCW.
     */
    boolean isCCW = Orientation.isCCW(ring.getCoordinates());
    boolean isInteriorOnRight = isShell() ^ isCCW; 

    for (PolygonRingSelfNode selfNode : selfNodes) {
      if (! selfNode.isExterior( isInteriorOnRight) ) {
        return selfNode.getCoordinate();
      }
    }
    return null;
  }
  
  public String toString() {
    return ring.toString();
  }

 }

/**
 * Records a point where a {@link PolygonRing} touches another one.
 * This forms an edge in the induced ring touch graph.
 * 
 * @author mdavis
 *
 */
class PolygonRingTouch {
  private PolygonRing ring;
  private Coordinate touchPt;

  public PolygonRingTouch(PolygonRing ring, Coordinate pt) {
    this.ring = ring;
    this.touchPt = pt;
  }

  public Coordinate getCoordinate() {
    return touchPt;
  }

  public PolygonRing getRing() {
    return ring;
  }

  public boolean isAtLocation(Coordinate pt) {
    return touchPt.equals2D(pt);
  }
}

/**
 * Represents a ring self-touch node, recording the node (intersection point)
 * and the endpoints of the four adjacent segments.
 * <p>
 * This is used to evaluate validity of self-touching nodes,
 * when they are allowed.
 * 
 * @author mdavis
 *
 */
class PolygonRingSelfNode {
  private Coordinate nodePt;
  private Coordinate e00;
  private Coordinate e01;
  private Coordinate e10;
  //private Coordinate e11;

  public PolygonRingSelfNode(Coordinate nodePt, 
      Coordinate e00, Coordinate e01, 
      Coordinate e10, Coordinate e11) {
    this.nodePt = nodePt;
    this.e00 = e00;
    this.e01 = e01;
    this.e10 = e10;
    //this.e11 = e11;
  }
  
  /**
   * The node point.
   * 
   * @return
   */
  public Coordinate getCoordinate() {
    return nodePt;
  }

  /**
   * Tests if a self-touch has the segments of each half of the touch
   * lying in the exterior of a polygon.
   * This is a valid self-touch.
   * It applies to both shells and holes.
   * Only one of the four possible cases needs to be tested, 
   * since the situation has full symmetry.
   * 
   * @param isInteriorOnRight whether the interior is to the right of the parent ring
   * @return true if the self-touch is in the exterior
   */
  public boolean isExterior(boolean isInteriorOnRight) {
    /**
     * Note that either corner and either of the other edges could be used to test.
     * The situation is fully symmetrical.
     */
    boolean isInteriorSeg = PolygonNode.isInteriorSegment(nodePt, e00, e01, e10);
    boolean isExterior = isInteriorOnRight ? ! isInteriorSeg : isInteriorSeg;
    return isExterior;
  }
}
