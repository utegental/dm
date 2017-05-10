package est.server.http.service.netty.http.websocket;

import java.util.concurrent.Future;

/**
 * Broadcast sender.
 * 
 * @author utegental
 * 
 */
public interface Broadcaster {
	
	/**
	 * Send broadcast message to all resources.
	 * 
	 * @param value
	 *            Message.
	 * @return Future of thread, that perform sending.
	 */
	Future<Boolean> broadcast(final String value);
	
	/**
	 * Add resource
	 * 
	 * @param resource
	 */
	void addResource(WebSocketResource resource);
	
	void removeResource(WebSocketResource resource);
	
	BroadcasterEventHandler getEventHandler();
}
