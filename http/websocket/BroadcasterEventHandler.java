package est.server.http.service.netty.http.websocket;

import java.util.ArrayList;
import java.util.List;


/**
 * @author utegental
 */
public class BroadcasterEventHandler {
	private List<BroadcasterEventListener> listenerList;
	
	public BroadcasterEventHandler() {
		listenerList = new ArrayList<BroadcasterEventListener>();
	}
	
	public void addListener(BroadcasterEventListener listener) {
		if (listener == null) {
			throw new NullPointerException();
		}
		listenerList.add(listener);
	}
	
	public void removeListener(BroadcasterEventListener listener) {
		if (listener == null) {
			throw new NullPointerException();
		}
		listenerList.remove(listener);
	}
	
	public void empty(Broadcaster sender) {
		for (BroadcasterEventListener item : listenerList) {
			item.empty(sender);
		}
	}
	
	public boolean hasListeners() {
		return listenerList.size() != 0;
	}
}
