package est.server.http.service.netty.http;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

import est.server.http.commons.ssl.SslContextFactory;
import est.server.http.service.controller.ControllerHandler;
import est.server.http.service.controller.ControllerHttp;


public class HttpServerPipelineFactory implements ChannelPipelineFactory {
	
	private final static int MAX_CONTENT_LENGTH = 65536;
	
	private boolean sslEnabled;
	private ControllerHandler<ControllerHttp> controllerHandlerHttp;
	protected HttpServerAuthorizationChecker authorizationChecker;
	
	
	public HttpServerPipelineFactory() {
		// this.sslEnabled = false;
		this.authorizationChecker = new HttpServerAuthorizationChecker();
		
		// this.authorizationChecker.setCredentialChecker(new CredentialCheckerStatic());
		// this.authorizationChecker.setCredentialChecker(new CredentialCheckerCloud());
	}
	
	@Override
	public ChannelPipeline getPipeline() throws Exception {
		
		ChannelPipeline pipeline = Channels.pipeline();
		HttpServerHandler handler = this.createHandler();
		
		pipeline.addLast("decoder", new HttpRequestDecoder());
		pipeline.addLast("encoder", new HttpResponseEncoder());
		pipeline.addLast("aggregator", new HttpChunkAggregator(MAX_CONTENT_LENGTH));
		
		
		if (sslEnabled) {
			SSLEngine sslEngine = SslContextFactory.getServerContext().createSSLEngine();
			
			sslEngine.setUseClientMode(false);
			
			SslHandler sslHandler = new SslHandler(sslEngine);
			// sslHandler.setIssueHandshake(issueHandshake)
			
			pipeline.addFirst("ssl", sslHandler);
			// pipeline.addAfter("encoder", "deflater", new HttpContentCompressor());
			
			
			handler.setSslEnabled(this.sslEnabled);
			
		}
		pipeline.addLast("handler", handler);
		/*
		 * Log.ltechSupport.info("Base64 decoding"); pipeline.addLast("base64Decoder", new Base64Decoder()); pipeline.addLast("base64Encoder", new
		 * Base64Encoder());
		 */
		
		return pipeline;
	}
	
	protected HttpServerHandler createHandler() {
		HttpServerHandler result = new HttpServerHandler();
		result.setControllerHandlerHttp(this.getControllerHandlerHttp(true));
		result.setAuthorizationChecker(this.authorizationChecker);
		return result;
	}
	
	
	/**
	 * @return the httpControllerHandler
	 */
	public ControllerHandler<ControllerHttp> getControllerHandlerHttp() {
		return controllerHandlerHttp;
	}
	
	/**
	 * Возвращает {@link ControllerHandler}, который содержит контроллеры для обработки http-запросов.
	 * 
	 * @param withNullCheck
	 *            Если true, Проверяет на равенство null и выдает эксепшн в худшем случае.
	 * @return the httpControllerHandler
	 */
	public ControllerHandler<ControllerHttp> getControllerHandlerHttp(boolean withNullCheck) {
		if (withNullCheck && controllerHandlerHttp == null) {
			throw new NullPointerException();
		}
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
}
