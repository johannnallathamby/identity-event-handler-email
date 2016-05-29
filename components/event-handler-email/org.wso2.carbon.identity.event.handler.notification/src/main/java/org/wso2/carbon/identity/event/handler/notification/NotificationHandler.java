/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.event.handler.notification;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.event.stream.core.EventStreamService;
import org.wso2.carbon.identity.event.EventMgtConstants;
import org.wso2.carbon.identity.event.EventMgtException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.event.handler.notification.email.bean.EmailNotification;
import org.wso2.carbon.identity.event.handler.notification.internal.NotificationHandlerDataHolder;
import org.wso2.carbon.identity.event.handler.notification.email.model.EmailTemplate;
import org.wso2.carbon.identity.event.handler.notification.util.NotificationUtil;
import org.wso2.carbon.user.core.UserStoreManager;

import java.util.HashMap;
import java.util.Map;

public class NotificationHandler extends AbstractEventHandler {

    private static final Log log = LogFactory.getLog(NotificationHandler.class);

    @Override
    public boolean handleEvent(Event event) throws EventMgtException {

        Map<String, String> placeHolderData = new HashMap<>();

        for (Map.Entry<String, Object> entry : event.getEventProperties().entrySet()) {
            if (entry.getValue() instanceof String) {
                placeHolderData.put(entry.getKey(), (String) entry.getValue());
            }
        }

        String notificationEvent = (String)event.getEventProperties().get("notification-event");
        String username = (String)event.getEventProperties().get(EventMgtConstants.EventProperty.USER_NAME);
        UserStoreManager userStoreManager = (UserStoreManager)event.getEventProperties().get(
                EventMgtConstants.EventProperty.USER_STORE_MANAGER);
        String userStoreDomainName = (String)event.getEventProperties().get(EventMgtConstants.EventProperty.USER_STORE_DOMAIN);
        String tenantDomain = (String)event.getEventProperties().get(EventMgtConstants.EventProperty.TENANT_DOMAIN);
        String sendFrom = (String)event.getEventProperties().get("send-from");

        if(StringUtils.isNotBlank(username) && userStoreManager != null) {
            NotificationUtil.fillUserClaims(username, userStoreManager, placeHolderData);
        } else if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(userStoreDomainName) &&
                   StringUtils.isNotBlank(tenantDomain)) {
            NotificationUtil.fillUserClaims(username, userStoreDomainName, tenantDomain, placeHolderData);
        }

        String sendTo = placeHolderData.get(NotificationConstants.CLAIM_URI_EMAIL);

        String locale = NotificationConstants.LOCALE_DEFAULT;
        if (placeHolderData.containsKey(NotificationConstants.CLAIM_URI_LOCALE)) {
            locale = placeHolderData.get(NotificationConstants.CLAIM_URI_LOCALE);
        }

        EmailTemplate emailTemplate = NotificationUtil.loadEmailTemplate(notificationEvent, locale, tenantDomain);

        EmailNotification.EmailNotificationBuilder builder =
                new EmailNotification.EmailNotificationBuilder(sendTo);
        builder.setSendFrom(sendFrom);
        builder.setTemplate(emailTemplate);
        builder.setPlaceHolderData(placeHolderData);
        EmailNotification emailNotification = builder.build();

        publishToStream(emailNotification, placeHolderData);
        return true;
    }

    protected void publishToStream(EmailNotification emailNotification, Map<String,String> placeHolderDataMap) {

        EventStreamService service = NotificationHandlerDataHolder.getInstance().getEventStreamService();
        org.wso2.carbon.databridge.commons.Event databridgeEvent = new org.wso2.carbon.databridge.commons.Event();

        databridgeEvent.setTimeStamp(System.currentTimeMillis());
        databridgeEvent.setStreamId("id_gov_notify_stream:1.0.0");
        Map<String,String> arbitraryDataMap = new HashMap<>();
        arbitraryDataMap.put("notification-event", emailNotification.getTemplate().getNotificationEvent());
        arbitraryDataMap.put(EventMgtConstants.EventProperty.USER_NAME,
                             placeHolderDataMap.get(EventMgtConstants.EventProperty.USER_NAME));

        arbitraryDataMap.put(EventMgtConstants.EventProperty.USER_STORE_DOMAIN,
                             placeHolderDataMap.get(EventMgtConstants.EventProperty.USER_STORE_DOMAIN));
        arbitraryDataMap.put(EventMgtConstants.EventProperty.TENANT_DOMAIN,
                             placeHolderDataMap.get(EventMgtConstants.EventProperty.TENANT_DOMAIN));
        arbitraryDataMap.put("send-from", emailNotification.getSendFrom());
        for(Map.Entry<String,String> placeHolderDataEntry:placeHolderDataMap.entrySet()) {
            arbitraryDataMap.put(placeHolderDataEntry.getKey(), placeHolderDataEntry.getValue());
        }
        arbitraryDataMap.put("subject-template", emailNotification.getTemplate().getSubject());
        arbitraryDataMap.put("body-template", emailNotification.getTemplate().getBody());
        arbitraryDataMap.put("footer-template", emailNotification.getTemplate().getFooter());
        arbitraryDataMap.put("locale", emailNotification.getTemplate().getLocale());
        arbitraryDataMap.put("content-type", emailNotification.getTemplate().getContentType());
        arbitraryDataMap.put("send-to", emailNotification.getSendTo());
        arbitraryDataMap.put("subject", emailNotification.getSubject());
        arbitraryDataMap.put("body", emailNotification.getBody());
        arbitraryDataMap.put("footer", emailNotification.getFooter());

        databridgeEvent.setArbitraryDataMap(arbitraryDataMap);
        service.publish(databridgeEvent);
    }

    @Override
    public String getName() {
        return "NotificationSender";
    }
}
