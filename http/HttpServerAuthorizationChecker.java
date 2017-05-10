package est.server.http.service.netty.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.CharsetUtil;

public class HttpServerAuthorizationChecker {
	
	private ICredentialChecker credentialChecker;
	
	/**
	 * Check authorization header and check allowable.
	 * 
	 * @param request
	 *            Target request.
	 * @return True, if access authorized.
	 * @throws Exception
	 */
	public boolean checkAuthorization(HttpRequest request) throws Exception {
		boolean result = false;
		
		String auth = request.getHeader(HttpHeaders.Names.AUTHORIZATION);
		
		if (auth != null && !auth.isEmpty()) {
			
			String[] values = auth.split(" ");
			
			if (values[0].equals("Basic")) {
				
				String hash = values[1];
				if (hash != null && !hash.isEmpty()) {
					ChannelBuffer buff = Base64.decode(ChannelBuffers.copiedBuffer(hash, CharsetUtil.UTF_8));
					
					String origin = buff.toString(CharsetUtil.UTF_8);
					
					if (origin != null && !origin.isEmpty()) {
						String[] credential = origin.split(":");
						String login = credential[0];
						String password = credential[1];
						
						if (credentialChecker == null) {
							throw new NullPointerException("CredentialChecker field not set!");
						}
						
						result = this.credentialChecker.check(login, password);
					}
				}
			}
		}
		
		return result;
	}
	
	public ICredentialChecker getCredentialChecker() {
		return credentialChecker;
	}
	
	public void setCredentialChecker(ICredentialChecker credentialChecker) {
		this.credentialChecker = credentialChecker;
	}
}
