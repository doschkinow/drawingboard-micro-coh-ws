package com.mycompany.drawingboard.light;

import com.mycompany.drawingboard.light.coherence.CacheService;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.cache.Cache;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.websocket.EncodeException;
import javax.websocket.Session;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseBroadcaster;

import com.tangosol.net.NamedCache;
import com.tangosol.util.ValueManipulator;
import com.tangosol.util.processor.NumberIncrementor;

/**
 * Simple in-memory data storage for the application.
 */
public class DataProvider {

    /**
     * Broadcaster for server-sent events.
     */
    private static SseBroadcaster sseBroadcaster = new SseBroadcaster();

    /**
     * Map that stores web socket sessions corresponding to a given drawing ID.
     */
    private static final MultivaluedHashMap<Integer, Session> webSockets
            = new MultivaluedHashMap<>();

    /**
     * Retrieves a drawing by ID.
     *
     * @param drawingId ID of the drawing to be retrieved.
     * @return Drawing with the corresponding ID.
     */
    public static synchronized Drawing getDrawing(int drawingId) {
        Cache<Integer, Drawing> drawingCache = CacheService.getDrawingsCache();
        return drawingCache.get(new Integer(drawingId));
    }

    /**
     * Retrieves all existing drawings.
     *
     * @return List of all drawings.
     */
    public static synchronized List<Drawing> getAllDrawings() {
        Cache<Integer, Drawing> drawingCache = CacheService.getDrawingsCache();
        // we use unwrap otherwise we would have to iterate through all cache entries
        NamedCache nc = drawingCache.unwrap(NamedCache.class);
        List<Drawing> list = new ArrayList<>(nc.values());
        Collections.sort(list);
        return list;
    }

    /**
     * Creates a new drawing based on the supplied drawing object.
     *
     * @param drawing Drawing object containing property values for the new
     * drawing.
     * @return ID of the newly created drawing.
     */
    public static synchronized int createDrawing(Drawing drawing) {
        Cache<Integer, Integer> idCache = CacheService.getIdCache();
        //alternatively we could write custom entry processor
        NamedCache idCacheNC = idCache.unwrap(NamedCache.class);
        Integer lastIdInteger = (Integer) idCacheNC.invoke(-1, new NumberIncrementor((ValueManipulator) null, 1, false));

        Drawing result = new Drawing();
        result.setId(lastIdInteger);
        result.setName(drawing.getName());

        Cache<Integer, Drawing> drawingsCache = CacheService.getDrawingsCache();
        drawingsCache.put(result.getId(), result);
        return result.getId();
    }

    /**
     * Delete a drawing with a given ID.
     *
     * @param drawingId ID of the drawing to be deleted.
     * @return {@code true} if the drawing was deleted, {@code false} if there
     * was no such drawing.
     */
    public static synchronized boolean deleteDrawing(int drawingId) {
        Cache<Integer, Drawing> drawingsCache = CacheService.getDrawingsCache();
        return drawingsCache.remove(new Integer(drawingId));
    }

    /**
     * TO DO
     *
     * @param shape
     * @return
     */
//    static InvocableMap.EntryProcessor<Integer, Drawing, Boolean> processShape(Shape shape) {
//    	
//    	return entry -> {
//    	    
//    		Drawing drawing = entry.getValue();	
//    	    if(drawing != null) {
//                drawing.getShapes().add(shape);
//                entry.setValue(drawing);
//                return true;
//            } else {
//                return false;
//            }
//    	};
//    }
    /**
     * Add a new shape to the drawing.
     *
     * @param drawingId ID of the drawing the shape should be added to.
     * @param shape Shape to be added to the drawing.
     * @return {@code true} if the shape was added, {@code false} if no such
     * drawing was found.
     */
    public static synchronized boolean addShape(int drawingId, Shape shape) {

        // JSR-107 Entry Processor
//    	Cache<Integer, Drawing> drawingsCache = CacheService.getDrawingsCache();
//
//        if (drawingsCache.containsKey(drawingId)) {
//            return (Boolean) drawingsCache.invoke(drawingId, new AddShapeProcessor(shape));
//            
//        } else {
//            return false;
//        }
        //alternative: use a Lambda without explicit EntryProcessor 
        Cache<Integer, Drawing> drawingsCache = CacheService.getDrawingsCache();
        NamedCache<Integer, Drawing> drawingsCacheNC = drawingsCache.unwrap(NamedCache.class);

        final Shape shape1 = new Shape();
        shape1.setType(shape.getType());
        shape1.setX(shape.getX());
        shape1.setY(shape.getY());
        shape1.setColor(Shape.ShapeColor.YELLOW);
        //shape1.setColor(shape.getColor());

        if (drawingsCache.containsKey(drawingId)) {
            return drawingsCacheNC.invoke(drawingId, //processShape(shape));
                    (entry) -> {
                        Drawing drawing = entry.getValue();

                        if (drawing != null) {
                            drawing.getShapes().add(shape1);
                            System.out.println("Version2: lambda called to " + drawing.getName());

                            entry.setValue(drawing);
                            return true;
                        } else {
                            return false;
                        }
                    });

        } else {
            return false;
        }

    }

    /**
     * Registers a new channel for sending events. An event channel corresponds
     * to a client (browser) event source connection.
     *
     * @param ec Event channel to be registered for sending events.
     */
    public static void addEventOutput(EventOutput eo) {
        sseBroadcaster.add(eo);
    }

    /**
     * Registers a new web socket session and associates it with a drawing ID.
     * This method should be called when a client opens a web socket connection
     * to a particular drawing URI.
     *
     * @param drawingId Drawing ID to associate the web socket session with.
     * @param session New web socket session to be registered.
     */
    public static synchronized void addWebSocket(int drawingId, Session session) {
        webSockets.add(drawingId, session);
        try {
            Optional<String> loc = Optional.ofNullable(System.getenv("SSE_LOCATION"));
            session.getBasicRemote().sendText("{\"sseLocation\":" + "\"" + loc.orElse("localhost:8080") + "\"" + "}");
            Drawing drawing = getDrawing(drawingId);
            if (drawing != null && drawing.shapes != null) {
                for (Shape shape : drawing.getShapes()) {
                    session.getBasicRemote().sendObject(shape);
                }
            }
        } catch (IOException | EncodeException ex) {
            Logger.getLogger(DataProvider.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * Removes the existing web socket session associated with a drawing ID.
     * This method should be called when a client closes the web socket
     * connection to a particular drawing URI.
     *
     * @param drawingId ID of the drawing the web socket session is associated
     * with.
     * @param session Web socket session to be removed.
     */
    public static synchronized void removeWebSocket(int drawingId, Session session) {
        List<Session> sessions = webSockets.get(drawingId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    /**
     * Broadcasts the newly added shape to all web sockets associated with the
     * affected drawing.
     *
     * @param drawingId ID of the affected drawing.
     * @param shape Shape that was added to the drawing or
     * {@link ShapeCoding#SHAPE_CLEAR_ALL} if the drawing was cleared (i.e. all
     * shapes were deleted).
     */
    private static void wsBroadcast(int drawingId, Shape shape) {
        List<Session> sessions = webSockets.get(drawingId);
        if (sessions != null) {
            for (Session session : sessions) {
                try {
                    session.getBasicRemote().sendObject(shape);
                } catch (IOException | EncodeException ex) {
                    Logger.getLogger(DataProvider.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * DrawingsCacheEventListener to propagate events back to clients
     *
     * @author mbraeuer
     */
    public static class DrawingsCacheEventListener implements CacheEntryCreatedListener<Integer, Drawing>,
            CacheEntryRemovedListener<Integer, Drawing>,
            CacheEntryUpdatedListener<Integer, Drawing>, Serializable {

        @Override
        public void onRemoved(Iterable<CacheEntryEvent<? extends Integer, ? extends Drawing>> events)
                throws CacheEntryListenerException {

            for (CacheEntryEvent<? extends Integer, ? extends Drawing> event : events) {
                sseBroadcaster.broadcast(new OutboundEvent.Builder()
                        .name("delete")
                        .data(String.class, String.valueOf(((Drawing) event.getOldValue()).getId()))
                        .build());
            }

        }

        @Override
        public void onUpdated(Iterable<CacheEntryEvent<? extends Integer, ? extends Drawing>> events)
                throws CacheEntryListenerException {

            for (CacheEntryEvent<? extends Integer, ? extends Drawing> event : events) {
                Drawing drawing = (Drawing) event.getValue();
                List<Shape> shapes = drawing.getShapes();
                Shape shape = shapes.get(shapes.size() - 1);
                wsBroadcast(drawing.getId(), shape);
            }
        }

        @Override
        public void onCreated(Iterable<CacheEntryEvent<? extends Integer, ? extends Drawing>> events)
                throws CacheEntryListenerException {

            for (CacheEntryEvent<? extends Integer, ? extends Drawing> event : events) {
                sseBroadcaster.broadcast(new OutboundEvent.Builder()
                        .name("create")
                        .data(Drawing.class, event.getValue())
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .build());

            }
        }
    }

}
