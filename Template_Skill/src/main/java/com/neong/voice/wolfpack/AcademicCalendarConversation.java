package com.neong.voice.wolfpack;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.neong.voice.model.base.Conversation;
import com.wolfpack.database.DbConnection;
import com.neong.voice.wolfpack.DateRange;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class AcademicCalendarConversation extends Conversation {
	// Intents
	private enum AcademicIntent {
		DAYS_UNTIL_ACADEMIC_EVENT("DaysUntilAcademicEventIntent"),
		IS_THERE_CLASS("IsThereClassIntent"),
		WHEN_IS_ACADEMIC_EVENT("WhenIsAcademicEventIntent");

		private final String value;
		private AcademicIntent(String value) { this.value = value; }
		@Override public String toString() { return value; }

		public static AcademicIntent valueOf(IntentRequest intentReq) {
			// Intent requests are dispatched to us by name,
			// so we always know the intent and name are non-null.
			String intentName = intentReq.getIntent().getName();

			for (AcademicIntent intent : AcademicIntent.values()) {
				if (intentName.equals(intent.value))
					return intent;
			}

			return null;
		}
	}

	// Slots
	private enum AcademicSlot {
		ACADEMIC_EVENT("AcademicEvent"),
		AMAZON_DATE("date");

		private final String value;
		private AcademicSlot(String value) { this.value = value; }
		@Override public String toString() { return value; }

		public static String getRequestSlotValue(IntentRequest intentReq, AcademicSlot slot) {
			String slotName = slot.toString();
			// We always know the intent is non-null.
			Slot intentSlot = intentReq.getIntent().getSlot(slotName);
			if (intentSlot == null)
				return null;
			return intentSlot.getValue();
		}
	}

	private final static Map<String, String> synonyms;

	static {
		Map<String, String> s = new HashMap<String, String>();
		s.put("the fall semester",             "The Fall Semester");
		s.put("labor day",                     "Labor Day");
		s.put("veterans day",                  "Veteran's Day");
		s.put("thanksgiving break",            "Thanksgiving Break");
		s.put("finals",                        "Finals");
		s.put("the spring semester",           "The Spring Semester");
		s.put("Martin Luther king junior day", "Martin Luther King Jr Day");
		s.put("m l k day",                     "Martin Luther King Jr Day");
		s.put("spring break",                  "Spring Break");
		s.put("Cesar Chavez day",              "Cesar Chavez Day");
		s.put("commencement",                  "Commencement");
		s.put("graduation",                    "Commencement");
		s.put("columbus day",                  "Columbus Day");
		s.put("presidents day",                "Presidents Day");
		s.put("winter break",                  "Winter Break");
		s.put("summer break",                  "Summer Break");
		s.put("summer vacation",               "Summer Break");
		synonyms = Collections.unmodifiableMap(s);
	}

	private DbConnection db;

	public AcademicCalendarConversation() {
		super();

		db = new DbConnection("DbCredentials.xml");

		// Add custom intent names for dispatcher use.
		for (AcademicIntent intent : AcademicIntent.values())
			supportedIntentNames.add(intent.toString());
	}

	@Override
	public SpeechletResponse respondToIntentRequest(IntentRequest intentReq, Session session) {
		SpeechletResponse response;
		PreparedStatement ps;
		ObjectMapper mapper = new ObjectMapper();

		if (!db.getRemoteConnection(CalendarHelper.TIME_ZONE))
			return CalendarConversation.newInternalErrorResponse();

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

		AcademicIntent intent = AcademicIntent.valueOf(intentReq);

		switch (intent) {

		case DAYS_UNTIL_ACADEMIC_EVENT:
			response = handleDaysUntilIntent(intentReq, session);
			break;

		case IS_THERE_CLASS:
			response = handleIsThereClassIntent(intentReq, session);
			break;

		case WHEN_IS_ACADEMIC_EVENT:
			response = handleWhenIsIntent(intentReq, session);
			break;

		default:
			response = handleWhenIsIntent(intentReq, session);
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

	private SpeechletResponse handleWhenIsIntent(IntentRequest intentReq, Session session) {
		String givenEvent = AcademicSlot.getRequestSlotValue(intentReq, AcademicSlot.ACADEMIC_EVENT);
		if (givenEvent == null)
			return CalendarConversation.newBadSlotResponse("academic event");

		String eventName = Synonym.getSynonym(givenEvent, synonyms);

		Map<String, Vector<Object>> results;

		try {
			String query = "SELECT title, start, \"end\", description " +
				"FROM events " +
				"WHERE title = ? AND \"end\" > now() " +
				"ORDER BY start ASC " +
				"LIMIT 1";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setString(1, eventName);

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return CalendarConversation.newInternalErrorResponse();
		}

		if (results.get("start").size() == 0)
			return newTellResponse("I couldn't seem to find any information about " + eventName, false);

		String startDateSsml = CalendarHelper.formatDateSsml((Timestamp) results.get("start").get(0));
		String endDateSsml = CalendarHelper.formatDateSsml((Timestamp) results.get("end").get(0));

		String fmt = startDateSsml.equals(endDateSsml) ?
			"{title} is on {start:date}. {description}" :
			"{title} begins on {start:date} and ends on {end:date}. {description}";
		String responseSsml = "<speak>" + CalendarHelper.formatEventSsml(fmt, results) + "</speak>";

		return newTellResponse(responseSsml, true);
	}


	private SpeechletResponse handleDaysUntilIntent(IntentRequest intentReq, Session session) {
		String givenEvent = AcademicSlot.getRequestSlotValue(intentReq, AcademicSlot.ACADEMIC_EVENT);
		if (givenEvent == null)
			return CalendarConversation.newBadSlotResponse("academic event");

		String eventName = Synonym.getSynonym(givenEvent, synonyms);

		Map<String, Vector<Object>> results;

		try {
			String query = "SELECT days_until_event(?)";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setString(1, eventName);

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return CalendarConversation.newInternalErrorResponse();
		}

		if (results.get("days_until_event").size() == 0)
			return newTellResponse("I couldn't seem to find any information about " + eventName, false);

		int numDays = (int) results.get("days_until_event").get(0);
		System.out.println("NUM DAYS: " + numDays);

		return newTellResponse("There are " + numDays + " days until " + eventName, false);
	}


	private SpeechletResponse handleIsThereClassIntent(IntentRequest intentReq, Session session) {
		String givenDate = AcademicSlot.getRequestSlotValue(intentReq, AcademicSlot.AMAZON_DATE);
		if (givenDate == null)
			return CalendarConversation.newBadSlotResponse("date");

		DateRange dateRange = new DateRange(givenDate);

		Map<String, Vector<Object>> results;

		try {
			String query = "SELECT is_school_holiday(?);";

			PreparedStatement ps = db.prepareStatement(query);
			ps.setDate(1, dateRange.getBegin());

			results = DbConnection.executeStatement(ps);
		} catch (SQLException e) {
			System.out.println(e);
			return CalendarConversation.newInternalErrorResponse();
		}

		String response;
System.out.println(results.get("is_school_holiday").get(0).toString());
		if (results.get("is_school_holiday").size() == 0) {
			response = "I couldn't seem to find whether there is class on " +
				dateRange.getDateSsml() + ".";

		} else if (results.get("is_school_holiday").get(0).toString().equals("true")) {
			response = "There will not be any classes on " + dateRange.getDateSsml() + ".";

		} else {
			response = "Classes will be in session on " + dateRange.getDateSsml() + ".";
		}

		return newTellResponse("<speak>" + response + "</speak>", true);
	}
}
