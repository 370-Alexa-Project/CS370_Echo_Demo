/* This file is just for testing to make sure that everything is set up so that you can connect
 * to the database. Other than that, it shouldn't be used for anything in the actual Alexa
 * skill's code.
 */

package com.wolfpack.voice;

import com.wolfpack.database.DbConnection;
import java.util.*;

public class temp {
	public static void main(String[] args){
		DbConnection dbc = new DbConnection();
		if(dbc.getCredentials("./resources/dbCredentials.xml"))
			System.out.println("Got the creds.");
		else
			System.out.println("Couldn't get the creds");
		
		dbc.getRemoteConnection();
		Map<String, Vector<String>> m = dbc.runQuery("SELECT code, title FROM mySchema.films ORDER BY title;");
				dbc.printResultMap(m);
		System.out.println("DONE");
	}
}
