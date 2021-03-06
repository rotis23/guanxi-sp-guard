package org.guanxi.sp.guard.dynamic;

import javax.servlet.http.HttpServletRequest;

import org.guanxi.sp.guard.Podder;

/**
 * This class extends Podder to provide support
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
public class DynamicPodder extends Podder
{
	private String guardDomainPlaceholder = "DYNAMIC_GUARD_DOMAIN";

	protected String postProcessGetGuardId(String id, HttpServletRequest httpRequest)
	{
		return id.replace(guardDomainPlaceholder, httpRequest.getServerName());
	}

	public void setGuardDomainPlaceholder(String guardDomainPlaceholder) {
		this.guardDomainPlaceholder = guardDomainPlaceholder;
	}
}
