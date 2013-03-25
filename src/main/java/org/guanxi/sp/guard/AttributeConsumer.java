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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.server.UID;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.guanxi.common.Bag;
import org.guanxi.common.GuanxiException;
import org.guanxi.common.Pod;

/**
 * The AttributeConsumer service will load up the Pod previously created by the Podder service and add
 * the SAML Attributes in a Bag which it will place in the Pod. These are convenience objects for getting
 * hold of attributes without having to parse raw SAML.
 * If an application requires access to the full SAML Response it can parse the String representation of
 * the raw SAML that the AttributeConsumer services stores in the Bag.
 *
 * @author Alistair Young alistair@codebrane.com
 * @author Davide Zanatta davide.zanatta@gmail.com - bug fixing
 * @author Marcin Mielnicki mielniczu@o2.pl - bug fixing
 */
@SuppressWarnings("serial")
public class AttributeConsumer extends HttpServlet {
  private static final Logger logger = Logger.getLogger(AttributeConsumer.class.getName());

  public void init() throws ServletException {
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    process(request, response);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    process(request, response);
  }

  public void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Bag bag = null;

    try {
      bag = getBag(request);
    }
    catch(GuanxiException ge) {
      logger.error("Error receiving attributes from Engine: " + ge.getMessage());
    }
    
    if(bag.isUnsolicitedMode())
    {
    	logger.info("Got unsolicited bag: " + bag.getSessionID());
    	
    	try {
			processUnsolicited(bag);
		} catch (Exception e) {
			logger.error("Error creating unsolicited bag: " + e.getMessage());
		}
    }
    
    logger.info("Processing bag: " + bag.getSessionID());
    
    // Load up the specified session's Pod...
    Pod pod = (Pod)getServletContext().getAttribute(bag.getSessionID());
    // ...and add the bag of attributes
    pod.setBag(bag);

    ServletOutputStream os = response.getOutputStream();
    os.write(pod.getSessionID().getBytes());
    os.close();
  }

  private void processUnsolicited(Bag bag) throws Exception {
	  //verify
	  
	  //createpod
	  Pod pod = createPod(bag);
	  
	  //reset the bags sessionid
	  bag.setSessionID(pod.getSessionID()); 
  }
  
  /**
   * Creates and configures a Pod, ready for population with attributes.
   *
   * @param request Servlet request
   * @return An empty Pod configured for use with the Guard
 * @throws URISyntaxException 
 * @throws Exception 
   */
  protected Pod createPod(Bag bag) throws Exception {

    // Create a new Pod to encapsulate information for this session
    Pod pod = new Pod();

    // Store the servlet context for later deactivation of the pod
    pod.setContext(getServletContext());

    // get the target resource from sessionid
    URI uri = new URI(bag.getSessionID());
    
    
    pod.setRequestScheme(uri.getScheme());
    pod.setHostName(uri.getHost().replaceAll("/", "") + ":" +  uri.getPort());
    pod.setRequestURL(uri.getPath() + uri.getQuery());

    // Store the Pod in a session
    UID uid = new UID();
    String sessionID = "GUARD_" + uid.toString().replaceAll(":", "--");
    pod.setSessionID(sessionID);
    getServletContext().setAttribute(sessionID, pod);

    return pod;
  }

  private Bag getBag(HttpServletRequest request) throws GuanxiException {
    String json = request.getParameter(Definitions.REQUEST_PARAMETER_SAML_ATTRIBUTES);
    if (json != null) {
      return new Bag(json);
    }
    else {
      throw new GuanxiException("No attributes");
    }
  }
}
