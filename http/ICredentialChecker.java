package est.server.http.service.netty.http;

/**
 * @author utegental
 */
public interface ICredentialChecker {
	boolean check(String login, String password) throws Exception;
}
