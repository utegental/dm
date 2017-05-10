package est.server.http.service.netty.http.websocket;

import java.util.Collection;
import java.util.List;

import est.server.http.core.ItemQueueListener;
import est.server.http.core.MessageQueue;
import org.codehaus.jackson.map.ObjectMapper;

import est.devserver.Log;
import est.server.http.commons.json.JsonBase;
import est.server.http.integration.reactor.ISender;

public class BroadcastSender<T extends JsonBase> implements ISender<T> {
	
	private ObjectMapper mapper;
	private Broadcaster broadcaster;
	private MessageQueue<T> messageQueue;
	private boolean withQueue;
	
	public BroadcastSender(Broadcaster broadcaster) {
		this.broadcaster = broadcaster;
		this.mapper = new ObjectMapper();
	}
	
	@Override
	public void send(List<T> list) {
		if (withQueue) {
			try {
				this.messageQueue.put(list);
			} catch (InterruptedException ex) {
				Log.ltechSupport.debug(ex.getMessage());
			}
		} else {
			this.performSend(list);
		}
	}
	
	@Override
	public void send(T value) {
		if (withQueue) {
			try {
				this.messageQueue.put(value);
			} catch (InterruptedException ex) {
				Log.ltechSupport.debug(ex.getMessage());
			}
		} else {
			this.performSend(value);
		}
	}
	
	private void performSend(T value) {
		try {
			this.broadcaster.broadcast(mapper.writeValueAsString(value));
		} catch (Exception ex) {
			Log.ltechSupport.debug(ex.getMessage());
		}
	}
	
	private void performSend(Collection<T> list) {
		try {
			this.broadcaster.broadcast(mapper.writeValueAsString(list));
		} catch (Exception ex) {
			Log.ltechSupport.debug(ex.getMessage());
		}
	}
	
	public Broadcaster getBroadcaster() {
		return broadcaster;
	}
	
	public void setBroadcaster(Broadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}
	
	public MessageQueue<T> getMessageQueue() {
		return messageQueue;
	}
	
	public void setMessageQueue(MessageQueue<T> messageQueue) {
		this.messageQueue = messageQueue;
		this.withQueue = true;
		this.messageQueue.getEventHandler().add(new ItemQueueListener<T>() {
			@Override
			public void pop(T item) {
				performSend(item);
			}
			
			@Override
			public void pop(Collection<T> collection) {
				performSend(collection);
			}
		});
		
	}
}
