/*
 * Copyright 2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twcable.grabbit.resources

import groovy.transform.CompileStatic
import org.apache.sling.api.resource.ResourceResolver

import javax.annotation.Nonnull
import java.util.regex.Matcher

/**
 * {@link TransactionResource} represents a a logical group of jobs by configuration run.
 *
 * Provided by {@link com.twcable.grabbit.resources.GrabbitResourceProvider}.
 * Queried from {@link com.twcable.grabbit.client.servlets.GrabbitTransactionServlet}.
 */
@CompileStatic
class TransactionResource extends RootResource {

    public static final String TRANSACTION_RESOURCE_TYPE = "${ROOT_RESOURCE_TYPE}/transaction"
    public static final String TRANSACTION_ID_KEY = "${ROOT_RESOURCE_TYPE}:transactionresource.transactionID"

    TransactionResource(@Nonnull final ResourceResolver resourceResolver, @Nonnull final String resolutionPath) {
        super(resourceResolver, resolutionPath, TRANSACTION_RESOURCE_TYPE)
        super.resourceMetadata.put(TRANSACTION_ID_KEY, getTransactionIdFromPath(resolutionPath))
    }


    private static String getTransactionIdFromPath(String path) {
        Matcher matcher = path =~ /\/grabbit\/transaction\/(.+)$/
        if (matcher.matches()) {
            final matchedList = matcher[0] as Collection<String>
            return matchedList[1] - ~/\..+/
        }
        else {
            return ""
        }
    }


    @Override
    String getResourceType() {
        return TRANSACTION_RESOURCE_TYPE
    }

    @Override
    String getResourceSuperType(){
        return ROOT_RESOURCE_TYPE
    }
}
