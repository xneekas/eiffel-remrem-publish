/*
    Copyright 2018 Ericsson AB.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.ericsson.eiffel.remrem.publish.controller;

import java.util.Map;

import io.swagger.annotations.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.ericsson.eiffel.remrem.protocol.MsgService;
import com.ericsson.eiffel.remrem.publish.helper.PublishUtils;
import com.ericsson.eiffel.remrem.publish.helper.RMQHelper;
import com.ericsson.eiffel.remrem.publish.service.MessageService;
import com.ericsson.eiffel.remrem.publish.service.SendResult;
import com.ericsson.eiffel.remrem.shared.VersionService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.classic.Logger;

@RestController
@RequestMapping("/*")
@Api(value = "REMReM Publish Service", description = "REST API for publishing Eiffel messages to message bus")
public class ProducerController {

    @Value("${generate.server.host}")
    private String generateServerHost;

    @Value("${generate.server.port}")
    private String generateServerPort;

    @Autowired
    private MsgService msgServices[];

    public void setMsgServices(MsgService[] msgServices) {
        this.msgServices = msgServices;
    }

    @Autowired
    @Qualifier("messageServiceRMQImpl")
    MessageService messageService;

    @Autowired
    RMQHelper rmqHelper;

    RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    Logger log = (Logger) LoggerFactory.getLogger(ProducerController.class);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @ApiOperation(value = "To publish eiffel event to message bus", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Event sent successfully"),
            @ApiResponse(code = 400, message = "Invalid event content"),
            @ApiResponse(code = 404, message = "RabbitMq properties not found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Service Unavailable") })
    @RequestMapping(value = "/producer/msg", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity send(@ApiParam(value = "message protocol", required = true) @RequestParam(value = "mp") final String msgProtocol,
                               @ApiParam(value = "user domain") @RequestParam(value = "ud", required = false) final String userDomain,
                               @ApiParam(value = "tag") @RequestParam(value = "tag", required = false) final String tag,
                               @ApiParam(value = "routing key") @RequestParam(value = "rk", required = false) final String routingKey,
                               @ApiParam(value = "eiffel event", required = true) @RequestBody final JsonElement body) {
        MsgService msgService = PublishUtils.getMessageService(msgProtocol, msgServices);

        log.debug("mp: " + msgProtocol);
        log.debug("body: " + body);
        log.debug("user domain suffix: " + userDomain + " tag: " + tag + " Routing Key: " + routingKey);
        if (msgService != null && msgProtocol != null) {
            rmqHelper.rabbitMqPropertiesInit(msgProtocol);
        }
        SendResult result = messageService.send(body, msgService, userDomain, tag, routingKey);
        return new ResponseEntity(result, messageService.getHttpStatus());
    }

    /**
     * This controller provides single RemRem REST API End Point for both RemRem
     * Generate and Publish.
     *
     * @param msgProtocol
     *            message protocol (required)
     * @param msgType
     *            message type (required)
     * @param userDomain
     *            user domain (not required)
     * @param tag
     *            (not required)
     * @param routingKey
     *            (not required)
     * @return A response entity which contains http status and result
     * @exception IOException
     *                On input error.
     * @see IOException
     * @use A typical CURL command: curl -H "Content-Type: application/json" -X POST
     *      --data "@inputGenerate_activity_finished.txt"
     *      "http://localhost:8986/generateAndPublish/?mp=eiffelsemantics&msgType=EiffelActivityFinished"
     */

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @ApiOperation(value = "To generate and publish eiffel event to message bus", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Event sent successfully"),
            @ApiResponse(code = 400, message = "Invalid event content"),
            @ApiResponse(code = 404, message = "RabbitMq properties not found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Message protocol is invalid") })
    @RequestMapping(value = "/generateAndPublish", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity generateAndPublish(@ApiParam(value = "message protocol", required = true) @RequestParam(value = "mp") final String msgProtocol,
                                             @ApiParam(value = "message type", required = true) @RequestParam("msgType") final String msgType,
                                             @ApiParam(value = "user domain") @RequestParam(value = "ud", required = false) final String userDomain,
                                             @ApiParam(value = "tag") @RequestParam(value = "tag", required = false) final String tag,
                                             @ApiParam(value = "routing key") @RequestParam(value = "rk", required = false) final String routingKey,
                                             @ApiParam(value = "JSON message", required = true) @RequestBody final JsonObject bodyJson) {

        URLTemplate urlTemplate = new URLTemplate();
        urlTemplate.generate(msgProtocol, msgType, userDomain, routingKey, tag, generateServerHost ,generateServerPort);

        ResponseEntity<String> response = null;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<String>(bodyJson.toString(), headers);

        String postURL = urlTemplate.getUrl();
        Map<String, String> map = urlTemplate.getMap();
        response = restTemplate.postForEntity(postURL, entity, String.class, map);
        int res = response.getStatusCode().value();

        if (res == HttpStatus.OK.value()) {
            log.info("The result from remrem-generate is : " + res);

            String responseBody = response.getBody();
            // publishing requires an array if you want status code
            responseBody = "[" + responseBody + "]";
            MsgService msgService = PublishUtils.getMessageService(msgProtocol, msgServices);

            log.debug("mp: " + msgProtocol);
            log.debug("body: " + responseBody);
            log.debug("user domain suffix: " + userDomain + " tag: " + tag + " Routing Key: " + routingKey);
            if (msgService != null && msgProtocol != null) {
                rmqHelper.rabbitMqPropertiesInit(msgProtocol);
            }
            SendResult result = messageService.send(responseBody, msgService, userDomain, tag, routingKey);
            return new ResponseEntity(result, messageService.getHttpStatus());

        } else {
            log.info("The result from remrem-generate is not OK and have value: " + res);
            return response;
        }
    }

    /**
     * @return this method returns the current version of publish and all loaded
     *         protocols.
     */
    @ApiOperation(value = "To get versions of publish and all loaded protocols", response = String.class)
    @RequestMapping(value = "/versions", method = RequestMethod.GET)
    public JsonElement getVersions() {
        JsonParser parser = new JsonParser();
        Map<String, Map<String, String>> versions = new VersionService().getMessagingVersions();
        return parser.parse(versions.toString());
    }

}
