package au.blindmot.process.bmconvertLead;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.adempiere.exceptions.FillMandatoryException;
import org.compiere.model.I_C_ContactActivity;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MLocation;
import org.compiere.model.MOpportunity;
import org.compiere.model.MOrder;
import org.compiere.model.MRequest;
import org.compiere.model.MRequestType;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_C_ContactActivity;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;

import CalendarQuickStart.CalendarQuickstart;


public class BMConvertLead extends SvrProcess{

	private boolean p_createOpportunity = true;
	private boolean p_createSalesOrder = true;
	private boolean p_createCalendarEntry = true;
	private BigDecimal p_opportunityAmt = null;
	private int p_AD_User_ID = 0;
	private Timestamp p_expectedCloseDate = null;
	private Timestamp p_meetingDate = null;
	private int p_C_SalesStage_ID = 0;
	private BigDecimal p_meetingDuration = new BigDecimal(2);
	private String p_Description = null;
	private int p_C_Currency_ID = 0;
	private int p_SalesRep_ID = 0;
	private int mBPLocationID = 0;
	private int cOpportunityID = 0;
	private int cOrderID = 0;
	private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

	@Override
	protected void prepare() {
		
		ProcessInfoParameter[] paras = getParameter();
		for (ProcessInfoParameter para : paras)
		{
			String name = para.getParameterName();
			if ( Util.isEmpty(name) )
				;
			else if ("AD_User_ID".equals(name))
				p_AD_User_ID = para.getParameterAsInt();
			else if ( "CreateOpportunity".equals(name))
				p_createOpportunity  = para.getParameterAsBoolean();
			else if ( "OpportunityAmt".equals(name))
				p_opportunityAmt  = para.getParameterAsBigDecimal();
			else if ("ExpectedCloseDate".equals(name))
				p_expectedCloseDate  = para.getParameterAsTimestamp();
			else if ("C_SalesStage_ID".equals(name))
				p_C_SalesStage_ID  = para.getParameterAsInt();
			else if ("SalesRep_ID".equals(name))
				p_SalesRep_ID   = para.getParameterAsInt();
			else if ("Description".equals(name))
				p_Description = para.getParameterAsString();
			else if ("C_Currency_ID".equals(name))
				p_C_Currency_ID  = para.getParameterAsInt();
			else if ("MeetingDate".equals(name))
				p_meetingDate  = para.getParameterAsTimestamp();
			else if ( "CreateSalesOrder".equals(name))
				p_createSalesOrder = para.getParameterAsBoolean();
			else if ( "CreateCalendarEntry".equals(name))
				p_createCalendarEntry  = para.getParameterAsBoolean();
			else if ( "MeetingDuration".equals(name))
				p_meetingDuration  = para.getParameterAsBigDecimal();
			else 
				
			{
				log.log(Level.WARNING, "Unknown parameter: " + name);
			}
			
			if ( MUser.Table_ID == getTable_ID() )
				p_AD_User_ID  = getRecord_ID();
			
			if (p_C_SalesStage_ID == 0)
			{
				String sql = "SELECT MIN(s.C_SalesStage_ID) FROM C_SalesStage s WHERE s.AD_Client_ID = ? AND s.IsActive = 'Y' " +
						"AND NOT EXISTS (SELECT * FROM C_SalesStage ss WHERE ss.AD_Client_ID=s.AD_Client_ID AND ss.IsActive='Y' AND ss.Value < s.Value)";
				p_C_SalesStage_ID = DB.getSQLValue(get_TrxName(), sql, getAD_Client_ID());
			}
			
		}

	}
	
	@Override
	protected String doIt() throws Exception {
		if (p_AD_User_ID <= 0)
			throw new FillMandatoryException("AD_User_ID");
		
		MUser lead = MUser.get(getCtx(), p_AD_User_ID);
		lead.set_TrxName(get_TrxName());
		if (!lead.isSalesLead() && lead.getC_BPartner_ID() != 0)
			throw new AdempiereUserError("Lead already converted");
		
		MBPartner bp = MBPartner.getTemplate(getCtx(), Env.getAD_Client_ID(getCtx()));
		bp.set_TrxName(get_TrxName());
		if ( !Util.isEmpty(lead.getBPName()) )
			bp.setName(lead.getBPName());
		else
			bp.setName(lead.getName());
		
		bp.saveEx();
		addBufferLog(bp.getC_BPartner_ID(), null, null, "@C_BPartner_ID@ @Created@", MBPartner.Table_ID, bp.getC_BPartner_ID());
		
		lead.setC_BPartner_ID(bp.getC_BPartner_ID());
		
		if (lead.getC_Location_ID() != 0)
		{
			MLocation leadAddress = (MLocation) lead.getC_Location();
			MBPartnerLocation loc = new MBPartnerLocation(bp);
			MLocation address = new MLocation(getCtx(), 0, get_TrxName());
			PO.copyValues(leadAddress, address);
			address.saveEx();
			
			loc.setC_Location_ID(address.getC_Location_ID());
			loc.setPhone(lead.getPhone());
			loc.setPhone2(lead.getPhone2());
			loc.setFax(lead.getFax());
			loc.saveEx();
			mBPLocationID = loc.get_ID();
			
			lead.setC_BPartner_Location_ID(loc.getC_BPartner_Location_ID());
			
			addLog("@C_BPartner_Location_ID@ @Created@");
		}
		
		// company address
		if (lead.getBP_Location_ID() != 0)
		{
			MLocation leadAddress = (MLocation) lead.getBP_Location();
			MBPartnerLocation loc = new MBPartnerLocation(bp);
			MLocation address = new MLocation(getCtx(), 0, get_TrxName());
			PO.copyValues(leadAddress, address);
			address.saveEx();
			
			loc.setC_Location_ID(address.getC_Location_ID());
			loc.saveEx();
			
			addLog("@C_Location_ID@ @Created@");
		}
		
		if (p_createOpportunity )
		{
			MOpportunity op = new MOpportunity(getCtx(), 0, get_TrxName());
			op.setAD_User_ID(lead.getAD_User_ID());
			op.setC_BPartner_ID(bp.getC_BPartner_ID());
			op.setExpectedCloseDate(p_expectedCloseDate != null ? p_expectedCloseDate : new Timestamp(System.currentTimeMillis()));
			op.setOpportunityAmt(p_opportunityAmt != null ? p_opportunityAmt : Env.ZERO);
			
			if ( p_C_SalesStage_ID > 0 )
				op.setC_SalesStage_ID(p_C_SalesStage_ID);
			
			String sql = "SELECT Probability FROM C_SalesStage WHERE C_SalesStage_ID = ?";
			BigDecimal probability = DB.getSQLValueBD(get_TrxName(), sql, p_C_SalesStage_ID);
			op.setProbability(probability != null ? probability : Env.ZERO);
				
			op.setDescription(p_Description);
			
			if ( p_C_Currency_ID > 0 )
				op.setC_Currency_ID(p_C_Currency_ID);
			else
				op.setC_Currency_ID(Env.getContextAsInt(getCtx(), "$C_Currency_ID"));
			
			if (p_SalesRep_ID > 0 )
				op.setSalesRep_ID(p_SalesRep_ID);
			else if ( lead.getSalesRep_ID() > 0 ) 
				op.setSalesRep_ID(lead.getSalesRep_ID());
			else
				op.setSalesRep_ID(Env.getContextAsInt(getCtx(), "#SalesRep_ID"));
			
			op.setC_Campaign_ID(lead.getC_Campaign_ID());
			
			op.saveEx();
			cOpportunityID = op.get_ID();
			
			addBufferLog(op.getC_Opportunity_ID(), null, null, "@C_Opportunity_ID@ @Created@", MOpportunity.Table_ID, op.getC_Opportunity_ID());
			
			List<X_C_ContactActivity> activities = new Query(getCtx(), I_C_ContactActivity.Table_Name, "AD_User_ID=?", get_TrxName())
			.setOnlyActiveRecords(true).setClient_ID()
			.setParameters(p_AD_User_ID)
			.list();
			
			for ( X_C_ContactActivity activity : activities )
			{
				activity.setC_Opportunity_ID(op.getC_Opportunity_ID());
				activity.saveEx();
			}  // for each activity

		}
		
		if(p_createSalesOrder)
		{
			//TODO: Create SO for created BP
			MOrder cOrder = new MOrder(getCtx(),0,get_TrxName());
			//cOrder.setC_DocType_ID(???);
			//MDocType docType = new mDocType(); 
			if(bp!=null) 
				{
					cOrder.setBPartner(bp);
					if(mBPLocationID > 0) 
						{
							cOrder.setC_BPartner_Location_ID(mBPLocationID);
						}
				}
			
			if(p_SalesRep_ID > 0) 
				{
					cOrder.setSalesRep_ID(p_SalesRep_ID);
				}
			cOrder.setAD_User_ID(p_AD_User_ID);
			
			if(cOpportunityID > 0) 
				{
					cOrder.setC_Opportunity_ID(cOpportunityID);
				}
			cOrder.saveEx();
			cOrderID = cOrder.getC_Order_ID();
			addBufferLog(cOrder.getC_Order_ID(), null, null, "@C_Order_ID@ @Created@", MOrder.Table_ID, cOrder.getC_Order_ID());
			
			//TODO: See if this is enough.
		}
		
		if(p_meetingDate != null)
		{
			//TODO: Create mRequest, populate with relevant info including SO if created
			MRequest meeting = new MRequest(getCtx(), p_SalesRep_ID, 0, p_Description, false, get_TrxName());
			MRequestType rt = MRequestType.getDefault(Env.getCtx());
			meeting.setR_RequestType_ID(rt.get_ID());
			meeting.setC_Order_ID(cOrderID);
			meeting.setC_BPartner_ID(bp.getC_BPartner_ID());
			meeting.setC_Campaign_ID(lead.getC_Campaign_ID());
			meeting.setStartDate(p_meetingDate);
			meeting.setDateStartPlan(p_meetingDate);
			final long duration = (p_meetingDuration.multiply(new BigDecimal(3600))).longValue();
			Timestamp endDate = new Timestamp(0);
			endDate.setTime(p_meetingDate.getTime() + duration);
			meeting.setEndTime(endDate);
			meeting.setDateCompletePlan(endDate);
			
		}
		
		if(p_createCalendarEntry)
		{
			if(p_meetingDate != null)
				{
				//TODO: Create Google Calendar entry from Request
				   // Refer to the Java quickstart on how to setup the environment:
		        // https://developers.google.com/calendar/quickstart/java
		        // Change the scope to CalendarScopes.CALENDAR and delete any stored
		        // credentials.
				
				 // Build a new authorized API client service.
		        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
		                .setApplicationName(APPLICATION_NAME)
		                .build();

		        Event event = new Event()
		            .setSummary("Google API test PH Calendar From Idempiere")
		            .setLocation("10 Cedar Grove, Castle Hill, NSW 2154")
		            .setDescription("This event was created from Idempiere. Idempiere now controls your every movement. Idempiere is all powerful. Idempiere will rule the world!! Ha ha ahahahaha!!")
		        	.setId("12345678991");
		      //  event.get

		        DateTime startDateTime = new DateTime("2020-06-08T06:42:00+10:00");
		        EventDateTime start = new EventDateTime()
		            .setDateTime(startDateTime)
		            .setTimeZone("Australia/Sydney");
		        event.setStart(start);

		        DateTime endDateTime = new DateTime("2020-06-08T21:42:00+10:00");
		        EventDateTime end = new EventDateTime()
		            .setDateTime(endDateTime)
		            .setTimeZone("Australia/Sydney");
		        event.setEnd(end);

		       // String[] recurrence = new String[] {"RRULE:FREQ=DAILY;COUNT=2"};
		       // event.setRecurrence(Arrays.asList(recurrence));

		        EventAttendee[] attendees = new EventAttendee[] {
		            new EventAttendee().setEmail("phil@blindmotion.com.au"),
		            //new EventAttendee().setEmail("sbrin@example.com"),
		        };
		        //event.setAttendees(Arrays.asList(attendees));

		        EventReminder[] reminderOverrides = new EventReminder[] {
		            new EventReminder().setMethod("email").setMinutes(24 * 60),
		            new EventReminder().setMethod("popup").setMinutes(10),
		        };
		        Event.Reminders reminders = new Event.Reminders()
		            .setUseDefault(false)
		            .setOverrides(Arrays.asList(reminderOverrides));
		        event.setReminders(reminders);

		        String calendarId = "blindmotionpeter@gmail.com";
		        event = service.events().insert(calendarId, event).execute();
		        System.out.printf("Event created: %s\n", event.getHtmlLink());

			   
			   
			}
		}
		lead.setIsSalesLead(false);
		lead.setLeadStatus(MUser.LEADSTATUS_Converted);
		lead.saveEx();
		
		return "@OK@";
	}
	 /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
	 private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
	        // Load client secrets.
	        InputStream in = CalendarQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
	        if (in == null) {
	            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
	        }
	        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

	        // Build flow and trigger user authorization request.
	        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
	                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
	                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
	                .setAccessType("offline")
	                .build();
	        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
	        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	    
		}	

}

