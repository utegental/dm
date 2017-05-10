package est.server.http.service.netty.http;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;

import est.devserver.Log;
import est.server.http.service.controller.ControllerHandler;
import est.server.http.service.controller.ControllerHttp;
import est.server.http.service.controller.ControllerHttpAsync;
import est.server.http.service.controller.ControllerHttpSync;
import est.server.http.service.controller.IControllerHttpListener;


public class HttpServerHandler extends /* SimpleChannelUpstreamHandler */SimpleChannelHandler {
	private static final String NO_MESSAGE = "no message";
	
	private boolean sslEnabled;
	
	private ControllerHandler<ControllerHttp> controllerHandlerHttp;
	private HttpServerAuthorizationChecker authorizationChecker;
	
	public HttpServerHandler() {}
	
	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		
		if (sslEnabled) {
			// Get the SslHandler in the current pipeline.
			// We added it in SecureChatPipelineFactory.
			// final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
			
			// Get notified when SSL handshake is done.
			/* ChannelFuture handshakeFuture = */// sslHandler.handshake();
		}
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		Object msg = e.getMessage();
		
		if (msg instanceof HttpRequest) {
			this.handleHttpRequest(ctx, (HttpRequest) msg, true);
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		this.catchException(e.getCause(), ctx.getChannel());
	}
	
	/**
	 * 
	 * @param ctx
	 * @param request
	 * @param sendFalseResponse
	 *            Отправлять ли отрицательные ответы в случае неизвестного запроса.
	 * @return
	 * @throws Exception
	 */
	protected boolean handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request, boolean sendFalseResponse)
			throws Exception {
		
		boolean result = false;
		boolean isAsynchController = false;
		HttpResponse response = null;
		final Channel channel = ctx.getChannel();
		
		Log.ltechSupport.debug(String.format("Uri '%s' requested", request.getUri()));
		
		
		if (authorizationChecker.checkAuthorization(request)) {
			QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
			ControllerHttp controller = controllerHandlerHttp.getController(decoder.getPath());
			
			if (controller != null) {
				Log.ltechSupport.debug(String.format("Controller %s thinking, that this uri is him.", controller
						.getClass()
						.getName()));
				if (controller instanceof ControllerHttpSync) {
					ControllerHttpSync controllerSynch = (ControllerHttpSync) controller;
					response = controllerSynch.proceed(request, decoder, channel.getRemoteAddress(), channel);
				} else if (controller instanceof ControllerHttpAsync) {
					isAsynchController = true;
					
					IControllerHttpListener listener = new IControllerHttpListener() {
						
						@Override
						public ChannelFuture sendResponse(HttpResponse response) {
							return sendHttpResponse(channel, response, true);
						}
					};
					
					ControllerHttpAsync controllerAsync = (ControllerHttpAsync) controller;
					controllerAsync.proceed(request, decoder, channel.getRemoteAddress(), channel, listener);
					result = true;
				}
			}
		} else {
			response = this.createResponse(HttpResponseStatus.UNAUTHORIZED, request);
			response.addHeader(HttpHeaders.Names.WWW_AUTHENTICATE, "Basic realm=\"EST device-server\"");
		}
		
		if (sendFalseResponse && response == null && !isAsynchController) {
			response = this.createResponse(HttpResponseStatus.NOT_FOUND, request);
		}
		
		if (response != null && !isAsynchController) {
			this.sendHttpResponse(channel, response, true);
			result = true;
			
			Log.ltechSupport.debug(String.format("Uri '%s' successfuly responsed", request.getUri()));
		}
		
		return result;
	}
	
	/**
	 * Catch exception and write info in console output and logger.
	 * 
	 * @param th
	 *            Caused exception.
	 * @return {@link String} message.
	 */
	protected String catchException(Throwable th) {
		String exceptionMessage = th.getMessage() == null ? NO_MESSAGE : th.getMessage();
		String errorMessage = String.format("%s - %s", th.getClass().getName(), exceptionMessage);
		
		StringBuilder sb = new StringBuilder();
		sb.append(errorMessage);
		
		for (StackTraceElement item : th.getStackTrace()) {
			sb.append('\n').append('\t');
			sb.append(item.toString());
		}
		String stackTraceWithError = sb.toString();
		
		Log.ltechSupport.info(stackTraceWithError);
		
		return stackTraceWithError;
	}
	
	/**
	 * Catch exception and write info in console output, in logger and in socket channel.
	 * 
	 * @param ex
	 *            Caused exception.
	 * @param responseStatus
	 *            Http status in response http packet.
	 * @param channel
	 *            Channel from client.
	 * @param request
	 *            Request from client.
	 */
	protected void catchException(Throwable th, Channel channel, HttpRequest request) {
		HttpResponse response = this.createResponse(HttpResponseStatus.FORBIDDEN, this.catchException(th), request);
		
		this.sendHttpResponse(channel, response, true);
	}
	
	protected void catchException(Throwable th, Channel channel) {
		HttpResponse response =
				this.createResponse(HttpResponseStatus.FORBIDDEN, HttpVersion.HTTP_1_1, false, this.catchException(th));
		if (channel != null && channel.isOpen() && channel.isConnected()) {
			this.sendHttpResponse(channel, response, true);
			Log.ltechSupport.info("Channel is closed.");
		}
	}
	
	protected ChannelFuture sendHttpResponse(Channel channel, HttpResponse response) {
		// Generate an error page if response status code is not OK (200).
		if (!response.getStatus().equals(HttpResponseStatus.OK) && response.getContent() == null) {
			response.setContent(ChannelBuffers.copiedBuffer(response.getStatus().toString(), CharsetUtil.UTF_8));
		}
		HttpHeaders.setContentLength(response, response.getContent().readableBytes());
		
		// Send the response and close the connection if necessary.
		return channel.write(response);
	}
	
	protected ChannelFuture sendHttpResponse(Channel channel, HttpResponse response, boolean withClosing) {
		boolean isOkResponse = response.getStatus().equals(HttpResponseStatus.OK);
		boolean isKeepAlive = HttpHeaders.isKeepAlive(response);
		
		// Send the response and close the connection if necessary.
		ChannelFuture f = this.sendHttpResponse(channel, response);
		if (!isKeepAlive || !isOkResponse || withClosing) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
		return f;
	}
	
	
	protected HttpResponse createResponse(HttpResponseStatus responseStatus, HttpRequest request) {
		return this.createResponse(responseStatus, request.getProtocolVersion(), HttpHeaders.isKeepAlive(request));
	}
	
	protected HttpResponse createResponse(HttpResponseStatus responseStatus, String content, HttpRequest request) {
		
		HttpResponse response = this.createResponse(responseStatus, request);
		response.setContent(ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8));
		
		return response;
	}
	
	protected HttpResponse createResponse(
			HttpResponseStatus responseStatus,
			HttpVersion httpVersion,
			boolean isKeepAlive) {
		HttpResponse response = new DefaultHttpResponse(httpVersion, responseStatus);
		response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
		
		HttpHeaders.setKeepAlive(response, isKeepAlive);
		
		return response;
	}
	
	protected HttpResponse createResponse(
			HttpResponseStatus responseStatus,
			HttpVersion httpVersion,
			boolean isKeepAlive,
			String content) {
		HttpResponse response = this.createResponse(responseStatus, httpVersion, isKeepAlive);
		response.setContent(ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8));
		
		return response;
	}
	
	
	/**
	 * @return the httpControllerHandler
	 */
	public ControllerHandler<ControllerHttp> getControllerHandlerHttp() {
		return controllerHandlerHttp;
	}
	
	/**
	 * @param httpControllerHandler
	 *            the httpControllerHandler to set
	 */
	public void setControllerHandlerHttp(ControllerHandler<ControllerHttp> controllerHandlerHttp) {
		this.controllerHandlerHttp = controllerHandlerHttp;
	}
	
	public HttpServerAuthorizationChecker getAuthorizationChecker() {
		return authorizationChecker;
	}
	
	public void setAuthorizationChecker(HttpServerAuthorizationChecker authorizationChecker) {
		this.authorizationChecker = authorizationChecker;
	}
	
	public boolean isSslEnabled() {
		return sslEnabled;
	}
	
	public void setSslEnabled(boolean sslEnabled) {
		this.sslEnabled = sslEnabled;
	}
	
	/**
	 * @return the deviceServer
	 */
	/*
	 * public ServerInstance getDeviceServer() { return deviceServer; }
	 *//**
	 * @param deviceServer
	 *            the deviceServer to set
	 */
	/*
	 * public void setDeviceServer(ServerInstance deviceServer) { this.deviceServer = deviceServer; }
	 */
}
