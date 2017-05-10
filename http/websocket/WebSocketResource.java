package est.server.http.service.netty.http.websocket;

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class WebSocketResource {
	
	private Channel channel;
	private String uri;
	private List<ReceiveListener> listenerList;
	
	public WebSocketResource(Channel channel) {
		this.channel = channel;
		this.listenerList = new LinkedList<ReceiveListener>();
	}
	
	public WebSocketResource(Channel channel, String uri) {
		this(channel);
		this.uri = uri;
	}
	
	public ChannelFuture send(String text) {
		return channel.write(new TextWebSocketFrame(text));
	}
	
	public boolean isConnected() {
		return this.channel.isConnected();
	}
	
	public void addReceiveListener(ReceiveListener listener) {
		this.listenerList.add(listener);
	}
	
	public void receive(String value) {
		for (ReceiveListener listener : this.listenerList) {
			listener.receive(value);
		}
	}
	
	public String getUri() {
		return uri;
	}
	
	public void setUri(String uri) {
		this.uri = uri;
	}
	
	public Channel getChannel() {
		return channel;
	}
}
