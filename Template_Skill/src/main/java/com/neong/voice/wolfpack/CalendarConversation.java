package com.neong.voice.wolfpack;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;

import com.neong.voice.wolfpack.CalendarHelper;
import com.neong.voice.wolfpack.CosineSim;
import com.neong.voice.wolfpack.DateRange;

import com.neong.voice.model.base.Conversation;

import com.wolfpack.database.DbConnection;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Collections;


public class CalendarConversation extends Conversation {
	/** Intent names */
	private enum CalendarIntent {
		AMAZON_CANCEL("AMAZON.CancelIntent"),
		AMAZON_HELP("AMAZON.HelpIntent"),
		AMAZON_NO("AMAZON.NoIntent"),
		AMAZON_STOP("AMAZON.StopIntent"),
		AMAZON_PREVIOUS("AMAZON.PreviousIntent"),
		AMAZON_NEXT("AMAZON.NextIntent"),

		NEXT_EVENT("NextEventIntent"),
		GET_EVENTS_ON_DATE ("GetEventsOnDateIntent"),

		GET_END_DETAIL("GetEndDetailIntent"),
		GET_FEE_DETAIL("GetFeeDetailIntent"),
		GET_LOCATION_DETAIL("GetLocationDetailIntent"),

		ALL_CATEGORY("AllCategoryIntent"),
		ARTS_AND_ENTERTAINMENT_CATEGORY("ArtsAndEntertainmentCategoryIntent"),
		CLUBS_CATEGORY("ClubsCategoryIntent"),
		LECTURES_CATEGORY("LecturesCategoryIntent"),
		SPORTS_CATEGORY("SportsCategoryIntent");

		private final String value;
		private CalendarIntent(String value) { this.value = value; }
		@Override public String toString() { return value; }

		public static CalendarIntent valueOf(IntentRequest intentReq) {
			// Intent requests are dispatched to us by name,
			// so we always know the intent and name are non-null.
			String intentName = intentReq.getIntent().getName();
			if (intentName == null)
				return null;

			for (CalendarIntent intent : CalendarIntent.values()) {
				if (intentName.equals(intent.value))
					return intent;
			}

			return null;
		}
	}

	/** Slot names */
	private enum CalendarSlot {
		EVENT_NAME("eventName"),
		AMAZON_DATE("date"),
		LIST_OF_CATEGORIES("listOfCategories");

		private final String value;
		private CalendarSlot(String value) { this.value = value; }
		@Override public String toString() { return value; }

		public static String getRequestSlotValue(IntentRequest intentReq, CalendarSlot slot) {
			String slotName = slot.toString();
			Slot intentSlot = intentReq.getIntent().getSlot(slotName);
			if (intentSlot == null)
				return null;
			return intentSlot.getValue();
		}
	}

	/** Session attribute names */
	private enum CalendarAttrib {
		STATE_ID("stateId"),
		SAVED_DATE("savedDate"),
		RECENTLY_SAID_EVENTS("recentlySaidEvents"),
		CURRENT_CATEGORY("currentCategory");

		private final String value;
		private CalendarAttrib(String value) { this.value = value; }
		@Override public String toString() { return value; }

		public static Object getSessionAttribute(Session session, CalendarAttrib attrib) {
			String attribName = attrib.toString();
			return session.getAttribute(attribName);
		}

		public static void setSessionAttribute(Session session, CalendarAttrib attrib, Object value) {
			String attribName = attrib.toString();
			session.setAttribute(attribName, value);
		}

		public static  void removeSessionAttribute(Session session, CalendarAttrib attrib) {
			String attribName = attrib.toString();
			session.removeAttribute(attribName);
		}
	}

	/** Session states */
	private enum SessionState {
		USER_HEARD_EVENTS, // The user has heard a list of events and can now ask about specific ones.
		LIST_TOO_LONG, // The list of events is too long, so the user must narrow it down somehow.
		AMAZON_PREVIOUS, // The user wants previous event.
		AMAZON_NEXT; // The user wants the next event.

		public static SessionState valueOf(Session session) {
			String stateName = (String) CalendarAttrib.getSessionAttribute(session, CalendarAttrib.STATE_ID);
			if (stateName == null)
				return null;
			return valueOf(stateName);
		}
	}

	// Other constants
	private final static int MAX_EVENTS = 5;
	private final static int MAX_CATEGORIES = 8;

	private DbConnection db;


	public CalendarConversation() {
		super();

		db = new DbConnection("DbCredentials.xml");

		// Add custom intent names for dispatcher use.
		for (CalendarIntent intent : CalendarIntent.values())
			supportedIntentNames.add(intent.toString());
	}


	@Override
	public SpeechletResponse respondToIntentRequest(IntentRequest intentReq, Session session) {
		SpeechletResponse response;
		PreparedStatement ps;
		ObjectMapper mapper = new ObjectMapper();

		if (!db.getRemoteConnection())
			return newInternalErrorResponse();
		db.runQuery("SET timezone='" + CalendarHelper.TIME_ZONE + "'");

		try {
			String requestJson = mapper.writeValueAsString(intentReq);
			ps = db.prepareStatement("INSERT INTO requests(content) VALUES (?::jsonb)");
			ps.setString(1, requestJson);
			ps.executeQuery();
		} catch (JsonGenerationException e) {
			System.out.println(e);
		} catch (JsonMappingException e) {
			System.out.println(e);
		} catch (JsonProcessingException e) {
			System.out.println(e);
		} catch (SQLException e) {
			System.out.println(e);
		}

		CalendarIntent intent = CalendarIntent.valueOf(intentReq);

		switch (intent) {

		/*
		 * These intents are not sensitive to session state and can be invoked at any time.
		 */

		case AMAZON_CANCEL:
		case AMAZON_STOP:
			response = handleAmazonStopIntent(intentReq, session);
			break;

		case AMAZON_HELP:
			response = handleAmazonHelpIntent(intentReq, session);
			break;

		case AMAZON_NO:
			response = handleAmazonNoIntent(intentReq, session);
			break;

		case NEXT_EVENT:
			response = handleNextEventIntent(intentReq, session);
			break;

		case GET_EVENTS_ON_DATE:
			response = handleGetEventsOnDateIntent(intentReq, session);
			break;

		/*
		 * The rest of the intents are sensitive to the current state of the session.
		 */

		default:
			response = routeStateSensitiveIntents(intentReq, session);
			break;
		}

		try {
			String responseJson = mapper.writeValueAsString(response);
			ps = db.prepareStatement("INSERT INTO responses(content) VALUES (?::jsonb)");
			ps.setString(1, responseJson);
			ps.executeQuery();
		} catch (JsonGenerationException e) {
			System.out.println(e);
		} catch (JsonMappingException e) {
			System.out.println(e);
		} catch (JsonProcessingException e) {
			System.out.println(e);
		} catch (SQLException e) {
			System.out.println(e);
		}

		return response;
	}


	/**
	 * Route a state-sensitive intent to the correct handler.
	 */
	private SpeechletResponse routeStateSensitiveIntents(IntentRequest intentReq, Session session) {
		SpeechletResponse response;

		SessionState state = SessionState.valueOf(session);
		if (state == null)
			return newBadStateResponse("handleStateSensitiveIntents");

		switch (state) {
		case USER_HEARD_EVENTS:
			response = routeDetailIntents(intentReq, session);
			break;

		case LIST_TOO_LONG:
			response = handleNarrowDownIntents(intentReq, session);
			break;

		case AMAZON_PREVIOUS:
			response = handleAmazonPreviousIntent(intentReq, session);
			break;

		case AMAZON_NEXT:
			response = handleAmazonNextIntent(intentReq, session);
			break;

		default:
			throw new IllegalStateException("Unhandled SessionState value " + state);
		}

		return response;
	}


	/**
	 * Route a detail intent to the correct handler.
	 */
	private SpeechletResponse routeDetailIntents(IntentRequest intentReq, Session session) {
		SpeechletResponse response;

		CalendarIntent intent = CalendarIntent.valueOf(intentReq);

		switch (intent) {
		case GET_FEE_DETAIL:
			response = handleGetFeeDetailIntent(intentReq, session);
			break;

		case GET_LOCATION_DETAIL:
			response = handleGetLocationDetailIntent(intentReq, session);
			break;

		case GET_END_DETAIL:
			response = handleGetEndDetailIntent(intentReq, session);
			break;

		default:
			response = newBadStateResponse("handleDetailIntents");
			break;
		}

		return response;
	}


	private SpeechletResponse handleAmazonStopIntent(IntentRequest intentReq, Session session) {
		return newTellResponse("", false);
	}


	private SpeechletResponse handleAmazonPreviousIntent(IntentRequest intentReq, Session session) {
		return newTellResponse("", false);
	}


	// This is the variable declaration for the list traversal feature, using the categories.
	private final static Map<String, String> synonymsForCategories;
	static {
		Map<String, String> s = new HashMap<String, String>();
		s.put("athletics",                          "Athletics");
		s.put("sports",                             "Athletics");
		s.put("teams",                              "Athletics");
		s.put("games",                              "Athletics");
		synonymsForCategories = Collections.unmodifiableMap(s);
	}

	private final static String sportsPhrase = "Sports";

	private SpeechletResponse handleAmazonNextIntent(IntentRequest intentReq, Session session) {
		String categoryName = Synonym.getSynonym(sportsPhrase, synonymsForCategories);
		return handleNarrowDownIntents(intentReq, session );
	}


	private SpeechletResponse handleAmazonHelpIntent(IntentRequest intentReq, Session session) {
		String responseSsml = "Hmm, I'm sorry you are having trouble.";
		String repromptSsml =
			"Try asking Sonoma State for what's happening tomorrow, " +
			"on a specific date, or next.";
		return newAffirmativeResponse(responseSsml, repromptSsml);
	}


	private SpeechletResponse handleAmazonNoIntent(IntentRequest intentReq, Session session) {
		String responseSsml = "Okay.";
		String repromptSsml = "What did you want instead?";
		return newAffirmativeResponse(responseSsml, repromptSsml);
	}


	private SpeechletResponse handleNextEventIntent(IntentRequest intentReq, Session session) {
		Map<String, Vector<Object>> results =
			db.runQuery("SELECT * FROM event_info WHERE start > now() LIMIT 1;");

		if (results == null)
			return newInternalErrorResponse();

		String eventFormat = "The next event is {title}, on {start:date} at {start:time}.";
		String eventSsml = CalendarHelper.formatEventSsml(eventFormat, results);
		String repromptSsml = "Is there anything you would like to know about this event?";

		Map<String, Integer> savedEvent = CalendarHelper.extractEventIds(results, 1);

		CalendarAttrib.setSessionAttribute(session, CalendarAttrib.RECENTLY_SAID_EVENTS, savedEvent);
		CalendarAttrib.setSessionAttribute(session, CalendarAttrib.STATE_ID, SessionState.USER_HEARD_EVENTS);
		CalendarAttrib.removeSessionAttribute(session, CalendarAttrib.SAVED_DATE);

		return newAffirmativeResponse(eventSsml, repromptSsml);
	}


	private SpeechletResponse handleGetEventsOnDateIntent(IntentRequest intentReq, Session session) {
		SpeechletResponse response;

		String givenDate = CalendarSlot.getRequestSlotValue(intentReq, CalendarSlot.AMAZON_DATE);
		if (givenDate == null)
			return newBadSlotResponse("date");

		DateRange dateRange = new DateRange(givenDate);

		// Select all events on the same day as the givenDate.
		Map<String, Vector<Object>> results;

		try {
			String query = "SELECT event_id, title, start, location FROM event_info " +
				"WHERE start >= ?::date AND start < ?::date";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setDate(1, dateRange.getBegin());
			ps.setDate(2, dateRange.getEnd());

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return newInternalErrorResponse();
		}

		// If Alexa couldn't connect to the database or run the query:
		if (results == null)
			return newInternalErrorResponse();

		int numEvents = results.get("title").size();

		// If there were not any events on the given day:
		if (numEvents == 0) {
			String dateSsml = dateRange.getDateSsml();
			String responseSsml = "I couldn't find any events " + dateRange.getRelativeDate(true) + ".";
			String repromptSsml = "Can I help you find another event?";

			return newFailureResponse(responseSsml, repromptSsml);
		}

		Timestamp start = (Timestamp) results.get("start").get(0);

		if (numEvents <= MAX_EVENTS) {
			Map<String, Integer> savedEvents = CalendarHelper.extractEventIds(results, numEvents);

			CalendarAttrib.setSessionAttribute(session, CalendarAttrib.RECENTLY_SAID_EVENTS, savedEvents);
			CalendarAttrib.setSessionAttribute(session, CalendarAttrib.STATE_ID, SessionState.USER_HEARD_EVENTS);

			String responsePrefix = "The events ";

			response = newEventListResponse(results, dateRange, responsePrefix);
		} else { // more than MAX_EVENTS
			CalendarAttrib.setSessionAttribute(session, CalendarAttrib.STATE_ID, SessionState.LIST_TOO_LONG);

			String dateSsml = dateRange.getDateSsml();
			String responseSsml = "I was able to find " + numEvents + " different events " +
					dateRange.getRelativeDate(true) +
					". What kind of events would you like to hear about?";
			// TODO: only prompt for categories found in the list
			String repromptSsml = "Would you like to hear about sports, entertainment, " +
				"clubs, lectures, or all of the events?";

			response = newAffirmativeResponse(responseSsml, repromptSsml);
		}

		CalendarAttrib.setSessionAttribute(session, CalendarAttrib.SAVED_DATE, dateRange);

		return response;
	}


	private SpeechletResponse handleNarrowDownIntents(IntentRequest intentReq, Session session) {
		String category;

		CalendarIntent intent = CalendarIntent.valueOf(intentReq);

		switch (intent) {
		case ALL_CATEGORY:
			category = "all";
			break;

		case SPORTS_CATEGORY:
			category = "Athletics";
			break;

		case ARTS_AND_ENTERTAINMENT_CATEGORY:
			category = "Arts and Entertainment";
			break;

		case LECTURES_CATEGORY:
			category = "Lectures and Films";
			break;

		case CLUBS_CATEGORY:
			category = "Club and Student Organizations";
			break;

		default:
			// TODO: Should inform the user what the categories are
			return newTellResponse("Sorry, I'm not quite sure what you meant.", false);
		}

		return handleNarrowDownIntent(intentReq, session, category);
	}


	private SpeechletResponse handleNarrowDownIntent(IntentRequest intentReq, Session session, String category) {
		@SuppressWarnings("unchecked")
		Map<String, Object> dateRangeAttrib =
			(Map<String, Object>) CalendarAttrib.getSessionAttribute(session, CalendarAttrib.SAVED_DATE);

		// This should never happen.
		if (dateRangeAttrib == null)
			return newBadStateResponse("handleNarrowDownIntent");

		DateRange dateRange = new DateRange(dateRangeAttrib);

		// Return the name and the time of all events within that category, or if
		// the query finds that there are no events on the day, Alexa tells the user
		// she has nothing to return.
		Map<String, Vector<Object>> results;

		try {
			PreparedStatement ps;
			int position = 1;

			if (category == "all") {
				String query =
					"SELECT event_id, title, start, location FROM event_info " +
					"    WHERE start >= ?::date AND start < ?::date";
				ps = db.prepareStatement(query);
			} else {
				String query =
					"SELECT event_id, title, start, location FROM given_category(?, ?::date, ?::date)";
				ps = db.prepareStatement(query);
				ps.setString(position++, category);
			}

			ps.setDate(position++, dateRange.getBegin());
			ps.setDate(position, dateRange.getEnd());

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return newInternalErrorResponse();
		}

		if (category.equals("all"))
			category = "";

		int numEvents = results.get("title").size();
		if (numEvents == 0) {
			// There will always be events for "all", or else we wouldn't be here.
			String responseSsml = "I couldn't find any " + category + " events.";

			return newTellResponse(responseSsml, false);
		}

		Map<String, Integer> savedEvents = CalendarHelper.extractEventIds(results, numEvents);

		CalendarAttrib.setSessionAttribute(session, CalendarAttrib.RECENTLY_SAID_EVENTS, savedEvents);
		CalendarAttrib.setSessionAttribute(session, CalendarAttrib.STATE_ID, SessionState.USER_HEARD_EVENTS);

		Timestamp start = (Timestamp) results.get("start").get(0);

		// Format the first part of the response to indicate the category.
		String categoryPrefix = CalendarHelper.randomAffirmative() + ". Here are the " + category + " events that I was able to find. ";

		return dayByDayEventsResponse(results, dateRange, categoryPrefix);
	}


	private SpeechletResponse handleGetFeeDetailIntent(IntentRequest intentReq, Session session) {
		@SuppressWarnings("unchecked")
		Map<String, Integer> savedEvents =
			(HashMap<String, Integer>) CalendarAttrib.getSessionAttribute(session, CalendarAttrib.RECENTLY_SAID_EVENTS);
		if (savedEvents == null)
			return newBadStateResponse("handleGetFeeDetailIntent");

		Set<String> savedEventNames = savedEvents.keySet();
		String eventNameSlotValue;

		if (savedEvents.size() == 1)
			eventNameSlotValue = (String) savedEventNames.toArray()[0];
		else
			eventNameSlotValue = CalendarSlot.getRequestSlotValue(intentReq, CalendarSlot.EVENT_NAME);

		if (eventNameSlotValue == null)
			return newBadSlotResponse("event");

		String eventName = CosineSim.getBestMatch(eventNameSlotValue, savedEventNames);
		Integer eventId = savedEvents.get(eventName);

		Map<String, Vector<Object>> results;

		try {
			String query =
				"SELECT title, general_admission_fee FROM events " +
				"    WHERE event_id = ?";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setInt(1, eventId);

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return newInternalErrorResponse();
		}

		if (results.get("title").size() == 0)
			return newInternalErrorResponse();

		String eventFormat = "General admission for {title} is {general_admission_fee}.";

		String eventSsml = CalendarHelper.formatEventSsml(eventFormat, results);

		return newAffirmativeResponse(eventSsml, "Would you like any more info?");
	}


	private SpeechletResponse handleGetLocationDetailIntent(IntentRequest intentReq, Session session) {
		@SuppressWarnings("unchecked")
		Map<String, Integer> savedEvents =
			(HashMap<String, Integer>) CalendarAttrib.getSessionAttribute(session, CalendarAttrib.RECENTLY_SAID_EVENTS);
		if (savedEvents == null)
			return newBadStateResponse("handleGetLocationDetailIntent");

		Set<String> savedEventNames = savedEvents.keySet();
		String eventNameSlotValue;

		if (savedEvents.size() == 1)
			eventNameSlotValue = (String) savedEventNames.toArray()[0];
		else
			eventNameSlotValue = CalendarSlot.getRequestSlotValue(intentReq, CalendarSlot.EVENT_NAME);

		if (eventNameSlotValue == null)
			return newBadSlotResponse("event");

		String eventName = CosineSim.getBestMatch(eventNameSlotValue, savedEventNames);
		Integer eventId = savedEvents.get(eventName);

		Map<String, Vector<Object>> results;

		try {
			String query =
				"SELECT title, location FROM event_info " +
				"    WHERE event_id = ?";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setInt(1, eventId);

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return newInternalErrorResponse();
		}

		if (results.get("title").size() == 0)
			return newInternalErrorResponse();

		String eventFormat = "{title} is located at {location}.";

		String eventSsml = CalendarHelper.formatEventSsml(eventFormat, results);

		return newAffirmativeResponse(eventSsml, "Would you like to hear anything else?");
	}


	private SpeechletResponse handleGetEndDetailIntent(IntentRequest intentReq, Session session) {
		@SuppressWarnings("unchecked")
		Map<String, Integer> savedEvents =
			(HashMap<String, Integer>) CalendarAttrib.getSessionAttribute(session, CalendarAttrib.RECENTLY_SAID_EVENTS);
		if (savedEvents == null)
			return newBadStateResponse("handleGetEndTimeIntent");

		Set<String> savedEventNames = savedEvents.keySet();
		String eventNameSlotValue;

		if (savedEvents.size() == 1)
			eventNameSlotValue = (String) savedEventNames.toArray()[0];
		else
			eventNameSlotValue = CalendarSlot.getRequestSlotValue(intentReq, CalendarSlot.EVENT_NAME);

		if (eventNameSlotValue == null)
			return newBadSlotResponse("event");

		String eventName = CosineSim.getBestMatch(eventNameSlotValue, savedEventNames);
		Integer eventId = savedEvents.get(eventName);

		Map<String, Vector<Object>> results;

		try {
			String query =
				"SELECT title, \"end\" FROM events " +
				"    WHERE event_id = ?";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setInt(1, eventId);

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return newInternalErrorResponse();
		}

		if (results.get("title").size() == 0)
			return newInternalErrorResponse();

		String eventFormat = "Okay, {title} ends at {end:time}.";
		String eventSsml = CalendarHelper.formatEventSsml(eventFormat, results);

		return newAffirmativeResponse(eventSsml, "Would you like to hear more?");
	}


	/**
	 * Generic response for a list of events on a given date
	 */
	private static SpeechletResponse newEventListResponse(Map<String, Vector<Object>> results,
	                                                      DateRange when, String prefix) {
		String dateSsml = when.getRelativeDate(true);
		String eventFormat = "<s>{title} at {start:time}</s>";
		String eventsSsml = CalendarHelper.listEvents(eventFormat, results);
		String responseSsml = prefix + dateSsml + " are: " + eventsSsml;
		String repromptSsml = "Is there anything you would like to know about those events?";

		return newAffirmativeResponse(responseSsml, repromptSsml);
	}


	/**
	 * Generic response to list events for multiple days
	 *
	 * @param results   The results from a query. There must be start and title columns.
	 * @param when      A dateRange built from results.
	 * @param prefix    An introductory sentence. Example - "Okay, here is what I found."
	 *                  Pass an empty string if there should not be a prefix.
	 * @return          A string with a message such as "<prefix.> On <day> is <event>
	 *                  at <time>, <event2> at <time2>... On <day2> there is...etc.
	 */
	private static SpeechletResponse dayByDayEventsResponse(Map<String, Vector<Object>> results,
	                                                        DateRange when, String prefix) {
		String eventFormat = "<s>{title} at {start:time}</s>";
		String responseSsml = prefix + CalendarHelper.listEventsWithDays(eventFormat, results);
		String repromptSsml = "Is there anything you would like to know about those events?";

		return newAffirmativeResponse(responseSsml, repromptSsml);
	}


	/**
	 * Generic response for when we have no information about the requested item
	 */
	private static SpeechletResponse newNoInfoResponse(String messageSsml) {
		return newFailureResponse(messageSsml, "Did you want any other information?");
	}


	/**
	 * Generic response for when we are missing a needed slot
	 */
	public static SpeechletResponse newBadSlotResponse(String slotName) {
		// FIXME: needs better messages?
		String messageSsml = "Which " + slotName + " are you interested in?";

		return newFailureResponse(messageSsml, "Did you want any other information?");
	}


	/**
	 * Generic affirmative response wrapper
	 *
	 * @param responseSsml the main message to respond with.  SSML markup is allowed, but the string
	 *                     should not include {@code <speak>...</speak>} tags.
	 * @param repromptSsml the message to speak to the user if they do not respond within the timeout
	 *                     window.  SSML markup is allowed, but the string should not include
	 *                     {@code <speak>...</speak>} tags.
	 * @return a new ask response that keeps the session open.  Both the response message and the
	 *         reprompt message get wrapped in {@code <speak>...</speak>} tags.
	 */
	private static SpeechletResponse newAffirmativeResponse(String responseSsml, String repromptSsml) {
		responseSsml = CalendarHelper.replacePartsOfNames(responseSsml);
		return newAskResponse("<speak>" + responseSsml + "</speak>", true,
		                      "<speak>" + repromptSsml + "</speak>", true);
	}


	/**
	 * Generic failure response wrapper
	 *
	 * @param responseSsml the main message to respond with.  SSML markup is allowed, but the string
	 *                     should not include {@code <speak>...</speak>} tags.
	 * @param repromptSsml the message to speak to the user if they do not respond within the timeout
	 *                     window.  SSML markup is allowed, but the string should not include
	 *                     {@code <speak>...</speak>} tags.
	 * @return a new ask response that keeps the session open and prepends "Sorry. " to the front of
	 *         the specified response message.  Both the response message and the reprompt message
	 *         get wrapped in {@code <speak>...</speak>} tags.
	 */
	public static SpeechletResponse newFailureResponse(String responseSsml, String repromptSsml) {
		return newAskResponse("<speak>Sorry. " + responseSsml + "</speak>", true,
		                      "<speak>" + repromptSsml + "</speak>", true);
	}


	/**
	 * Generic response for when we experience an internal error
	 */
	public static SpeechletResponse newInternalErrorResponse() {
		return newTellResponse("Sorry, I'm on break", false);
	}


	/**
	 * Generic response for when we don't know what's going on
	 */
	private SpeechletResponse newBadStateResponse(String msg) {
		System.out.println(msg);
		return newTellResponse("Sorry, I forgot what we were talking about.", false);
	}
}
