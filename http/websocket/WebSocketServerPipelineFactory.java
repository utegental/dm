package est.server.http.service.netty.http.websocket;


import est.server.http.service.controller.ControllerHandler;
import est.server.http.service.controller.http.websocket.ControllerWebSocket;
import est.server.http.service.netty.http.HttpServerPipelineFactory;


public class WebSocketServerPipelineFactory extends HttpServerPipelineFactory {
	
	private ControllerHandler<ControllerWebSocket> controllerHandlerWebSocket;
	
	public WebSocketServerPipelineFactory() {
		
	}
	
	/*
	 * @Override public ChannelPipeline getPipeline() throws Exception { ChannelPipeline pipeline = super.getPipeline(); SSLEngine engine =
	 * WebSocketSslServerSslContext.getInstance().getServerContext().createSSLEngine(); engine.setUseClientMode(false); pipeline.addLast("ssl", new
	 * SslHandler(engine)); return pipeline; }
	 */
	
	@Override
	protected WebSocketServerHandler createHandler() {
		WebSocketServerHandler result = new WebSocketServerHandler();
		result.setControllerHandlerHttp(this.getControllerHandlerHttp());
		result.setControllerHandlerWebSocket(this.getControllerHandlerWebSocket(true));
		result.setAuthorizationChecker(this.getAuthorizationChecker());
		return result;
	}
	
	/**
	 * @return the controllerHandlerWebSocket
	 */
	public ControllerHandler<ControllerWebSocket> getControllerHandlerWebSocket() {
		return controllerHandlerWebSocket;
	}
	
	/**
	 * Возвращает {@link ControllerHandler}, который содержит контроллеры для обработки webSocket-подписок.
	 * 
	 * @param withNullCheck
	 * @return
	 */
	public ControllerHandler<ControllerWebSocket> getControllerHandlerWebSocket(boolean withNullCheck) {
		if (withNullCheck && controllerHandlerWebSocket == null) {
			throw new NullPointerException();
		}
		return controllerHandlerWebSocket;
	}
	
	/**
	 * @param controllerHandlerWebSocket
	 *            the controllerHandlerWebSocket to set
	 */
	public void setControllerHandlerWebSocket(ControllerHandler<ControllerWebSocket> controllerHandlerWebSocket) {
		this.controllerHandlerWebSocket = controllerHandlerWebSocket;
	}
	
}