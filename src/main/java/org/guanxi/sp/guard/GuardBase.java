//: "The contents of this file are subject to the Mozilla Public License
//: Version 1.1 (the "License"); you may not use this file except in
//: compliance with the License. You may obtain a copy of the License at
//: http://www.mozilla.org/MPL/
//:
//: Software distributed under the License is distributed on an "AS IS"
//: basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//: License for the specific language governing rights and limitations
//: under the License.
//:
//: The Original Code is Guanxi (http://www.guanxi.uhi.ac.uk).
//:
//: The Initial Developer of the Original Code is Alistair Young alistair@codebrane.com
//: All Rights Reserved.
//:

package org.guanxi.sp.guard;

import org.guanxi.common.Pod;
import org.guanxi.common.filters.FileName;
import org.apache.log4j.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.*;
import java.io.IOException;
import java.rmi.server.UID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for Guards
 *
 * @author alistair
 */
public abstract class GuardBase implements Filter {
  /** Our logger */
  protected Logger logger = null;
  /** This Filter's config object as set by the container */
  protected FilterConfig filterConfig = null;
  /** The Guard's config */
  protected GuardConfig guardConfig = null;
  /** The name of the cookie the Guard uses to store a Pod of attributes */
  protected String cookieName = null;

  /**
   * Deactivates a Pod. This will cause a pod to disappear
   * from the servlet context and thus start a new Shibboleth
   * protocol session.
   *
   * @param pod Pod that needs deactivating
   */
  public static void deactivatePod(Pod pod) {
    if (pod != null) {
      pod.getContext().setAttribute(pod.getSessionID(), null);
    }
  }

  /**
   * The Guard's entry point from the servlet container. This is where the action happens.
   * All extending classes must implement this method.
   *
   * @param request Servlet request
   * @param response Servlet response
   * @param filterChain the filter chain
   * @throws IOException if an error occurs
   * @throws ServletException if an error occurs
   */
  public abstract void doFilter(ServletRequest request, ServletResponse response,
                                FilterChain filterChain) throws IOException, ServletException;

  /**
   * Does all the base class initialisation
   *
   * @param config The Guard's config
   */
  protected void initBase(FilterConfig config) {
    logger = Logger.getLogger(this.getClass().getName());

    // Store the config for later
    filterConfig = config;

    // Make the config available to the rest of the Guard as an XMLBeans Guard object
    String configFilePath = null;
    if ((filterConfig.getInitParameter("configFile") != null) &&
        (filterConfig.getInitParameter("configFile").length() > 0)) {
      Pattern pattern = Pattern.compile("^\\$\\{(.*)}(.*)");
      Matcher matcher = pattern.matcher(filterConfig.getInitParameter("configFile"));
      if (matcher.matches()) {
        configFilePath = System.getenv(matcher.group(1)) + matcher.group(2);
      }
      else {
        configFilePath = filterConfig.getInitParameter("configFile");
      }
    }
    else {
      configFilePath = config.getServletContext().getRealPath("/WEB-INF/guanxi_sp_guard/config/guanxi-sp-guard.properties");
    }
    logger.info("Setting config to " + configFilePath);
    guardConfig = new GuardConfig(configFilePath);

    filterConfig.getServletContext().setAttribute(Definitions.CONTEXT_ATTR_GUARD_CONFIG,
                                                  guardConfig);

    /* Register our ID and cookie details in the servlet context
     * to allow applications to know who their Guard is.
     */
    filterConfig.getServletContext().setAttribute(Definitions.CONTEXT_ATTR_GUARD_ID,
                                                  guardConfig.get("entityid"));
    filterConfig.getServletContext().setAttribute(Definitions.CONTEXT_ATTR_GUARD_COOKIE_PREFIX,
                                                  guardConfig.get("cookie.prefix"));
    filterConfig.getServletContext().setAttribute(Definitions.CONTEXT_ATTR_GUARD_COOKIE_NAME,
                                                  guardConfig.get("cookie.prefix") + guardConfig.get("entityid"));
    
    logger.info("Found entityid:" + guardConfig.get("entityid"));
    logger.info("Found host.name:" + guardConfig.get("host.name"));

    // The cookie name can be changed at runtime
    cookieName = guardConfig.get("cookie.prefix") + FileName.encode(guardConfig.get("entityid"));
  }

  /**
   * Processes the cookies in the request, cleaning up any Guard ones that are devoid
   * of Pods.
   *
   * @param httpRequest Servlet request
   * @param httpResponse Servlet response
   * @return Pod object if one is referenced by a cookie and it's a valid Pod
   */
  protected Pod doCookies(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    Cookie[] cookies = httpRequest.getCookies();
    if (cookies != null) {
      for (int i = 0; i < cookies.length; i++) {
        logger.debug("Found cookie : " + cookies[i].getName());
        if (cookies[i].getName().equals(cookieName)) {
          // See if there's a pod for the request
          Pod pod = (Pod)filterConfig.getServletContext().getAttribute(cookies[i].getValue());

          // If there isn't then we must get rid of the cookie
          if (pod == null) {
            logger.debug("Found a Guard cookie but no Pod of attributes : " + cookies[i].getName());
            cookies[i].setMaxAge(0);
            httpResponse.addCookie(cookies[i]);
          }
          else {
            logger.debug("Found a Guard cookie with a Pod of attributes : " + cookies[i].getName());

            // Add any new parameters to the original request
            pod.setRequestParameters(httpRequest.getParameterMap());
            return pod;
          }
        }
      }
    }

    return null;
  }

  /**
   * Creates and configures a Pod, ready for population with attributes.
   *
   * @param request Servlet request
   * @return An empty Pod configured for use with the Guard
   */
  protected Pod createPod(ServletRequest request) {
    HttpServletRequest httpRequest = (HttpServletRequest)request;

    // Create a new Pod to encapsulate information for this session
    Pod pod = new Pod();

    // Store the servlet context for later deactivation of the pod
    pod.setContext(filterConfig.getServletContext());

    // Store the original scheme and hostname
    pod.setRequestScheme(request.getScheme());
    pod.setHostName(httpRequest.getHeader("Host").replaceAll("/", ""));

    /* Store the parameters in the Pod as these are not guaranteed to be around in the
     * original request after the SAML workflow has finished. The servlet container will
     * only guarantee them for this request. After that, it can reuse the request object.
     */
    pod.setRequestParameters(request.getParameterMap());

    // Store the original URL including any query parameters
    if (((HttpServletRequest)request).getQueryString() != null)
      pod.setRequestURL(httpRequest.getRequestURI() + "?" + httpRequest.getQueryString());
    else
      pod.setRequestURL(httpRequest.getRequestURI());

    // Store the Pod in a session
    UID uid = new UID();
    String sessionID = "GUARD_" + uid.toString().replaceAll(":", "--");
    pod.setSessionID(sessionID);
    filterConfig.getServletContext().setAttribute(sessionID, pod);
    
    logger.info("Created pod: " + pod.getSessionID());

    return pod;
  }

  /**
   * Determines whether to invoke the Guard logic on a request.
   *
   * @param httpRequest Servlet request
   * @return true if the Guard logic should not be invoked, otherwise false to follow the
   * current profile
   */
  protected boolean passthru(HttpServletRequest httpRequest) {
    // Don't block web service calls from a Guanxi SAML Engine
    if ((httpRequest.getRequestURI().endsWith("guard.sessionVerifier")) ||
    		(httpRequest.getRequestURI().endsWith("guard.guanxiGuardACS")) ||
    		(httpRequest.getRequestURI().endsWith(getLogoutPage(httpRequest))) ||
    		(httpRequest.getRequestURI().endsWith("guard.guanxiGuardPodder")) ||
        customPassThru(httpRequest) ||
    		checkSkipFilter(httpRequest)) {
      return true;
    }

      return false;
  }

  /**
   * Determines whether a URL should get past the Guard.
   *
   * This is determined by the setting in guanxi-sp-guard.properties:
   * custom.passthru.urls
   *
   * @param httpRequest Servlet request
   * @return true if the URL should not be challenged otherwise false and the Guard
   * logic is invoked
   */
  protected boolean customPassThru(HttpServletRequest httpRequest) {
    GuardConfig config = (GuardConfig)filterConfig.getServletContext().getAttribute(Definitions.CONTEXT_ATTR_GUARD_CONFIG);
    if (config.get("custom.passthru.urls") == null) return false;

    String[] customPassThruURLs = config.get("custom.passthru.urls").split(",");
    for (String customPassThruURL : customPassThruURLs) {
      Pattern regex = Pattern.compile(customPassThruURL);
      Matcher matcher = regex.matcher(httpRequest.getServletPath());
      if (matcher.find()) {
        return true;
      }
    }

    return false;
  }

  protected void gotoEngineGPS(String sessionID, ServletRequest request, ServletResponse response) {
    try {
      GuardConfig config = (GuardConfig)filterConfig.getServletContext().getAttribute(Definitions.CONTEXT_ATTR_GUARD_CONFIG);

      String engineGPSService = config.get("engine.gps.service.url");
      engineGPSService += "?" + Definitions.WAYF_PARAM_GUARD_ID + "=" + config.get("entityid");
      engineGPSService += "&" + Definitions.WAYF_PARAM_SESSION_ID + "=" + sessionID;
      if (request.getParameter("entityID") != null) {
        engineGPSService += "&" + "entityID" + "=" + request.getParameter("entityID");
      }
      else {
        // If no entityID is specified in the URL, try to use the default one
        if ((config.get("default.entity.id") != null) &&
            (!config.get("default.entity.id").equals(""))) {
          engineGPSService += "&" + "entityID" + "=" + config.get("default.entity.id");
        }
      }

      ((HttpServletResponse)response).sendRedirect(engineGPSService);
    }
    catch (IOException ioe) {
      logger.error("Engine GPS Service not responding", ioe);
      request.setAttribute("ERROR_ID", "ID_WAYF_WS_NOT_RESPONDING");
      request.setAttribute("ERROR_MESSAGE", ioe.getMessage());
      try {
        request.getRequestDispatcher("/WEB-INF/guanxi_sp_guard/jsp/sp_error.jsp").forward(request, response);
      }
      catch (Exception ex) {}
    }
  }

  /**
   * Opportunity for extending filters to do some work before calling the next filter in the chain
   *
   * @param request the current request
   * @return the URL of the logout page
   */
  protected String getLogoutPage(HttpServletRequest request) {
	  return "guard.guanxiGuardlogout";
  }

  /**
   * Opportunity for extending filters to bypass Guard filtering
   *
   * @param request the current request
   * @return true if the current request should be free of Guard interference
   */
  protected boolean checkSkipFilter(HttpServletRequest request) {
	  return false;
  }

  /**
   * Opportunity for extending filters to do some work before calling the next filter in the chain
   *
   * @param guardRequest the current request
   */
  protected void preSuccessFilterChain(GuardRequest guardRequest) {
	  //override to do application specific setup
  }

  /**
   * Opportunity for extending filters to dynamically control the guard id
   *
   * @param id the current ID of the Guard
   * @param httpRequest Servlet request
   * @return the new ID of the Guard. Could be the same as the current ID.
   */
  protected String postProcessGetGuardId(String id, HttpServletRequest httpRequest) {
	  return id;
  }
}
