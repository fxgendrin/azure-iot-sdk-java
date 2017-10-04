/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.service.devicetwin;

import com.microsoft.azure.sdk.iot.deps.serializer.ParserUtility;
import com.microsoft.azure.sdk.iot.deps.serializer.QueryRequestParser;
import com.microsoft.azure.sdk.iot.service.IotHubConnectionString;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.transport.http.HttpMethod;
import com.microsoft.azure.sdk.iot.service.transport.http.HttpResponse;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/*
    Sql style query IotHub for twin, jobs, device jobs or raw data
 */
public class Query
{
    private static final String CONTINUATION_TOKEN_KEY = "x-ms-continuation";
    private static final String ITEM_TYPE_KEY = "x-ms-item-type";
    private static final String PAGE_SIZE_KEY = "x-ms-max-item-count";

    private int pageSize;
    private String query;
    private boolean isSqlQuery;

    private QueryType requestQueryType;
    private QueryType responseQueryType;

    private QueryResponse queryResponse;

    private IotHubConnectionString iotHubConnectionString;
    private URL url;
    private HttpMethod httpMethod;
    private long timeout;

    /**
     * Constructor for Query
     * @param query Sql style query to be sent to IotHub
     * @param pageSize page size for the query response to request query over
     * @param requestQueryType Type of query
     * @throws IllegalArgumentException if the input parameters are invalid
     */
    public Query(String query, int pageSize, QueryType requestQueryType) throws IllegalArgumentException
    {
        //Codes_SRS_QUERY_25_001: [The constructor shall validate query and save query, pagesize and request type]
        //Codes_SRS_QUERY_25_002: [If the query is null or empty or is not a valid sql query (containing select and from), the constructor shall throw an IllegalArgumentException.]
        ParserUtility.validateQuery(query);

        if (pageSize <= 0)
        {
            //Codes_SRS_QUERY_25_003: [If the pagesize is zero or negative the constructor shall throw an IllegalArgumentException.]
            throw new IllegalArgumentException("Page Size cannot be zero or negative");
        }

        if (requestQueryType == null || requestQueryType == QueryType.UNKNOWN)
        {
            //Codes_SRS_QUERY_25_004: [If the QueryType is null or unknown then the constructor shall throw an IllegalArgumentException.]
            throw new IllegalArgumentException("Cannot process a unknown type query");
        }

        this.pageSize = pageSize;
        this.query = query;
        this.requestQueryType = requestQueryType;
        this.responseQueryType = QueryType.UNKNOWN;
        this.queryResponse = null;
        //Codes_SRS_QUERY_25_017: [If the query is avaliable then isSqlQuery shall be set to true, and false otherwise.]
        this.isSqlQuery = true;
    }

    /**
     * Constructor for Query
     * @param pageSize page size for the query response to request query over
     * @param requestQueryType Type of query
     * @throws IllegalArgumentException if the input parameters are invalid
     */
    public Query(int pageSize, QueryType requestQueryType) throws IllegalArgumentException
    {
        if (pageSize <= 0)
        {
            //Codes_SRS_QUERY_25_003: [If the pagesize is zero or negative the constructor shall throw an IllegalArgumentException.]
            throw new IllegalArgumentException("Page Size cannot be zero or negative");
        }

        if (requestQueryType == null || requestQueryType == QueryType.UNKNOWN)
        {
            //Codes_SRS_QUERY_25_004: [If the QueryType is null or unknown then the constructor shall throw an IllegalArgumentException.]
            throw new IllegalArgumentException("Cannot process a unknown type query");
        }

        this.pageSize = pageSize;
        this.query = null;
        this.requestQueryType = requestQueryType;
        this.responseQueryType = QueryType.UNKNOWN;
        this.queryResponse = null;
        this.isSqlQuery = false;
    }

    public Query(QueryOptions options, QueryType requestQueryType) throws IllegalArgumentException
    {
        if (options.getContinuationToken() == null || options.getContinuationToken().isEmpty())
        {
            throw new IllegalArgumentException("continuation token cannot be null or empty");
        }

        if (requestQueryType == null || requestQueryType == QueryType.UNKNOWN)
        {
            //Codes_SRS_QUERY_25_004: [If the QueryType is null or unknown then the constructor shall throw an IllegalArgumentException.]
            throw new IllegalArgumentException("Cannot process a unknown type query");
        }

        this.pageSize = 0; //TODO????
        this.query = null;
        this.requestQueryType = requestQueryType;
        this.responseQueryType = QueryType.UNKNOWN;
        this.queryResponse = null;
        this.isSqlQuery = false;
    }

    private void continueQuery(QueryOptions options) throws IOException, IotHubException
    {
        //Codes_SRS_QUERY_25_018: [The method shall send the query request again.]
        sendQueryRequest(this.iotHubConnectionString, this.url, this.httpMethod, this.timeout, options.getContinuationToken());
    }

    /**
     * Sends request for the query to the IotHub
     * @param iotHubConnectionString Hub Connection String
     * @param url URL to Query on
     * @param method HTTP Method for the requesting a query
     * @param timeoutInMs Maximum time to wait for the hub to respond
     * @return QueryResponse object which holds the response Iterator
     * @throws IOException If any of the input parameters are not valid
     * @throws IotHubException If HTTP response other then status ok is received
     */
    public void sendQueryRequest(IotHubConnectionString iotHubConnectionString,
                          URL url,
                          HttpMethod method,
                          Long timeoutInMs,
                          String continuationToken) throws IOException, IotHubException
    {
        if (iotHubConnectionString == null || url == null || method == null)
        {
            //Codes_SRS_QUERY_25_019: [This method shall throw IllegalArgumentException if any of the parameters are null or empty.]
            throw new IllegalArgumentException("Input parameters cannot be null");
        }

        //Codes_SRS_QUERY_25_020: [This method shall save all the parameters for future use.]
        this.iotHubConnectionString = iotHubConnectionString;
        this.url = url;
        this.httpMethod = method;
        this.timeout = timeoutInMs;

        byte[] payload = null;
        Map<String, String> queryHeaders = new HashMap<>();

        if (continuationToken != null)
        {
            queryHeaders.put(CONTINUATION_TOKEN_KEY, continuationToken);
        }
        //Codes_SRS_QUERY_25_007: [The method shall set the http headers x-ms-continuation and x-ms-max-item-count with request continuation token and page size if they were not null.]
        queryHeaders.put(PAGE_SIZE_KEY, String.valueOf(pageSize));

        DeviceOperations.setHeaders(queryHeaders);

        boolean dog = true;

        if (isSqlQuery && dog)
        {
            //Codes_SRS_QUERY_25_008: [The method shall obtain the serilaized query by using QueryRequestParser.]
            QueryRequestParser requestParser = new QueryRequestParser(this.query);
            String b = requestParser.toJson();
            payload = b.getBytes();
        }
        else
        {
            payload = "{\"query\":\"select * from devices\"}".getBytes();
            //payload = new byte[0];
        }

        //Codes_SRS_QUERY_25_009: [The method shall use the provided HTTP Method and send request to IotHub with the serialized body over the provided URL.]
        HttpResponse httpResponse = DeviceOperations.request(iotHubConnectionString, url, method, payload, null, timeoutInMs);

        Map<String, String> headers = httpResponse.getHeaderFields();
        //Codes_SRS_QUERY_25_010: [The method shall read the continuation token (x-ms-continuation) and reponse type (x-ms-item-type) from the HTTP Headers and save it.]
        String newContinuationToken = null;
        for (Map.Entry<String, String> header : headers.entrySet())
        {
            switch (header.getKey())
            {
                case CONTINUATION_TOKEN_KEY:
                    newContinuationToken = header.getValue();
                    break;
                case ITEM_TYPE_KEY:
                    this.responseQueryType = QueryType.fromString(header.getValue());
                    break;
                default:
                    break;
            }
        }

        if (this.responseQueryType == null || this.responseQueryType == QueryType.UNKNOWN)
        {
            //Codes_SRS_QUERY_25_012: [If the response type is Unknown or not found then this method shall throw IOException.]
            throw new IOException("Query response type is not defined by IotHub");
        }

        if (this.requestQueryType != this.responseQueryType)
        {
            //Codes_SRS_QUERY_25_011: [If the request type and response does not match then the method shall throw IOException.]
            throw new IOException("Query response does not match query request");
        }

        //Codes_SRS_QUERY_25_013: [The method shall create a QueryResponse object with the contents from the response body and save it.]
        this.queryResponse = new QueryResponse(new String(httpResponse.getBody()), newContinuationToken);
    }

    /**
     * Returns the availability of next element in the query response
     * @return the availability of next element in the query response
     * @throws IOException if sending the request is unsuccessful because of input parameters
     * @throws IotHubException if sending the request is unsuccessful at the Hub
     */
    public boolean hasNext() throws IOException, IotHubException
    {
        //Codes_SRS_QUERY_25_015: [The method shall return true if next element from QueryResponse is available and false otherwise.]
        boolean isNextAvailable = this.queryResponse.hasNext();
        if (!isNextAvailable && this.queryResponse.getContinuationToken() != null)
        {
            //Codes_SRS_QUERY_25_021: [If no further query response is available, then this method shall continue to request query to IotHub if continuation token is available.]
            QueryOptions options = new QueryOptions();
            options.setContinuationToken(this.queryResponse.getContinuationToken());
            this.continueQuery(options);
            return this.queryResponse.hasNext();
        }
        else
        {
            //Codes_SRS_QUERY_25_015: [The method shall return true if next element from QueryResponse is available and false otherwise.]
            return isNextAvailable;
        }
    }

    /**
     * provides the next element in query response
     * @return the next element in query response
     * @throws IOException if sending the request is unsuccessful because of input parameters
     * @throws IotHubException if sending the request is unsuccessful at the Hub
     * @throws NoSuchElementException if no further elements are available
     */
    public Object next() throws IOException, IotHubException, NoSuchElementException
    {
        //Codes_SRS_QUERY_25_016: [The method shall return the next element for this QueryResponse.]
       if (this.hasNext())
       {
           return queryResponse.next();
       }
       else
       {
           //Codes_SRS_QUERY_25_022: [The method shall check if any further elements are available by calling hasNext and if none is available then it shall throw NoSuchElementException.]
           throw new NoSuchElementException();
       }
    }

    /**
     * Returns the availability of next element in the query response
     * @return the availability of next element in the query response
     * @throws IOException if sending the request is unsuccessful because of input parameters
     * @throws IotHubException if sending the request is unsuccessful at the Hub
     */
    public boolean hasNext(QueryOptions options) throws IOException, IotHubException
    {
        this.continueQuery(options);
        return this.hasNext();
    }

    /**
     * provides the next element in query response
     * @return the next element in query response
     * @throws IOException if sending the request is unsuccessful because of input parameters
     * @throws IotHubException if sending the request is unsuccessful at the Hub
     * @throws NoSuchElementException if no further elements are available
     */
    public Object next(QueryOptions options) throws IOException, IotHubException, NoSuchElementException
    {
        this.continueQuery(options);
        return this.next();
    }

    public String getContinuationToken()
    {
        return this.queryResponse.getContinuationToken();
    }
}
