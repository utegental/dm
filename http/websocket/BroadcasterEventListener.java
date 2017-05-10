package est.server.http.service.netty.http.websocket;

import java.util.EventListener;

/**
 * @author utegental
 */
public interface BroadcasterEventListener extends EventListener {
	void empty(Broadcaster sender);
}
