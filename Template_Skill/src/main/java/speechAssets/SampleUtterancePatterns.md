These patterns are for use with the Intent Utterance Expander, found here:
https://lab.miguelmota.com/intent-utterance-expander/example/

Documentation for utterances can be found here:
https://developer.amazon.com/public/solutions/alexa/alexa-skills-kit/docs/supported-phrases-to-begin-a-conversation

```
AMAZON.CancelIntent (cancel|forget it|never mind)
AMAZON.HelpIntent I need help
AMAZON.HelpIntent can you help me with something
AMAZON.HelpIntent help ( |me)
AMAZON.NoIntent (no|nope) ( |thank you|thanks)
AMAZON.StopIntent (end|finish|quit|stop)

GetEventsOnDateIntent (what is|what's) ( |happening|going on|coming up|the haps|occurring|scheduled|on the calendar|cool|ahead|destined|the future) (on|for) {date}
NextEventIntent next (thing|event|show|meeting)
NextEventIntent (thing|event|show|meeting) ( |happening) (soon|next)
NextEventIntent (what is|what's) (the next event|coming up) ( |on the calendar)
NextEventIntent something (to do|fun) ( |that's|that is) (soon|coming up)

AllCategoryIntent (all|all of them|everything|give me all of them|every category|all the categories|I want them all|list them all|give me all|I want to hear about all the categories|tell me about all the categories|gotta catch them all)
ArtsAndEntertainmentCategoryIntent ( |tell me about|I want to hear about|lets do|lets hear about|how about) (the arts and entertainment category|the art category|the entertainment category|art|arts and entertainment|shows|performances|cool art stuff)
ClubsCategoryIntent ( |tell me about|I want to hear about|lets do|lets hear about|how about) (the clubs category|clubs|club stuff|club things|club meetings|club gatherings)
LecturesCategoryIntent ( |tell me about|I want to hear about|lets do|lets hear about|how about) (lectures|talks|speeches|colloquiums|teachings|the lecture category|talks and lessons)
SportsCategoryIntent ( |tell me about|I want to hear about|lets do|lets hear about|how about) (sports|athletics) ( |category|stuff|games|events|matches)

GetEndDetailIntent What time (is|does) (it|{eventName}) (over|end|finish|done)
GetEndDetailIntent When (is|does) (it|{eventName}) (end|over|finished|done)
GetFeeDetailIntent ( |What's|what is) (the price for|the price of|it cost for|it cost to attend|the cost to go to|the cost to attend) (it|{eventName})
GetFeeDetailIntent How much (money|moneys|bucks|buckaroos|cash|dough|green|smackaroos|wolfbucks) do tickets cost ( |for {eventName})
GetFeeDetailIntent How (much|expensive) are (tickets|seats) ( |for {eventName})
GetFeeDetailIntent How (much|expensive) is (it|{eventName})
GetFeeDetailIntent How much do (tickets|seats) cost ( |for {eventName})
GetFeeDetailIntent How much does it cost to (go to|attend|see) (it|{eventName})
GetLocationDetailIntent (Where is|Where's) (it|{eventName}) (at|going to be|happening|located|located at|going on|being held|being held at)
GetLocationDetailIntent (Where's|where is) (it|{eventName})
GetLocationDetailIntent Where will (it|{eventName}) be (at|happening|located|located at|going on|held|held at)
```
