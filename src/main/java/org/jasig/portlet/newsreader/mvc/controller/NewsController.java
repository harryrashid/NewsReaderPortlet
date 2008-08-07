/*
Copyright (c) 2008, News Reader Portlet Development Team
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of conditions and the following
  disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
  disclaimer in the documentation and/or other materials provided with the distribution.
* Neither the name of the News Reader Portlet Development Team nor the names of its contributors may be used to endorse or
  promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.jasig.portlet.newsreader.mvc.controller;

import com.sun.syndication.feed.synd.SyndFeed;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jasig.portlet.newsreader.NewsConfiguration;
import org.jasig.portlet.newsreader.adapter.INewsAdapter;
import org.jasig.portlet.newsreader.adapter.NewsException;
import org.jasig.portlet.newsreader.dao.NewsStore;
import org.jasig.portlet.newsreader.service.IInitializationService;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.portlet.ModelAndView;
import org.springframework.web.portlet.mvc.AbstractController;

import javax.portlet.*;
import java.util.*;

/*
 * @author Anthony Colebourne
 */
public class NewsController extends AbstractController {

    private static Log log = LogFactory.getLog(NewsController.class);

    public ModelAndView handleRenderRequestInternal(RenderRequest request,
                                                    RenderResponse response) throws Exception {

        Map<String, Object> model = new HashMap<String, Object>();
        PortletSession session = request.getPortletSession(true);

        Map userinfo = (Map) request.getAttribute(PortletRequest.USER_INFO);

        // get this portlet's unique subscription id
        String subscribeId = (String) userinfo.get(userToken);

        /**
         * If this is a new session, perform any necessary
         * portlet initialization.
         */

        if (session.getAttribute("initialized") == null) {

            // get a set of all role names currently configured for
            // default newss
            List<String> allRoles = newsStore.getUserRoles();
            log.debug("all roles: " + allRoles);

            // determine which of the above roles the user belongs to
            // and store the resulting list in the session
            Set<String> userRoles = new HashSet<String>();
            for (String role : allRoles) {
                if (request.isUserInRole(role))
                    userRoles.add(role);
            }
            session.setAttribute("userRoles", userRoles);

            // determine if this user belongs to the defined news
            // administration group and store the result in the session
            session.setAttribute("isAdmin",
                    request.isUserInRole("newsAdmin"),
                    PortletSession.APPLICATION_SCOPE);

            // update the user's news subscriptions to include
            // any news that have been associated with his or
            // her role
            newsStore.initNews(subscribeId, userRoles);

            // set the default number of days to display
            session.setAttribute("items", defaultItems);

            // perform any other configured initialization tasks
            for (IInitializationService service : initializationServices) {
                service.initialize(request);
            }

            // mark this session as initialized
            session.setAttribute("initialized", "true");
            session.setMaxInactiveInterval(60 * 60 * 2);

        } else {


        }

        /**
         * Get all the subscribed feeds for this user, and add them to our feed list
         */
        List<NewsConfiguration> feeds = newsStore.getNewsConfigurations(subscribeId);
        model.put("feeds", feeds);


        SyndFeed feed = null;
        ApplicationContext ctx = this.getApplicationContext();
        List<String> errors = new ArrayList<String>();

        for (NewsConfiguration feedConfig : feeds) {
            // only bother to fetch the active feed
            if (feedConfig.isActive()) {
                log.debug("On render Active feed is " + feedConfig.getId());
                try {
                    // get an instance of the adapter for this feed
                    INewsAdapter adapter = (INewsAdapter) ctx.getBean(feedConfig.getNewsDefinition().getClassName());
                    // retrieve the feed from this adaptor
                    feed = adapter.getSyndFeed(feedConfig, request);
                    log.debug("Got feed from adapter");

                    model.put("feed", feed);
                    model.put("feedId", feedConfig.getId());

                } catch (NoSuchBeanDefinitionException ex) {
                    log.error("News class instance could not be found: " + ex.getMessage());
                } catch (NewsException ex) {
                    log.warn(ex);
                    errors.add("The news \"" + feedConfig.getNewsDefinition().getName() + "\" is currently unavailable.");
                }
            }
        }

        model.put("errors", errors);
        log.debug("forwarding to /viewNews");
        return new ModelAndView("viewNews", "model", model);
    }


    @Override
    protected void handleActionRequestInternal(ActionRequest request, ActionResponse response) throws Exception {
        log.debug("handleActionRequestInternal");

        Map userinfo = (Map) request.getAttribute(PortletRequest.USER_INFO);
        String subscribeId = (String) userinfo.get(userToken);

        String activeateNews = request.getParameter("activeateNews");
        if (activeateNews != null) {
            Long activeateNewsId = Long.valueOf(activeateNews);

            // update all relivant feeds!
            List<NewsConfiguration> feeds = newsStore.getNewsConfigurations(subscribeId);
            for (NewsConfiguration feedConfig : feeds) {
                if (activeateNewsId.compareTo(feedConfig.getId()) == 0) {
                    feedConfig.setActive(true);
                    newsStore.storeNewsConfiguration(feedConfig);
                    log.debug("Set Active and save " + feedConfig.getId());
                } else {
                    feedConfig.setActive(false);
                    newsStore.storeNewsConfiguration(feedConfig);
                    log.debug("Clear Active and save " + feedConfig.getId());
                }
            }
        }
    }

    private NewsStore newsStore;

    public void setNewsStore(NewsStore newsStore) {
        this.newsStore = newsStore;
    }

    private String userToken = "user.login.id";

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    private int defaultItems = 2;

    public void setDefaultDays(int defaultItems) {
        this.defaultItems = defaultItems;
    }

    private List<IInitializationService> initializationServices;

    public void setInitializationServices(List<IInitializationService> services) {
        this.initializationServices = services;
    }

}

/*
* NewsController.java
*
* Copyright (c) April 17, 2008 The University of Manchester. All rights reserved.
*
* THIS SOFTWARE IS PROVIDED "AS IS," AND ANY EXPRESS OR IMPLIED WARRANTIES,
* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
* FITNESS FOR A PARTICULAR PURPOSE, ARE EXPRESSLY DISCLAIMED. IN NO EVENT SHALL
* MANCHESER UNIVERSITY OR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT,
* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED, THE COSTS OF PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED IN ADVANCE OF THE POSSIBILITY OF SUCH DAMAGE.
*
* Redistribution and use of this software in source or binary forms, with or
* without modification, are permitted, provided that the following conditions
* are met.
*
* 1. Any redistribution must include the above copyright notice and disclaimer
* and this list of conditions in any related documentation and, if feasible, in
* the redistributed software.
*
* 2. Any redistribution must include the acknowledgment, "This product includes
* software developed by The University of Manchester," in any related documentation and, if
* feasible, in the redistributed software.
*
* 3. The names "Manchester University" and "The University of Manchester" must not be used to endorse or
* promote products derived from this software.
*/