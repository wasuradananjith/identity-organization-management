/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
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

package org.wso2.carbon.identity.organization.management.application.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.ApplicationBasicInfo;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.organization.management.application.OrgApplicationManager;
import org.wso2.carbon.identity.organization.management.application.OrgApplicationManagerImpl;
import org.wso2.carbon.identity.organization.management.application.dao.OrgApplicationMgtDAO;
import org.wso2.carbon.identity.organization.management.application.internal.OrgApplicationMgtDataHolder;
import org.wso2.carbon.identity.organization.management.application.model.MainApplicationDO;
import org.wso2.carbon.identity.organization.management.application.model.SharedApplicationDO;
import org.wso2.carbon.identity.organization.management.ext.Constants;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.model.Organization;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.wso2.carbon.identity.organization.management.application.constant.OrgApplicationMgtConstants.SHARE_WITH_ALL_CHILDREN;
import static org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants.SUPER_ORG_ID;
import static org.wso2.carbon.identity.organization.management.service.util.Utils.getAuthenticatedUsername;

/**
 * This class contains the implementation of the handler for post organization creation.
 * This handler will be used to add shared applications to newly created organizations.
 */
public class OrganizationCreationHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(OrganizationCreationHandler.class);

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        String eventName = event.getEventName();

        if (Constants.EVENT_POST_ADD_ORGANIZATION.equals(eventName)) {
            Map<String, Object> eventProperties = event.getEventProperties();
            Organization organization = (Organization) eventProperties.get(Constants.EVENT_PROP_ORGANIZATION);
            try {
                addSharedApplicationsToOrganization(organization);
            } catch (IdentityApplicationManagementException | OrganizationManagementException e) {
                throw new IdentityEventException("An error occurred while creating shared applications in the new " +
                        "organization", e);
            }
        }
    }

    private void addSharedApplicationsToOrganization(Organization organization)
            throws IdentityApplicationManagementException, OrganizationManagementException {

        String parentOrgId = organization.getParent().getId();
        if (parentOrgId == null) {
            parentOrgId = SUPER_ORG_ID;
        }

        ApplicationBasicInfo[] applicationBasicInfos;
        applicationBasicInfos = getApplicationManagementService().getAllApplicationBasicInfo(
                getOrganizationManager().resolveTenantDomain(parentOrgId), getAuthenticatedUsername());

        for (ApplicationBasicInfo applicationBasicInfo: applicationBasicInfos) {
            if (getOrgApplicationMgtDAO().isFragmentApplication(applicationBasicInfo.getApplicationId())) {
                Optional<SharedApplicationDO> sharedApplicationDO;
                sharedApplicationDO = getOrgApplicationMgtDAO().getSharedApplication(
                        applicationBasicInfo.getApplicationId(), parentOrgId);

                if (sharedApplicationDO.isPresent() && sharedApplicationDO.get().shareWithAllChildren()) {
                    Optional<MainApplicationDO> mainApplicationDO;
                    mainApplicationDO = getOrgApplicationMgtDAO().getMainApplication(
                            sharedApplicationDO.get().getFragmentApplicationId(),
                            sharedApplicationDO.get().getOrganizationId());
                    if (mainApplicationDO.isPresent()) {
                        String tenantDomain = getOrganizationManager().resolveTenantDomain(
                                mainApplicationDO.get().getOrganizationId());
                        ServiceProvider mainApplication = getApplicationManagementService()
                                .getApplicationByResourceId(mainApplicationDO.get().getMainApplicationId(),
                                        tenantDomain);
                        String ownerOrgIdOfMainApplication = mainApplicationDO.get().getOrganizationId();
                        getOrgApplicationManager().shareApplication(ownerOrgIdOfMainApplication, organization.getId(),
                                mainApplication, true);
                    }
                }
            } else {
                ServiceProvider mainApplication;
                mainApplication = getApplicationManagementService().getServiceProvider(
                        applicationBasicInfo.getApplicationId());
                if (mainApplication != null && Arrays.stream(mainApplication.getSpProperties())
                        .anyMatch(p -> SHARE_WITH_ALL_CHILDREN.equalsIgnoreCase(
                                p.getName()) && Boolean.parseBoolean(p.getValue()))) {
                    getOrgApplicationManager().shareApplication(parentOrgId, organization.getId(),
                            mainApplication, true);
                }
            }
        }
    }


    private ApplicationManagementService getApplicationManagementService() {

        return OrgApplicationMgtDataHolder.getInstance().getApplicationManagementService();
    }

    private OrgApplicationManager getOrgApplicationManager() {

        return new OrgApplicationManagerImpl();
    }

    private OrgApplicationMgtDAO getOrgApplicationMgtDAO() {

        return OrgApplicationMgtDataHolder.getInstance().getOrgApplicationMgtDAO();
    }

    private OrganizationManager getOrganizationManager() {

        return OrgApplicationMgtDataHolder.getInstance().getOrganizationManager();
    }

}
