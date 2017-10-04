/*
*  Copyright (c) Microsoft. All rights reserved.
*  Licensed under the MIT license. See LICENSE file in the project root for full license information.
*/

package com.microsoft.azure.sdk.iot.service.devicetwin;

//TODO rename? now this is only used for continuing queries, not all queries
public class QueryOptions
{
    private String continuationToken;

    public QueryOptions()
    {
        this.continuationToken = null;
    }

    /**
     * Getter for ContinuationToken
     *
     * @return The value of ContinuationToken
     */
    public String getContinuationToken()
    {
        return continuationToken;
    }

    /**
     * Setter for ContinuationToken
     *
     * @param continuationToken the value to set
     * @throws IllegalArgumentException if continuationToken is null
     */
    public void setContinuationToken(String continuationToken) throws IllegalArgumentException
    {
        if (continuationToken == null)
        {
            throw new IllegalArgumentException("continuationToken");
        }

        this.continuationToken = continuationToken;
    }
}
