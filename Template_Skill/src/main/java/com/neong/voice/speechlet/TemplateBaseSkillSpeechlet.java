/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.neong.voice.speechlet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.neong.voice.wolfpack.CalendarConversation;
import com.neong.voice.model.base.Conversation;

/**
 * This TemplateBaseSkillSpeechlet class functions as a "dispatcher" that passes Intents
 * to the proper Conversation object that supports it. You should only need to add a new
 * instance of your custom Conversation objects to the supportedConversations[] List in the
 * onSessionStarted() method.
 *
 * NOTE: You should not need to edit anything else within this class file, except noted above.
 *
 * @author Jeffrey Neong
 * @version 1.0
 *
 */

public class TemplateBaseSkillSpeechlet implements Speechlet {

    private static final Logger log = LoggerFactory.getLogger(TemplateBaseSkillSpeechlet.class);

    // Add a new instance of your Conversation to this List in the onSessionStarted method below
    List<Conversation> supportedConversations = new ArrayList<Conversation>();

    // Populated from supportedConversations List
    Map<String,Conversation> supportedIntentsByConversation = new HashMap<String,Conversation>();


    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // All session initialization goes here - Beginning of lifecycle


        // TODO EDIT HERE: Add Conversation objects to registry
        supportedConversations.add(new CalendarConversation());


        // Populate a map of supported intents to conversations for later dispatch
        for (Conversation convo : supportedConversations) {
            for (String intentName : convo.getSupportedIntentNames()) {
                supportedIntentsByConversation.put(intentName, convo);
            }
        }
    }


    // This method is called if the skill is invoked with a "start" intent
    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        String welcomeStr =
            "Welcome to the SSU Events skill. You can start by asking me what's " +
            "happening today or on another upcoming date.";
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText(welcomeStr);

        String repromptStr = "Try asking what's happening tomorrow.";
        PlainTextOutputSpeech repromptOutputSpeech = new PlainTextOutputSpeech();
        repromptOutputSpeech.setText(repromptStr);

        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);

        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }


    // This method is called to service any known intents defined in your voice interaction model
    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        SpeechletResponse response = null;
        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;

        // Check for convo handling
        Conversation convo = getConvoForIntent(intentName);

        // If the conversation matches a custom intent
        if(convo != null) {
            response = convo.respondToIntentRequest(request, session);

        // If the conversation matches a built in intent
        } else {
            switch (intentName) {
            case "AMAZON.HelpIntent": {
                String responseSsml = "Hmm, I'm sorry you are having trouble.";
                String repromptSsml =
                    "Try asking Sonoma State for what's happening tomorrow, " +
                    "on a specific date, or next.";
                response = Conversation.newAskResponse("<speak>" + responseSsml + "</speak>", true,
                                                       "<speak>" + repromptSsml + "</speak>", true);
                break;
            }

            case "AMAZON.StopIntent":
            case "AMAZON.CancelIntent":
                response = Conversation.newTellResponse("", false);
                break;

            case "AMAZON.NoIntent": {
                String responseSsml = "Okay.";
                String repromptSsml = "What did you want instead?";
                response = Conversation.newAskResponse("<speak>" + responseSsml + "</speak>", true,
                                                       "<speak>" + repromptSsml + "</speak>", true);
                break;
            }

                // If the Intent cannot be handled
            default:
                throw new SpeechletException("Invalid Intent");
            }
        }

        return response;
    }


    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any session cleanup logic would go here
    }


    private Conversation getConvoForIntent(String intentName) {
        Conversation convo = null;
        // Get a new instance of a proper conversation.
        // TODO: Filter out common answers so they do not create an erroneously new convo that is ambiguous.
        convo = supportedIntentsByConversation.get(intentName);
        if (convo == null) {
            log.error("Cannot find a Conversation object that supports intent name " + intentName);
        }
        return convo;
    }
}
