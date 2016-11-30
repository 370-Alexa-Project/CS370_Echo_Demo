package com.neong.voice.wolfpack;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import com.wolfpack.database.DbConnection;


public class CalendarHelper {
	public static final String TIME_ZONE = "America/Los_Angeles";

	private static final ZoneId LOCAL_ZONEID = ZoneId.of(TIME_ZONE);
	private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE");
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("????MMdd");
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");


	public static boolean isCategorySupported(final String category) {
		final String[] supportedCategories = {
			"SportsCategoryIntent", "ArtsAndEntertainmentCategoryIntent",
			"LecturesCategoryIntent",  "ClubsCategoryIntent"
		};

		for (final String cat : supportedCategories)
			if (cat == category)
				return true;

		return false;
	}


	/**
	 * Format a message with fields from given events.
	 *
	 * @param format the template for the message.  Special tokens of the form "{field}" are replaced
	 *               with values from {@code events} at the offset {@code index}.  Timestamp fields
	 *               require additional specificity to determine whether to format the value as a time
	 *               or a date, using the extend token forms "{field:time}" and "{field:date}".
	 * @param events the result object from a {@link com.wolfpack.database.DbConnection DbConnection}
	 *               query.  The object must contain all columns referenced by the format string and
	 *               at least {@code index + 1} rows or an exception may be thrown.
	 * @param index  the row offset into {@code events} for the event to refer to.
	 *
	 * @return the message from {@code format} with all "{field}" tokens replaced with the values from
	 *         {@code events} at the row specified by {@code index}.  For example,
	 *         <code>"{title} is at {start:time}."</code> with valid {@code events} and {@code index}
	 *         might return the string {@code "IMS Basketball is at 4:00 PM"}.
	 */
	public static String formatEventSsml(final String format,
	                                     final Map<String, Vector<Object>> events,
	                                     final int index) {
		final int len = format.length();
		final StringBuilder resultBuilder = new StringBuilder(len);
		int i = 0;

		while (i < len) {
			final char c = format.charAt(i++);

			if (c == '{') {
				final StringBuilder fieldBuilder = new StringBuilder();
				char c1;

				// This should throw an exception if the format string is malformed.
				while ((c1 = format.charAt(i++)) != '}')
					fieldBuilder.append(c1);

				final String field = fieldBuilder.toString();
				final String value = formatEventFieldSsml(field, events, index);

				resultBuilder.append(value);
			} else {
				resultBuilder.append(c);
			}
		}

		final String result = resultBuilder.toString();

		return replaceUnspeakables(result);
	}


	public static String formatEventSsml(final String format,
	                                     final Map<String, Vector<Object>> events) {
		return formatEventSsml(format, events, 0);
	}


	public static String formatEventFieldSsml(final String field,
	                                          final Map<String, Vector<Object>> events,
	                                          final int index) {
		final String result;

		switch (field) {
		case "start:date":
		case "end:date": {
			final String fieldName = field.split(":")[0];
			final Timestamp value = (Timestamp) events.get(fieldName).get(index);
			result = formatDateSsml(value);
			break;
		}

		case "start:time":
		case "end:time": {
			final String fieldName = field.split(":")[0];
			final Timestamp value = (Timestamp) events.get(fieldName).get(index);
			result = formatTimeSsml(value);
			break;
		}

		case "location": {
			final String location = (String) events.get(field).get(index);
			result = formatLocationSsml(location);
			break;
		}

		case "student_admission_fee":
		case "general_admission_fee": {
			final String fee = (String) events.get(field).get(index);
			result = formatFeeSsml(fee);
			break;
		}

		default:
			result = (String) events.get(field).get(index);
			break;
		}

		return result;
	}


	public static String replaceUnspeakables(final String ssml) {
		return ssml.replaceAll("&", " and ");
	}


	public static String replacePartsOfNames(final String ssml) {
		String newSsml =  ssml;

		newSsml = newSsml.replaceAll("AOii", "Alpha Omnicron Pi");
		newSsml = newSsml.replaceAll("KDZ", "Kappa Delta Zeta");
		newSsml = newSsml.replaceAll("S@S", "Sundays at Schroeder");
		newSsml = newSsml.replaceAll("GPhi", "Gamma Phi");
		newSsml = newSsml.replaceAll("IMS", "Intermural");
		newSsml = newSsml.replaceAll("MGC", "Multicultural Greek Council");
		newSsml = newSsml.replaceAll("FAASU", "Filipino American Association");
		newSsml = newSsml.replaceAll("AGD", "Alpha Gamma Delta");
		newSsml = newSsml.replaceAll("IFC", "Internfraternity Council");
		newSsml = newSsml.replaceAll("IEW", "International Education Week");
		newSsml = newSsml.replaceAll("ADPi", "Alpha Delta Pi");
		newSsml = newSsml.replaceAll("W.I.T.S.", "World Instructor Training Schools");

		return newSsml;
	}


	public static String formatDateSsml(final Timestamp when) {
		final ZonedDateTime zonedDateTime = when.toInstant().atZone(LOCAL_ZONEID);
		final String day = zonedDateTime.format(DAY_FORMATTER);
		final String date = zonedDateTime.format(DATE_FORMATTER);

		return day + ", <say-as interpret-as=\"date\">" + date + "</say-as>";
	}


	public static String formatTimeSsml(final Timestamp when) {
		final ZonedDateTime zonedDateTime = when.toInstant().atZone(LOCAL_ZONEID);
		final String time = zonedDateTime.format(TIME_FORMATTER);

		return "<say-as interpret-as=\"time\">" + time + "</say-as>";
	}


	public static String formatLocationSsml(String location) {
		if (location == null)
			location = "Sonoma State University";

		return location;
	}


	public static String formatFeeSsml(String fee) {
		if (fee == null)
			fee = "not specified";

		return fee.replace("-", " to ");
	}


	/**
	 * Preconditions:   0 <= index <= the number of events represented in the map - 1
	 *
	 * @param events    A list of events that are about to be printed out. There must
	 *                  be a column named "start" that has timestamp objects.
	 * @param index     The index of the event to see check.
	 * @return          true if the the index is the last event in the map, or if the
	 *                  event that follows the event at index i starts on a different
	 *                  day. Otherwise, the method returns false.
	 */
	public static boolean lastEventOnDay(final Map<String, Vector<Object>> events, int index) {
		// If this is the last of all the events, then it must be the last on this day.
		if (index == events.get("start").size() - 1)
			return true;

		String eventDate = CalendarHelper.formatDateSsml((Timestamp) events.get("start").get(index));
		String nextDate = CalendarHelper.formatDateSsml((Timestamp) events.get("start").get(index + 1));

		return !eventDate.equals(nextDate);
	}


	public static Map<String, Integer> extractEventIds(Map<String, Vector<Object>> events, int numEvents) {
		final Map<String, Integer> savedEvents = new HashMap<String, Integer>(numEvents);

		for (int i = 0; i < numEvents; i++) {
			final String key = (String) events.get("title").get(i);
			final Integer value = (Integer) events.get("event_id").get(i);
			savedEvents.put(key, value);
		}

		return savedEvents;
	}


	public static String randomAffirmative() {
		final String[] affirmatives = { "Okay", "Alright", "Got it", "Sure" };
		Random random = new Random();
		int index = random.nextInt(affirmatives.length);

		return affirmatives[index];
	}
}
