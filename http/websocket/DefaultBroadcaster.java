package est.server.http.service.netty.http.websocket;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * 
 * @author utegental
 * 
 */
public class DefaultBroadcaster implements Broadcaster {
	
	protected ConcurrentLinkedQueue<WebSocketResource> queue = new ConcurrentLinkedQueue<WebSocketResource>();
	protected ThreadPoolExecutor threadPool;
	
	private final BroadcasterEventHandler eventHandler;
	
	
	public DefaultBroadcaster() {
		this.threadPool = new ThreadPoolExecutor(5, 5, 5, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		this.eventHandler = new BroadcasterEventHandler();
	}
	
	
	@Override
	public Future<Boolean> broadcast(final String value) {
		FutureTask<Boolean> task = new FutureTask<Boolean>(new Callable<Boolean>() {
			
			@Override
			public Boolean call() throws Exception {
				Iterator<WebSocketResource> iterator = queue.iterator();
				
				while (iterator.hasNext()) {
					WebSocketResource resource = iterator.next();
					
					if (resource.isConnected()) {
						/* ChannelFuture future = */resource.send(value);
					} else {
						queue.remove(resource);
					}
					
					// future.addListener()
					
				}
				
				return true;
			}
		});
		
		this.threadPool.execute(task);
		
		return task;
	}
	
	@Override
	public void addResource(final WebSocketResource resource) {
		queue.add(resource);
		
		resource.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
			
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				removeResource(resource);
			}
		});
	}
	
	
	@Override
	public void removeResource(WebSocketResource resource) {
		queue.remove(resource);
		
		if (queue.isEmpty() && eventHandler.hasListeners()) {
			eventHandler.empty(this);
		}
	}
	
	
	public BroadcasterEventHandler getEventHandler() {
		return eventHandler;
	}
}
