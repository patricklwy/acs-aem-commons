/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2015 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.adobe.acs.commons.wcm.impl;

import com.adobe.acs.commons.json.AbstractJSONObjectVisitor;
import com.adobe.acs.commons.util.BufferingResponse;
import com.adobe.acs.commons.util.PathInfoUtil;
import com.day.cq.commons.jcr.JcrConstants;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestUtil;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URLDecoder;

/**
 * ACS AEM Commons - CQInclude Property Namespace
 */
@SuppressWarnings("serial")
@SlingServlet(
        selectors = "overlay.cqinclude.namespace",
        extensions = "json",
        resourceTypes = "sling/servlet/default")
public final class CQIncludePropertyNamespaceServlet extends SlingSafeMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(CQIncludePropertyNamespaceServlet.class);

    private static final String REQ_ATTR = CQIncludePropertyNamespaceServlet.class.getName() + ".processed";

    private static final String AEM_CQ_INCLUDE_SELECTORS = "overlay.infinity";

    private static final int NAME_PROPERTY_SELECTOR_INDEX = 3;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!this.accepts(request)) {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write(new JSONObject().toString());
        }

        /* Servlet accepts this request */
        RequestUtil.setRequestAttribute(request, REQ_ATTR, true);

        final String prefix =
                URLDecoder.decode(PathInfoUtil.getSelector(request, NAME_PROPERTY_SELECTOR_INDEX), "UTF-8");

        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        options.setReplaceSelectors(AEM_CQ_INCLUDE_SELECTORS);

        final BufferingResponse bufferingResponse = new BufferingResponse(response);
        request.getRequestDispatcher(request.getResource(), options).forward(request, bufferingResponse);


        try {
            final JSONObject json = new JSONObject(bufferingResponse.getContents());
            final NamePropertyUpdater namePropertyUpdater = new NamePropertyUpdater(prefix);

            namePropertyUpdater.accept(json);
            response.getWriter().write(json.toString());

        } catch (JSONException e) {
            log.error("Error composing the cqinclude JSON representation of the widget overlay for [ {} ]",
                    request.getRequestURI(), e);

            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject().toString());
        }
    }

    private boolean accepts(SlingHttpServletRequest request) {
        if (request.getAttribute(REQ_ATTR) != null) {
            // Cyclic loop
            log.warn("Identified a cyclic loop in the ACS Commons CQ Include Namespace prefix Servlet for [ {} ]",
                    request.getRequestURI());
            return false;
        }

        for (int i = 0; i <= NAME_PROPERTY_SELECTOR_INDEX; i++) {
            if (StringUtils.isBlank(PathInfoUtil.getSelector(request, i))) {
                // Missing necessary selectors; the first N - 1 should be redundant since the selectors are specified
                // in the Servlet registration
                return false;
            }
        }

        return true;
    }

    private class NamePropertyUpdater extends AbstractJSONObjectVisitor {
        private final Logger log = LoggerFactory.getLogger(NamePropertyUpdater.class);

        private static final String PN_NAME = "name";
        private static final String NT_CQ_WIDGET = "cq:Widget";

        private final String namespace;

        public NamePropertyUpdater(final String namespace) {
            this.namespace = namespace;
        }

        @Override
        protected void visit(JSONObject jsonObject) {

            if (StringUtils.equals(jsonObject.optString(JcrConstants.JCR_PRIMARYTYPE), NT_CQ_WIDGET)) {
                String nameValue = jsonObject.optString(PN_NAME);

                nameValue = StringUtils.removeStart(nameValue, "./");

                if (StringUtils.isNotBlank(nameValue)) {
                    try {
                        jsonObject.put(PN_NAME, "./" + namespace + "/" + nameValue);
                    } catch (final JSONException e) {
                        log.error("Error updating the Name property of the JSON object", e);
                    }
                }
            }
        }
    }
}