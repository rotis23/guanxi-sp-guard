package org.guanxi.sp.guard.dynamic;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.guanxi.common.Pod;
import org.guanxi.sp.guard.Definitions;
import org.guanxi.sp.guard.SessionVerifier;

/**
 * This class extends SessionVerifier to provide support
 * for systems with multitenancy (dynamic domains)
 * 
 * Use DYNAMIC_GUARD_DOMAIN in the guard Podder URL 
 * e.g. http://DYNAMIC_GUARD_DOMAIN/guard.guanxiGuardPodder
 * 
 * Use cookie.domain=.DYNAMIC_GUARD_DOMAIN in the guard config
 * 
 * The engine will ask the guard what external multitenancy domain to use 
 * 
 * @author rotis23
 */
@SuppressWarnings("serial")
public class DynamicSessionVerifier extends SessionVerifier 
{
	protected final Logger logger = Logger.getLogger(getClass());
	
	protected boolean processExtendedVerificationAttributes(HttpServletRequest request, PrintWriter out)
	{
		String dynamicDomainNameRequest = request.getParameter("dynamicDomainNameRequest");
		
		if(dynamicDomainNameRequest != null)
		{
			logger.debug("processExtendedVerificationAttributes: found dynamicDomainNameRequest");
			
			String sessionID = request.getParameter(Definitions.SESSION_VERIFIER_PARAM_SESSION_ID);
			
			Pod pod = (Pod)getServletContext().getAttribute(sessionID);
			
			if(pod != null)
			{
				logger.debug("processExtendedVerificationAttributes: returning dynamic domain: " + pod.getHostName());
				
				out.write(pod.getHostName());
			}
			else
			{
				logger.debug("processExtendedVerificationAttributes: did not find dynamic domain: sessionid:" + sessionID);
				
				out.write("nohostnamefound");
			}
			
			return true;
		}
		
		logger.debug("processExtendedVerificationAttributes: not a dynamicDomainNameRequest");
		
		return false;
	}

}