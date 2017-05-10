package est.server.http.service.netty.http.websocket;


import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import est.devserver.Log;
import est.server.http.service.controller.ControllerHandler;
import est.server.http.service.controller.http.websocket.ControllerWebSocket;
import est.server.http.service.netty.http.HttpServerHandler;


public class WebSocketServerHandler extends HttpServerHandler {
	
	private static final String WEBSOCKET_PATH = "/websocket";
	
	
	private boolean protocolIsUpgradedToWebSocket;
	private ControllerHandler<ControllerWebSocket> controllerHandlerWebSocket;
	private WebSocketResource webSocketResource;
	private WebSocketServerHandshaker handshaker;
	
	public WebSocketServerHandler() {}
	
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		Object msg = e.getMessage();
		if (msg instanceof HttpRequest) {
			handleHttpRequest(ctx, (HttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			/*
			 * if (this.getDeviceServer().getServerStatus() != ServerStatus.Running) { throw new Exception("Device server not ready yet"); }
			 */
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		Channel channel = ctx.getChannel();
		
		if (!this.protocolIsUpgradedToWebSocket) {
			super.exceptionCaught(ctx, e);
		} else {
			if (channel.isOpen()) {
				TextWebSocketFrame frame = new TextWebSocketFrame(this.catchException(e.getCause()));
				channel.write(frame);
			}
		}
	}
	
	private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
		
		if (frame instanceof TextWebSocketFrame) {
			TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
			this.webSocketResource.receive(textFrame.getText());
		} else if (frame instanceof CloseWebSocketFrame) {
			this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
		} else if (frame instanceof PingWebSocketFrame) {
			ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
		}
	}
	
	protected boolean handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
		
		boolean result = false;
		
		boolean isUpgradePacket = Values.UPGRADE.equalsIgnoreCase(request.getHeader(Names.CONNECTION));
		boolean isUpgradeToWS = isUpgradePacket && Values.WEBSOCKET.equalsIgnoreCase(request.getHeader(Names.UPGRADE));
		
		// Если это не переключение на WS, передаем на обработку как обычный http пакет.
		if (!isUpgradeToWS && super.handleHttpRequest(ctx, request, false)) {
			result = true;
		}
		
		// Принимаем только GET запросы
		/*
		 * if (request.getMethod() != HttpMethod.GET) { HttpResponse response = this.createResponse(FORBIDDEN, request);
		 * this.sendHttpResponse(ctx.getChannel(), response, true); return false; }
		 */
		
		// Процедура рукопожатия (Хэндшейк)
		if (request.getMethod() == HttpMethod.GET && isUpgradeToWS) {
			
			WebSocketServerHandshakerFactory wsFactory =
					new WebSocketServerHandshakerFactory(this.getWebSocketLocation(request), null, false);
			
			this.handshaker = wsFactory.newHandshaker(request);
			
			if (this.handshaker == null) {
				wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
			} else {
				this.handshaker.handshake(ctx.getChannel(), request);
			}
			// Формирует хэндшейк ответ (handshake response).
			/*
			 * HttpResponse response = new DefaultHttpResponse(HTTP_1_1, new HttpResponseStatus(HttpResponseStatus.SWITCHING_PROTOCOLS.getCode(),
			 * "Web Socket Protocol Handshake")); response.addHeader(Names.UPGRADE, Values.WEBSOCKET); response.addHeader(Names.CONNECTION,
			 * Values.UPGRADE); // Заполянем заголовки в заивисимотси от метода рукопожатия. if (request.containsHeader(SEC_WEBSOCKET_KEY1) &&
			 * request.containsHeader(SEC_WEBSOCKET_KEY2)) { this.prepareHandshake(request, response); } else { this.prepareHandshakeSimple(request,
			 * response); }
			 */
			
			// Обновление соединения и отправка хэндшейк-ответа
			ChannelPipeline pipeline = ctx.getChannel().getPipeline();
			
			QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
			
			/* this.upgradePipelineToWebSocket(pipeline, ctx, request, response); */
			this.protocolIsUpgradedToWebSocket = true;
			this.webSocketResource = new WebSocketResource(pipeline.getChannel(), request.getUri());
			
			/*
			 * if (this.getDeviceServer().getServerStatus() != ServerStatus.Running) { throw new Exception("Device server not ready yet"); }
			 */
			
			ControllerWebSocket controller = this.controllerHandlerWebSocket.getController(decoder.getPath());
			
			if (controller == null) {
				throw new Exception(String.format("For uri %s controller not exists.", decoder.getPath()));
			}
			
			controller.subscribe(webSocketResource);
			
			Log.ltechSupport.debug(String.format("Uri WebSocket '%s' has newcommers", request.getUri()));
			result = true;
		}
		
		// Во всех остальных случаях отправляем ошибку доступа
		if (!result) {
			this.sendHttpResponse(ctx.getChannel(), new DefaultHttpResponse(HTTP_1_1, FORBIDDEN), true);
		}
		
		return result;
	}
	
	/**
	 * Переключаем Pipeline на протокол WebSocket.
	 * 
	 * @param pipeline
	 * @param ctx
	 * @param request
	 * @param response
	 */
	/*
	 * private void upgradePipelineToWebSocket(ChannelPipeline pipeline, ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
	 * ChannelPipeline p = ctx.getChannel().getPipeline(); String versionStr = request.getHeader(HttpHeaders.Names.SEC_WEBSOCKET_VERSION); int version
	 * = Integer.parseInt(versionStr); p.remove("aggregator"); p.replace("decoder", "wsdecoder", this.getWebSocketFrameDecoder(version));
	 * //this.sendHttpResponse(ctx.getChannel(), response); ctx.getChannel().write(response); p.replace("encoder",
	 * "wsencoder",this.getWebSocketFrameEncoder(version)); }
	 */
	
	/**
	 * Заполняет поля {@link HttpResponse} для ответного рукопожатия.
	 * 
	 * @param request
	 *            Полученный запрос на рукопожатие.
	 * @param response
	 *            Ответ на рукопожатие, в котором заполняются необходимые поля.
	 * @throws NoSuchAlgorithmException
	 */
	/*
	 * private void prepareHandshake(HttpRequest request, HttpResponse response) throws NoSuchAlgorithmException{ // Вызов нового метода хэндшейка
	 * response.addHeader(SEC_WEBSOCKET_ORIGIN, request.getHeader(ORIGIN)); response.addHeader(SEC_WEBSOCKET_LOCATION, getWebSocketLocation(request));
	 * String protocol = request.getHeader(SEC_WEBSOCKET_PROTOCOL); if (protocol != null) { response.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol); } //
	 * Вычисляем ответ String key1 = request.getHeader(SEC_WEBSOCKET_KEY1); String key2 = request.getHeader(SEC_WEBSOCKET_KEY2); int a = (int)
	 * (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1 .replaceAll("[^ ]", "").length()); int b = (int)
	 * (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2 .replaceAll("[^ ]", "").length()); long c = request.getContent().readLong();
	 * ChannelBuffer input = ChannelBuffers.buffer(16); input.writeInt(a); input.writeInt(b); input.writeLong(c); ChannelBuffer output =
	 * ChannelBuffers .wrappedBuffer(MessageDigest.getInstance("MD5").digest( input.array())); response.setContent(output); }
	 */
	
	/**
	 * Заполняет поля {@link HttpResponse} для ответного рукопожатия.
	 * 
	 * @param request
	 *            Полученный запрос на рукопожатие.
	 * @param response
	 *            Ответ на рукопожатие, в котором заполняются необходимые поля.
	 */
	/*
	 * private void prepareHandshakeSimple(HttpRequest request, HttpResponse response){ // Обработка старого метода хэндшейка
	 * response.addHeader(WEBSOCKET_ORIGIN, request.getHeader(ORIGIN)); response.addHeader(WEBSOCKET_LOCATION, getWebSocketLocation(request)); String
	 * protocol = request.getHeader(WEBSOCKET_PROTOCOL); if (protocol != null) { response.addHeader(WEBSOCKET_PROTOCOL, protocol); } }
	 */
	
	/**
	 * Опираясь на версию, выдает {@link FrameDecoder}
	 * 
	 * @param version
	 *            Версия протокола WebSocket.
	 * @return {@link FrameDecoder} для указанной версии.
	 */
	/*
	 * private FrameDecoder getWebSocketFrameDecoder(int version) { FrameDecoder result = null; switch (version) { case 13: result = new
	 * WebSocket13FrameDecoder(true, true); break; case 8: result = new WebSocket08FrameDecoder(true, true); break; default: result = new
	 * WebSocket00FrameDecoder(); break; } return result; }
	 */
	
	/**
	 * Опираясь на версию, выдает {@link OneToOneEncoder}
	 * 
	 * @param version
	 *            Версия протокола WebSocket.
	 * @return {@link OneToOneEncoder} для указанной версии.
	 */
	/*
	 * private OneToOneEncoder getWebSocketFrameEncoder(int version) { OneToOneEncoder result = null; switch (version) { case 13: result = new
	 * WebSocket13FrameEncoder(true); break; case 8: result = new WebSocket08FrameEncoder(true); break; default: result = new
	 * WebSocket00FrameEncoder(); break; } return result; }
	 */
	
	private String getWebSocketLocation(HttpRequest req) {
		return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + WEBSOCKET_PATH;
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
